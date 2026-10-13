package com.vibeaiteam.brokerintegration.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.brokerintegration.adapter.BrokerFetchContext;
import com.vibeaiteam.brokerintegration.adapter.BrokerQueryPort;
import com.vibeaiteam.brokerintegration.adapter.BrokerSaleSource;
import com.vibeaiteam.brokerintegration.adapter.kis.KisAccessToken;
import com.vibeaiteam.brokerintegration.adapter.kis.KisCredentials;
import com.vibeaiteam.brokerintegration.adapter.kis.KisSaleSource;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitDetail;
import com.vibeaiteam.brokerintegration.controller.sync.dto.SyncConfirmRequest;
import com.vibeaiteam.brokerintegration.controller.sync.dto.SyncPreviewResponse;
import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialValidationException;
import com.vibeaiteam.brokerintegration.exception.RateLimitExceededException;
import com.vibeaiteam.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.vibeaiteam.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.vibeaiteam.brokerintegration.service.credential.ApiCredentialService;
import com.vibeaiteam.brokerintegration.service.matching.MatchCandidateAssembler;
import com.vibeaiteam.brokerintegration.service.matching.UnmatchedReason;
import com.vibeaiteam.brokerintegration.service.ratelimit.SlidingWindowRateLimiter;
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
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증권사 동기화 미리보기·확정 서비스 단위 테스트.
 *
 * <p>AC 인용: docs/breakdown/broker-readonly-api-pilot.md (SF-5) - AC-1840: 미리보기는 저장하지 않는다 - AC-1841:
 * 확정 후에만 매도가 만들어진다 - AC-1842: 사용자가 고친 값이 그대로 저장된다 - AC-1843: 기존 매도 등록 로직(SaleService)을 그대로 호출한다.
 */
class BrokerSyncServiceTest {

  private static final Long ACCOUNT_ID = 5L;
  private static final Long ALLOCATION_ID = 30L;
  private static final String STOCK_CODE = "0123A4";
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

  private final ApiCredentialService apiCredentialService = mock(ApiCredentialService.class);
  private final SecuritiesAccountApiCredentialRepository credentialRepository =
      mock(SecuritiesAccountApiCredentialRepository.class);
  private final BrokerTokenService tokenService = mock(BrokerTokenService.class);
  private final BrokerQueryPort port = mock(BrokerQueryPort.class);
  private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
  private final AllocationRepository allocationRepository = mock(AllocationRepository.class);
  private final IpoRepository ipoRepository = mock(IpoRepository.class);
  private final SaleRepository saleRepository = mock(SaleRepository.class);
  private final SaleService saleService = mock(SaleService.class);

  private BrokerSyncService service;
  private BrokerSecretEncryptor secretEncryptor;
  private AccountNumberEncryptor accountEncryptor;

