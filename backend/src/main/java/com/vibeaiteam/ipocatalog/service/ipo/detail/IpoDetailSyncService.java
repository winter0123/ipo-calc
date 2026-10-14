package com.vibeaiteam.ipocatalog.service.ipo.detail;

import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.domain.model.ipo.IpoDetail;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoDetailRepository;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.detail.dart.DartFilingParser;
import com.vibeaiteam.ipocatalog.service.ipo.detail.dart.ParsedFiling;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.EquityOffering;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.FilingRef;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartCompanyOverview;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공모주 상세 정보를 금융감독원 DART에서 가져와 {@code ipo_details}에 저장한다.
 *
 * <p>공모주마다 최신 투자설명서(없으면 증권신고서) 원문을 파싱하고, 기업개황(대표이사·설립일·주소)과 지분증권 주요정보(액면가·인수단)를
 * 합친다. 이미 같은 공시(접수번호)를 같은 파서 버전으로 저장했으면 원문을 다시 내려받지 않는다.
 * 대상은 DART 고유번호가 있고 상장 전이거나 상장 후 30일 이내인 공모주다.
 */
@Service
@Slf4j
public class IpoDetailSyncService {

  private static final int LISTED_WITHIN_DAYS = 30;

  private final IpoRepository ipoRepository;
  private final IpoDetailRepository detailRepository;
  private final IpoDetailService detailService;
  private final DartIpoFilingClient filingClient;
  private final OpenDartCorpDisclosureClient disclosureClient;
  private final Clock clock;
  private final DartFilingParser parser = new DartFilingParser();

  /**
   * 생성자.
   *
   * @param ipoRepository 공모주 저장소
   * @param detailRepository 상세 정보 저장소
   * @param detailService 상세 정보 저장 서비스
   * @param filingClient DART 공모 공시 클라이언트
   * @param disclosureClient DART 기업개황 클라이언트
   * @param clock 오늘 날짜 기준
   */
  public IpoDetailSyncService(
      IpoRepository ipoRepository,
      IpoDetailRepository detailRepository,
      IpoDetailService detailService,
      DartIpoFilingClient filingClient,
      OpenDartCorpDisclosureClient disclosureClient,
      Clock clock) {
    this.ipoRepository = ipoRepository;
    this.detailRepository = detailRepository;
    this.detailService = detailService;
    this.filingClient = filingClient;
    this.disclosureClient = disclosureClient;
    this.clock = clock;
  }

  /**
   * 대상 공모주 전체를 동기화한다. 한 건이 실패해도 나머지는 계속한다.
   *
   * @return 새로 저장(갱신)한 공모주 수
   */
  public int syncRecent() {
    LocalDate today = LocalDate.now(clock);
    int saved = 0;
    for (Ipo ipo : ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()) {
      if (!isTarget(ipo, today)) {
        continue;
      }
      try {
        if (syncOne(ipo, today)) {
          saved++;
        }
      } catch (RuntimeException e) {
        // 외부 호출 예외 메시지에 키가 든 URL이 섞일 수 있어 클래스명만 남긴다.
        log.warn("공모주 상세 동기화 실패: ipoId={}, {}", ipo.getId(), e.getClass().getSimpleName());
      }
    }
    log.info("공모주 상세 동기화 완료: saved={}", saved);
    return saved;
  }

  static boolean isTarget(Ipo ipo, LocalDate today) {
    return ipo.getCorpCode() != null
        && (ipo.getListingDate() == null
            || !ipo.getListingDate().isBefore(today.minusDays(LISTED_WITHIN_DAYS)));
  }

