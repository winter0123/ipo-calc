package com.vibeaiteam.ipocatalog.service.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoUpdateRequest;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.exception.IpoConflictException;
import com.vibeaiteam.ipocatalog.exception.IpoValidationException;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.BusinessDayCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** {@link IpoService} 단위 테스트. 부분 수정 검증과 중복·충돌 분기를 목 저장소로 확인한다. */
class IpoServiceTest {

  private static final LocalDate START = LocalDate.of(2026, 10, 20);
  private static final LocalDate END = LocalDate.of(2026, 10, 21);

  private final IpoRepository repository = mock(IpoRepository.class);
  private final BusinessDayCalculator calculator = mock(BusinessDayCalculator.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private final IpoService service = new IpoService(repository, clock, calculator);

  private Ipo existing(LocalDate start, LocalDate end) {
    Ipo ipo =
        Ipo.builder()
            .id(1L)
            .companyName("기존회사")
            .ipoName("기존공모")
            .stockCode("123456")
            .roundLabel("1차")
            .lowerBandPrice(1_000L)
            .upperBandPrice(2_000L)
            .subscriptionStartDate(start)
            .subscriptionEndDate(end)
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.of(2026, 10, 1, 0, 0))
            .updatedAt(LocalDateTime.of(2026, 10, 1, 0, 0))
            .build();
    when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ipo));
    when(repository.save(any(Ipo.class))).thenAnswer(inv -> inv.getArgument(0));
    return ipo;
  }

  private void assertInvalid(IpoUpdateRequest request, String messagePart) {
    assertThatThrownBy(() -> service.update(1L, request))
        .isInstanceOf(IpoValidationException.class)
        .hasMessageContaining(messagePart);
    verify(repository, never()).save(any(Ipo.class));
  }

  @Test
  void update_emptyRequest_keepsEveryField() {
    Ipo ipo = existing(START, END);

    IpoResponse response = service.update(1L, new IpoUpdateRequest());

    assertThat(response.getCompanyName()).isEqualTo("기존회사");
    assertThat(ipo.getStockCode()).isEqualTo("123456");
    assertThat(ipo.getRoundLabel()).isEqualTo("1차");
    assertThat(ipo.getSubscriptionStartDate()).isEqualTo(START);
    assertThat(ipo.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
  }

  @Test
  void update_blankStockCodeAndRoundLabel_clearsThem() {
    Ipo ipo = existing(START, END);
    when(repository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "기존공모", START, RecordStatus.ACTIVE))
        .thenReturn(Optional.empty());

    service.update(1L, IpoUpdateRequest.builder().stockCode("  ").roundLabel("").build());

    assertThat(ipo.getStockCode()).isNull();
    assertThat(ipo.getRoundLabel()).isNull();
  }

  @Test
  void update_onlyStartDateWhenExistingEndDateMissing_isRejected() {
    existing(null, null);

    assertInvalid(IpoUpdateRequest.builder().subscriptionStartDate(START).build(), "필수");
  }

  @Test
  void update_onlyEndDateWhenExistingStartDateMissing_isRejected() {
    existing(null, null);

    assertInvalid(IpoUpdateRequest.builder().subscriptionEndDate(END).build(), "필수");
  }

  @Test
  void update_endBeforeStartUsingStoredStart_isRejected() {
    existing(START, END);

    assertInvalid(
        IpoUpdateRequest.builder().subscriptionEndDate(START.minusDays(1)).build(),
        "빠를 수 없습니다");
  }

  @Test
  void update_lowerBandAboveStoredUpperBand_isRejected() {
    existing(START, END);

    assertInvalid(IpoUpdateRequest.builder().lowerBandPrice(2_001L).build(), "클 수 없습니다");
  }

  @Test
  void update_upperBandBelowStoredLowerBand_isRejected() {
    existing(START, END);

    assertInvalid(IpoUpdateRequest.builder().upperBandPrice(999L).build(), "클 수 없습니다");
  }

  @Test
  void update_negativePrices_areRejected() {
    existing(START, END);

    assertInvalid(IpoUpdateRequest.builder().lowerBandPrice(-1L).build(), "lowerBandPrice");
    assertInvalid(IpoUpdateRequest.builder().upperBandPrice(-1L).build(), "upperBandPrice");
    assertInvalid(IpoUpdateRequest.builder().fixedPrice(-1L).build(), "fixedPrice");
  }

  @Test
  void update_zeroOfferingShareCount_isRejected() {
    existing(START, END);

    assertInvalid(IpoUpdateRequest.builder().offeringShareCount(0L).build(), "offeringShareCount");
  }

  @Test
  void update_blankOrTooLongNames_areRejected() {
    existing(START, END);

    assertInvalid(IpoUpdateRequest.builder().companyName("  ").build(), "companyName");
    assertInvalid(IpoUpdateRequest.builder().ipoName("가".repeat(256)).build(), "ipoName");
  }

  @Test
  void update_sameStockCodeOnOtherIpo_isConflict_butSelfIsNot() {
    Ipo ipo = existing(START, END);
    Ipo other = Ipo.builder().id(2L).stockCode("999999").build();
    when(repository.findByStockCodeAndStatusAndDeletedAtIsNull("999999", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(other));
    when(repository.findByStockCodeAndStatusAndDeletedAtIsNull("123456", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(ipo));

    assertThatThrownBy(
            () -> service.update(1L, IpoUpdateRequest.builder().stockCode("999999").build()))
        .isInstanceOf(IpoConflictException.class);
    // 자기 자신과의 중복은 충돌이 아니다
    assertThat(service.update(1L, IpoUpdateRequest.builder().stockCode("123456").build()))
        .isNotNull();
  }

  @Test
  void update_databaseUniqueViolation_becomesConflict() {
    existing(START, END);
    when(repository.save(any(Ipo.class))).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.update(1L, new IpoUpdateRequest()))
        .isInstanceOf(IpoConflictException.class);
  }

  @Test
  void register_nullRequestOrBadFields_isRejected() {
    assertThatThrownBy(() -> service.register(null)).isInstanceOf(IpoValidationException.class);

    IpoRegisterRequest ok =
        IpoRegisterRequest.builder()
            .ipoName("공모")
            .subscriptionStartDate(START)
            .subscriptionEndDate(END)
            .build();
    ok.setLowerBandPrice(3_000L);
    ok.setUpperBandPrice(2_000L);
    assertThatThrownBy(() -> service.register(ok)).hasMessageContaining("클 수 없습니다");

    ok.setLowerBandPrice(null);
    ok.setUpperBandPrice(null);
    ok.setSubscriptionEndDate(START.minusDays(1));
    assertThatThrownBy(() -> service.register(ok)).hasMessageContaining("빠를 수 없습니다");

    ok.setSubscriptionEndDate(null);
    assertThatThrownBy(() -> service.register(ok)).hasMessageContaining("subscriptionEndDate");
    ok.setSubscriptionStartDate(null);
    assertThatThrownBy(() -> service.register(ok)).hasMessageContaining("subscriptionStartDate");

    ok.setSubscriptionStartDate(START);
    ok.setSubscriptionEndDate(END);
    ok.setOfferingShareCount(-5L);
    assertThatThrownBy(() -> service.register(ok)).hasMessageContaining("offeringShareCount");
    verify(repository, never()).save(any(Ipo.class));
  }

  @Test
  void register_withoutCompanyName_usesIpoNameAsCompanyName() {
    IpoRegisterRequest request =
        IpoRegisterRequest.builder()
            .ipoName("  테스트공모  ")
            .subscriptionStartDate(START)
            .subscriptionEndDate(END)
            .build();
    when(repository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "테스트공모", START, RecordStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(repository.save(any(Ipo.class))).thenAnswer(inv -> inv.getArgument(0));

    IpoResponse response = service.register(request);

    assertThat(response.getCompanyName()).isEqualTo("테스트공모");
    assertThat(response.getIpoName()).isEqualTo("테스트공모");
  }

  @Test
  void register_withCompanyName_usesProvidedCompanyName() {
    IpoRegisterRequest request =
        IpoRegisterRequest.builder()
            .companyName("  별도회사  ")
            .ipoName("테스트공모")
            .subscriptionStartDate(START)
            .subscriptionEndDate(END)
            .build();
    when(repository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "테스트공모", START, RecordStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(repository.save(any(Ipo.class))).thenAnswer(inv -> inv.getArgument(0));

    IpoResponse response = service.register(request);

    assertThat(response.getCompanyName()).isEqualTo("별도회사");
    assertThat(response.getIpoName()).isEqualTo("테스트공모");
  }

  @Test
  void register_companyNameTooLong_isRejected() {
    IpoRegisterRequest request =
        IpoRegisterRequest.builder()
            .companyName("가".repeat(256))
            .ipoName("공모")
            .subscriptionStartDate(START)
            .subscriptionEndDate(END)
            .build();
    assertThatThrownBy(() -> service.register(request)).hasMessageContaining("companyName");
    verify(repository, never()).save(any(Ipo.class));
  }

  @Test
  void register_uniqueViolationOnSave_becomesConflict() {
    IpoRegisterRequest request =
        IpoRegisterRequest.builder()
            .companyName("회사")
            .ipoName("공모")
            .subscriptionStartDate(START)
            .subscriptionEndDate(END)
            .build();
    when(repository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "공모", START, RecordStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(repository.save(any(Ipo.class))).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.register(request)).isInstanceOf(IpoConflictException.class);
  }

  @Test
  void getById_estimatesRefundDateOnlyWhenNotStoredAndEndDateKnown() {
    Ipo withRefund = existing(START, END);
    withRefund.setRefundDate(LocalDate.of(2026, 10, 23));
    IpoResponse stored = service.getById(1L);
    assertThat(stored.getRefundDate()).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(stored.isRefundDateEstimated()).isFalse();

    withRefund.setRefundDate(null);
    when(calculator.plusBusinessDaysOrNull(END, 2)).thenReturn(LocalDate.of(2026, 10, 23));
    assertThat(service.getById(1L).isRefundDateEstimated()).isTrue();

    withRefund.setSubscriptionEndDate(null);
    IpoResponse none = service.getById(1L);
    assertThat(none.getRefundDate()).isNull();
    assertThat(none.isRefundDateEstimated()).isFalse();
  }
}
