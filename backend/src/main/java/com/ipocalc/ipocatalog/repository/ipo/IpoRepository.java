package com.ipocalc.ipocatalog.repository.ipo;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 공모주 JPA 저장소. */
@Repository
public interface IpoRepository extends JpaRepository<Ipo, Long> {

  /** ID 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다. */
  Optional<Ipo> findByIdAndDeletedAtIsNull(Long id);

  /** 삭제되지 않은 공모주를 목록 조회한다(생성일 내림차순). */
  List<Ipo> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  /** 종목코드 · 상태 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다. */
  Optional<Ipo> findByStockCodeAndStatusAndDeletedAtIsNull(String stockCode, RecordStatus status);

  /** 법인코드 · 상태 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다. */
  Optional<Ipo> findByCorpCodeAndStatusAndDeletedAtIsNull(String corpCode, RecordStatus status);

  /** 공모주명 · 청약 시작일 · 상태 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다. */
  Optional<Ipo> findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
      String ipoName, LocalDate subscriptionStartDate, RecordStatus status);

  /** 청약 시작일 · 상태 조건(삭제되지 않은 건만)으로 공모주 목록을 조회한다. 동기화 중복 검사에서 이름 정규화 비교에 쓴다. */
  List<Ipo> findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
      LocalDate subscriptionStartDate, RecordStatus status);

  /**
   * 청약 시작일 범위 · 법인코드 존재 · 상태 조건(삭제되지 않은 건만)으로 공모주 목록을 조회한다. 범위 동기화 후 상세 동기화 대상 선정에 사용한다.
   *
   * @param startDate 청약 시작일 시작 (포함)
   * @param endDate 청약 시작일 종료 (포함)
   * @return 조건에 맞는 공모주 목록
   */
  List<Ipo> findBySubscriptionStartDateBetweenAndCorpCodeIsNotNullAndDeletedAtIsNull(
      LocalDate startDate, LocalDate endDate);

  /**
   * 종목코드가 null이고 동기화 출처가 있는(자동 동기화) · 상태 조건(삭제되지 않은 건만) 공모주 목록을 조회한다. ustockplus 일정 병합 시 회사명 정규화 매칭
   * 후보군 조회에 사용한다.
   *
   * @param status 조회할 상태 (예: RecordStatus.ACTIVE)
   * @return 종목코드가 null이고 syncSource가 null이 아닌 ACTIVE 상태의 공모주 목록
   */
  List<Ipo> findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
      RecordStatus status);

  /**
   * 상장일 시초가 수집 대상 공모주 목록을 조회한다.
   *
   * <p>조건: status ACTIVE, stock_code NOT NULL, listing_date &lt; 오늘(Asia/Seoul), listing_open_price
   * IS NULL, 삭제되지 않은 건.
   *
   * @param today 오늘 날짜(Asia/Seoul, 주입된 Clock 기준)
   * @return 시초가 수집 대상 공모주 목록
   */
  @SuppressWarnings("checkstyle:LineLength")
  List<Ipo>
      findByStatusAndStockCodeIsNotNullAndListingDateBeforeAndListingOpenPriceIsNullAndDeletedAtIsNull(
          RecordStatus status, LocalDate today);

  /** 상장일 시초가 수집 대상 공모주를 조회한다. */
  @SuppressWarnings("checkstyle:LineLength")
  default List<Ipo> findOpenPriceSyncTargets(LocalDate today) {
    return findByStatusAndStockCodeIsNotNullAndListingDateBeforeAndListingOpenPriceIsNullAndDeletedAtIsNull(
        RecordStatus.ACTIVE, today);
  }
}
