package com.ipocalc.ipocatalog.repository.ipo;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 공모주 JPA 저장소. */
@Repository
public interface IpoRepository extends JpaRepository<Ipo, Long> {

  /** 같은 공시로 철회된 공모의 재생성을 막는다. */
  boolean existsByCorpCodeAndWithdrawnReceiptNo(String corpCode, String withdrawnReceiptNo);

  /** ID 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다. */
  Optional<Ipo> findByIdAndDeletedAtIsNull(Long id);

  /** DART 상장일 저장 직전에 현재 행을 잠가 KIND 확정과의 경합을 직렬화한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Ipo i where i.id = :id and i.deletedAt is null")
  Optional<Ipo> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

  /** 삭제되지 않은 공모주를 목록 조회한다(생성일 내림차순). */
  List<Ipo> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  /** 종목코드 · 상태 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다. */
  Optional<Ipo> findByStockCodeAndStatusAndDeletedAtIsNull(String stockCode, RecordStatus status);

  /** 법인코드 · 상태 조건(삭제되지 않은 건만)으로 공모주를 목록 조회한다. 동일 회사 ACTIVE 복수 건 감지용. */
  List<Ipo> findAllByCorpCodeAndStatusAndDeletedAtIsNull(String corpCode, RecordStatus status);

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
   * 상태 · 자동 수집 출처 존재 · 삭제되지 않은 공모주 목록을 조회한다(재점검 대상). 상장일 유무·경과와 무관하게 조회한다.
   *
   * @param status 조회할 상태 (예: RecordStatus.ACTIVE)
   * @return 자동 수집 출처가 있는 ACTIVE 공모주 목록
   */
  List<Ipo> findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus status);

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
   * <p>ESTIMATED_DART(예정일)만 있는 행은 제외하고, 출처가 null인 레거시 행과 KRX 확정 행은 포함한다.
   */
  @Query(
      "select i from Ipo i where i.status = :status and i.stockCode is not null"
          + " and i.listingDate < :today and i.listingOpenPrice is null and i.deletedAt is null"
          + " and (i.listingDateEvidenceType is null or i.listingDateEvidenceType <> 'ESTIMATED_DART')")
  @SuppressWarnings("checkstyle:LineLength")
  List<Ipo> findOpenPriceSyncTargetsByStatusAndToday(
      @Param("status") RecordStatus status, @Param("today") LocalDate today);

  /** 상장일 시초가 수집 대상 공모주를 조회한다. */
  @SuppressWarnings("checkstyle:LineLength")
  default List<Ipo> findOpenPriceSyncTargets(LocalDate today) {
    return findOpenPriceSyncTargetsByStatusAndToday(RecordStatus.ACTIVE, today);
  }

  /** 조회 시점의 종목코드/상장일과 현재 DB 값이 일치할 때만 시초가를 저장한다. 영향 행이 1일 때만 갱신 성공으로 본다. */
  @Modifying
  @Transactional
  @Query(
      "update Ipo i set i.listingOpenPrice = :listingOpenPrice "
          + "where i.id = :id and i.stockCode = :stockCode and i.listingDate = :listingDate "
          + "and i.status = com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE "
          + "and i.deletedAt is null and i.listingOpenPrice is null "
          + "and (i.listingDateEvidenceType is null or i.listingDateEvidenceType <> 'ESTIMATED_DART')")
  @SuppressWarnings("checkstyle:LineLength")
  int updateListingOpenPriceIfCurrent(
      @Param("id") Long id,
      @Param("stockCode") String stockCode,
      @Param("listingDate") LocalDate listingDate,
      @Param("listingOpenPrice") Long listingOpenPrice);

  /** KRX 확정일을 조건부로 원자 갱신한다. 조건이 하나라도 맞지 않으면 0을 반환한다. */
  @Modifying
  @Transactional
  @Query(
      "update Ipo i set "
          + "i.listingDate = :krxListingDate, "
          + "i.listingDateEvidenceType = 'CONFIRMED_KRX', "
          + "i.krxListingNoticeUrl = :noticeUrl, "
          + "i.lastImportedListingDate = case when i.lastImportedListingDate is null then :dartListingDate else i.lastImportedListingDate end, "
          + "i.listingOpenPrice = case when i.listingDate = :krxListingDate then i.listingOpenPrice else null end, "
          + "i.lastSyncedAt = :now, "
          + "i.updatedAt = :now "
          + "where i.id = :id "
          + "and i.status = com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE "
          + "and i.deletedAt is null "
          + "and i.stockCode = :stockCode "
          + "and i.listingDateEvidenceType = 'ESTIMATED_DART' "
          + "and i.listingDateEvidenceReceipt = :dartReceipt "
          + "and i.listingDate = :dartListingDate "
          + "and (i.lastImportedListingDate is null or i.lastImportedListingDate = :dartListingDate)")
  @SuppressWarnings("checkstyle:LineLength")
  int confirmKrxListingIfCurrent(
      @Param("id") Long id,
      @Param("stockCode") String stockCode,
      @Param("dartReceipt") String dartReceipt,
      @Param("dartListingDate") LocalDate dartListingDate,
      @Param("krxListingDate") LocalDate krxListingDate,
      @Param("noticeUrl") String noticeUrl,
      @Param("now") LocalDateTime now);

  /** 동시 적용된 확정 후보가 이 요청과 동일한지 스칼라 집계로 확인한다. */
  @Query(
      "select count(i) from Ipo i where i.id = :id "
          + "and i.listingDateEvidenceType = 'CONFIRMED_KRX' "
          + "and i.krxListingNoticeUrl = :noticeUrl "
          + "and i.listingDate = :krxListingDate "
          + "and i.stockCode = :stockCode "
          + "and i.listingDateEvidenceReceipt = :dartReceipt "
          + "and i.status = com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE "
          + "and i.deletedAt is null")
  @SuppressWarnings("checkstyle:LineLength")
  int countSameConfirmedKrxCandidate(
      @Param("id") Long id,
      @Param("noticeUrl") String noticeUrl,
      @Param("krxListingDate") LocalDate krxListingDate,
      @Param("stockCode") String stockCode,
      @Param("dartReceipt") String dartReceipt);

  /** 공식 신규상장 70760 공고로 날짜가 비어 있는 자동 수집 행만 원자 갱신한다. */
  @Modifying
  @Transactional
  @Query(
      "update Ipo i set i.listingDate = :date, "
          + "i.listingDateEvidenceType = 'CONFIRMED_KRX', "
          + "i.krxListingNoticeUrl = :url, "
          + "i.lastImportedListingDate = :date, "
          + "i.lastSyncedAt = :now, i.updatedAt = :now "
          + "where i.id = :id "
          + "and i.status = com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE "
          + "and i.deletedAt is null and i.stockCode = :stockCode "
          + "and i.listingDate is null and i.syncSource is not null "
          + "and i.sourceReceiptNo = :sourceReceipt "
          + "and i.listingDateEvidenceType is null "
          + "and i.listingDateEvidenceReceipt is null "
          + "and i.krxListingNoticeUrl is null "
          + "and i.lastImportedListingDate is null "
          + "and i.listingOpenPrice is null "
          + "and i.withdrawnReceiptNo is null")
  @SuppressWarnings("checkstyle:LineLength")
  int confirmNewKrxListingIfEmpty(
      @Param("id") Long id,
      @Param("stockCode") String stockCode,
      @Param("sourceReceipt") String sourceReceipt,
      @Param("date") LocalDate date,
      @Param("url") String url,
      @Param("now") LocalDateTime now);

  /** 같은 70760 공식 공고가 이미 원자 적용됐는지 확인한다. */
  @Query(
      "select count(i) from Ipo i where i.id = :id "
          + "and i.status = com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE "
          + "and i.deletedAt is null and i.stockCode = :stockCode "
          + "and i.sourceReceiptNo = :sourceReceipt "
          + "and i.listingDate = :date "
          + "and i.listingDateEvidenceType = 'CONFIRMED_KRX' "
          + "and i.krxListingNoticeUrl = :url")
  @SuppressWarnings("checkstyle:LineLength")
  int countSameConfirmedNewKrxCandidate(
      @Param("id") Long id,
      @Param("stockCode") String stockCode,
      @Param("sourceReceipt") String sourceReceipt,
      @Param("date") LocalDate date,
      @Param("url") String url);

  /** ACTIVE 공모주의 청약 시작일 최소값을 조회한다. */
  Optional<LocalDate> findMinSubscriptionStartDateByStatusAndDeletedAtIsNull(RecordStatus status);

  /** ACTIVE 공모주의 청약 시작일 최대값을 조회한다. */
  Optional<LocalDate> findMaxSubscriptionStartDateByStatusAndDeletedAtIsNull(RecordStatus status);
}
