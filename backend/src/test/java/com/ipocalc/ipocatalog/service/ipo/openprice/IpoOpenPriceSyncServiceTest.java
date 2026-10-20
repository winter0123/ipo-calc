package com.ipocalc.ipocatalog.service.ipo.openprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** {@link IpoOpenPriceSyncService} 테스트. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IpoOpenPriceSyncServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Mock private IpoRepository ipoRepository;
  @Mock private StockPriceClient stockPriceClient;

  @InjectMocks private IpoOpenPriceSyncService service;

  private Clock clock;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-10-16T05:30:00Z"), SEOUL);
    service = new IpoOpenPriceSyncService(ipoRepository, stockPriceClient, clock);
  }

  private Ipo buildIpo(Long id, String stockCode, LocalDate listingDate, Long listingOpenPrice) {
    return Ipo.builder()
        .id(id)
        .companyName("테스트")
        .ipoName("테스트")
        .stockCode(stockCode)
        .fixedPrice(10000L)
        .subscriptionStartDate(listingDate.minusDays(7))
        .subscriptionEndDate(listingDate.minusDays(5))
        .listingDate(listingDate)
        .status(RecordStatus.ACTIVE)
        .listingOpenPrice(listingOpenPrice)
        .build();
  }

  @Test
  void syncOpenTargets_savesOpenPriceWhenPresent() {
    // given
    Ipo target = buildIpo(1L, "123450", LocalDate.of(2026, 10, 10), null);
    when(ipoRepository.findOpenPriceSyncTargets(TODAY)).thenReturn(List.of(target));
    when(stockPriceClient.fetchOpenPrice("123450", LocalDate.of(2026, 10, 10)))
        .thenReturn(Optional.of(27400L));

    // when
    IpoOpenPriceSyncService.SyncResult result = service.syncOpenPrices();

    // then
    verify(ipoRepository).save(target);
    assertThat(result.summary()).contains("targetCount=1").contains("saved=1");
  }

  @Test
  void syncOpenTargets_emptyResponse_doesNotSave() {
    // given
    Ipo target = buildIpo(1L, "123450", LocalDate.of(2026, 10, 10), null);
    when(ipoRepository.findOpenPriceSyncTargets(TODAY)).thenReturn(List.of(target));
    when(stockPriceClient.fetchOpenPrice("123450", LocalDate.of(2026, 10, 10)))
        .thenReturn(Optional.empty());

    // when
    IpoOpenPriceSyncService.SyncResult result = service.syncOpenPrices();

    // then
    verify(ipoRepository, never()).save(any());
    assertThat(result.summary()).contains("empty=1");
  }

  @Test
  void syncOpenTargets_oneFailure_continuesNext() {
    // given
    Ipo target1 = buildIpo(1L, "111111", LocalDate.of(2026, 10, 10), null);
    Ipo target2 = buildIpo(2L, "222222", LocalDate.of(2026, 10, 10), null);
    when(ipoRepository.findOpenPriceSyncTargets(TODAY)).thenReturn(List.of(target1, target2));
    when(stockPriceClient.fetchOpenPrice("111111", LocalDate.of(2026, 10, 10)))
        .thenThrow(new StockPriceClient.StockPriceApiException("test error"));
    when(stockPriceClient.fetchOpenPrice("222222", LocalDate.of(2026, 10, 10)))
        .thenReturn(Optional.of(15000L));

    // when
    IpoOpenPriceSyncService.SyncResult result = service.syncOpenPrices();

    // then
    verify(ipoRepository).save(target2);
    assertThat(result.summary()).contains("saved=1").contains("failed=1");
  }

  @Test
  void syncOpenTargets_missingServiceKey_abortsAll() {
    // given
    Ipo target1 = buildIpo(1L, "111111", LocalDate.of(2026, 10, 10), null);
    Ipo target2 = buildIpo(2L, "222222", LocalDate.of(2026, 10, 10), null);
    when(ipoRepository.findOpenPriceSyncTargets(TODAY)).thenReturn(List.of(target1, target2));
    when(stockPriceClient.fetchOpenPrice(anyString(), any(LocalDate.class)))
        .thenThrow(new IllegalStateException("service key missing"));

    // when
    IpoOpenPriceSyncService.SyncResult result = service.syncOpenPrices();

    // then
    assertThat(result.summary()).contains("aborted=true");
    verify(stockPriceClient, times(1)).fetchOpenPrice(anyString(), any(LocalDate.class));
    verify(ipoRepository, never()).save(any());
  }

  @Test
  void syncOpenTargets_alreadyHasValue_notCalled() {
    // given — 이미 listingOpenPrice가 있는 공모주는 대상 조회에서 제외되므로 repository에서 호출되지 않음
    when(ipoRepository.findOpenPriceSyncTargets(TODAY)).thenReturn(List.of());

    // when
    IpoOpenPriceSyncService.SyncResult result = service.syncOpenPrices();

    // then
    verify(stockPriceClient, never()).fetchOpenPrice(anyString(), any(LocalDate.class));
    assertThat(result.summary()).contains("targetCount=0");
  }
}
