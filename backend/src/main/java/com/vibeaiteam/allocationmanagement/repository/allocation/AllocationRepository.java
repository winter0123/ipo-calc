package com.vibeaiteam.allocationmanagement.repository.allocation;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByIdAndDeletedAtIsNull(Long id);

    List<Allocation> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<Allocation> findBySubscriptionIdAndStatusAndDeletedAtIsNull(Long subscriptionId, String status);
}
