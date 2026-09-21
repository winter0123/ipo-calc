package com.vibeaiteam.allocationmanagement.repository.allocation;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByIdAndDeletedAtIsNull(Long id);

    List<Allocation> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<Allocation> findBySubscriptionIdAndStatusAndDeletedAtIsNull(Long subscriptionId, String status);

    List<Allocation> findBySubscriptionIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long subscriptionId);

    @Query("SELECT a FROM Allocation a " +
           "JOIN Subscription s ON a.subscriptionId = s.id " +
           "JOIN Investor i ON s.investorId = i.id " +
           "WHERE a.deletedAt IS NULL AND i.id = :investorId " +
           "ORDER BY a.createdAt DESC")
    List<Allocation> findByInvestorId(@Param("investorId") Long investorId);
}
