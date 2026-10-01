package com.vibeaiteam.brokerintegration.service.sync;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.brokerintegration.adapter.BrokerQueryPort;
import com.vibeaiteam.brokerintegration.adapter.kis.KisCredentials;
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
import com.vibeaiteam.brokerintegration.service.matching.ExistingSale;
import com.vibeaiteam.brokerintegration.service.matching.KisTradeProfitMapper;
import com.vibeaiteam.brokerintegration.service.matching.MatchCandidate;
import com.vibeaiteam.brokerintegration.service.matching.MatchResult;
import com.vibeaiteam.brokerintegration.service.token.BrokerTokenService;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleResponse;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.salemanagement.service.sale.SaleService;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증권사 조회 결과로 매도 기록을 채우는 서비스.
 *
 * <p>미리보기는 조회만 하고 저장하지 않는다. 확정은 사용자가 보낸 건을 {@link SaleService#register}로 저장하므로 수동 등록과 같은
 * 검증(소유권·누적 수량 초과)을 거치며, 한 건이라도 실패하면 전체가 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class BrokerSyncService {

  /** KIS 기간별매매손익 조회는 최대 89일이다(E17 SF-2와 같은 제한). */
  private static final int LOOKBACK_DAYS = 89;

  private static final int CANO_LENGTH = 8;
  private static final int ACCOUNT_NUMBER_LENGTH = 10;

  private final ApiCredentialService apiCredentialService;
  private final SecuritiesAccountApiCredentialRepository credentialRepository;
  private final BrokerSecretEncryptor brokerSecretEncryptor;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final BrokerTokenService tokenService;
  private final BrokerQueryPort brokerQueryPort;
  private final SubscriptionRepository subscriptionRepository;
  private final AllocationRepository allocationRepository;
  private final IpoRepository ipoRepository;
  private final SaleRepository saleRepository;
  private final SaleService saleService;
  private final Clock clock;

  /**
   * 최근 89일 증권사 매도 내역을 조회해 우리 배정과 매칭한다. 아무것도 저장하지 않는다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @return 매칭 성공·실패 목록과 종목코드 없는 배정 목록
   */
  @Transactional(readOnly = true)
  public SyncPreviewResponse preview(Long securitiesAccountId) {
    SecuritiesAccount account =
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

    LocalDate today = LocalDate.now(clock);
    List<BrokerSaleSummary> summaries =
        KisTradeProfitMapper.toSaleSummaries(
            brokerQueryPort.queryPeriodTradeProfit(
                securitiesAccountId,
                toKisCredentials(account, credential),
                tokenService.getValidToken(credential),
                today.minusDays(LOOKBACK_DAYS),
                today));

    MatchReport report =
        BrokerTransactionMatcher.match(summaries, buildCandidates(securitiesAccountId));
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
    Set<Long> accountAllocationIds =
        loadAllocations(securitiesAccountId).stream()
            .map(Allocation::getId)
            .collect(Collectors.toSet());
    for (SaleRegisterRequest sale : request.sales()) {
      if (sale == null || !accountAllocationIds.contains(sale.getAllocationId())) {
        throw new ApiCredentialValidationException("이 증권계좌의 배정이 아닌 매도는 확정할 수 없습니다.");
      }
    }
    return request.sales().stream().map(saleService::register).toList();
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

  private KisCredentials toKisCredentials(
      SecuritiesAccount account, SecuritiesAccountApiCredential credential) {
    String digits =
        accountNumberEncryptor.decrypt(account.getAccountNumber()).replaceAll("\\D", "");
    if (digits.length() != ACCOUNT_NUMBER_LENGTH) {
      throw new ApiCredentialValidationException("계좌번호가 8자리+2자리 형식이 아니라 증권사 조회를 할 수 없습니다.");
    }
    return new KisCredentials(
        brokerSecretEncryptor.decrypt(credential.getAppKeyEncrypted()),
        brokerSecretEncryptor.decrypt(credential.getAppSecretEncrypted()),
        digits.substring(0, CANO_LENGTH),
        digits.substring(CANO_LENGTH));
  }

  private List<Allocation> loadAllocations(Long securitiesAccountId) {
    List<Long> subscriptionIds =
        loadSubscriptions(securitiesAccountId).stream().map(Subscription::getId).toList();
    return subscriptionIds.isEmpty()
        ? List.of()
        : allocationRepository.findBySubscriptionIdIn(subscriptionIds).stream()
            .filter(a -> a.getStatus() == RecordStatus.ACTIVE)
            .toList();
  }

  private List<Subscription> loadSubscriptions(Long securitiesAccountId) {
    return subscriptionRepository
        .findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(securitiesAccountId)
        .stream()
        .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
        .toList();
  }

  private List<MatchCandidate> buildCandidates(Long securitiesAccountId) {
    List<Subscription> subscriptions = loadSubscriptions(securitiesAccountId);
    if (subscriptions.isEmpty()) {
      return List.of();
    }
    Map<Long, Subscription> subscriptionById =
        subscriptions.stream().collect(Collectors.toMap(Subscription::getId, s -> s));
    Map<Long, Ipo> ipoById =
        ipoRepository
            .findAllById(subscriptions.stream().map(Subscription::getIpoId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Ipo::getId, i -> i));
    List<Allocation> allocations = loadAllocations(securitiesAccountId);
    Map<Long, List<ExistingSale>> salesByAllocation = new HashMap<>();
    if (!allocations.isEmpty()) {
      for (Sale sale :
          saleRepository.findByAllocationIdInAndStatus(
              allocations.stream().map(Allocation::getId).toList(), RecordStatus.ACTIVE)) {
        salesByAllocation
            .computeIfAbsent(sale.getAllocationId(), k -> new ArrayList<>())
            .add(
                new ExistingSale(
                    sale.getId(),
                    sale.getSaleDate().toLocalDate(),
                    sale.getQuantity(),
                    sale.getAmount() == null ? 0 : sale.getAmount(),
                    sale.getFee() == null ? 0 : sale.getFee(),
                    sale.getTax() == null ? 0 : sale.getTax()));
      }
    }
    return allocations.stream()
        .map(
            a -> {
              Ipo ipo = ipoById.get(subscriptionById.get(a.getSubscriptionId()).getIpoId());
              return new MatchCandidate(
                  a.getId(),
                  ipo == null ? null : ipo.getStockCode(),
                  a.getAllocatedQuantity(),
                  salesByAllocation.getOrDefault(a.getId(), List.of()));
            })
        .toList();
  }
}
