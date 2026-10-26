package com.ipocalc.subscriptionmanagement.repository.subscription;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 청약 JPA 저장소. */
@Repository
public interface SubscriptionRepository
    extends JpaRepository<Subscription, Long>, SubscriptionQueryRepository {
  /** ID 조건(삭제되지 않은 건만)으로 청약을 단건 조회한다. */
  Optional<Subscription> findByIdAndDeletedAtIsNull(Long id);

  /** 삭제되지 않은 청약을 목록 조회한다(생성일 내림차순). */
  List<Subscription> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  /** 투자자 ID 조건(삭제되지 않은 건만)으로 청약을 목록 조회한다(생성일 내림차순). */
  List<Subscription> findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long investorId);

  /** 증권계좌 ID 조건(삭제되지 않은 건만)으로 청약을 목록 조회한다(생성일 내림차순). */
  List<Subscription> findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      Long securitiesAccountId);

  /** 투자자 ID · 증권계좌 ID · 공모주 ID · 상태 조건(삭제되지 않은 건만)으로 청약을 단건 조회한다. */
  Optional<Subscription> findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
      Long investorId, Long securitiesAccountId, Long ipoId, RecordStatus status);
}
