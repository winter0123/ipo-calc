package com.ipocalc.allocationmanagement.repository.allocation;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 배정 JPA 저장소. */
@Repository
public interface AllocationRepository
    extends JpaRepository<Allocation, Long>, AllocationQueryRepository {
  /** ID 조건(삭제되지 않은 건만)으로 배정을 단건 조회한다. */
  Optional<Allocation> findByIdAndDeletedAtIsNull(Long id);

  /** 삭제되지 않은 배정을 목록 조회한다(생성일 내림차순). */
  List<Allocation> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  /** 청약 ID · 상태 조건(삭제되지 않은 건만)으로 배정을 단건 조회한다. */
  Optional<Allocation> findBySubscriptionIdAndStatusAndDeletedAtIsNull(
      Long subscriptionId, RecordStatus status);

  /** 배치 조회: 청약 ID 목록에 연결된 삭제되지 않은 배정을 조회한다. */
  List<Allocation> findBySubscriptionIdInAndDeletedAtIsNull(List<Long> subscriptionIds);

  /** 배치 조회: ID 목록에 포함된 삭제되지 않은 배정을 조회한다. */
  List<Allocation> findByIdInAndDeletedAtIsNull(List<Long> allocationIds);

  /** 배치 조회 메서드의 기존 이름을 유지한다. */
  default List<Allocation> findBySubscriptionIdIn(List<Long> subscriptionIds) {
    return findBySubscriptionIdInAndDeletedAtIsNull(subscriptionIds);
  }

  /** 배치 조회 메서드의 기존 이름을 유지한다. */
  default List<Allocation> findByIdIn(List<Long> allocationIds) {
    return findByIdInAndDeletedAtIsNull(allocationIds);
  }
}
