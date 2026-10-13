package com.vibeaiteam.allocationmanagement.repository.allocation;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 배정 JPA 저장소. */
@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

  /**
   * 배정에 연결된 ACTIVE 매도 수량 합계(없으면 0). salemanagement가 이 패키지에 의존하므로 순환을 피하려고 엔티티 이름으로만
   * 조회한다.
   */
  @Query(
      "SELECT COALESCE(SUM(s.quantity), 0) FROM Sale s WHERE s.allocationId = :allocationId "
          + "AND s.deletedAt IS NULL AND s.status = "
          + RecordStatus.ACTIVE_HQL)
  long sumActiveSaleQuantity(@Param("allocationId") Long allocationId);

  /** ID 조건(삭제되지 않은 건만)으로 배정을 단건 조회한다. */
  Optional<Allocation> findByIdAndDeletedAtIsNull(Long id);

  /** 삭제되지 않은 배정을 목록 조회한다(생성일 내림차순). */
  List<Allocation> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  /** 청약 ID · 상태 조건(삭제되지 않은 건만)으로 배정을 단건 조회한다. */
  Optional<Allocation> findBySubscriptionIdAndStatusAndDeletedAtIsNull(
      Long subscriptionId, RecordStatus status);

  /** 현재 계정 소유 투자자의 배정만 목록 조회한다(생성일 내림차순). subscriptionId/investorId는 null이면 무시한다. */
  @Query(
      "SELECT a FROM Allocation a "
          + "JOIN Subscription s ON a.subscriptionId = s.id "
          + "JOIN Investor i ON s.investorId = i.id "
          + "WHERE a.deletedAt IS NULL AND i.accountId = :accountId "
          + "AND (:subscriptionId IS NULL OR a.subscriptionId = :subscriptionId) "
          + "AND (:investorId IS NULL OR i.id = :investorId) "
          + "ORDER BY a.createdAt DESC")
  List<Allocation> findAllByAccountWithFilters(
      @Param("accountId") Long accountId,
      @Param("subscriptionId") Long subscriptionId,
      @Param("investorId") Long investorId);

  /** 배치 조회: subscriptionId 목록으로 Allocation 일괄 조회 (N+1 방지용) deletedAt IS NULL 조건 포함. */
  @Query(
      "SELECT a FROM Allocation a WHERE a.subscriptionId IN :subscriptionIds "
          + "AND a.deletedAt IS NULL")
  List<Allocation> findBySubscriptionIdIn(@Param("subscriptionIds") List<Long> subscriptionIds);

  /** 배치 조회: allocationId 목록으로 Allocation 일괄 조회 (N+1 방지용) deletedAt IS NULL 조건 포함. */
  @Query("SELECT a FROM Allocation a WHERE a.id IN :allocationIds AND a.deletedAt IS NULL")
  List<Allocation> findByIdIn(@Param("allocationIds") List<Long> allocationIds);
}
