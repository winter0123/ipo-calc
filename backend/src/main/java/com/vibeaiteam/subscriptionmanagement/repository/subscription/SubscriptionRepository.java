package com.vibeaiteam.subscriptionmanagement.repository.subscription;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 청약 JPA 저장소. */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

  /** 청약에 ACTIVE 배정이 있는지. allocationmanagement가 이 패키지에 의존하므로 순환을 피하려고 엔티티 이름으로만 조회한다. */
  @Query(
      "SELECT COUNT(a) > 0 FROM Allocation a WHERE a.subscriptionId = :subscriptionId "
          + "AND a.deletedAt IS NULL AND a.status = "
          + RecordStatus.ACTIVE_HQL)
  boolean hasActiveAllocation(@Param("subscriptionId") Long subscriptionId);

  /** 청약에 걸린 ACTIVE 배정 수량(없으면 0). 순환을 피하려고 엔티티 이름으로만 조회한다. */
  @Query(
      "SELECT COALESCE(MAX(a.allocatedQuantity), 0) FROM Allocation a "
          + "WHERE a.subscriptionId = :subscriptionId AND a.deletedAt IS NULL AND a.status = "
          + RecordStatus.ACTIVE_HQL)
  long activeAllocatedQuantity(@Param("subscriptionId") Long subscriptionId);

  /** ID 조건(삭제되지 않은 건만)으로 청약을 단건 조회한다. */
  Optional<Subscription> findByIdAndDeletedAtIsNull(Long id);

  /** 삭제되지 않은 청약을 목록 조회한다(생성일 내림차순). */
  List<Subscription> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  /** 투자자 ID 조건(삭제되지 않은 건만)으로 청약을 목록 조회한다(생성일 내림차순). */
  List<Subscription> findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long investorId);

  /** 증권계좌 ID 조건(삭제되지 않은 건만)으로 청약을 목록 조회한다(생성일 내림차순). */
  List<Subscription> findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      Long securitiesAccountId);

  /** 현재 계정 소유 투자자의 청약만 목록 조회한다(생성일 내림차순). investorId/securitiesAccountId는 null이면 무시한다. */
  @Query(
      "SELECT s FROM Subscription s "
          + "JOIN Investor i ON s.investorId = i.id "
          + "WHERE i.accountId = :accountId "
          + "AND (:investorId IS NULL OR i.id = :investorId) "
          + "AND (:securitiesAccountId IS NULL OR s.securitiesAccountId = :securitiesAccountId) "
          + "AND s.deletedAt IS NULL "
          + "ORDER BY s.createdAt DESC")
  List<Subscription> findAllByAccountWithFilters(
      @Param("accountId") Long accountId,
      @Param("investorId") Long investorId,
      @Param("securitiesAccountId") Long securitiesAccountId);

  /** 투자자 ID · 증권계좌 ID · 공모주 ID · 상태 조건(삭제되지 않은 건만)으로 청약을 단건 조회한다. */
  Optional<Subscription> findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
      Long investorId, Long securitiesAccountId, Long ipoId, RecordStatus status);

  /**
   * ipoId 필터 소유권 재검증용: 현재 계정이 소유한 청약 중 해당 IPO 건이 존재하는지 확인 Subscription → Investor(accountId) 조인으로
   * 확인.
   */
  @Query(
      "SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Subscription s "
          + "JOIN Investor i ON s.investorId = i.id "
          + "WHERE s.ipoId = :ipoId AND i.accountId = :currentAccountId AND s.deletedAt IS NULL")
  boolean existsByIpoIdAndInvestorAccountId(
      @Param("ipoId") Long ipoId, @Param("currentAccountId") Long currentAccountId);

  /**
   * 필터 미지정 시 기본 조회: 현재 계정이 소유한 Investor의 Subscription만 페이징 조회 Subscription → Investor(accountId)
   * 조인으로 필터링, subscribedAt DESC 정렬.
   */
  @Query(
      "SELECT s FROM Subscription s "
          + "JOIN Investor i ON s.investorId = i.id "
          + "WHERE i.accountId = :currentAccountId AND s.deletedAt IS NULL")
  Page<Subscription> findByInvestorAccountId(
      @Param("currentAccountId") Long currentAccountId, Pageable pageable);

  /**
   * 계정 스코프를 강제하는 필터 조회 메서드 ipoId/investorId/securitiesAccountId 필터 + deletedAt IS NULL 조건 포함 반드시 현재
   * 계정 소유 Investor로 스코프를 강제 (AC-005 리스크 방지) 서브쿼리 방식이 아닌 JOIN으로 현재 계정의 Investor Subscription만 조회.
   */
  @Query(
      "SELECT s FROM Subscription s "
          + "JOIN Investor i ON s.investorId = i.id "
          + "WHERE i.accountId = :currentAccountId "
          + "AND (:investorId IS NULL OR i.id = :investorId) "
          + "AND (:securitiesAccountId IS NULL OR s.securitiesAccountId = :securitiesAccountId) "
          + "AND (:ipoId IS NULL OR s.ipoId = :ipoId) "
          + "AND s.deletedAt IS NULL "
          + "ORDER BY s.subscribedAt DESC")
  Page<Subscription> findByInvestorAccountIdWithFilters(
      @Param("currentAccountId") Long currentAccountId,
      @Param("investorId") Long investorId,
      @Param("securitiesAccountId") Long securitiesAccountId,
      @Param("ipoId") Long ipoId,
      Pageable pageable);
}
