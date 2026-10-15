package com.ipocalc.notification.repository;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.notification.domain.model.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 푸시 구독 저장소. */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

  /** endpoint(기기 주소)로 구독을 찾는다. */
  Optional<PushSubscription> findByEndpoint(String endpoint);

  /** 계정의 특정 상태 구독 목록. */
  List<PushSubscription> findByAccountIdAndStatus(Long accountId, RecordStatus status);
}
