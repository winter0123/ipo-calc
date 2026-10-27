package com.ipocalc.allocationmanagement.repository.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/spec/allocation-management.md - AC-001: 배정 등록 성공 — 신규 Allocation 생성 시 status=ACTIVE,
 * deletedAt=null - AC-005: 동일 subscriptionId의 ACTIVE 기록 중복은 DB partial unique index로 방지 (1:1,
 * D-A-002) - AC-006: CANCELLED 기록이 있어도 동일 subscriptionId 재등록 허용 (중복 검사는 ACTIVE 기준) - AC-018:
 * 취소(CANCELLED + deletedAt)된 기록은 목록/상세 조회에서 노출하지 않음
 *
 * <p>allocations.subscription_id는 subscriptions.id를 참조하는 FK(ON DELETE RESTRICT)이므로, 각 테스트는 배정을 저장하기
 * 전에 참조할 Subscription을 먼저 저장한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class AllocationRepositoryTest {

  @Autowired private AllocationRepository allocationRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  private Long createSubscription(Long ipoId, LocalDateTime createdAt) {
    Subscription subscription =
        Subscription.builder()
            .investorId(1L)
            .securitiesAccountId(1L)
            .ipoId(ipoId)
            .quantity(100L)
            .depositAmount(1000000L)
            .subscribedAt(createdAt)
            .status(RecordStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    return subscriptionRepository.saveAndFlush(subscription).getId();
  }

  private Allocation buildAllocation(Long subscriptionId, String note, LocalDateTime createdAt) {
    return Allocation.builder()
        .subscriptionId(subscriptionId)
        .allocatedQuantity(50L)
        .allocatedAt(createdAt)
        .allocationMethod("균등배정")
        .allocationPrice(10000L)
        .note(note)
        .status(RecordStatus.ACTIVE)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  @Test
  void saveAllocationFindByIdReturnsAllocationWithActiveStatus() {
    // Given: 저장할 배정 기록이 존재함 (필수 필드 설정, status 기본값 ACTIVE)
    // - spec AC-001: 등록 시 status=ACTIVE, deletedAt=null로 생성된다

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Allocation allocation = buildAllocation(subscriptionId, "비고", now);

    // When: 저장 후 findByIdAndDeletedAtIsNull로 조회
    Allocation saved = allocationRepository.save(allocation);
    Optional<Allocation> found = allocationRepository.findByIdAndDeletedAtIsNull(saved.getId());

    // Then: 저장된 배정이 id, subscriptionId, 배정정보로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getSubscriptionId()).isEqualTo(subscriptionId);
    assertThat(found.get().getAllocatedQuantity()).isEqualTo(50L);
    assertThat(found.get().getAllocationMethod()).isEqualTo("균등배정");
    assertThat(found.get().getAllocationPrice()).isEqualTo(10000L);
    assertThat(found.get().getNote()).isEqualTo("비고");
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findAllByDeletedAtIsNullOrderByCreatedAtDescReturnsOnlyNonDeletedAllocations() {
    // Given: ACTIVE 배정 2건과 취소(CANCELLED + deletedAt)된 배정 1건이 저장되어 있음
    // - spec AC-018: 취소된 배정은 목록 조회에서 제외

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionA = createSubscription(1L, now);
    Long subscriptionB = createSubscription(2L, now.plusDays(1));
    Long subscriptionC = createSubscription(3L, now.minusDays(1));

    allocationRepository.save(buildAllocation(subscriptionA, "배정A", now));
    allocationRepository.save(buildAllocation(subscriptionB, "배정B", now.plusDays(1)));

    Allocation cancelled = buildAllocation(subscriptionC, "취소된 배정", now.minusDays(1));
    cancelled.cancel(now);
    allocationRepository.saveAndFlush(cancelled);

    // When: findAllByDeletedAtIsNullOrderByCreatedAtDesc 호출
    List<Allocation> result = allocationRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    // Then: 취소된 배정이 제외되고 createdAt 내림차순으로 정렬된 목록이 반환된다
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSubscriptionId()).isEqualTo(subscriptionB); // createdAt이 더 나중
    assertThat(result.get(1).getSubscriptionId()).isEqualTo(subscriptionA);
  }

  @Test
  void findByIdAndDeletedAtIsNullExcludesCancelledAllocation() {
    // Given: ACTIVE 배정과 취소된 배정이 저장되어 있음
    // - spec AC-011: 취소(CANCELLED + deletedAt)된 기록은 상세 조회에서 노출하지 않음

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionA = createSubscription(1L, now);
    Long subscriptionB = createSubscription(2L, now);

    Allocation active = allocationRepository.save(buildAllocation(subscriptionA, null, now));

    Allocation cancelled = buildAllocation(subscriptionB, null, now);
    cancelled.cancel(now);
    Allocation savedCancelled = allocationRepository.saveAndFlush(cancelled);

    // When: 각각 findByIdAndDeletedAtIsNull 호출
    Optional<Allocation> activeResult =
        allocationRepository.findByIdAndDeletedAtIsNull(active.getId());
    Optional<Allocation> cancelledResult =
        allocationRepository.findByIdAndDeletedAtIsNull(savedCancelled.getId());

    // Then: ACTIVE 배정만 반환되고 취소된 배정은 반환되지 않는다
    assertThat(activeResult).isPresent();
    assertThat(activeResult.get().getSubscriptionId()).isEqualTo(subscriptionA);
    assertThat(cancelledResult).isEmpty();
  }

  @Test
  void findBySubscriptionIdAndStatusAndDeletedAtIsNullReturnsActiveDuplicateRecord() {
    // Given: 동일 subscriptionId의 ACTIVE 배정 하나가 저장되어 있음
    // - spec AC-005: Service 계층 중복 검사는 이 메서드로 ACTIVE 기록을 탐지한다

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    allocationRepository.saveAndFlush(buildAllocation(subscriptionId, null, now));

    // When: 동일 subscriptionId + status='ACTIVE' + deletedAt IS NULL 조건으로 조회
    Optional<Allocation> found =
        allocationRepository.findBySubscriptionIdAndStatusAndDeletedAtIsNull(
            subscriptionId, RecordStatus.ACTIVE);

    // Then: 등록 시 중복 검사에 사용할 ACTIVE 기록이 반환된다
    assertThat(found).isPresent();
    assertThat(found.get().getSubscriptionId()).isEqualTo(subscriptionId);
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void saveDuplicateActiveSubscriptionThrowsDataIntegrityViolationException() {
    // Given: 동일 subscriptionId의 ACTIVE 배정이 하나 저장되어 있음
    // - spec AC-005: uk_allocation_active_subscription 부분 유니크 인덱스가 활성 배정 간 중복을 방지 (1:1, D-A-002)
    // - DB 제약상 데이터 무결성 위반 예외로 변환된다

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    allocationRepository.saveAndFlush(buildAllocation(subscriptionId, "최초 배정", now));

    Allocation duplicate = buildAllocation(subscriptionId, "중복 배정", now.plusMinutes(1));

    // When & Then: 동일 subscriptionId + ACTIVE로 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class, () -> allocationRepository.saveAndFlush(duplicate));
  }

  @Test
  void saveSameSubscriptionAfterCancelAllowed() {
    // Given: 동일 subscriptionId의 ACTIVE 배정이 취소(CANCELLED + deletedAt)됨
    // - spec AC-006: CANCELLED 기록이 있어도 동일 subscriptionId 재등록은 허용 (중복 검사는 ACTIVE 기준)

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Allocation existing = allocationRepository.save(buildAllocation(subscriptionId, "기존 배정", now));
    existing.cancel(now);
    allocationRepository.saveAndFlush(existing);

    // When: 동일 subscriptionId로 새 ACTIVE 배정 저장
    Allocation reRegistered =
        allocationRepository.saveAndFlush(
            buildAllocation(subscriptionId, "재등록 배정", now.plusDays(1)));

    // Then: 유니크 위반 없이 저장된다 (ACTIVE 기록 간 중복만 제약)
    assertThat(reRegistered.getId()).isNotNull();
    assertThat(reRegistered.getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }
}
