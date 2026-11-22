package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusScheduleDates;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 수동 과거 상장일만 보완하며 기존 DART 데이터는 보존한다. */
class IpoHistoricalListingServiceTest {
  private final UstockPlusCalendarClient client = mock(UstockPlusCalendarClient.class);
  private final IpoRepository repo = mock(IpoRepository.class);
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private IpoHistoricalListingService service;

  @BeforeEach
  void init() {
    when(tx.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
    service =
        new IpoHistoricalListingService(
            client,
            repo,
            tx,
            Clock.fixed(
                LocalDate.of(2026, 11, 20).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")));
  }

  private Ipo ipo(Long id, String code, LocalDate end, LocalDate listed) {
    return Ipo.builder()
        .id(id)
        .corpCode("01025617")
        .ipoName("하나에어로")
        .companyName("하나에어로")
        .stockCode(code)
        .syncSource("OPENDART")
        .subscriptionStartDate(end.minusDays(1))
        .subscriptionEndDate(end)
        .listingDate(listed)
        .status(RecordStatus.ACTIVE)
        .build();
  }

  private UstockPlusScheduleDates item(String code, LocalDate listing) {
    return new UstockPlusScheduleDates(code, "다른 종목명", listing, null, null);
  }

  @Test
  void ninetyDayPartitionsHaveNoGapOrOverlapAndPreserveCrossMonthListing() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    LocalDate ninetyEnd = start.plusDays(89);
    LocalDate secondStart = ninetyEnd.plusDays(1);
    LocalDate secondEnd = secondStart.plusDays(89);
    LocalDate thirdStart = secondEnd.plusDays(1);
    LocalDate end = LocalDate.of(2026, 10, 31);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 9, 25), null);
    when(client.fetchListingDatesStrict(thirdStart.plusDays(90), end))
        .thenReturn(List.of(item("271160", LocalDate.of(2026, 10, 15))));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));

    var result = service.backfill(start, end);

    verify(client).fetchListingDatesStrict(start, ninetyEnd);
    verify(client).fetchListingDatesStrict(secondStart, secondEnd);
    verify(client).fetchListingDatesStrict(thirdStart, thirdStart.plusDays(89));
    verify(client).fetchListingDatesStrict(thirdStart.plusDays(90), end);
    assertThat(result.listingBackfilled()).isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(result.failedRanges()).isEmpty();
  }

  @Test
  void partialFailureRecordsWindowAndCommitsSuccessfulEntriesOnlyOnce() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    LocalDate second = start.plusDays(90);
    LocalDate end = LocalDate.of(2026, 7, 1);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 4, 25), null);
    when(client.fetchListingDatesStrict(start, start.plusDays(89)))
        .thenThrow(new IllegalStateException("503"));
    when(client.fetchListingDatesStrict(second, second.plusDays(89)))
        .thenReturn(List.of(item("271160", LocalDate.of(2026, 5, 5))));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));

    var first = service.backfill(start, end);
    var secondRun = service.backfill(start, end);

    assertThat(first.listingBackfilled()).isEqualTo(1);
    assertThat(first.failedRanges())
        .containsExactly(new IpoHistoricalListingService.FailedRange(start, start.plusDays(89)));
    assertThat(secondRun.listingBackfilled()).isZero();
    verify(repo).saveAndFlush(row);
  }

  @Test
  void neverUpdatesManualOrAlreadyListedOrPreSubscriptionListing() {
    LocalDate day = LocalDate.of(2026, 9, 30);
    Ipo manual =
        Ipo.builder()
            .id(1L)
            .stockCode("111111")
            .status(RecordStatus.ACTIVE)
            .subscriptionEndDate(day.minusDays(5))
            .build();
    Ipo listed = ipo(2L, "222222", day.minusDays(5), day.minusDays(1));
    Ipo impossible = ipo(3L, "333333", day.plusDays(1), null);
    when(client.fetchListingDatesStrict(LocalDate.of(2026, 9, 1), day))
        .thenReturn(List.of(item("111111", day), item("222222", day), item("333333", day)));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("111111", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(manual));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("222222", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(listed));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("333333", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(impossible));

    var result = service.backfill(LocalDate.of(2026, 9, 1), day);
    assertThat(result.listingBackfilled()).isZero();
    assertThat(manual.getListingDate()).isNull();
    assertThat(listed.getListingDate()).isEqualTo(day.minusDays(1));
    assertThat(impossible.getListingDate()).isNull();
  }

  @Test
  void databaseSaveFailureReportsStockCodeAndRetryWindow() {
    LocalDate start = LocalDate.of(2026, 3, 1);
    LocalDate end = LocalDate.of(2026, 3, 31);
    LocalDate listing = LocalDate.of(2026, 3, 30);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 3, 20), null);
    when(client.fetchListingDatesStrict(start, end)).thenReturn(List.of(item("271160", listing)));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));
    org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
        .when(repo)
        .saveAndFlush(row);

    var result = service.backfill(start, end);

    assertThat(result.listingBackfilled()).isZero();
    assertThat(result.failedRanges())
        .containsExactly(new IpoHistoricalListingService.FailedRange(start, end));
    assertThat(result.failedStocks())
        .containsExactly(
            new IpoHistoricalListingService.FailedStock("271160", listing, start, end));
    assertThat(result.listingConflicts()).isEmpty();
  }

  @Test
  void retryWithDifferentDateMarksPreviouslyImportedDateForReview() {
    LocalDate start = LocalDate.of(2026, 3, 1);
    LocalDate end = LocalDate.of(2026, 4, 30);
    LocalDate firstDate = LocalDate.of(2026, 3, 30);
    LocalDate revisedDate = LocalDate.of(2026, 4, 2);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 3, 20), null);
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));
    when(client.fetchListingDatesStrict(start, end))
        .thenReturn(List.of(item("271160", firstDate)))
        .thenReturn(List.of(item("271160", revisedDate)));

    var first = service.backfill(start, end);
    var retry = service.backfill(start, end);

    assertThat(first.listingBackfilled()).isEqualTo(1);
    assertThat(retry.listingBackfilled()).isZero();
    assertThat(retry.listingConflicts())
        .containsExactly(
            new IpoHistoricalListingService.ListingConflict(
                "271160", firstDate, revisedDate, start, end));
    assertThat(row.getListingDate()).isEqualTo(firstDate);
    assertThat(row.getLastImportedListingDate()).isEqualTo(firstDate);
    verify(repo, times(1)).saveAndFlush(row);
  }

  @Test
  void retryWithTwoCandidatesInSameWindowReportsConflictAgainstStoredAutomaticDate() {
    LocalDate start = LocalDate.of(2026, 6, 1);
    LocalDate end = LocalDate.of(2026, 6, 30);
    LocalDate firstDate = LocalDate.of(2026, 6, 29);
    LocalDate secondDate = LocalDate.of(2026, 6, 30);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 6, 20), null);
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));
    when(client.fetchListingDatesStrict(start, end))
        .thenReturn(List.of(item("271160", firstDate)))
        .thenReturn(List.of(item("271160", firstDate), item("271160", secondDate)));

    var first = service.backfill(start, end);
    var retry = service.backfill(start, end);

    assertThat(first.listingBackfilled()).isEqualTo(1);
    assertThat(retry.listingBackfilled()).isZero();
    assertThat(retry.listingConflicts())
        .containsExactly(
            new IpoHistoricalListingService.ListingConflict(
                "271160", firstDate, secondDate, start, end));
    assertThat(retry.failureCount()).isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(firstDate);
    assertThat(row.getLastImportedListingDate()).isEqualTo(firstDate);
    verify(repo, times(1)).saveAndFlush(row);
  }

  @Test
  void simultaneousCandidatesForNewAutomaticRowAreReportedWithoutSavingEitherDate() {
    LocalDate start = LocalDate.of(2026, 6, 1);
    LocalDate end = LocalDate.of(2026, 6, 30);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 6, 20), null);
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));
    when(client.fetchListingDatesStrict(start, end))
        .thenReturn(
            List.of(
                item("271160", LocalDate.of(2026, 6, 29)),
                item("271160", LocalDate.of(2026, 6, 30))));

    var result = service.backfill(start, end);

    assertThat(result.listingBackfilled()).isZero();
    assertThat(result.listingConflicts())
        .containsExactly(
            new IpoHistoricalListingService.ListingConflict(
                "271160", LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 30), start, end));
    assertThat(row.getListingDate()).isNull();
    verify(repo, never()).saveAndFlush(row);
  }

  @Test
  void manuallyEditedListingNeverBecomesAutomaticConflict() {
    LocalDate start = LocalDate.of(2026, 3, 1);
    LocalDate end = LocalDate.of(2026, 4, 30);
    LocalDate manualDate = LocalDate.of(2026, 3, 28);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 3, 20), manualDate);
    when(client.fetchListingDatesStrict(start, end))
        .thenReturn(List.of(item("271160", LocalDate.of(2026, 4, 2))));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));

    var result = service.backfill(start, end);

    assertThat(result.listingBackfilled()).isZero();
    assertThat(result.listingConflicts()).isEmpty();
    assertThat(row.getListingDate()).isEqualTo(manualDate);
    verify(repo, never()).saveAndFlush(row);
  }

  @Test
  void conflictingDatesForSameCodeAreNeverApplied() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    LocalDate end = start.plusDays(90);
    when(client.fetchListingDatesStrict(start, start.plusDays(89)))
        .thenReturn(List.of(item("271160", start.plusDays(12))));
    when(client.fetchListingDatesStrict(end, end)).thenReturn(List.of(item("271160", end)));
    assertThat(service.backfill(start, end).listingBackfilled()).isZero();
    verify(repo, times(0)).saveAndFlush(any());
  }

  @Test
  void additionalLookupSavesOnlyRowsSubscribedWithinSelectedMonth() {
    LocalDate start = LocalDate.of(2026, 11, 1);
    LocalDate end = LocalDate.of(2026, 11, 20);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 10, 20), null);
    when(client.fetchListingDatesStrict(start, end))
        .thenReturn(List.of(item("271160", LocalDate.of(2026, 11, 5))));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));

    var result =
        service.backfill(start, end, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    assertThat(result.listingBackfilled()).isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    verify(repo).saveAndFlush(row);
  }

  @Test
  void additionalLookupSkipsRowsOutsideSelectedSubscriptionMonth() {
    LocalDate start = LocalDate.of(2026, 11, 1);
    LocalDate end = LocalDate.of(2026, 11, 20);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 11, 20), null);
    when(client.fetchListingDatesStrict(start, end))
        .thenReturn(List.of(item("271160", LocalDate.of(2026, 11, 5))));
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));

    var result =
        service.backfill(start, end, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    assertThat(result.listingBackfilled()).isZero();
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void additionalLookupConflictAggregationRespectsSelectedSubscriptionMonth() {
    LocalDate start = LocalDate.of(2026, 11, 1);
    LocalDate end = LocalDate.of(2026, 11, 20);
    Ipo row = ipo(111L, "271160", LocalDate.of(2026, 11, 20), null);
    when(repo.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(row));
    when(client.fetchListingDatesStrict(start, end))
        .thenReturn(
            List.of(
                item("271160", LocalDate.of(2026, 11, 5)),
                item("271160", LocalDate.of(2026, 11, 6))));

    var result =
        service.backfill(start, end, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    assertThat(result.listingBackfilled()).isZero();
    assertThat(result.listingConflicts()).isEmpty();
    verify(repo, never()).saveAndFlush(any());
  }
}
