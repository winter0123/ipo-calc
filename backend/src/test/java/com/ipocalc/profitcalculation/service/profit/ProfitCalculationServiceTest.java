package com.ipocalc.profitcalculation.service.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.service.ownership.OwnershipGuard;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.profitcalculation.controller.profit.exception.ProfitForbiddenException;
import com.ipocalc.profitcalculation.controller.profit.exception.ProfitNotFoundException;
import com.ipocalc.profitcalculation.controller.profit.exception.ProfitValidationException;
import com.ipocalc.profitcalculation.dto.profit.InvestorProfitDto;
import com.ipocalc.profitcalculation.dto.profit.ProfitDto;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** {@link ProfitCalculationService}의 조회 실패·소유권·기간 필터·대체 가격 분기. */
class ProfitCalculationServiceTest {

  private final SaleRepository sales = mock(SaleRepository.class);
  private final AllocationRepository allocs = mock(AllocationRepository.class);
  private final SubscriptionRepository subs = mock(SubscriptionRepository.class);
  private final IpoRepository ipos = mock(IpoRepository.class);
  private final OwnershipGuard guard = mock(OwnershipGuard.class);
  private final ProfitCalculationService service =
      new ProfitCalculationService(sales, allocs, subs, ipos, guard);

  private static Sale sale(long id, long allocId, LocalDateTime at, Long amount) {
    return Sale.builder()
        .id(id)
        .allocationId(allocId)
        .quantity(10L)
        .amount(amount)
        .fee(100L)
        .tax(50L)
        .otherCost(null)
        .saleDate(at)
        .status(RecordStatus.ACTIVE)
        .build();
  }

  /** 소유권 가드가 통과(owner)하거나 던지도록 stub 한다. */
  @SuppressWarnings("unchecked")
  private void guardAllows() {
    when(guard.requireOwnedInvestor(anyLong(), any(Supplier.class), any(Supplier.class)))
        .thenReturn(Investor.builder().id(1L).accountId(9L).build());
  }

  @SuppressWarnings("unchecked")
  private void guardThrows(boolean notFound) {
    when(guard.requireOwnedInvestor(anyLong(), any(Supplier.class), any(Supplier.class)))
        .thenAnswer(
            inv -> {
              Supplier<RuntimeException> nf = inv.getArgument(1);
              Supplier<RuntimeException> fb = inv.getArgument(2);
              throw (notFound ? nf : fb).get();
            });
  }

  private void givenChain(Allocation a, Subscription s, Ipo ipo) {
    when(sales.findByIdAndDeletedAtIsNull(1L))
        .thenReturn(Optional.of(sale(1, 5, LocalDateTime.of(2026, 10, 1, 9, 0), 2_000L)));
    when(allocs.findById(5L)).thenReturn(Optional.ofNullable(a));
    when(subs.findById(7L)).thenReturn(Optional.ofNullable(s));
    when(ipos.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.ofNullable(ipo));
  }

  private static Allocation alloc(Long price) {
    return Allocation.builder().id(5L).subscriptionId(7L).allocationPrice(price).build();
  }

  private static Subscription subscription() {
    return Subscription.builder().id(7L).investorId(1L).ipoId(3L).build();
  }

  @Test
  void 매도가_없거나_취소면_404() {
    when(sales.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.calculateForSale(1L))
        .isInstanceOf(ProfitNotFoundException.class);

    Sale cancelled = sale(1, 5, LocalDateTime.now(), 1L);
    cancelled.cancel(LocalDateTime.now());
    when(sales.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(cancelled));
    assertThatThrownBy(() -> service.calculateForSale(1L))
        .isInstanceOf(ProfitNotFoundException.class);
    verifyNoInteractions(allocs, guard);
  }

  @Test
  void 배정이나_청약이_없으면_404() {
    givenChain(null, subscription(), null);
    assertThatThrownBy(() -> service.calculateForSale(1L))
        .isInstanceOf(ProfitNotFoundException.class)
        .hasMessageContaining("배정");

    givenChain(alloc(1_000L), null, null);
    assertThatThrownBy(() -> service.calculateForSale(1L))
        .isInstanceOf(ProfitNotFoundException.class)
        .hasMessageContaining("청약");
  }

  @Test
  void 소유권_검증_실패는_404_또는_403() {
    givenChain(alloc(1_000L), subscription(), null);

    guardThrows(true);
    assertThatThrownBy(() -> service.calculateForSale(1L))
        .isInstanceOf(ProfitNotFoundException.class);
    guardThrows(false);
    assertThatThrownBy(() -> service.calculateForSale(1L))
        .isInstanceOf(ProfitForbiddenException.class);
  }

  @Test
  void 배정가가_있으면_공모가를_쓰지_않고_없으면_공모가로_대체한다() {
    guardAllows();
    Ipo ipo = Ipo.builder().id(3L).fixedPrice(500L).ipoName("테스트").build();

    givenChain(alloc(1_000L), subscription(), ipo);
    ProfitDto withAlloc = service.calculateForSale(1L);
    // 취득 10*1000, 세전 2000-10000-100 = -8100, 세후 -8150
    assertThat(withAlloc.getMatchedAcquisitionAmount()).isEqualTo(10_000L);
    assertThat(withAlloc.getPreTaxProfit()).isEqualTo(-8_100L);
    assertThat(withAlloc.getAfterTaxProfit()).isEqualTo(-8_150L);
    assertThat(withAlloc.getSaleOtherCost()).isZero();

    givenChain(alloc(null), subscription(), ipo);
    assertThat(service.calculateForSale(1L).getMatchedAcquisitionAmount()).isEqualTo(5_000L);

    // 공모주를 못 찾으면 대체 가격이 없어 취득가액 0
    givenChain(alloc(null), subscription(), null);
    ProfitDto noIpo = service.calculateForSale(1L);
    assertThat(noIpo.getMatchedAcquisitionAmount()).isZero();
    assertThat(noIpo.getIpoName()).isNull();
    assertThat(noIpo.getListingDate()).isNull();
  }

