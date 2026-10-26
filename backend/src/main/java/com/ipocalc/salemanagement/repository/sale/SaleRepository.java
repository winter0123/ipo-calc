package com.ipocalc.salemanagement.repository.sale;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 매도 JPA 저장소. */
@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, SaleQueryRepository {
  /** ID 조건(삭제되지 않은 건만)으로 매도를 단건 조회한다. */
  Optional<Sale> findByIdAndDeletedAtIsNull(Long id);

  /** 삭제되지 않은 매도를 목록 조회한다(매도일 내림차순, 생성일 내림차순). */
  List<Sale> findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc();

  /** 배정 ID · 상태 조건(삭제되지 않은 건만)으로 매도를 목록 조회한다. */
  List<Sale> findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(
      Long allocationId, RecordStatus status);

  /** 배정 ID 목록과 상태로 삭제되지 않은 매도를 매도일 내림차순으로 조회한다. */
  List<Sale> findByAllocationIdInAndStatusAndDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc(
      List<Long> allocationIds, RecordStatus status);

  /** 기존 배치 조회 메서드 이름을 유지한다. */
  default List<Sale> findByAllocationIdInAndStatus(List<Long> allocationIds, RecordStatus status) {
    return findByAllocationIdInAndStatusAndDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc(
        allocationIds, status);
  }
}
