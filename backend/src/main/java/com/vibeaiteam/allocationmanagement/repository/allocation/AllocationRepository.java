package com.vibeaiteam.allocationmanagement.repository.allocation;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

  Optional<Allocation> findByIdAndDeletedAtIsNull(Long id);

  List<Allocation> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  Optional<Allocation> findBySubscriptionIdAndStatusAndDeletedAtIsNull(
      Long subscriptionId, String status);

  List<Allocation> findBySubscriptionIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long subscriptionId);

  @Query(
      "SELECT a FROM Allocation a "
          + "JOIN Subscription s ON a.subscriptionId = s.id "
          + "JOIN Investor i ON s.investorId = i.id "
          + "WHERE a.deletedAt IS NULL AND i.id = :investorId "
          + "ORDER BY a.createdAt DESC")
  List<Allocation> findByInvestorId(@Param("investorId") Long investorId);

  /** 배치 조회: subscriptionId 목록으로 Allocation 일괄 조회 (N+1 방지용) deletedAt IS NULL 조건 포함 */
  @Query(
      "SELECT a FROM Allocation a WHERE a.subscriptionId IN :subscriptionIds AND a.deletedAt IS NULL")
  List<Allocation> findBySubscriptionIdIn(@Param("subscriptionIds") List<Long> subscriptionIds);

  /** 배치 조회: allocationId 목록으로 Allocation 일괄 조회 (N+1 방지용) deletedAt IS NULL 조건 포함 */
  @Query("SELECT a FROM Allocation a WHERE a.id IN :allocationIds AND a.deletedAt IS NULL")
  List<Allocation> findByIdIn(@Param("allocationIds") List<Long> allocationIds);
}
