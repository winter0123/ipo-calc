package com.vibeaiteam.costrecording.repository.cost;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 비용 JPA 저장소.
 */
@Repository
public interface CostRepository extends JpaRepository<Cost, Long> {

  /**
   * ID 조건(삭제되지 않은 건만)으로 비용을 단건 조회한다.
   */
  Optional<Cost> findByIdAndDeletedAtIsNull(Long id);

  /**
   * 삭제되지 않은 비용을 목록 조회한다(발생일 내림차순, 생성일 내림차순).
   */
  List<Cost> findAllByDeletedAtIsNullOrderByIncurredAtDescCreatedAtDesc();

  /**
   * 청약 ID 조건(삭제되지 않은 건만)으로 비용을 목록 조회한다(발생일 내림차순, 생성일 내림차순).
   */
  List<Cost> findBySubscriptionIdAndDeletedAtIsNullOrderByIncurredAtDescCreatedAtDesc(
      Long subscriptionId);

  /**
   * 현재 계정(accountId) 소유 투자자의 ACTIVE 비용을 청약 ID·투자자 ID·투자자명 필터로 조회한다(발생일·생성일 내림차순).
   */
  @Query(
      "SELECT c FROM Cost c "
          + "JOIN Subscription s ON c.subscriptionId = s.id "
          + "JOIN Investor i ON s.investorId = i.id "
          + "WHERE c.deletedAt IS NULL AND c.status = "
          + RecordStatus.ACTIVE_HQL
          + " "
          + "AND i.accountId = :accountId "
          + "AND (:subscriptionId IS NULL OR c.subscriptionId = :subscriptionId) "
          + "AND (:investorId IS NULL OR i.id = :investorId) "
          + "AND (:investorName IS NULL OR i.displayName LIKE :investorName) "
          + "ORDER BY c.incurredAt DESC, c.createdAt DESC")
  List<Cost> findActiveWithFilters(
      @Param("accountId") Long accountId,
      @Param("subscriptionId") Long subscriptionId,
      @Param("investorId") Long investorId,
      @Param("investorName") String investorName);

  /**
   * 청약 ID · 상태 조건(삭제되지 않은 건만)으로 비용을 목록 조회한다.
   */
  List<Cost> findBySubscriptionIdAndStatusAndDeletedAtIsNull(
      Long subscriptionId, RecordStatus status);

  /** 배치 조회: subscriptionId 목록으로 ACTIVE Cost 일괄 조회 (N+1 방지용). */
  List<Cost> findBySubscriptionIdInAndStatusAndDeletedAtIsNull(
      List<Long> subscriptionIds, RecordStatus status);
}
