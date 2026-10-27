package com.ipocalc.subscriptionmanagement.repository.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
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
 * AC 인용: docs/spec/subscription-management.md - AC-001: 청약 등록 성공 — 신규 Subscription 생성 시
 * status=ACTIVE, deleted_at=null - AC-005: 동일 (investorId, securitiesAccountId, ipoId) 조합의 ACTIVE
 * 기록 중복은 DB partial unique index로 방지 - AC-006: CANCELLED 기록이 있어도 동일 조합 재등록 허용 (중복 검사는 ACTIVE 기준) -
 * AC-007/008/009: 목록 조회(전체/투자자별/계좌별)는 deletedAt IS NULL만 노출, createdAt 내림차순 - AC-011/017/018:
 * 취소(CANCELLED + deletedAt)된 기록은 상세/목록에서 노출하지 않음.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class SubscriptionRepositoryTest {

  @Autowired private SubscriptionRepository subscriptionRepository;

  private Subscription buildSubscription(
      Long investorId, Long securitiesAccountId, Long ipoId, String memo, LocalDateTime createdAt) {
    return Subscription.builder()
        .investorId(investorId)
        .securitiesAccountId(securitiesAccountId)
        .ipoId(ipoId)
        .quantity(100L)
        .depositAmount(1000000L)
        .subscribedAt(createdAt)
        .memo(memo)
        .status(RecordStatus.ACTIVE)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  @Test
  void saveSubscriptionFindByIdReturnsSubscriptionWithActiveStatus() {
    // Given: 저장할 청약 기록이 존재함 (필수 필드 설정, status 기본값 ACTIVE)
    // - spec AC-001: 등록 시 status=ACTIVE, deleted_at=null로 생성된다

    LocalDateTime now = LocalDateTime.now();
    Subscription subscription = buildSubscription(1L, 2L, 3L, "비대면 청약", now);

    // When: 저장 후 findByIdAndDeletedAtIsNull로 조회
    Subscription saved = subscriptionRepository.save(subscription);
    Optional<Subscription> found = subscriptionRepository.findByIdAndDeletedAtIsNull(saved.getId());

    // Then: 저장된 청약이 id, 참조 키, quantity, memo, status로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getInvestorId()).isEqualTo(1L);
    assertThat(found.get().getSecuritiesAccountId()).isEqualTo(2L);
    assertThat(found.get().getIpoId()).isEqualTo(3L);
    assertThat(found.get().getQuantity()).isEqualTo(100L);
    assertThat(found.get().getDepositAmount()).isEqualTo(1000000L);
    assertThat(found.get().getMemo()).isEqualTo("비대면 청약");
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findAllByDeletedAtIsNullOrderByCreatedAtDescReturnsOnlyNonDeletedSubscriptions() {
    // Given: ACTIVE 청약 2건과 취소(CANCELLED + deletedAt)된 청약 1건이 저장되어 있음
    // - spec AC-018: 취소된 청약은 목록 조회에서 제외
    // - spec AC-007: 목록은 createdAt 내림차순 정렬

    LocalDateTime now = LocalDateTime.now();
    subscriptionRepository.save(buildSubscription(1L, 2L, 3L, "청약A", now));
    subscriptionRepository.save(buildSubscription(1L, 2L, 4L, "청약B", now.plusDays(1)));

    Subscription cancelled = buildSubscription(1L, 2L, 5L, "취소된 청약", now.minusDays(1));
    cancelled.cancel(now);
    subscriptionRepository.saveAndFlush(cancelled);

    // When: findAllByDeletedAtIsNullOrderByCreatedAtDesc 호출
    List<Subscription> result =
        subscriptionRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    // Then: 취소된 청약이 제외되고 createdAt 내림차순으로 정렬된 목록이 반환된다
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getIpoId()).isEqualTo(4L); // createdAt이 더 나중
    assertThat(result.get(1).getIpoId()).isEqualTo(3L);
  }

  @Test
  void findByIdAndDeletedAtIsNullExcludesCancelledSubscription() {
    // Given: ACTIVE 청약과 취소된 청약이 저장되어 있음
    // - spec AC-016/017: 취소(CANCELLED + deletedAt)된 기록은 조회에서 노출하지 않음

    LocalDateTime now = LocalDateTime.now();
    Subscription active = subscriptionRepository.save(buildSubscription(1L, 2L, 3L, null, now));

    Subscription cancelled = buildSubscription(1L, 2L, 4L, null, now);
    cancelled.cancel(now);
    Subscription savedCancelled = subscriptionRepository.saveAndFlush(cancelled);

    // When: 각각 findByIdAndDeletedAtIsNull 호출
    Optional<Subscription> activeResult =
        subscriptionRepository.findByIdAndDeletedAtIsNull(active.getId());
    Optional<Subscription> cancelledResult =
        subscriptionRepository.findByIdAndDeletedAtIsNull(savedCancelled.getId());

    // Then: ACTIVE 청약만 반환되고 취소된 청약은 반환되지 않는다
    assertThat(activeResult).isPresent();
    assertThat(activeResult.get().getIpoId()).isEqualTo(3L);
    assertThat(cancelledResult).isEmpty();
  }

  @Test
  void findAllByInvestorIdAndDeletedAtIsNullFiltersByInvestor() {
    // Given: 투자자 1의 청약 2건, 투자자 2의 청약 1건이 저장되어 있음
    // - spec AC-008: 투자자별 목록 조회는 해당 투자자의 기록만 반환

    LocalDateTime now = LocalDateTime.now();
    subscriptionRepository.save(buildSubscription(1L, 2L, 3L, null, now));
    subscriptionRepository.save(buildSubscription(1L, 2L, 4L, null, now.plusDays(1)));
    subscriptionRepository.save(buildSubscription(2L, 2L, 5L, null, now.plusDays(2)));

    // When: findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc 호출
    List<Subscription> result =
        subscriptionRepository.findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(1L);

    // Then: 투자자 1의 청약 2건만 createdAt 내림차순으로 반환된다
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getIpoId()).isEqualTo(4L);
    assertThat(result.get(1).getIpoId()).isEqualTo(3L);
  }

  @Test
  void findAllBySecuritiesAccountIdAndDeletedAtIsNullFiltersByAccount() {
    // Given: 계좌 2의 청약 2건, 계좌 7의 청약 1건이 저장되어 있음
    // - spec AC-009: 계좌별 목록 조회는 해당 계좌의 기록만 반환

    LocalDateTime now = LocalDateTime.now();
    subscriptionRepository.save(buildSubscription(1L, 2L, 3L, null, now));
    subscriptionRepository.save(buildSubscription(2L, 2L, 4L, null, now.plusDays(1)));
    subscriptionRepository.save(buildSubscription(3L, 7L, 5L, null, now.plusDays(2)));

    // When: findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc 호출
    List<Subscription> result =
        subscriptionRepository.findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            2L);

    // Then: 계좌 2의 청약 2건만 createdAt 내림차순으로 반환된다
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getIpoId()).isEqualTo(4L);
    assertThat(result.get(1).getIpoId()).isEqualTo(3L);
  }

  @Test
  void findByInvestorAccountIpoAndStatusReturnsActiveDuplicateRecord() {
    // Given: 동일 (investorId, securitiesAccountId, ipoId) 조합의 ACTIVE 청약 하나가 저장되어 있음
    // - spec AC-005: Service 계층 중복 검사는 이 메서드로 ACTIVE 기록을 탐지한다

    LocalDateTime now = LocalDateTime.now();
    subscriptionRepository.saveAndFlush(buildSubscription(1L, 2L, 3L, null, now));

    // When: 동일 조합 + status='ACTIVE' + deletedAt IS NULL 조건으로 조회
    Optional<Subscription> found =
        subscriptionRepository
            .findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
                1L, 2L, 3L, RecordStatus.ACTIVE);

    // Then: 등록 시 중복 검사에 사용할 ACTIVE 기록이 반환된다
    assertThat(found).isPresent();
    assertThat(found.get().getInvestorId()).isEqualTo(1L);
    assertThat(found.get().getSecuritiesAccountId()).isEqualTo(2L);
    assertThat(found.get().getIpoId()).isEqualTo(3L);
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void saveDuplicateActiveComboThrowsDataIntegrityViolationException() {
    // Given: 동일 (investorId, securitiesAccountId, ipoId) 조합의 ACTIVE 청약이 하나 저장되어 있음
    // - spec AC-005: uk_subscription_active_combo 부분 유니크 인덱스가 활성 청약 간 조합 중복을 방지
    // - DB 제약상 데이터 무결성 위반 예외로 변환된다

    LocalDateTime now = LocalDateTime.now();
    subscriptionRepository.saveAndFlush(buildSubscription(1L, 2L, 3L, "최초 청약", now));

    Subscription duplicate = buildSubscription(1L, 2L, 3L, "중복 청약", now.plusMinutes(1));

    // When & Then: 동일 조합 + ACTIVE로 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class,
        () -> subscriptionRepository.saveAndFlush(duplicate));
  }

  @Test
  void saveSameActiveComboAfterCancelAllowed() {
    // Given: 동일 조합의 ACTIVE 청약이 취소(CANCELLED + deletedAt)됨
    // - spec AC-006: CANCELLED 기록이 있어도 동일 조합 재등록은 허용 (중복 검사는 ACTIVE 기준)

    LocalDateTime now = LocalDateTime.now();
    Subscription existing =
        subscriptionRepository.save(buildSubscription(1L, 2L, 3L, "기존 청약", now));
    existing.cancel(now);
    subscriptionRepository.saveAndFlush(existing);

    // When: 동일 조합으로 새 ACTIVE 청약 저장
    Subscription reRegistered =
        subscriptionRepository.saveAndFlush(
            buildSubscription(1L, 2L, 3L, "재등록 청약", now.plusDays(1)));

    // Then: 유니크 위반 없이 저장된다 (ACTIVE 기록 간 중복만 제약)
    assertThat(reRegistered.getId()).isNotNull();
    assertThat(reRegistered.getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }
}
