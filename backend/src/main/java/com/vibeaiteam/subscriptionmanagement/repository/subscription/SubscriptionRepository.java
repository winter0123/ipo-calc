package com.vibeaiteam.subscriptionmanagement.repository.subscription;

import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByIdAndDeletedAtIsNull(Long id);

    List<Subscription> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    List<Subscription> findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long investorId);

    List<Subscription> findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long securitiesAccountId);

    Optional<Subscription> findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
            Long investorId, Long securitiesAccountId, Long ipoId, String status);
}