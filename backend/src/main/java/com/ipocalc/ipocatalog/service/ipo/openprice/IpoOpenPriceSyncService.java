package com.ipocalc.ipocatalog.service.ipo.openprice;

import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 상장일 시초가 동기화 서비스.
 *
 * <p>상장일이 지난 공모주 중 아직 시초가가 없는 종목을 대상으로 공공데이터포털 주식시세 API에서 시초가를 조회해 {@code ipos.listing_open_price}에
 * 저장한다.
 *
 * <ul>
 *   <li>대상: status ACTIVE, stock_code NOT NULL, listing_date &lt; 오늘(Asia/Seoul),
 *       listing_open_price IS NULL
 *   <li>종목마다 {@link StockPriceClient#fetchOpenPrice} 호출 → 값이 있으면 저장
 *   <li>한 종목 실패는 로그만 남기고 다음 종목 계속
 *   <li>키 미설정({@link IllegalStateException})이면 WARN 한 번 남기고 전체 중단
 *   <li>이미 값이 있는 공모주는 건드리지 않음(멱등)
 *   <li>앱 내 재시도는 하지 않음 — 다음 날 배치가 같은 대상을 다시 조회
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoOpenPriceSyncService {

  private final IpoRepository ipoRepository;
  private final StockPriceClient stockPriceClient;
  private final Clock clock;

  /**
   * 상장일 시초가 동기화를 실행한다.
   *
   * @return 동기화 결과 요약
   */
  public SyncResult syncOpenPrices() {
    SyncResult result = new SyncResult();

    List<Ipo> targets = ipoRepository.findOpenPriceSyncTargets(LocalDate.now(clock));
    result.setTargetCount(targets.size());

    if (targets.isEmpty()) {
      log.info("시초가 동기화 대상 없음");
      return result;
    }

    log.info("시초가 동기화 시작: 대상 {}건", targets.size());

    for (Ipo ipo : targets) {
      try {
        // ESTIMATED_DART(예정일)만 있는 행은 자동 수집 대상에서 제외한다.
        if ("ESTIMATED_DART".equals(ipo.getListingDateEvidenceType())) {
          log.debug("시초가 수집 제외(ESTIMATED_DART): id={}", ipo.getId());
          continue;
        }
        LocalDate queriedListingDate = ipo.getListingDate();
        String queriedStockCode = ipo.getStockCode();
        Long ipoId = ipo.getId();
        if (queriedListingDate == null || queriedStockCode == null || ipoId == null) {
          continue;
        }
        // 기존 시초가가 이미 있으면 보호한다.
        if (ipo.getListingOpenPrice() != null) {
          log.debug("시초가 보호(기존 값 존재): id={}", ipoId);
          continue;
        }
        // 외부 시세 HTTP 호출은 트랜잭션/DB 락 없이 수행한다.
        var openPrice = stockPriceClient.fetchOpenPrice(queriedStockCode, queriedListingDate);
        if (openPrice.isPresent()) {
          // 실제 DB 조건부 갱신: 조회 시점 값과 현재 DB 값이 일치할 때만 1행 갱신.
          int updated =
              ipoRepository.updateListingOpenPriceIfCurrent(
                  ipoId, queriedStockCode, queriedListingDate, openPrice.get());
          if (updated == 1) {
            result.incrementSaved();
            log.debug(
                "시초가 저장: id={}, stockCode={}, openPrice={}",
                ipoId,
                queriedStockCode,
                openPrice.get());
          } else {
            log.debug("시초가 저장 건너뜀(값 변경/기존 값 보호): id={}", ipoId);
          }
        } else {
          result.incrementEmpty();
          log.debug("시초가 없음(빈 응답): id={}, stockCode={}", ipoId, queriedStockCode);
        }
      } catch (IllegalStateException e) {
        // 키 미설정 — 전체 중단
        log.warn("서비스 키 미설정으로 시초가 동기화 중단: {}", e.getClass().getSimpleName());
        result.setAborted(true);
        return result;
      } catch (RuntimeException e) {
        result.incrementFailed();
        log.error(
            "시초가 조회 실패: id={}, stockCode={}, reason={}",
            ipo.getId(),
            ipo.getStockCode(),
            e.getClass().getSimpleName());
      }
    }

    log.info("시초가 동기화 완료: {}", result.summary());
    return result;
  }

  /** 동기화 결과 요약 DTO. */
  public static class SyncResult {
    private int targetCount;
    private int saved;
    private int empty;
    private int failed;
    private boolean aborted;

    /** 대상 건수를 설정한다. */
    public void setTargetCount(int targetCount) {
      this.targetCount = targetCount;
    }

    /** 저장 건수를 1 증가시킨다. */
    public void incrementSaved() {
      this.saved++;
    }

    /** 빈 응답 건수를 1 증가시킨다. */
    public void incrementEmpty() {
      this.empty++;
    }

    /** 실패 건수를 1 증가시킨다. */
    public void incrementFailed() {
      this.failed++;
    }

    /** 중단 여부를 설정한다. */
    public void setAborted(boolean aborted) {
      this.aborted = aborted;
    }

    /** 결과 요약 문자열. */
    public String summary() {
      return "targetCount="
          + targetCount
          + ", saved="
          + saved
          + ", empty="
          + empty
          + ", failed="
          + failed
          + ", aborted="
          + aborted;
    }
  }
}
