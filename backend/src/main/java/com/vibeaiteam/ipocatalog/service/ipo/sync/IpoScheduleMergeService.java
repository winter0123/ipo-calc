package com.vibeaiteam.ipocatalog.service.ipo.sync;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.UstockPlusScheduleDates;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ustockplus 일정(상장예정일·배정일·환불일)을 기존 IPO 레코드에 병합하는 서비스.
 *
 * <p>OpenDART로 생성된 IPO 레코드는 stockCode가 null인 상태로 저장된다(신규 상장 회사는 공시 시점에 종목코드가 없기 때문). 이 서비스는
 * ustockplus에서 조회한 종목코드·종목명·일정을 이용해 기존 레코드를 찾아 stockCode와 세 날짜를 채운다.
 *
 * <p>주요 규칙:
 *
 * <ul>
 *   <li>종목코드 우선 매칭: {@code findByStockCodeAndStatusAndDeletedAtIsNull}으로 정확히 1건 매칭되면 해당 레코드를 대상으로 함
 *   <li>회사명 정규화 매칭: 종목코드 매칭 실패 시 {@code
 *       findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull} 후보들 중 정규화한 ipoName이
 *       정규화한 ustockplus stockName과 같은 것이 <b>정확히 1건</b>일 때만 대상으로 함. 0건 또는 2건 이상이면 건너뜀(DEBUG 로그)
 *   <li>수동 등록 보호: syncSource가 null인 레코드는 절대 수정하지 않음 (종목코드 매칭 경우도 동일)
 *   <li>채우기 규칙:
 *       <ul>
 *         <li>allotmentDate, refundDate: ustockplus 값이 null이 아니면 덮어씀
 *         <li>listingDate: 대상의 listingDate가 null일 때만 ustockplus 값으로 채움(이미 값이 있으면 수동 입력으로 간주해 보존).
 *             ustockplus 값이 null이면 해당 필드는 건드리지 않음
 *       </ul>
 *   <li>갱신 시: {@code syncSource="OPENDART+USTOCKPLUS"}, {@code lastSyncedAt=now}, {@code
 *       updatedAt=now}로 설정하고 saveAndFlush. 변경된 게 없으면 저장하지 않음
 *   <li>건별 독립 트랜잭션(TransactionTemplate): 한 종목 예외가 다음 종목에 영향 없음
 *   <li>예외 발생 시 WARN 로그만 남기고 건수에 미포함, 다음 종목으로 계속
 * </ul>
 *
 * <p>이름 정규화 규칙: "(주)", "㈜", "주식회사" 제거, 모든 공백 제거, 소문자화. 예: "(주)진코스텍", "주식회사 진코스텍", "진코스텍" → 모두
 * "진코스텍"으로 정규화됨.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoScheduleMergeService {
  private static final String SYNC_SOURCE_MERGED = "OPENDART+USTOCKPLUS";

  // 정규화용 패턴: "(주)", "㈜", "주식회사"
  private static final Pattern CORP_PREFIX_PATTERN = Pattern.compile("\\(주\\)|㈜|주식회사");
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  private final UstockPlusCalendarClient ustockPlusCalendarClient;
  private final IpoRepository ipoRepository;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  /**
   * 오늘 기준 과거 30일 ~ 미래 59일(총 89일) ustockplus 일정을 조회해 기존 IPO 레코드에 병합한다.
   *
   * @param today 기준일 (보통 {@code LocalDate.now()})
   * @return 병합으로 갱신된 IPO 건수
   */
  public int merge(LocalDate today) {
    LocalDate startDate = today.minusDays(30);
    LocalDate endDate = today.plusDays(59);

    List<UstockPlusScheduleDates> scheduleDates;
    try {
      scheduleDates = ustockPlusCalendarClient.fetchScheduleDates(startDate, endDate);
    } catch (Exception e) {
      log.warn("ustockplus 일정 조회 실패 (빈 리스트로 처리): {}", e.getClass().getSimpleName());
      return 0;
    }

    if (scheduleDates.isEmpty()) {
      log.debug("ustockplus 일정 결과가 비어 있음 (startDate={}, endDate={})", startDate, endDate);
      return 0;
    }

    // stockCode가 null인 자동 동기화 레코드 후보군 미리 조회 (회사명 정규화 매칭용)
    List<Ipo> nullStockCodeCandidates =
        ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            RecordStatus.ACTIVE);

    int mergedCount = 0;
    for (UstockPlusScheduleDates s : scheduleDates) {
      try {
        // 건별 독립 트랜잭션
        boolean merged =
            transactionTemplate.execute(
                status -> {
                  try {
                    return mergeSingle(s, nullStockCodeCandidates);
                  } catch (Exception e) {
                    status.setRollbackOnly();
                    log.warn(
                        "일정 병합 중 예외 발생 (건수 미반영, 계속 진행): stockCode={}, stockName={}, error={}",
                        s.stockCode(),
                        s.stockName(),
                        e.getMessage());
                    return false;
                  }
                });
        if (merged) {
          mergedCount++;
        }
      } catch (Exception e) {
        // TransactionTemplate 실행 자체 예외 (드물지만 방어)
        log.warn(
            "일정 병합 트랜잭션 실행 실패 (건수 미반영, 계속 진행): stockCode={}, stockName={}, error={}",
            s.stockCode(),
            s.stockName(),
            e.getMessage());
      }
    }

    log.info(
        "ustockplus 일정 병합 완료: merged={}, startDate={}, endDate={}",
        mergedCount,
        startDate,
        endDate);
    return mergedCount;
  }

  /** 단일 종목 일정 병합. 병합 여부(true/false)를 반환. */
  private boolean mergeSingle(UstockPlusScheduleDates s, List<Ipo> nullStockCodeCandidates) {
    // (a) 종목코드로 직접 매칭
    Optional<Ipo> byStockCode =
        ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull(
            s.stockCode(), RecordStatus.ACTIVE);
    Ipo target = null;

    if (byStockCode.isPresent()) {
      Ipo found = byStockCode.get();
      if (found.getSyncSource() == null) {
        // 수동 등록 레코드 → 절대 수정하지 않음
        log.debug("종목코드 매칭되었으나 수동 등록 레코드라 건너뜀: stockCode={}", s.stockCode());
        return false;
      }
      target = found;
    } else {
      // (b) 회사명 정규화 매칭 (stockCode가 null인 자동 동기화 레코드 중에서)
      if (s.stockName() == null || s.stockName().isBlank()) {
        log.debug("stockName이 없어 회사명 매칭 불가: stockCode={}", s.stockCode());
        return false;
      }
      String normalizedUstockName = normalizeName(s.stockName());
      List<Ipo> matches =
          nullStockCodeCandidates.stream()
              .filter(
                  ipo ->
                      ipo.getIpoName() != null
                          && normalizeName(ipo.getIpoName()).equals(normalizedUstockName))
              .toList();

      if (matches.size() != 1) {
        // 0건 또는 2건 이상 → 건너뜀
        log.debug(
            "회사명 정규화 매칭 결과가 1건이 아님 (건너뜀): stockCode={}, stockName={}, normalized={}, matches={}",
            s.stockCode(),
            s.stockName(),
            normalizedUstockName,
            matches.size());
        return false;
      }
      target = matches.get(0);
      // stockCode 채우기
      target.setStockCode(s.stockCode());
    }

    // 채우기 및 변경 여부 확인
    boolean changed = applyScheduleDates(target, s);
    if (!changed) {
      return false;
    }

    // 메타데이터 갱신
    target.setSyncSource(SYNC_SOURCE_MERGED);
    LocalDateTime now = LocalDateTime.now(clock);
    target.setLastSyncedAt(now);
    target.setUpdatedAt(now);

    try {
      ipoRepository.saveAndFlush(target);
      log.debug("일정 병합 완료: stockCode={}, ipoName={}", s.stockCode(), target.getIpoName());
      return true;
    } catch (DataIntegrityViolationException e) {
      // stockCode 유니크 제약 위반 등 → 실패로 처리
      log.warn("일정 병합 저장 실패 (중복 키 등): stockCode={}, error={}", s.stockCode(), e.getMessage());
      return false;
    }
  }

  /** ustockplus 일정 값으로 대상 IPO 필드 채우기. 하나라도 변경되면 true 반환. */
  private boolean applyScheduleDates(Ipo target, UstockPlusScheduleDates s) {
    boolean changed = false;

    // allotmentDate: ustockplus 값이 null이 아니면 덮어씀
    if (s.allotmentDate() != null) {
      if (target.getAllotmentDate() == null
          || !target.getAllotmentDate().equals(s.allotmentDate())) {
        target.setAllotmentDate(s.allotmentDate());
        changed = true;
      }
    }

    // refundDate: ustockplus 값이 null이 아니면 덮어씀
    if (s.refundDate() != null) {
      if (target.getRefundDate() == null || !target.getRefundDate().equals(s.refundDate())) {
        target.setRefundDate(s.refundDate());
        changed = true;
      }
    }

    // listingDate: 대상의 listingDate가 null일 때만 ustockplus 값으로 채움
    if (s.listingDate() != null && target.getListingDate() == null) {
      target.setListingDate(s.listingDate());
      changed = true;
    }

    return changed;
  }

  /**
   * 회사명 정규화: "(주)", "㈜", "주식회사" 제거, 모든 공백 제거, 소문자화.
   *
   * @param name 원본 회사명
   * @return 정규화된 이름 (null이면 빈 문자열 반환)
   */
  static String normalizeName(String name) {
    if (name == null) {
      return "";
    }
    String normalized = CORP_PREFIX_PATTERN.matcher(name).replaceAll("");
    normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll("");
    return normalized.toLowerCase();
  }
}
