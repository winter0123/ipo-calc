package com.ipocalc.brokerintegration.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link MatchCandidateAssembler}의 활성 필터와 null 방어 분기. */
class MatchCandidateAssemblerTest {

  private final SubscriptionRepository subs = mock(SubscriptionRepository.class);
  private final AllocationRepository allocs = mock(AllocationRepository.class);
  private final IpoRepository ipos = mock(IpoRepository.class);
  private final SaleRepository sales = mock(SaleRepository.class);
  private final MatchCandidateAssembler assembler =
      new MatchCandidateAssembler(subs, allocs, ipos, sales);

  private static Subscription sub(long id, long ipoId, RecordStatus st) {
    return Subscription.builder().id(id).ipoId(ipoId).status(st).build();
  }

  private static Allocation alloc(long id, long subId, long qty, RecordStatus st) {
    return Allocation.builder()
        .id(id)
        .subscriptionId(subId)
        .allocatedQuantity(qty)
        .status(st)
        .build();
  }

  private void givenSubs(Subscription... s) {
    when(subs.findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(7L))
        .thenReturn(List.of(s));
  }

  @Test
  void 활성_청약이_없으면_후보도_배정ID도_비고_배정을_조회하지_않는다() {
    givenSubs(sub(1, 10, RecordStatus.CANCELLED));

    assertThat(assembler.candidates(7L)).isEmpty();
    assertThat(assembler.allocationIds(7L)).isEmpty();
    verifyNoInteractions(allocs, sales);
  }

  @Test
  void 비활성_배정은_제외하고_매도의_null_금액은_0으로_채운다() {
    givenSubs(sub(1, 10, RecordStatus.ACTIVE), sub(2, 11, RecordStatus.INACTIVE));
    Ipo ipo = Ipo.builder().id(10L).stockCode("005930").build();
    when(ipos.findAllById(List.of(10L))).thenReturn(List.of(ipo));
    when(allocs.findBySubscriptionIdIn(List.of(1L)))
        .thenReturn(
            List.of(
                alloc(100, 1, 5, RecordStatus.ACTIVE), alloc(101, 1, 9, RecordStatus.CANCELLED)));
    Sale sale =
        Sale.builder()
            .id(500L)
            .allocationId(100L)
            .quantity(2L)
            .saleDate(LocalDateTime.of(2026, 10, 1, 9, 0))
            .build();
    when(sales.findByAllocationIdInAndStatus(eq(List.of(100L)), eq(RecordStatus.ACTIVE)))
        .thenReturn(List.of(sale));

    List<MatchCandidate> out = assembler.candidates(7L);

    assertThat(out).hasSize(1);
    assertThat(out.get(0).allocationId()).isEqualTo(100L);
    assertThat(out.get(0).stockCode()).isEqualTo("005930");
    assertThat(out.get(0).allocatedQuantity()).isEqualTo(5);
    assertThat(out.get(0).existingSales())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.amount()).isZero();
              assertThat(s.fee()).isZero();
              assertThat(s.tax()).isZero();
              assertThat(s.quantity()).isEqualTo(2);
            });
    assertThat(assembler.allocationIds(7L)).isEqualTo(Set.of(100L));
  }

  @Test
  void 공모주가_없으면_종목코드는_null이고_배정이_없으면_매도를_조회하지_않는다() {
    givenSubs(sub(1, 10, RecordStatus.ACTIVE));
    when(ipos.findAllById(anyList())).thenReturn(List.of());
    when(allocs.findBySubscriptionIdIn(anyList()))
        .thenReturn(List.of(alloc(100, 1, 5, RecordStatus.ACTIVE)));
    when(sales.findByAllocationIdInAndStatus(anyList(), eq(RecordStatus.ACTIVE)))
        .thenReturn(List.of());

    List<MatchCandidate> out = assembler.candidates(7L);

    assertThat(out)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.stockCode()).isNull();
              assertThat(c.existingSales()).isEmpty();
            });

    when(allocs.findBySubscriptionIdIn(anyList())).thenReturn(List.of());
    assertThat(assembler.candidates(7L)).isEmpty();
  }
}
