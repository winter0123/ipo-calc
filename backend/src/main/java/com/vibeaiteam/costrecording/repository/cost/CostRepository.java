package com.vibeaiteam.costrecording.repository.cost;

import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CostRepository extends JpaRepository<Cost, Long> {

    Optional<Cost> findByIdAndDeletedAtIsNull(Long id);

    List<Cost> findAllByDeletedAtIsNullOrderByIncurredAtDescCreatedAtDesc();
}