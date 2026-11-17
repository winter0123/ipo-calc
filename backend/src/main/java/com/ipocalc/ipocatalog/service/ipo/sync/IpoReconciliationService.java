package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTermsParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartAuthenticationException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 활성 자동 공모의 원공시 재점검(철회·가격·일정 반영)을 담당한다.
 *
 * <p>04:30 배치와 재기동 자동복구가 이 단계를 공유한다. 상장일 경과·90일 경과 여부와 무관하게 ACTIVE + syncSource가 있는 건을 전부 점검하며,
 * 회사(corp_code) 단위로 묶어 DS001(전체 기간, 모든 페이지) 1세트 + DS006(전체 기간) 1세트만 조회해 회사의 여러 건이 호출을 공유한다.
 *
 * <ul>
 *   <li>철회 확정: DS001의 rm에 '철'이 포함된 원공시를 DS006의 같은 접수번호 상세와 매칭하고, corp_code + 원공시 접수번호 + 청약 시작일
 *       3자(레거시 행은 청약 시작일 유일 매칭)가 일치하는 ACTIVE 건만 {@code withdraw()}한다.
 *   <li>철회가 없으면 최신 유효 지분증권 공시(접수일자·접수번호 기준 최신)로 document.xml 원문에서 검증된 가격·청약/배정/환불 일정을 반영한다.
 *   <li>빈 응답·파싱 실패·API 오류는 '철회점검: ' 접두 실패로 기록하고 철회하지 않는다(일정 누락 ≠ 철회).
 *   <li>인증 오류·020(요청 제한)은 같은 키를 쓰므로 재점검 단계를 즉시 중단하고 aborted를 반환한다. 일반 건별 오류는 격리하고 계속한다.
 *   <li>별도 트랜잭션으로 철회·갱신하며, 한 건의 실패가 다음 건을 막지 않는다.
 * </ul>
 *  */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoReconciliationService {

  /** 전체 기간 조회 시작일 (DS001/DS006 공통). */
  private static final LocalDate FULL_RANGE_START = LocalDate.of(2000, 1, 1);

  private final OpenDartCorpDisclosureClient corpDisclosureClient;
  private final OpenDartEquitySecuritiesClient equitySecuritiesClient;
  private final DartIpoFilingClient dartIpoFilingClient;
  private final DartOfferingTermsParser offeringTermsParser;
  private final IpoRepository ipoRepository;
  private final IpoSyncPersistence ipoSyncPersistence;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  /**
   * 활성 자동 공모 전부의 원공시 상태를 재확인하고 철회를 반영한다(runLock은 호출자 소유).
   *
   * <p>{@code aborted=true}면 인증·020 등으로 단계가 중단된 것이며, 호출자는 이를 전체 실행 중단으로 전파해야 한다(같은 키를 더 소모하지 않음).
   *
   * @return 재점검 집계
   */
  public IpoReconcileResult reconcileActiveOfferings() {
    IpoReconcileResult result = new IpoReconcileResult();
    List<Ipo> targets =
        ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE);
    result.setTotalTargets(targets.size());

    Map<String, List<Ipo>> byCorp = new LinkedHashMap<>();
    for (Ipo row : targets) {
      byCorp.computeIfAbsent(row.getCorpCode(), key -> new ArrayList<>()).add(row);
    }

    for (Map.Entry<String, List<Ipo>> entry : byCorp.entrySet()) {
      if (result.isAborted()) {
        break;
      }
      processCorp(result, entry.getKey(), entry.getValue());
    }
    log.info(
        "재점검 완료: totalTargets={}, succeeded={}, failed={}, withdrawn={}, refreshed={}, aborted={}",
        result.getTotalTargets(),
        result.getSucceeded(),
        result.getFailed(),
        result.getWithdrawn(),
        result.getRefreshed(),
        result.isAborted());
    return result;
  }

  private void processCorp(IpoReconcileResult result, String corpCode, List<Ipo> corpRows) {
    try {
      doProcessCorp(result, corpCode, corpRows);
    } catch (RuntimeException e) {
      // 한 회사의 예상 밖 오류가 전체 재점검을 중단시키지 않도록 격리한다. 인증/020은 안쪽에서 즉시 중단한다
      log.error("재점검 중 예외 (corpCode={}, 클래스만 기록): {}", corpCode, e.getClass().getSimpleName());
      for (Ipo row : corpRows) {
        recordFailure(result, row, "철회점검: 예외: " + e.getClass().getSimpleName());
      }
      result.setFailed(result.getFailed() + corpRows.size());
    }
  }

  private void doProcessCorp(IpoReconcileResult result, String corpCode, List<Ipo> corpRows) {
    if (corpRows.size() > 1) {
      // 동일 회사 ACTIVE 자동 공모 2건 이상: 어느 건을 철회/정정할지 구분할 수 없어 전부 보류한다
      corpRows.forEach(row -> recordFailure(result, row, "철회점검: 동일 회사 ACTIVE 복수 건"));
      corpRows.forEach(row -> result.setFailed(result.getFailed() + 1));
      return;
    }
    Ipo row = corpRows.get(0);
    if (corpCode == null || corpCode.isBlank()) {
      recordFailure(result, row, "철회점검: corp_code 없음");
      result.setFailed(result.getFailed() + 1);
      return;
    }

    List<OpenDartDisclosureItem> disclosures;
    List<OpenDartEquitySecurityDetail> details;
    try {
      LocalDate today = LocalDate.now(clock);
      disclosures = corpDisclosureClient.searchAllByDateRange(corpCode, FULL_RANGE_START, today);
      details = equitySecuritiesClient.getEquitySecurityDetail(corpCode, FULL_RANGE_START, today);
    } catch (OpenDartAuthenticationException e) {
      abort(result, "인증 오류", false);
      log.error("재점검 중 인증 오류로 단계 중단 (corpCode={})", corpCode);
      return;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020)", false);
        log.error("재점검 중 요청 제한 초과로 단계 중단 (corpCode={})", corpCode);
        return;
      }
      recordFailure(result, row, "철회점검: DS 조회 실패 (status=" + e.getStatusCode() + ")");
      result.setFailed(result.getFailed() + 1);
      return;
    } catch (IllegalStateException e) {
      // API 키 미설정 등 설정 오류: 재시도해도 같은 결과이므로 단계 중단으로 기록한다
      abort(result, "API 키 미설정", false);
      log.error("재점검 중 API 키 미설정으로 단계 중단");
      return;
    }

    boolean anyFailure = false;
    for (OpenDartDisclosureItem disclosure : withdrawals(disclosures)) {
      OpenDartEquitySecurityDetail detail = matchDetail(disclosure, details);
      if (detail == null) {
        recordFailure(
            result, row, "철회점검: 원공시 DS006 상세 없음 (rcept_no=" + disclosure.getRceptNo() + ")");
        anyFailure = true;
        continue;
      }
      if (detail.getSubscriptionStartDate() == null) {
        recordFailure(result, row, "철회점검: 청약 시작일 파싱 실패 (rcept_no=" + disclosure.getRceptNo() + ")");
        anyFailure = true;
        continue;
      }
      boolean receiptMatches =
          row.getSourceReceiptNo() == null
              || row.getSourceReceiptNo().equals(disclosure.getRceptNo());
      if (!receiptMatches) {
        // 이 행에 반영된 최신 공시가 다른 공모의 것(예: 재추진 건)이면 철회 대상이 아니다
        if (row.getSubscriptionStartDate().equals(detail.getSubscriptionStartDate())) {
          // 같은 청약일인데 접수번호가 다르면 어느 회차인지 구분할 수 없어 보류한다
          recordFailure(
              result, row, "철회점검: 원공시 구분 불가 (rcept_no=" + disclosure.getRceptNo() + "): 자동 철회 보류");
          anyFailure = true;
        }
        continue;
      }
      if (!row.getSubscriptionStartDate().equals(detail.getSubscriptionStartDate())) {
        recordFailure(
            result, row, "철회점검: 청약 시작일 불일치 (rcept_no=" + disclosure.getRceptNo() + "): 자동 철회 보류");
        anyFailure = true;
        continue;
      }
      if (attemptWithdraw(result, corpCode, row, detail)) {
        // 철회 처리됨: 이 행은 더 이상 ACTIVE가 아니다. 앞선 실패 기록이 있으면 실패로 센다
        result.setWithdrawn(result.getWithdrawn() + 1);
        if (anyFailure) {
          result.setFailed(result.getFailed() + 1);
        } else {
          result.setSucceeded(result.getSucceeded() + 1);
        }
        return;
      }
      anyFailure = true;
    }

    if (anyFailure) {
      result.setFailed(result.getFailed() + 1);
      return;
    }
    if (!hasVerifiableConfirmation(row, disclosures, details)) {
      // 빈 DS001/DS006 응답이거나 대상 공모와 연결되는 유효 공시가 없으면 철회가 없어도
      // 확인되지 않은 상태이므로 실패로 기록하고 다음 실행이 재시도하게 한다
      recordFailure(result, row, "철회점검: 대상 공모 확인 불가 (빈 응답 또는 매칭 없음)");
      result.setFailed(result.getFailed() + 1);
      return;
    }
    // 철회가 없으면 최신 유효 공시로 document.xml 원문에서 검증된 가격·일정을 갱신한다(90일 신규 수집에 안 잡혀도 반영)
    refreshFromLatestFiling(result, corpCode, row, disclosures, details);
    result.setSucceeded(result.getSucceeded() + 1);
  }

  /**
   * 철회가 아닌 지분증권 공시 중 접수일자·접수번호 기준 가장 최신 건으로 document.xml 원문에서
   * 검증된 가격·일정을 갱신한다. 상세 매칭 실패·파싱 실패는 확인 성공을 되돌리지 않고 경고만 남긴다.
   * 가격 표가 없는 최신 문서는 기존 검증 가격을 덮지 않는다.
   */
  private void refreshFromLatestFiling(
      IpoReconcileResult result,
      String corpCode,
      Ipo row,
      List<OpenDartDisclosureItem> disclosures,
      List<OpenDartEquitySecurityDetail> details) {
    OpenDartDisclosureItem filing = selectRefreshFiling(disclosures);
    if (filing == null) {
      return;
    }
    OpenDartEquitySecurityDetail detail = matchDetail(filing, details);
    if (detail == null) {
      log.warn("재점검 갱신: DS006 상세 없음 (corpCode={}, rceptNo={})", corpCode, filing.getRceptNo());
      return;
    }
    if (detail.getSubscriptionStartDate() == null) {
      log.warn(
          "재점검 갱신: 청약 시작일 파싱 실패 (corpCode={}, rceptNo={}, sbd={})",
          corpCode,
          filing.getRceptNo(),
          detail.getSbd());
      return;
    }
    if (detail.isSubscriptionEndUnreadable()) {
      log.warn(
          "재점검 갱신: 청약 종료일 파싱 실패 (corpCode={}, rceptNo={}, sbd={})",
          corpCode,
          filing.getRceptNo(),
          detail.getSbd());
      return;
    }

    // 문서 원문 다운로드 및 가격 파싱
    DartOfferingTerms offeringTerms = null;
    if (filing.getRceptNo() != null) {
      try {
        Optional<String> xmlOpt = dartIpoFilingClient.downloadDocument(filing.getRceptNo());
        if (xmlOpt.isPresent()) {
          String xml = xmlOpt.get();
          if (!xml.isBlank()) {
            offeringTerms = offeringTermsParser.parse(xml, filing.getRceptNo(), filing.isFinalizedReport());
          } else {
            log.warn("재점검: 문서 원문 다운로드 결과 빈 값 (rceptNo={})", filing.getRceptNo());
          }
        } else {
          log.warn("재점검: 문서 원문 다운로드 실패 (빈 응답) (rceptNo={})", filing.getRceptNo());
        }
      } catch (Exception e) {
        log.warn("재점검: 문서 원문 다운로드/파싱 실패 (rceptNo={}): {}", filing.getRceptNo(), e.getClass().getSimpleName());
      }
    }

    attemptRefresh(result, corpCode, row, filing, detail, offeringTerms);
  }

  /** 갱신에 쓸 DS001 공시를 고른다. 지분증권·비철회만 대상으로 하고, 접수일자·접수번호 기준 가장 최신 공시를 선택한다.
   * 발행조건확정 우선순위는 없앤다. 원문 파서가 가격 표를 찾지 못하면 가격 필드를 변경하지 않고 실패를 기록한다. */
  static OpenDartDisclosureItem selectRefreshFiling(List<OpenDartDisclosureItem> disclosures) {
    if (disclosures == null || disclosures.isEmpty()) {
      return null;
    }
    List<OpenDartDisclosureItem> candidates =
        disclosures.stream()
            .filter(OpenDartDisclosureItem::isEquitySecuritiesReport)
            .filter(item -> item.getRm() == null || !item.getRm().contains("철"))
            .toList();
    if (candidates.isEmpty()) {
      return null;
    }
    Comparator<OpenDartDisclosureItem> newestFirst =
        Comparator.comparing(
                OpenDartDisclosureItem::getRceptDt, Comparator.nullsFirst(String::compareTo))
            .thenComparing(
                OpenDartDisclosureItem::getRceptNo, Comparator.nullsFirst(String::compareTo))
            .reversed();
    return candidates.stream().max(newestFirst).orElse(null);
  }

  private void attemptRefresh(
      IpoReconcileResult result,
      String corpCode,
      Ipo row,
      OpenDartDisclosureItem filing,
      OpenDartEquitySecurityDetail detail,
      DartOfferingTerms offeringTerms) {
    try {
      IpoSyncPersistence.Outcome outcome =
          transactionTemplate.execute(
              status ->
                  ipoSyncPersistence.persist(
                      corpCode,
                      Objects.requireNonNullElse(filing.getCorpName(), row.getCompanyName()),
                      detail,
                      filing.isFinalizedReport(),
                      filing.isCorrectionOrFinalized(),
                      offeringTerms));
      if (outcome == IpoSyncPersistence.Outcome.UPDATED) {
        result.setRefreshed(result.getRefreshed() + 1);
        log.debug(
            "재점검 갱신 완료: corpCode={}, rceptNo={}, finalized={}",
            corpCode,
            filing.getRceptNo(),
            filing.isFinalizedReport());
      } else if (outcome == IpoSyncPersistence.Outcome.HELD_UNDETERMINED
          || outcome == IpoSyncPersistence.Outcome.BLOCKED_MULTIPLE) {
        log.warn(
            "재점검 갱신 보류: corpCode={}, rceptNo={}, outcome={}",
            corpCode,
            filing.getRceptNo(),
            outcome);
      }
    } catch (Exception e) {
      log.warn("재점검 갱신 중 예외 (corpCode={}, 클래스만): {}", corpCode, e.getClass().getSimpleName());
    }
  }

  /**
   * 대상 공모와 연결되는 유효한 정정/원공시가 확인되는지 판정한다. 빈 응답만으로는 성공으로 보지 않는다.
   *
   * <ul>
   *   <li>추적 receipt가 있으면 같은 rcept_no의 DS006 상세(청약 시작일 파싱됨)와 DS001의 정정/원공시가 함께 있어야 한다.
   *   <li>레거시 행(추적 receipt 없음)은 청약 시작일이 같은 DS006 상세와 일치하는 DS001 정정/원공시가 있어야 한다.
   *   <li>상세 접수번호의 DS001 항목이 rm에 '철'을 포함하면 확인 실패로 본다(대상 철회 미반영 상태).
   * </ul>
   */
  private boolean hasVerifiableConfirmation(
      Ipo row,
      List<OpenDartDisclosureItem> disclosures,
      List<OpenDartEquitySecurityDetail> details) {
    if (disclosures == null || disclosures.isEmpty()) {
      return false;
    }
    List<OpenDartEquitySecurityDetail> items = details == null ? List.of() : details;
    for (OpenDartEquitySecurityDetail detail : items) {
      if (detail.getRceptNo() == null || detail.getSubscriptionStartDate() == null) {
        continue;
      }
      boolean trackedMatch =
          row.getSourceReceiptNo() != null && row.getSourceReceiptNo().equals(detail.getRceptNo());
      boolean legacyMatch =
          row.getSourceReceiptNo() == null
              && row.getSubscriptionStartDate() != null
              && row.getSubscriptionStartDate().equals(detail.getSubscriptionStartDate());
      if (!trackedMatch && !legacyMatch) {
        continue;
      }
      OpenDartDisclosureItem filing =
          disclosures.stream()
              .filter(item -> detail.getRceptNo().equals(item.getRceptNo()))
              .findFirst()
              .orElse(null);
      if (filing == null) {
        continue;
      }
      boolean withdrawalFiled = filing.getRm() != null && filing.getRm().contains("철");
      if (!withdrawalFiled) {
        return true;
      }
    }
    return false;
  }

  private boolean attemptWithdraw(
      IpoReconcileResult result, String corpCode, Ipo row, OpenDartEquitySecurityDetail detail) {
    try {
      Boolean withdrawn =
          transactionTemplate.execute(status -> ipoSyncPersistence.withdraw(corpCode, detail));
      if (Boolean.TRUE.equals(withdrawn)) {
        return true;
      }
      recordFailure(result, row, "철회점검: 유일 매칭 실패 (rcept_no=" + detail.getRceptNo() + ")");
      return false;
    } catch (Exception e) {
      recordFailure(result, row, "철회점검: 철회 처리 중 예외: " + e.getClass().getSimpleName());
      return false;
    }
  }

  private static List<OpenDartDisclosureItem> withdrawals(List<OpenDartDisclosureItem> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream()
        .filter(item -> item.getRm() != null && item.getRm().contains("철"))
        .toList();
  }

  private static OpenDartEquitySecurityDetail matchDetail(
      OpenDartDisclosureItem disclosure, List<OpenDartEquitySecurityDetail> details) {
    if (details == null || disclosure.getRceptNo() == null) {
      return null;
    }
    return details.stream()
        .filter(d -> disclosure.getRceptNo().equals(d.getRceptNo()))
        .findFirst()
        .orElse(null);
  }

  private static void abort(IpoReconcileResult result, String reason, boolean retryable) {
    result.setAborted(true);
    result.setRetryable(retryable);
    result.setAbortReason(reason);
  }

  private static void recordFailure(IpoReconcileResult result, Ipo row, String reason) {
    result
        .getFailures()
        .add(
            IpoSyncFailure.builder()
                .corpCode(row.getCorpCode())
                .corpName(row.getCompanyName())
                .reason(reason)
                .ipoId(row.getId())
                .build());
  }
}