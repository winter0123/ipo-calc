package com.vibeaiteam.costrecording.repository.cost;

import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CostRepository extends JpaRepository<Cost, Long> {

    Optional<Cost> findByIdAndDeletedAtIsNull(Long id);

    List<Cost> findAllByDeletedAtIsNullOrderByIncurredAtDescCreatedAtDesc();

    List<Cost> findBySubscriptionIdAndDeletedAtIsNullOrderByIncurredAtDescCreatedAtDesc(Long subscriptionId);

    @Query("SELECT c FROM Cost c " +
           "JOIN Subscription s ON c.subscriptionId = s.id " +
           "JOIN Investor i ON s.investorId = i.id " +
           "WHERE c.deletedAt IS NULL AND c.status = 'ACTIVE' " +
           "AND (:subscriptionId IS NULL OR c.subscriptionId = :subscriptionId) " +
           "AND (:investorId IS NULL OR i.id = :investorId) " +
           "AND (:investorName IS NULL OR i.displayName LIKE :investorName) " +
           "ORDER BY c.incurredAt DESC, c.createdAt DESC")
    List<Cost> findActiveWithFilters(@Param("subscriptionId") Long subscriptionId,
                                      @Param("investorId") Long investorId,
                                      @Param("investorName") String investorName);

    List<Cost> findBySubscriptionIdAndStatusAndDeletedAtIsNull(Long subscriptionId, String status);

    /**
     * 배치 조회: subscriptionId 목록으로 ACTIVE Cost 일괄 조회 (N+1 방지용)
     */
    List<Cost> findBySubscriptionIdInAndStatusAndDeletedAtIsNull(List<Long> subscriptionIds, String status);
}