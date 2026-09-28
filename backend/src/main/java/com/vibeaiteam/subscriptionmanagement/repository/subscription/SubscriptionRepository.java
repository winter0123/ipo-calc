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

    /**
     * 계정 스코프를 강제하는 필터 조회 메서드
     * ipoId/investorId/securitiesAccountId 필터 + deletedAt IS NULL 조건 포함
     * 반드시 현재 계정 소유 Investor로 스코프를 강제 (AC-005 리스크 방지)
     * 서브쿼리 방식이 아닌 JOIN으로 현재 계정의 Investor Subscription만 조회
     */
    @Query("SELECT s FROM Subscription s " +
            "JOIN Investor i ON s.investorId = i.id " +
            "WHERE i.accountId = :currentAccountId " +
            "AND (:investorId IS NULL OR i.id = :investorId) " +
            "AND (:securitiesAccountId IS NULL OR s.securitiesAccountId = :securitiesAccountId) " +
            "AND (:ipoId IS NULL OR s.ipoId = :ipoId) " +
            "AND s.deletedAt IS NULL " +
            "ORDER BY s.subscribedAt DESC")
    Page<Subscription> findByInvestorAccountIdWithFilters(
            @Param("currentAccountId") Long currentAccountId,
            @Param("investorId") Long investorId,
            @Param("securitiesAccountId") Long securitiesAccountId,
            @Param("ipoId") Long ipoId,
            Pageable pageable);
}