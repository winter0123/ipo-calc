package com.vibeaiteam.subscriptionmanagement.repository.subscription;

import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * ipoId 필터 소유권 재검증용: 현재 계정이 소유한 청약 중 해당 IPO 건이 존재하는지 확인
     * Subscription → Investor(accountId) 조인으로 확인
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Subscription s " +
            "JOIN Investor i ON s.investorId = i.id " +
            "WHERE s.ipoId = :ipoId AND i.accountId = :currentAccountId AND s.deletedAt IS NULL")
    boolean existsByIpoIdAndInvestorAccountId(@Param("ipoId") Long ipoId, @Param("currentAccountId") Long currentAccountId);

    /**
     * 필터 미지정 시 기본 조회: 현재 계정이 소유한 Investor의 Subscription만 페이징 조회
     * Subscription → Investor(accountId) 조인으로 필터링, subscribedAt DESC 정렬
     */
    @Query("SELECT s FROM Subscription s " +
            "JOIN Investor i ON s.investorId = i.id " +
            "WHERE i.accountId = :currentAccountId AND s.deletedAt IS NULL")
    Page<Subscription> findByInvestorAccountId(@Param("currentAccountId") Long currentAccountId, Pageable pageable);
}