package com.ipocalc.ipocatalog.service.ipo.detail;

import com.ipocalc.ipocatalog.controller.ipo.dto.IpoInsightResponse;
import com.ipocalc.ipocatalog.domain.model.ipo.IpoDetail;
import com.ipocalc.ipocatalog.exception.IpoNotFoundException;
import com.ipocalc.ipocatalog.repository.ipo.IpoDetailRepository;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.detail.dart.DartFilingParser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공모주 상세 정보(DART 공시 스냅샷)를 저장하고 조회한다. */
@Service
@RequiredArgsConstructor
public class IpoDetailService {

  private static final String DART_URL_PREFIX = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";
  private static final Pattern RCEPT_NO = Pattern.compile("\\d{14}");

  private final IpoRepository ipoRepository;
  private final IpoDetailRepository detailRepository;
  private final Clock clock;

  /**
   * 공모주의 상세 정보를 저장한다. 이미 있으면 덮어쓰고 없으면 새로 만든다.
   *
   * @param ipoId 공모주 ID
   * @param snapshot 공시에서 읽은 값
   * @throws IpoNotFoundException 공모주가 없거나 삭제된 경우
   */
  @Transactional
  public void save(Long ipoId, IpoDetailSnapshot snapshot) {
    ipoRepository
        .findByIdAndDeletedAtIsNull(ipoId)
        .orElseThrow(() -> new IpoNotFoundException("IPO를 찾을 수 없습니다."));
    LocalDateTime now = LocalDateTime.now(clock);
    IpoDetail detail =
        detailRepository
            .findById(ipoId)
            .orElseGet(() -> IpoDetail.builder().ipoId(ipoId).createdAt(now).build());
    detail.setSourceRceptNo(snapshot.sourceRceptNo());
    detail.setSourceReportName(snapshot.sourceReportName());
    detail.setParserVersion(DartFilingParser.VERSION);
    detail.setSyncedAt(now);
    detail.setCeoName(snapshot.ceoName());
    detail.setEstablishedOn(snapshot.establishedOn());
    detail.setAddress(snapshot.address());
    detail.setParValue(snapshot.parValue());
    detail.setCapitalStock(snapshot.capitalStock());
    detail.setLargestShareholderName(snapshot.largestShareholderName());
    detail.setLargestShareholderRatio(snapshot.largestShareholderRatio());
    detail.setInstitutionalCompetitionRate(snapshot.institutionalCompetitionRate());
    detail.setLockUpCommitmentRatio(snapshot.lockUpCommitmentRatio());
    detail.setFinancials(orEmpty(snapshot.financials()));
    detail.setFloatSchedule(orEmpty(snapshot.floatSchedule()));
    detail.setUnderwriters(orEmpty(snapshot.underwriters()));
    detail.setUpdatedAt(now);
    detailRepository.save(detail);
  }

  /**
   * 공모주 부가 정보를 조회한다. 저장된 상세가 없으면 null 필드와 빈 배열로 답한다.
   *
   * @param ipoId 공모주 ID
   * @return 부가 정보
   * @throws IpoNotFoundException 공모주가 없거나 삭제된 경우
   */
  @Transactional(readOnly = true)
  public IpoInsightResponse getInsight(Long ipoId) {
    ipoRepository
        .findByIdAndDeletedAtIsNull(ipoId)
        .orElseThrow(() -> new IpoNotFoundException("IPO를 찾을 수 없습니다."));
    return detailRepository
        .findById(ipoId)
        .map(IpoDetailService::toResponse)
        .orElseGet(IpoDetailService::emptyResponse);
  }

  private static IpoInsightResponse emptyResponse() {
    return new IpoInsightResponse(
        null, null, null, null, null, null, null, null, null, null, null, null, List.of(),
        List.of(), List.of());
  }

  private static IpoInsightResponse toResponse(IpoDetail d) {
    String rcept = d.getSourceRceptNo();
    String url =
        rcept != null && RCEPT_NO.matcher(rcept).matches() ? DART_URL_PREFIX + rcept : null;
    return new IpoInsightResponse(
        d.getSyncedAt(),
        d.getSourceReportName(),
        url,
        d.getCeoName(),
        d.getEstablishedOn(),
        d.getAddress(),
        d.getParValue(),
        d.getCapitalStock(),
        d.getLargestShareholderName(),
        d.getLargestShareholderRatio(),
        d.getInstitutionalCompetitionRate(),
        d.getLockUpCommitmentRatio(),
        d.getFinancials(),
        d.getFloatSchedule(),
        d.getUnderwriters());
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }
}