  @Test
  void 기간의_시작이_종료보다_늦으면_400이고_소유권도_보지_않는다() {
    assertThatThrownBy(
            () ->
                service.calculateForInvestor(
                    1L, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
        .isInstanceOf(ProfitValidationException.class);
    verifyNoInteractions(guard, sales);
  }

  @Test
  void 투자자_소유권_실패는_404_또는_403() {
    guardThrows(true);
    assertThatThrownBy(() -> service.calculateForInvestor(1L))
        .isInstanceOf(ProfitNotFoundException.class);
    guardThrows(false);
    assertThatThrownBy(() -> service.calculateForInvestor(1L))
        .isInstanceOf(ProfitForbiddenException.class);
  }

  @Test
  void 기간은_상장일_기준이고_상장일이_없으면_매도일이며_양끝을_포함한다() {
    guardAllows();
    // 매도1: 10월 상장, 11월 매도 -> 10월. 매도2: 상장일 없음 -> 매도일(11월 5일)
    Sale s1 = sale(11, 5, LocalDateTime.of(2026, 11, 20, 9, 0), 2_000L);
    Sale s2 = sale(12, 6, LocalDateTime.of(2026, 11, 5, 9, 0), 3_000L);
    when(sales.findActiveWithFilters(any(), any(), any(), any())).thenReturn(List.of(s1, s2));
    when(allocs.findAllById(any()))
        .thenReturn(
            List.of(
                alloc(1_000L),
                Allocation.builder().id(6L).subscriptionId(8L).allocationPrice(100L).build()));
    when(subs.findAllById(any()))
        .thenReturn(
            List.of(
                subscription(), Subscription.builder().id(8L).investorId(1L).ipoId(4L).build()));
    when(ipos.findAllById(any()))
        .thenReturn(
            List.of(
                Ipo.builder().id(3L).listingDate(LocalDate.of(2026, 10, 31)).build(),
                Ipo.builder().id(4L).listingDate(null).build()));

    InvestorProfitDto oct =
        service.calculateForInvestor(1L, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31));
    assertThat(oct.getSales()).extracting(ProfitDto::getSaleId).containsExactly(11L);

    InvestorProfitDto nov =
        service.calculateForInvestor(1L, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5));
    assertThat(nov.getSales()).extracting(ProfitDto::getSaleId).containsExactly(12L);

    InvestorProfitDto none = service.calculateForInvestor(1L, LocalDate.of(2027, 1, 1), null);
    assertThat(none.getSales()).isEmpty();
    assertThat(none.getTotalPreTaxProfit()).isZero();

    InvestorProfitDto all = service.calculateForInvestor(1L);
    assertThat(all.getSales()).hasSize(2);
    assertThat(all.getTotalSaleAmount()).isEqualTo(5_000L);
    assertThat(all.getTotalPreTaxProfit())
        .isEqualTo(all.getSales().stream().mapToLong(ProfitDto::getPreTaxProfit).sum());
  }

  @Test
  void 매도금액이_null이면_합계에서_0으로_센다() {
    guardAllows();
    Sale s = sale(11, 5, LocalDateTime.of(2026, 11, 20, 9, 0), null);
    when(sales.findActiveWithFilters(any(), any(), any(), any())).thenReturn(List.of(s));
    when(allocs.findAllById(any())).thenReturn(List.of(alloc(100L)));
    when(subs.findAllById(any())).thenReturn(List.of(subscription()));
    when(ipos.findAllById(any())).thenReturn(List.of());

    InvestorProfitDto dto = service.calculateForInvestor(1L);

    assertThat(dto.getTotalSaleAmount()).isZero();
    assertThat(dto.getSales().get(0).getPreTaxProfit()).isEqualTo(-1_100L);
  }

  @Test
  void 배정이나_청약_기록이_사라진_매도는_404() {
    guardAllows();
    Sale s = sale(11, 5, LocalDateTime.of(2026, 11, 20, 9, 0), 1L);
    when(sales.findActiveWithFilters(any(), any(), any(), any())).thenReturn(List.of(s));
    when(allocs.findAllById(any())).thenReturn(List.of());
    when(subs.findAllById(any())).thenReturn(List.of());
    when(ipos.findAllById(any())).thenReturn(List.of());
    assertThatThrownBy(() -> service.calculateForInvestor(1L))
        .isInstanceOf(ProfitNotFoundException.class)
        .hasMessageContaining("배정");

    when(allocs.findAllById(any())).thenReturn(List.of(alloc(1L)));
    assertThatThrownBy(() -> service.calculateForInvestor(1L))
        .isInstanceOf(ProfitNotFoundException.class)
        .hasMessageContaining("청약");
  }
}
