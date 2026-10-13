package com.vibeaiteam.brokerintegration.service.sync;

import com.vibeaiteam.brokerintegration.adapter.BrokerFetchContext;
import com.vibeaiteam.brokerintegration.adapter.BrokerSaleSource;
import com.vibeaiteam.brokerintegration.controller.sync.dto.SyncConfirmRequest;
import com.vibeaiteam.brokerintegration.controller.sync.dto.SyncPreviewItem;
import com.vibeaiteam.brokerintegration.controller.sync.dto.SyncPreviewResponse;
import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialNotFoundException;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialValidationException;
import com.vibeaiteam.brokerintegration.exception.BrokerConnectionUnavailableException;
import com.vibeaiteam.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.vibeaiteam.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.vibeaiteam.brokerintegration.service.credential.ApiCredentialService;
import com.vibeaiteam.brokerintegration.service.matching.BrokerSaleSummary;
import com.vibeaiteam.brokerintegration.service.matching.BrokerTransactionMatcher;
import com.vibeaiteam.brokerintegration.service.matching.BrokerTransactionMatcher.MatchReport;
import com.vibeaiteam.brokerintegration.service.matching.MatchCandidate;
import com.vibeaiteam.brokerintegration.service.matching.MatchCandidateAssembler;
import com.vibeaiteam.brokerintegration.service.matching.MatchResult;
import com.vibeaiteam.brokerintegration.service.ratelimit.SlidingWindowRateLimiter;
import com.vibeaiteam.brokerintegration.service.token.BrokerTokenService;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleResponse;
import com.vibeaiteam.salemanagement.service.sale.SaleService;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증권사 조회 결과로 매도 기록을 채우는 서비스.
 *
 * <p>미리보기는 조회만 하고 저장하지 않는다. 확정은 사용자가 보낸 건을 {@link SaleService#register}로 저장하므로 수동 등록과 같은 검증(소유권·누적
 * 수량 초과)을 거치며, 한 건이라도 실패하면 전체가 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class BrokerSyncService {

  /** 기간을 주지 않았을 때 조회하는 기간(일). */
  private static final int DEFAULT_LOOKBACK_DAYS = 365;

  /**
   * 한 번에 조회할 수 있는 최대 기간(일). KIS 공식 한도는 문서에서 확인하지 못했고, 2026-10-01 실호출로 365·730·1825·3650일이 모두 성공했다.
   * 확인한 가장 긴 값(10년)을 보수적 상한으로 둔다.
   */
  private static final int MAX_RANGE_DAYS = 3650;

  /** 미리보기는 증권계좌당 10초에 1회, 1시간에 30회까지만 허용한다(KIS 호출·토큰 제한 보호). */
  private static final SlidingWindowRateLimiter.Limit[] PREVIEW_LIMITS = {
    new SlidingWindowRateLimiter.Limit(1, Duration.ofSeconds(10)),
    new SlidingWindowRateLimiter.Limit(30, Duration.ofHours(1))
  };

  private final ApiCredentialService apiCredentialService;
  private final SecuritiesAccountApiCredentialRepository credentialRepository;
  private final BrokerSecretEncryptor brokerSecretEncryptor;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final BrokerTokenService tokenService;
  private final List<BrokerSaleSource> saleSources;
  private final SaleService saleService;
  private final MatchCandidateAssembler candidateAssembler;
  private final Clock clock;
  private final SlidingWindowRateLimiter rateLimiter;

  /**
   * 기본 기간(최근 1년)의 증권사 매도 내역을 조회해 우리 배정과 매칭한다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @return 매칭 성공·실패 목록과 종목코드 없는 배정 목록
   */
  public SyncPreviewResponse preview(Long securitiesAccountId) {
    return preview(securitiesAccountId, null, null);
  }

  /**
   * 증권사 매도 내역을 조회해 우리 배정과 매칭한다. 아무것도 저장하지 않는다.
   *
   * <p>기간을 주지 않으면 오늘까지 최근 1년이고, 시작일이 종료일보다 늦거나 종료일이 오늘 이후이거나 기간이 10년을 넘으면 400이다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @param from 조회 시작일(선택)
   * @param to 조회 종료일(선택)
   * @return 매칭 성공·실패 목록과 종목코드 없는 배정 목록
   */
  // 트랜잭션을 걸지 않는다. 읽기 전용 트랜잭션 안에서는 BrokerTokenService의 토큰 캐시 저장이 반영되지 않아
  // 미리보기마다 토큰을 다시 발급하다 KIS 분당 1회 제한(EGW00133)에 걸린다(2026-10-01 실호출로 확인).
  // 증권사 호출 동안 DB 트랜잭션을 붙잡지 않는 효과도 있다.
  public SyncPreviewResponse preview(Long securitiesAccountId, LocalDate from, LocalDate to) {
    LocalDate today = LocalDate.now(clock);
    LocalDate end = to == null ? today : to;
    LocalDate start = from == null ? end.minusDays(DEFAULT_LOOKBACK_DAYS) : from;
    validateRange(start, end, today);
    final SecuritiesAccount account =
        apiCredentialService.requireOwnedSecuritiesAccount(securitiesAccountId);
    SecuritiesAccountApiCredential credential =
        credentialRepository
            .findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(securitiesAccountId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiCredentialNotFoundException("연결된 브로커 API 인증정보가 없습니다."));
    if (!brokerSecretEncryptor.isConfigured()) {
      throw new BrokerConnectionUnavailableException("브로커 API 연동이 설정되지 않았습니다.");
    }

    BrokerSaleSource source = findSource(credential.getApiProvider());
    if (ChronoUnit.DAYS.between(start, end) > source.maxRangeDays()) {
      if (from != null) {
        throw new ApiCredentialValidationException(source.rangeLimitMessage());
      }
      start = end.minusDays(source.maxRangeDays());
    }

    rateLimiter.acquire("preview:" + securitiesAccountId, PREVIEW_LIMITS);
    List<MatchCandidate> candidates = candidateAssembler.candidates(securitiesAccountId);
    Set<String> stockCodes =
        candidates.stream()
            .map(MatchCandidate::stockCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<BrokerSaleSummary> summaries =
        source.fetchSales(
            new BrokerFetchContext(
                securitiesAccountId,
                brokerSecretEncryptor.decrypt(credential.getAppKeyEncrypted()),
                brokerSecretEncryptor.decrypt(credential.getAppSecretEncrypted()),
                accountNumberEncryptor.decrypt(account.getAccountNumber()).replaceAll("\\D", ""),
                tokenService.getValidToken(credential),
                stockCodes),
            start,
            end);

    MatchReport report = BrokerTransactionMatcher.match(summaries, candidates);
    List<SyncPreviewItem> matched = new ArrayList<>();
    List<SyncPreviewItem> unmatched = new ArrayList<>();
    for (MatchResult result : report.results()) {
      (result.isMatched() ? matched : unmatched).add(toItem(result));
    }
    return new SyncPreviewResponse(matched, unmatched, report.candidatesWithoutStockCode());
  }

  /**
   * 사용자가 확정한 매도를 기존 매도 등록 로직으로 저장한다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @param request 확정할 매도 목록(미리보기 값을 고친 것이어도 된다)
   * @return 저장된 매도 목록
   */
  @Transactional
  public List<SaleResponse> confirm(Long securitiesAccountId, SyncConfirmRequest request) {
    apiCredentialService.requireOwnedSecuritiesAccount(securitiesAccountId);
    if (request == null || request.sales() == null || request.sales().isEmpty()) {
      throw new ApiCredentialValidationException("확정할 매도 목록이 비어 있습니다.");
    }
    Set<Long> accountAllocationIds = candidateAssembler.allocationIds(securitiesAccountId);
    for (SaleRegisterRequest sale : request.sales()) {
      if (sale == null || !accountAllocationIds.contains(sale.getAllocationId())) {
        throw new ApiCredentialValidationException("이 증권계좌의 배정이 아닌 매도는 확정할 수 없습니다.");
      }
    }
    return request.sales().stream().map(saleService::register).toList();
  }

  private static void validateRange(LocalDate start, LocalDate end, LocalDate today) {
    if (end.isAfter(today)) {
      throw new ApiCredentialValidationException("조회 종료일은 오늘 이후일 수 없습니다.");
    }
    if (start.isAfter(end)) {
      throw new ApiCredentialValidationException("조회 시작일은 종료일보다 늦을 수 없습니다.");
    }
    if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
      throw new ApiCredentialValidationException("조회 기간은 최대 10년까지입니다.");
    }
  }

  private SyncPreviewItem toItem(MatchResult result) {
    BrokerSaleSummary s = result.summary();
    // ponytail: 단가는 금액÷수량 반올림 근사값이다. 증권사 평균단가(sll_pric)를 쓰려면 매퍼가 가중평균을 합산해야 한다.
    long price = Math.round((double) s.amount() / s.quantity());
    return new SyncPreviewItem(
        s.tradeDate(),
        s.stockCode(),
        s.quantity(),
        s.amount(),
        price,
        s.fee(),
        s.tax(),
        result.allocationId(),
        result.reason());
  }

  private BrokerSaleSource findSource(String provider) {
    return saleSources.stream()
        .filter(s -> s.provider().equals(provider))
        .findFirst()
        .orElseThrow(() -> new ApiCredentialValidationException("지원하지 않는 증권사 연동입니다."));
  }
}
