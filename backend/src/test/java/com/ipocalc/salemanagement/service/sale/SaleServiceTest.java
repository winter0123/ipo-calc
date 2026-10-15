package com.ipocalc.salemanagement.service.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.allocationmanagement.service.ownership.AllocationOwnership;
import com.ipocalc.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.ipocalc.salemanagement.controller.sale.dto.SaleResponse;
import com.ipocalc.salemanagement.controller.sale.dto.SaleUpdateRequest;
import com.ipocalc.salemanagement.controller.sale.exception.SaleAllocationExceededException;
import com.ipocalc.salemanagement.controller.sale.exception.SaleNotFoundException;
import com.ipocalc.salemanagement.controller.sale.exception.SaleValidationException;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** {@link SaleService}의 입력 검증·누적 수량 한도·부분 수정 분기(컨트롤러 통합 테스트가 안 타는 경로). */
class SaleServiceTest {

  private final SaleRepository sales = mock(SaleRepository.class);
  private final AllocationRepository allocs = mock(AllocationRepository.class);
  private final AllocationOwnership ownership = mock(AllocationOwnership.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC);
  private final SaleService service =
      new SaleService(sales, allocs, ownership, mock(SecurityUtils.class), clock);

  private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 1, 9, 0);

  private static SaleRegisterRequest reg(Consumer<SaleRegisterRequest> c) {
    SaleRegisterRequest r =
        SaleRegisterRequest.builder().allocationId(5L).quantity(3L).saleDate(AT).build();
    c.accept(r);
    return r;
  }

  private static Allocation alloc(long allocated) {
    return Allocation.builder().id(5L).allocatedQuantity(allocated).build();
  }

  private Sale existingSale() {
    Sale s = Sale.builder().id(1L).allocationId(5L).quantity(4L).amount(100L).fee(7L).build();
    s.setStatus(RecordStatus.ACTIVE);
    when(sales.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(s));
    when(allocs.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(alloc(10)));
    when(sales.save(any(Sale.class))).thenAnswer(i -> i.getArgument(0));
    return s;
  }

  @Test
  void 등록_필수값과_음수는_검증_예외() {
    java.util.List<Consumer<SaleRegisterRequest>> bad =
        java.util.List.of(
          r -> r.setAllocationId(null),
          r -> r.setQuantity(null),
          r -> r.setSaleDate(null),
          r -> r.setQuantity(0L),
          r -> r.setPrice(-1L),
          r -> r.setAmount(-1L),
          r -> r.setFee(-1L),
          r -> r.setTax(-1L),
          r -> r.setOtherCost(-1L));
    for (Consumer<SaleRegisterRequest> c : bad) {
      assertThatThrownBy(() -> service.register(reg(c)))
          .isInstanceOf(SaleValidationException.class);
    }
    assertThatThrownBy(() -> service.register(null)).isInstanceOf(SaleValidationException.class);
    verify(sales, never()).save(any());
  }

  @Test
  void 등록은_기존_합계가_null이면_0으로_보고_배정수량과_같으면_허용_초과면_거부() {
    when(ownership.requireOwnedActive(any(), any())).thenReturn(alloc(3));
    when(sales.sumActiveQuantityByAllocationId(5L)).thenReturn(null);
    when(sales.save(any(Sale.class))).thenAnswer(i -> i.getArgument(0));

    SaleResponse ok = service.register(reg(r -> {}));
    assertThat(ok.getQuantity()).isEqualTo(3L);

    when(sales.sumActiveQuantityByAllocationId(5L)).thenReturn(1L);
    assertThatThrownBy(() -> service.register(reg(r -> {})))
        .isInstanceOf(SaleAllocationExceededException.class);
  }

  @Test
  void 수정_값_검증과_null_요청() {
    java.util.List<Consumer<SaleUpdateRequest>> bad =
        java.util.List.of(
          r -> r.setQuantity(0L),
          r -> r.setPrice(-1L),
          r -> r.setAmount(-1L),
          r -> r.setFee(-1L),
          r -> r.setTax(-1L),
          r -> r.setOtherCost(-1L));
    for (Consumer<SaleUpdateRequest> c : bad) {
      SaleUpdateRequest r = new SaleUpdateRequest();
      c.accept(r);
      assertThatThrownBy(() -> service.update(1L, r)).isInstanceOf(SaleValidationException.class);
    }
    assertThatThrownBy(() -> service.update(1L, null))
        .isInstanceOf(SaleValidationException.class);
  }

  @Test
  void 수정은_제공된_필드만_바꾸고_자기_수량은_합계에서_제외한다() {
    Sale s = existingSale();
    when(sales.sumActiveQuantityByAllocationIdExcludingId(5L, 1L)).thenReturn(null);

    SaleUpdateRequest only = new SaleUpdateRequest();
    only.setTax(9L);
    service.update(1L, only);

    assertThat(s.getTax()).isEqualTo(9L);
    assertThat(s.getQuantity()).isEqualTo(4L);
    assertThat(s.getAmount()).isEqualTo(100L);
    assertThat(s.getFee()).isEqualTo(7L);
    assertThat(s.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 0, 0));

    // 다른 매도 6건 + 수정 4건 = 10 = 배정수량: 허용. 자기 수량(4)을 더하지 않아야 통과한다.
    when(sales.sumActiveQuantityByAllocationIdExcludingId(5L, 1L)).thenReturn(6L);
    SaleUpdateRequest all = new SaleUpdateRequest();
    all.setQuantity(4L);
    all.setPrice(1L);
    all.setAmount(2L);
    all.setSaleDate(AT);
    all.setFee(3L);
    all.setTax(4L);
    all.setOtherCost(5L);
    service.update(1L, all);
    assertThat(s)
        .extracting(
            Sale::getQuantity, Sale::getPrice, Sale::getAmount, Sale::getSaleDate, Sale::getFee,
            Sale::getTax, Sale::getOtherCost)
        .containsExactly(4L, 1L, 2L, AT, 3L, 4L, 5L);
  }

  @Test
  void 수정으로_누적_수량이_배정수량을_넘으면_거부하고_저장하지_않는다() {
    Sale s = existingSale();
    when(sales.sumActiveQuantityByAllocationIdExcludingId(5L, 1L)).thenReturn(6L);
    SaleUpdateRequest r = new SaleUpdateRequest();
    r.setQuantity(5L);

    assertThatThrownBy(() -> service.update(1L, r))
        .isInstanceOf(SaleAllocationExceededException.class);
    // 수량 미제공이면 기존 수량(4)으로 재검증한다: 6 + 4 = 10 허용, 7 + 4 = 11 거부.
    when(sales.sumActiveQuantityByAllocationIdExcludingId(5L, 1L)).thenReturn(7L);
    assertThatThrownBy(() -> service.update(1L, new SaleUpdateRequest()))
        .isInstanceOf(SaleAllocationExceededException.class);
    assertThat(s.getQuantity()).isEqualTo(4L);
    verify(sales, never()).save(any());
  }

  @Test
  void 수정_대상이_없거나_배정이_사라지면_404() {
    when(sales.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(1L, new SaleUpdateRequest()))
        .isInstanceOf(SaleNotFoundException.class);
    assertThatThrownBy(() -> service.cancel(1L)).isInstanceOf(SaleNotFoundException.class);

    existingSale();
    when(allocs.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(1L, new SaleUpdateRequest()))
        .isInstanceOf(SaleNotFoundException.class);
  }

  @Test
  void 취소는_상태와_삭제시각을_바꾸고_물리삭제하지_않는다() {
    Sale s = existingSale();

    service.cancel(1L);

    assertThat(s.getStatus()).isEqualTo(RecordStatus.CANCELLED);
    assertThat(s.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 0, 0));
    verify(sales).save(s);
    verify(sales, never()).delete(any());
  }
}