  private BrokerSyncService newService(List<BrokerSaleSource> sources) {
    return new BrokerSyncService(
        apiCredentialService,
        credentialRepository,
        secretEncryptor,
        accountEncryptor,
        tokenService,
        sources,
        saleService,
        new MatchCandidateAssembler(
            subscriptionRepository, allocationRepository, ipoRepository, saleRepository),
        Clock.fixed(
            TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")),
        new SlidingWindowRateLimiter(Clock.systemDefaultZone()));
  }

  @BeforeEach
  void setUp() {
    secretEncryptor = new BrokerSecretEncryptor(newKey());
    accountEncryptor = new AccountNumberEncryptor(newKey());
    service = newService(List.of(new KisSaleSource(port)));

    when(apiCredentialService.requireOwnedSecuritiesAccount(ACCOUNT_ID))
        .thenReturn(
            SecuritiesAccount.builder()
                .id(ACCOUNT_ID)
                .accountNumber(accountEncryptor.encrypt("12345678-01"))
                .build());
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            ACCOUNT_ID))
        .thenReturn(
            List.of(
                SecuritiesAccountApiCredential.builder()
                    .id(1L)
                    .securitiesAccountId(ACCOUNT_ID)
                    .apiProvider("KIS")
                    .appKeyEncrypted(secretEncryptor.encrypt("dummy-key"))
                    .appSecretEncrypted(secretEncryptor.encrypt("dummy-secret"))
                    .build()));
    when(tokenService.getValidToken(any(SecuritiesAccountApiCredential.class)))
        .thenReturn(new KisAccessToken("dummy-token", LocalDateTime.of(2026, 10, 2, 0, 0)));
    when(subscriptionRepository.findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            ACCOUNT_ID))
        .thenReturn(
            List.of(
                Subscription.builder()
                    .id(20L)
                    .securitiesAccountId(ACCOUNT_ID)
                    .ipoId(10L)
                    .status(RecordStatus.ACTIVE)
                    .build()));
    when(ipoRepository.findAllById(anyList()))
        .thenReturn(List.of(Ipo.builder().id(10L).stockCode(STOCK_CODE).build()));
    when(allocationRepository.findBySubscriptionIdIn(anyList()))
        .thenReturn(
            List.of(
                Allocation.builder()
                    .id(ALLOCATION_ID)
                    .subscriptionId(20L)
                    .allocatedQuantity(10L)
                    .status(RecordStatus.ACTIVE)
                    .build()));
    when(saleRepository.findByAllocationIdInAndStatus(anyList(), any())).thenReturn(List.of());
  }

  private String newKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private static KisPeriodTradeProfitDetail sellRow(String date, String pdno) {
    return new KisPeriodTradeProfitDetail(
        date, pdno, "더미종목", "매도", "0", "0", "10000", "10", "100000", "5000", "5.0", "150", "230");
  }

  private void stubBrokerRows(KisPeriodTradeProfitDetail... rows) {
    when(port.queryPeriodTradeProfit(any(), any(KisCredentials.class), any(), any(), any()))
        .thenReturn(new KisPeriodTradeProfit(List.of(rows), null));
  }

  @Test
  void previewIsNotTransactionalSoTokenCacheSaveIsCommitted() throws Exception {
    // 읽기 전용 트랜잭션 안에서는 토큰 캐시 저장이 버려져 미리보기마다 토큰을 재발급한다(KIS 분당 1회 제한).
    var preview = BrokerSyncService.class.getMethod("preview", Long.class);

    assertThat(preview.getAnnotation(Transactional.class)).isNull();
    assertThat(BrokerSyncService.class.getAnnotation(Transactional.class)).isNull();
  }

  @Test
  void previewMatchesAlphanumericStockCodeAndNeverSaves() {
    stubBrokerRows(sellRow("20260930", STOCK_CODE), sellRow("20260929", "999999"));

    SyncPreviewResponse response = service.preview(ACCOUNT_ID);

    assertThat(response.matched()).hasSize(1);
    assertThat(response.matched().get(0).allocationId()).isEqualTo(ALLOCATION_ID);
    assertThat(response.matched().get(0).price()).isEqualTo(10_000L);
    assertThat(response.matched().get(0).fee()).isEqualTo(150L);
    assertThat(response.unmatched()).hasSize(1);
    assertThat(response.unmatched().get(0).reason())
        .isEqualTo(UnmatchedReason.NO_ALLOCATION_FOR_STOCK);
    verifyNoInteractions(saleService);
    verify(saleRepository, never()).save(any());
  }

  @Test
  void previewDefaultsToTheLastYearEndingToday() {
    stubBrokerRows();

    service.preview(ACCOUNT_ID);

    verify(port)
        .queryPeriodTradeProfit(
            any(), any(KisCredentials.class), any(), eq(TODAY.minusDays(365)), eq(TODAY));
  }

  @Test
  void previewUsesTheRequestedRange() {
    stubBrokerRows();

    service.preview(ACCOUNT_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));

    verify(port)
        .queryPeriodTradeProfit(
            any(),
            any(KisCredentials.class),
            any(),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 6, 30)));
  }

  @Test
  void previewRejectsInvalidRangesWithoutCallingTheBroker() {
    assertThrows(
        ApiCredentialValidationException.class,
        () -> service.preview(ACCOUNT_ID, TODAY, TODAY.minusDays(1)));
    assertThrows(
        ApiCredentialValidationException.class,
        () -> service.preview(ACCOUNT_ID, TODAY.minusDays(10), TODAY.plusDays(1)));
    assertThrows(
        ApiCredentialValidationException.class,
        () -> service.preview(ACCOUNT_ID, TODAY.minusDays(3651), TODAY));
    verifyNoInteractions(port);
  }

  @Test
  void previewIsRateLimitedPerSecuritiesAccountAndSkipsTheBrokerWhenRejected() {
    stubBrokerRows();
    service.preview(ACCOUNT_ID);

    assertThrows(RateLimitExceededException.class, () -> service.preview(ACCOUNT_ID));
    verify(port, org.mockito.Mockito.times(1))
        .queryPeriodTradeProfit(any(), any(KisCredentials.class), any(), any(), any());
  }

  /** provider별 소스 선택을 확인하는 가짜 소스. 받은 입력을 기록하고 정해진 결과를 돌려준다. */
  private static final class FakeSource implements BrokerSaleSource {
    private final String provider;
    private final int maxRangeDays;
    private BrokerFetchContext lastContext;
    private LocalDate lastFrom;

    FakeSource(String provider, int maxRangeDays) {
      this.provider = provider;
      this.maxRangeDays = maxRangeDays;
    }

    @Override
    public String provider() {
      return provider;
    }

    @Override
    public int maxRangeDays() {
      return maxRangeDays;
    }

    @Override
    public String rangeLimitMessage() {
      return "한도 초과";
    }

    @Override
    public List<com.vibeaiteam.brokerintegration.service.matching.BrokerSaleSummary> fetchSales(
        BrokerFetchContext context, LocalDate from, LocalDate to) {
      lastContext = context;
      lastFrom = from;
      return List.of();
    }
  }

  private void useProvider(String provider) {
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            ACCOUNT_ID))
        .thenReturn(
            List.of(
                SecuritiesAccountApiCredential.builder()
                    .id(1L)
                    .securitiesAccountId(ACCOUNT_ID)
                    .apiProvider(provider)
                    .appKeyEncrypted(secretEncryptor.encrypt("dummy-key"))
                    .appSecretEncrypted(secretEncryptor.encrypt("dummy-secret"))
                    .build()));
  }

  @Test
  void previewPicksTheSourceByCredentialProviderAndPassesStockCodes() {
    FakeSource kiwoom = new FakeSource("KIWOOM", 3650);
    FakeSource ls = new FakeSource("LS", 30);
    service = newService(List.of(new KisSaleSource(port), kiwoom, ls));
    useProvider("KIWOOM");

    service.preview(ACCOUNT_ID);

    assertThat(kiwoom.lastContext).isNotNull();
    assertThat(kiwoom.lastContext.stockCodes()).containsExactly(STOCK_CODE);
    assertThat(kiwoom.lastContext.accountNumber()).isEqualTo("1234567801");
    assertThat(ls.lastContext).isNull();
    verifyNoInteractions(port);
  }

  @Test
  void previewRejectsAnUnsupportedProvider() {
    service = newService(List.of(new KisSaleSource(port)));
    useProvider("NOPE");

    assertThrows(ApiCredentialValidationException.class, () -> service.preview(ACCOUNT_ID));
    verifyNoInteractions(port);
  }

  @Test
  void previewUsesTheSourceRangeLimit() {
    FakeSource ls = new FakeSource("LS", 30);
    service = newService(List.of(ls));
    useProvider("LS");

    // 기간을 주지 않으면 소스 한도까지만 줄여서 조회한다.
    service.preview(ACCOUNT_ID);
    assertThat(ls.lastFrom).isEqualTo(TODAY.minusDays(30));

    // 직접 고른 기간이 한도(31일)를 넘으면 400이다.
    ApiCredentialValidationException e =
        assertThrows(
            ApiCredentialValidationException.class,
            () -> service.preview(ACCOUNT_ID, TODAY.minusDays(31), TODAY));
    assertThat(e.getMessage()).isEqualTo("한도 초과");
  }

  @Test
  void confirmSavesOnlyThroughSaleServiceWithUserEditedValues() {
    SaleRegisterRequest edited =
        SaleRegisterRequest.builder()
            .allocationId(ALLOCATION_ID)
            .quantity(10L)
            .price(10_100L)
            .amount(101_000L)
            .saleDate(LocalDateTime.of(2026, 9, 30, 0, 0))
            .fee(150L)
            .tax(230L)
            .build();
    SaleResponse saved = mock(SaleResponse.class);
    when(saleService.register(edited)).thenReturn(saved);

    List<SaleResponse> result =
        service.confirm(ACCOUNT_ID, new SyncConfirmRequest(List.of(edited)));

    assertThat(result).containsExactly(saved);
    verify(saleService).register(edited);
  }

  @Test
  void confirmRejectsAllocationOfAnotherSecuritiesAccount() {
    SaleRegisterRequest foreign = SaleRegisterRequest.builder().allocationId(999L).build();

    assertThrows(
        ApiCredentialValidationException.class,
        () -> service.confirm(ACCOUNT_ID, new SyncConfirmRequest(List.of(foreign))));
    verifyNoInteractions(saleService);
  }

  @Test
  void confirmRejectsEmptyList() {
    assertThrows(
        ApiCredentialValidationException.class,
        () -> service.confirm(ACCOUNT_ID, new SyncConfirmRequest(List.of())));
  }

  @Test
  void previewClassifiesAlreadyRegisteredSale() {
    when(saleRepository.findByAllocationIdInAndStatus(anyList(), any()))
        .thenReturn(
            List.of(
                Sale.builder()
                    .id(1L)
                    .allocationId(ALLOCATION_ID)
                    .quantity(10L)
                    .amount(100_000L)
                    .fee(150L)
                    .tax(230L)
                    .saleDate(LocalDateTime.of(2026, 9, 30, 0, 0))
                    .build()));
    stubBrokerRows(sellRow("20260930", STOCK_CODE));

    SyncPreviewResponse response = service.preview(ACCOUNT_ID);

    assertThat(response.matched()).isEmpty();
    assertThat(response.unmatched().get(0).reason()).isEqualTo(UnmatchedReason.ALREADY_REGISTERED);
  }
}
