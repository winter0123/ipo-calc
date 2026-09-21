package com.vibeaiteam.ipocatalog.repository.ipo;

import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IpoRepository extends JpaRepository<Ipo, Long> {

    Optional<Ipo> findByIdAndDeletedAtIsNull(Long id);

    List<Ipo> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<Ipo> findByStockCodeAndStatusAndDeletedAtIsNull(String stockCode, String status);

    Optional<Ipo> findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            String ipoName, LocalDate subscriptionStartDate, String status);
}
