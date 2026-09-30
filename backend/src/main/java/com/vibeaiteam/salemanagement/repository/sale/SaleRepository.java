package com.vibeaiteam.salemanagement.repository.sale;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 매도 JPA 저장소.
 */
@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

  /**
   * ID 조건(삭제되지 않은 건만)으로 매도를 단건 조회한다.
   */
  Optional<Sale> findByIdAndDeletedAtIsNull(Long id);

  /**
   * 삭제되지 않은 매도를 목록 조회한다(매도일 내림차순, 생성일 내림차순).
   */
  List<Sale> findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc();

  /**
   * 배정 ID · 상태 조건(삭제되지 않은 건만)으로 매도를 목록 조회한다(매도일 내림차순, 생성일 내림차순).
   */
  List<Sale> findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(
      Long allocationId, RecordStatus status);

  /**
   * 배정별 ACTIVE 매도 수량 합계를 조회한다.
   */
  @Query(
      "SELECT SUM(s.quantity) FROM Sale s WHERE s.allocationId = :allocationId "
          + "AND s.deletedAt IS NULL AND s.status = "
          + RecordStatus.ACTIVE_HQL)
  Long sumActiveQuantityByAllocationId(@Param("allocationId") Long allocationId);

  /**
   * 특정 매도를 제외한 배정별 ACTIVE 매도 수량 합계를 조회한다. 수정 시 초과 검증에 쓴다.
   */
  @Query(
      "SELECT SUM(s.quantity) FROM Sale s WHERE s.allocationId = :allocationId "
          + "AND s.id != :excludeId AND s.deletedAt IS NULL AND s.status = "
          + RecordStatus.ACTIVE_HQL)
  Long sumActiveQuantityByAllocationIdExcludingId(
      @Param("allocationId") Long allocationId, @Param("excludeId") Long excludeId);

  /**
   * 상태 · InvestorAccountId 조건으로 매도를 목록 조회한다.
   */
  @Query(
      "SELECT s FROM Sale s "
          + "JOIN Allocation a ON s.allocationId = a.id "
          + "JOIN Subscription sub ON a.subscriptionId = sub.id "
          + "JOIN Investor i ON sub.investorId = i.id "
          + "WHERE s.deletedAt IS NULL AND s.status = :status "
          + "AND i.accountId = :accountId")
  List<Sale> findAllByStatusAndInvestorAccountId(
      @Param("status") RecordStatus status, @Param("accountId") Long accountId);

  /**
   * AllocationIdIn · 상태 조건으로 매도를 목록 조회한다.
   */
  @Query(
      "SELECT s FROM Sale s "
          + "WHERE s.allocationId IN ?1 AND s.status = ?2 AND s.deletedAt IS NULL")
  List<Sale> findByAllocationIdInAndStatus(List<Long> allocationIds, RecordStatus status);

  /**
   * 배정 ID·투자자 ID·투자자명 필터로 ACTIVE 매도를 조회한다.
   */
  @Query(
      "SELECT s FROM Sale s "
          + "JOIN Allocation a ON s.allocationId = a.id "
          + "JOIN Subscription sub ON a.subscriptionId = sub.id "
          + "JOIN Investor i ON sub.investorId = i.id "
          + "WHERE s.deletedAt IS NULL AND s.status = "
          + RecordStatus.ACTIVE_HQL
          + " "
          + "AND (:allocationId IS NULL OR s.allocationId = :allocationId) "
          + "AND (:investorId IS NULL OR i.id = :investorId) "
          + "AND (:investorName IS NULL OR i.displayName = :investorName) "
          + "ORDER BY s.saleDate DESC, s.createdAt DESC")
  List<Sale> findActiveWithFilters(
      @Param("allocationId") Long allocationId,
      @Param("investorId") Long investorId,
      @Param("investorName") String investorName);
}
