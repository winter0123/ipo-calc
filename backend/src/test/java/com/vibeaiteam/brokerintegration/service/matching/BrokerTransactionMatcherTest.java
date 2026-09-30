package com.vibeaiteam.brokerintegration.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vibeaiteam.brokerintegration.service.matching.BrokerTransactionMatcher.MatchReport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrokerTransactionMatcherTest {

  private static final LocalDate TRADE_DATE_2026_09_29 = LocalDate.of(2026, 9, 29);
  private static final LocalDate TRADE_DATE_2026_09_30 = LocalDate.of(2026, 9, 30);
  private static final LocalDate TRADE_DATE_2026_10_01 = LocalDate.of(2026, 10, 1);
  private static final LocalDate TRADE_DATE_2026_10_02 = LocalDate.of(2026, 10, 2);

  @Nested
  @DisplayName("AC-1830: 종목코드·수량 일치, 기존 Sale 없음 → 매칭 성공, allocationId 일치")
  class Ac1830Tests {

    @Test
    @DisplayName("종목코드와 수량이 일치하고 기존 Sale이 없으면 매칭 성공")
    void matchWhenStockCodeAndQuantityMatchAndNoExistingSales() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      MatchCandidate candidate =
          new MatchCandidate(
              1L, "005930", 150, List.of()); // allocatedQuantity=150, existingSales empty

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isTrue();
      assertThat(result.allocationId()).isEqualTo(1L);
      assertThat(result.reason()).isNull();
      assertThat(report.candidatesWithoutStockCode()).isEmpty();
    }
  }

  @Nested
  @DisplayName(
      "AC-1831: 후보 stockCode null → candidatesWithoutStockCode 에 담기고 예외 없음")
  class Ac1831Tests {

    @Test
    @DisplayName(
        "stockCode가 null인 후보는 candidatesWithoutStockCode로 가고, 해당 종목 요약은 NO_ALLOCATION_FOR_STOCK")
    void nullStockCodeCandidateGoesToCandidatesWithoutStockCode() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      MatchCandidate candidateWithNullStockCode = new MatchCandidate(1L, null, 150, List.of());

      // When
      MatchReport report =
          BrokerTransactionMatcher.match(List.of(summary), List.of(candidateWithNullStockCode));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.NO_ALLOCATION_FOR_STOCK);
      assertThat(result.allocationId()).isNull();
      assertThat(report.candidatesWithoutStockCode()).containsExactly(1L);
    }
  }

  @Nested
  @DisplayName("AC-1832: 같은 날 우리 Sale 합계(수량·금액·수수료·세금)가 브로커와 정확히 같음 → ALREADY_REGISTERED")
  class Ac1832Tests {

    @Test
    @DisplayName("같은 날의 Sale 합계가 브로커와 정확히 일치하면 ALREADY_REGISTERED")
    void sameDateSalesExactlyMatchBroker() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      ExistingSale existingSale =
          new ExistingSale(1L, TRADE_DATE_2026_09_30, 100, 1000000, 5000, 1000);

      MatchCandidate candidate = new MatchCandidate(1L, "005930", 150, List.of(existingSale));

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.ALREADY_REGISTERED);
      assertThat(result.allocationId()).isEqualTo(1L); // allocationId는 채워짐
      assertThat(report.candidatesWithoutStockCode()).isEmpty();
    }

    @Test
    @DisplayName("같은 날의 여러 Sale 합계가 브로커와 정확히 일치하면 ALREADY_REGISTERED")
    void multipleSameDateSalesExactlyMatchBroker() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      ExistingSale existingSale1 =
          new ExistingSale(1L, TRADE_DATE_2026_09_30, 60, 600000, 3000, 600);
      ExistingSale existingSale2 =
          new ExistingSale(2L, TRADE_DATE_2026_09_30, 40, 400000, 2000, 400);

      MatchCandidate candidate =
          new MatchCandidate(1L, "005930", 150, List.of(existingSale1, existingSale2));

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.ALREADY_REGISTERED);
      assertThat(result.allocationId()).isEqualTo(1L);
      assertThat(report.candidatesWithoutStockCode()).isEmpty();
    }
  }

  @Nested
  @DisplayName(
      "분할 매도: 합이 같으면 ALREADY_REGISTERED, 하나라도 다르면 SPLIT_SALE_MISMATCH")
  class SplitSaleTests {

    @Test
    @DisplayName("같은 날 우리 Sale 2건의 합이 브로커와 정확히 같으면 ALREADY_REGISTERED")
    void splitSalesSumExactlyMatchesBroker() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      ExistingSale existingSale1 =
          new ExistingSale(1L, TRADE_DATE_2026_09_30, 40, 400000, 2000, 400);
      ExistingSale existingSale2 =
          new ExistingSale(2L, TRADE_DATE_2026_09_30, 60, 600000, 3000, 600);

      MatchCandidate candidate =
          new MatchCandidate(1L, "005930", 150, List.of(existingSale1, existingSale2));

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("같은 날 우리 Sale 2건의 합이 수량에서 다르면 SPLIT_SALE_MISMATCH")
    void splitSalesSumDifferentQuantity() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      ExistingSale existingSale1 =
          new ExistingSale(1L, TRADE_DATE_2026_09_30, 30, 300000, 1500, 300);
      ExistingSale existingSale2 =
          new ExistingSale(2L, TRADE_DATE_2026_09_30, 60, 600000, 3000, 600); // 총 90주 (부족)

      MatchCandidate candidate =
          new MatchCandidate(1L, "005930", 150, List.of(existingSale1, existingSale2));

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.SPLIT_SALE_MISMATCH);
    }

    @Test
    @DisplayName("같은 날 우리 Sale 2건의 합이 금액에서 다르면 SPLIT_SALE_MISMATCH")
    void splitSalesSumDifferentAmount() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      ExistingSale existingSale1 =
          new ExistingSale(1L, TRADE_DATE_2026_09_30, 50, 500000, 2500, 500);
      ExistingSale existingSale2 =
          new ExistingSale(2L, TRADE_DATE_2026_09_30, 50, 400000, 2500, 500); // 금액 차이

      MatchCandidate candidate =
          new MatchCandidate(1L, "005930", 150, List.of(existingSale1, existingSale2));

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.SPLIT_SALE_MISMATCH);
    }

    @Test
    @DisplayName("수수료만 다르면 SPLIT_SALE_MISMATCH")
    void onlyFeeDifferent() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      ExistingSale existingSale =
          new ExistingSale(1L, TRADE_DATE_2026_09_30, 100, 1000000, 3000, 1000); // 수수료 차이

      MatchCandidate candidate = new MatchCandidate(1L, "005930", 150, List.of(existingSale));

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.SPLIT_SALE_MISMATCH);
    }

    @Test
    @DisplayName("세금만 다르면 SPLIT_SALE_MISMATCH")
    void onlyTaxDifferent() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      ExistingSale existingSale =
          new ExistingSale(1L, TRADE_DATE_2026_09_30, 100, 1000000, 5000, 800); // 세금 차이

      MatchCandidate candidate = new MatchCandidate(1L, "005930", 150, List.of(existingSale));

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.SPLIT_SALE_MISMATCH);
    }
  }

  @Nested
  @DisplayName("같은 입력으로 2회 호출해도 결과 동일(equals), 입력 리스트 불변")
  class IdempotencyAndImmutabilityTests {

    @Test
    @DisplayName("같은 입력으로 2회 호출해도 결과가 동일함")
    void sameInputProducesSameOutput() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      MatchCandidate candidate = new MatchCandidate(1L, "005930", 150, List.of());

      List<BrokerSaleSummary> summaries = List.of(summary);
      List<MatchCandidate> candidates = List.of(candidate);

      // When
      MatchReport report1 = BrokerTransactionMatcher.match(summaries, candidates);
      MatchReport report2 = BrokerTransactionMatcher.match(summaries, candidates);

      // Then
      assertThat(report1).isEqualTo(report2);
      // 입력 리스트가 변경되지 않았는지 확인
      assertThat(summaries).hasSize(1);
      assertThat(candidates).hasSize(1);
    }

    @Test
    @DisplayName("입력 리스트가 변경되지 않음 (불변성)")
    void inputListsAreNotModified() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      MatchCandidate candidate = new MatchCandidate(1L, "005930", 150, List.of());

      List<BrokerSaleSummary> summaries = new java.util.ArrayList<>(List.of(summary));
      List<MatchCandidate> candidates = new java.util.ArrayList<>(List.of(candidate));
      int initialSummariesSize = summaries.size();
      int initialCandidatesSize = candidates.size();

      // When
      BrokerTransactionMatcher.match(summaries, candidates);

      // Then
      assertThat(summaries).hasSize(initialSummariesSize);
      assertThat(candidates).hasSize(initialCandidatesSize);
    }
  }

  @Nested
  @DisplayName(
      "다른 날짜의 기존 Sale 은 '그 날짜 합계'에는 안 들어가지만 남은 수량 계산에는 들어간다(QUANTITY_EXCEEDS_ALLOCATION 케이스)")
  class DifferentDateSalesTests {

    @Test
    @DisplayName("다른 날짜의 기존 Sale은 당일 합계에 포함되지 않지만 remaining 수량 계산에는 포함됨")
    void differentDateSalesNotInDailySumButInRemainingCalculation() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      // 9월 29일의 기존 Sale (다른 날짜)
      ExistingSale yesterdaySale =
          new ExistingSale(1L, TRADE_DATE_2026_09_29, 50, 500000, 2500, 500);

      MatchCandidate candidate =
          new MatchCandidate(
              1L,
              "005930",
              100,
              List.of(
                  yesterdaySale)); // allocatedQuantity=100, but yesterday's sale doesn't count for
      // today

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      // 할당량 100 - 이전일 매도 50 = 남은 수량 50
      // 브로커 매도량 100 > 남은 수량 50 → QUANTITY_EXCEEDS_ALLOCATION
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.QUANTITY_EXCEEDS_ALLOCATION);
    }
  }

  @Nested
  @DisplayName("같은 종목 후보 2건 → AMBIGUOUS_ALLOCATIONS")
  class AmbiguousAllocationsTests {

    @Test
    @DisplayName("같은 종목에 대해 2개 이상의 배정이 존재하면 AMBIGUOUS_ALLOCATIONS")
    void sameStockTwoCandidates() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      MatchCandidate candidate1 = new MatchCandidate(1L, "005930", 150, List.of());
      MatchCandidate candidate2 = new MatchCandidate(2L, "005930", 200, List.of());

      // When
      MatchReport report =
          BrokerTransactionMatcher.match(List.of(summary), List.of(candidate1, candidate2));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.AMBIGUOUS_ALLOCATIONS);
      assertThat(result.allocationId()).isNull();
      assertThat(report.candidatesWithoutStockCode()).isEmpty();
    }
  }

  @Nested
  @DisplayName("종목 후보 0건 → NO_ALLOCATION_FOR_STOCK")
  class NoAllocationForStockTests {

    @Test
    @DisplayName("해당 종목에 대한 배정이 존재하지 않으면 NO_ALLOCATION_FOR_STOCK")
    void noAllocationForStock() {
      // Given
      BrokerSaleSummary summary =
          new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

      // 해당 종목과 관련 없는 후보만 존재
      MatchCandidate candidate = new MatchCandidate(1L, "000660", 150, List.of()); // 다른 종목

      // When
      MatchReport report = BrokerTransactionMatcher.match(List.of(summary), List.of(candidate));

      // Then
      assertThat(report.results()).hasSize(1);
      MatchResult result = report.results().get(0);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.reason()).isEqualTo(UnmatchedReason.NO_ALLOCATION_FOR_STOCK);
      assertThat(result.allocationId()).isNull();
      assertThat(report.candidatesWithoutStockCode()).isEmpty();
    }
  }

  @Nested
  @DisplayName("MatchResult 생성자 불변식")
  class MatchResultConstructorInvariantTests {

    private final BrokerSaleSummary summary =
        new BrokerSaleSummary(TRADE_DATE_2026_09_30, "005930", 100, 1000000, 5000, 1000);

    @Test
    @DisplayName("성공(reason null)인데 allocationId가 없으면 IllegalArgumentException 발생")
    void successWithoutAllocationIdThrowsException() {
      assertThrows(IllegalArgumentException.class, () -> new MatchResult(summary, null, null));
    }

    @Test
    @DisplayName("실패(reason 있음)는 allocationId가 없어도 생성되고 isMatched 는 false")
    void failureWithoutAllocationIdIsAllowed() {
      MatchResult result = new MatchResult(summary, null, UnmatchedReason.NO_ALLOCATION_FOR_STOCK);
      assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("실패여도 대상 배정이 특정되면 allocationId를 담을 수 있다")
    void failureWithAllocationIdKeepsIt() {
      MatchResult result = new MatchResult(summary, 1L, UnmatchedReason.ALREADY_REGISTERED);
      assertThat(result.isMatched()).isFalse();
      assertThat(result.allocationId()).isEqualTo(1L);
    }
  }
}
