package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.util.validation.StockCodes;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusScheduleDates;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 관리자 수동 기간 조회에서만 사용하는 과거 상장일 보완. 일일 90일 병합과 독립적이며 기존 상장일이 비어 있고 검증된 종목코드로 매칭되는 자동수집 IPO만 변경한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoHistoricalListingService {
  private final UstockPlusCalendarClient calendarClient;
  private final IpoRepository ipoRepository;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  private enum SaveOutcome {
    SAVED,
    SKIPPED,
    CONFLICT
  }

  /** 실패한 조회 구간은 날짜를 보존하여 다시 조회할 수 있다. */
  public record FailedRange(LocalDate startDate, LocalDate endDate) {}

  /** 저장 오류로 복구하지 못한 종목과 원본 조회 구간. */
  public record FailedStock(
      String stockCode, LocalDate candidateDate, LocalDate startDate, LocalDate endDate) {}

  /** 자동 보완 이력과 현재 후보가 충돌하여 수동 확인이 필요한 종목. */
  public record ListingConflict(
      String stockCode,
      LocalDate existingDate,
      LocalDate candidateDate,
      LocalDate startDate,
      LocalDate endDate) {}

  /** 부분 성공을 보존하고 조회 오류·저장 오류·상충 후보를 각각 보고한다. */
  public record Result(
      int listingBackfilled,
      List<FailedRange> failedRanges,
      List<FailedStock> failedStocks,
      List<ListingConflict> listingConflicts) {
    /** 조회 구간만 추적하던 기존 회귀 테스트와 호환되는 생성자. */
    public Result(int listingBackfilled, List<FailedRange> failedRanges) {
      this(listingBackfilled, failedRanges, List.of(), List.of());
    }

    /** 조회 실패는 구간별, DB 저장 실패와 상충 항목은 종목별로 센다. */
    public int failureCount() {
      long queryFailures =
          failedRanges.stream()
              .filter(
                  window ->
                      failedStocks.stream()
                          .noneMatch(
                              stock ->
                                  window.startDate().equals(stock.startDate())
                                      && window.endDate().equals(stock.endDate())))
              .count();
      return (int) queryFailures + failedStocks.size() + listingConflicts.size();
    }
  }

  /** 양끝 포함 90일씩 조회한다. 다음 구간은 전 구간 종료 다음 날이므로 경계 누락·중복이 없다. */
  public Result backfill(LocalDate start, LocalDate end) {
    if (start == null || end == null || start.isAfter(end)) {
      throw new IllegalArgumentException("상장일 조회 기간이 올바르지 않습니다.");
    }
    LocalDate today = LocalDate.now(clock);
    LocalDate lastDay = end.isAfter(today) ? today : end;
    if (start.isAfter(lastDay)) {
      return new Result(0, List.of());
    }
    Map<String, LocalDate> uniqueDates = new HashMap<>();
    Map<String, FailedRange> sourceWindows = new HashMap<>();
    Set<String> ambiguous = new HashSet<>();
    List<FailedRange> failures = new ArrayList<>();
    List<FailedStock> failedStocks = new ArrayList<>();
    List<ListingConflict> listingConflicts = new ArrayList<>();
    for (LocalDate cursor = start; !cursor.isAfter(lastDay); ) {
      LocalDate rangeEnd = cursor.plusDays(89);
      if (rangeEnd.isAfter(lastDay)) {
        rangeEnd = lastDay;
      }
      try {
        for (UstockPlusScheduleDates schedule :
            calendarClient.fetchListingDatesStrict(cursor, rangeEnd)) {
          String code = StockCodes.normalize(schedule.stockCode());
          LocalDate date = schedule.listingDate();
          if (code == null
              || code.isBlank()
              || date == null
              || date.isBefore(start)
              || date.isAfter(lastDay)
              || ambiguous.contains(code)) {
            continue;
          }
          LocalDate previous = uniqueDates.putIfAbsent(code, date);
          sourceWindows.putIfAbsent(code, new FailedRange(cursor, rangeEnd));
          if (previous != null && !previous.equals(date)) {
            ambiguous.add(code);
            uniqueDates.remove(code);
            sourceWindows.remove(code);
            log.warn("동일 종목의 상장일 상충으로 보완 제외: stockCode={}", code);
          }
        }
      } catch (Exception e) {
        failures.add(new FailedRange(cursor, rangeEnd));
        log.warn(
            "과거 상장일 조회 구간 실패: start={}, end={}, error={}",
            cursor,
            rangeEnd,
            e.getClass().getSimpleName());
      }
      cursor = rangeEnd.plusDays(1);
    }

    int updated = 0;
    for (Map.Entry<String, LocalDate> entry : uniqueDates.entrySet()) {
      FailedRange window = sourceWindows.get(entry.getKey());
      try {
        SaveOutcome outcome =
            transactionTemplate.execute(
                status -> {
                  var found =
                      ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull(
                          entry.getKey(), RecordStatus.ACTIVE);
                  if (found.isEmpty()) {
                    return SaveOutcome.SKIPPED;
                  }
                  Ipo ipo = found.get();
                  LocalDate subscriptionEnd = ipo.getSubscriptionEndDate();
                  if (ipo.getSyncSource() == null
                      || subscriptionEnd == null
                      || !subscriptionEnd.isBefore(entry.getValue())) {
                    return SaveOutcome.SKIPPED;
                  }
                  if (ipo.getListingDate() != null) {
                    if (ipo.getListingDate().equals(entry.getValue())) {
                      return SaveOutcome.SKIPPED;
                    }
                    if (ipo.getLastImportedListingDate() != null
                        && ipo.getLastImportedListingDate().equals(ipo.getListingDate())) {
                      return SaveOutcome.CONFLICT;
                    }
                    // 출처가 불명확하거나 수동으로 편집된 날짜는 보존한다.
                    return SaveOutcome.SKIPPED;
                  }
                  boolean changed =
                      ipo.mergeSupplementalSchedule(
                          null,
                          null,
                          null,
                          entry.getValue(),
                          "OPENDART+USTOCKPLUS",
                          LocalDateTime.now(clock));
                  if (changed) {
                    ipoRepository.saveAndFlush(ipo);
                  }
                  return changed ? SaveOutcome.SAVED : SaveOutcome.SKIPPED;
                });
        if (outcome == SaveOutcome.SAVED) {
          updated++;
        } else if (outcome == SaveOutcome.CONFLICT) {
          LocalDate existing =
              ipoRepository
                  .findByStockCodeAndStatusAndDeletedAtIsNull(entry.getKey(), RecordStatus.ACTIVE)
                  .map(Ipo::getListingDate)
                  .orElse(null);
          listingConflicts.add(
              new ListingConflict(
                  entry.getKey(),
                  existing,
                  entry.getValue(),
                  window.startDate(),
                  window.endDate()));
        }
      } catch (Exception e) {
        failedStocks.add(
            new FailedStock(
                entry.getKey(), entry.getValue(), window.startDate(), window.endDate()));
        if (!failures.contains(window)) {
          failures.add(window);
        }
        log.warn(
            "과거 상장일 보완 저장 실패: stockCode={}, start={}, end={}, error={}",
            entry.getKey(),
            window.startDate(),
            window.endDate(),
            e.getClass().getSimpleName());
      }
    }
    return new Result(
        updated, List.copyOf(failures), List.copyOf(failedStocks), List.copyOf(listingConflicts));
  }
}
