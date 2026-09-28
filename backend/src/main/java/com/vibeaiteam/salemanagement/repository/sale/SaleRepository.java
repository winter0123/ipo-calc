package com.vibeaiteam.salemanagement.repository.sale;

import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndDeletedAtIsNull(Long id);

    List<Sale> findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc();

    List<Sale> findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(Long allocationId, String status);

    @Query("SELECT SUM(s.quantity) FROM Sale s WHERE s.allocationId = :allocationId AND s.deletedAt IS NULL AND s.status = 'ACTIVE'")
    Long sumActiveQuantityByAllocationId(@Param("allocationId") Long allocationId);

    @Query("SELECT SUM(s.quantity) FROM Sale s WHERE s.allocationId = :allocationId AND s.id != :excludeId AND s.deletedAt IS NULL AND s.status = 'ACTIVE'")
    Long sumActiveQuantityByAllocationIdExcludingId(@Param("allocationId") Long allocationId, @Param("excludeId") Long excludeId);

    @Query("SELECT s FROM Sale s " +
           "JOIN Allocation a ON s.allocationId = a.id " +
           "JOIN Subscription sub ON a.subscriptionId = sub.id " +
           "JOIN Investor i ON sub.investorId = i.id " +
           "WHERE s.deletedAt IS NULL AND s.status = :status " +
           "AND i.accountId = :accountId")
    List<Sale> findAllByStatusAndInvestorAccountId(@Param("status") String status, @Param("accountId") Long accountId);

    @Query("SELECT s FROM Sale s " +
           "WHERE s.allocationId IN ?1 AND s.status = ?2 AND s.deletedAt IS NULL")
    List<Sale> findByAllocationIdInAndStatus(List<Long> allocationIds, String status);

    @Query("SELECT s FROM Sale s " +
           "JOIN Allocation a ON s.allocationId = a.id " +
           "JOIN Subscription sub ON a.subscriptionId = sub.id " +
           "JOIN Investor i ON sub.investorId = i.id " +
           "WHERE s.deletedAt IS NULL AND s.status = 'ACTIVE' " +
           "AND (:allocationId IS NULL OR s.allocationId = :allocationId) " +
           "AND (:investorId IS NULL OR i.id = :investorId) " +
           "AND (:investorName IS NULL OR i.displayName = :investorName) " +
           "ORDER BY s.saleDate DESC, s.createdAt DESC")
    List<Sale> findActiveWithFilters(@Param("allocationId") Long allocationId,
                                       @Param("investorId") Long investorId,
                                       @Param("investorName") String investorName);
}