  /**
   * 공모주 하나를 동기화한다.
   *
   * @param ipo 공모주
   * @param today 오늘
   * @return 저장했으면 true, 공시가 없거나 이미 같은 공시로 저장돼 있으면 false
   */
  boolean syncOne(Ipo ipo, LocalDate today) {
    String corpCode = ipo.getCorpCode();
    Optional<FilingRef> filing = filingClient.findLatestOfferingDocument(corpCode, today);
    if (filing.isEmpty()) {
      return false;
    }
    String rceptNo = filing.get().rceptNo();
    Optional<IpoDetail> existing = detailRepository.findById(ipo.getId());
    boolean unchanged =
        existing
            .filter(d -> rceptNo.equals(d.getSourceRceptNo()))
            .filter(d -> d.getParserVersion() >= DartFilingParser.VERSION)
            .isPresent();
    if (unchanged) {
      return false;
    }
    // 원문을 못 받았으면 빈 상세를 저장하지 않고 다음 주기에 다시 시도한다.
    Optional<String> document = filingClient.downloadDocument(rceptNo);
    if (document.isEmpty()) {
      return false;
    }
    ParsedFiling parsed = parser.parse(document.get());
    OpenDartCompanyOverview company = disclosureClient.getCompanyOverview(corpCode).orElse(null);
    EquityOffering offering = filingClient.fetchEquityOffering(corpCode, today).orElse(null);
    IpoDetailSnapshot snapshot = toSnapshot(filing.get(), parsed, company, offering);
    detailService.save(ipo.getId(), keepStoredWhenMissing(snapshot, company, offering, existing));
    return true;
  }

  // 기업개황·지분증권 조회가 일시적으로 비었으면 그 부분은 기존 저장값을 유지한다.
  private static IpoDetailSnapshot keepStoredWhenMissing(
      IpoDetailSnapshot s,
      OpenDartCompanyOverview company,
      EquityOffering offering,
      Optional<IpoDetail> existing) {
    if (existing.isEmpty() || (company != null && offering != null)) {
      return s;
    }
    IpoDetail old = existing.get();
    boolean keepCompany = company == null;
    boolean keepOffering = offering == null;
    return new IpoDetailSnapshot(
        s.sourceRceptNo(),
        s.sourceReportName(),
        keepCompany ? old.getCeoName() : s.ceoName(),
        keepCompany ? old.getEstablishedOn() : s.establishedOn(),
        keepCompany ? old.getAddress() : s.address(),
        keepOffering ? old.getParValue() : s.parValue(),
        s.capitalStock(),
        s.largestShareholderName(),
        s.largestShareholderRatio(),
        s.institutionalCompetitionRate(),
        s.lockUpCommitmentRatio(),
        s.financials(),
        s.floatSchedule(),
        keepOffering ? old.getUnderwriters() : s.underwriters());
  }

  static IpoDetailSnapshot toSnapshot(
      FilingRef filing,
      ParsedFiling parsed,
      OpenDartCompanyOverview company,
      EquityOffering offering) {
    List<IpoDetailSnapshot.Financial> financials =
        parsed.financials().stream()
            .map(
                f ->
                    new IpoDetailSnapshot.Financial(
                        f.period(), f.revenue(), f.operatingProfit(), f.netIncome()))
            .toList();
    List<IpoDetailSnapshot.FloatStep> floats =
        parsed.floatSchedule().stream()
            .map(s -> new IpoDetailSnapshot.FloatStep(s.label(), s.shares(), s.ratio()))
            .toList();
    List<IpoDetailSnapshot.Underwriter> underwriters =
        offering == null
            ? List.of()
            : offering.underwriters().stream()
                .map(u -> new IpoDetailSnapshot.Underwriter(u.name(), u.role(), u.shares()))
                .toList();
    return new IpoDetailSnapshot(
        filing.rceptNo(),
        filing.reportName(),
        company == null ? null : blankToNull(company.ceoName()),
        company == null ? null : dartDate(company.establishedOn()),
        company == null ? null : blankToNull(company.address()),
        offering == null ? null : offering.parValue(),
        parsed.capitalStock(),
        parsed.largestShareholderName(),
        parsed.largestShareholderRatio(),
        parsed.competitionRate(),
        parsed.lockUpCommitmentRatio(),
        financials,
        floats,
        underwriters);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static LocalDate dartDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim(), DateTimeFormatter.BASIC_ISO_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
