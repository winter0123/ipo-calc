package com.ipocalc.notification.domain.model;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** 웹 푸시 구독(기기 하나). endpoint는 기기마다 유일하다. */
@Entity
@Table(name = "push_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {
  /** Creates an uninitialized subscription for the persistence workflow. */
  public static PushSubscription create() {
    return new PushSubscription();
  }

  /** Registers or reactivates a push subscription. */
  public void register(
      Long accountId, String endpoint, String p256dh, String auth, String userAgent) {
    this.accountId = accountId;
    this.endpoint = endpoint;
    this.p256dh = p256dh;
    this.auth = auth;
    this.userAgent = userAgent;
    this.status = RecordStatus.ACTIVE;
    this.deletedAt = null;
  }

  /** Records the latest successful push delivery time. */
  public void recordSuccess(LocalDateTime at) {
    this.lastSuccessAt = at;
  }

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Comment("푸시 구독 고유 식별자")
  private Long id;

  /** 구독을 소유한 로그인 계정 ID(accounts.id). */
  @Column(name = "account_id", nullable = false)
  @Comment("계정 ID")
  private Long accountId;

  /** 브라우저 푸시 서비스 주소(https). 비밀로 취급해 로그에 남기지 않는다. */
  @Column(name = "endpoint", nullable = false, length = 1000)
  @Comment("푸시 서비스 엔드포인트")
  private String endpoint;

  /** 브라우저 공개키(Base64URL). */
  @Column(name = "p256dh", nullable = false, length = 200)
  @Comment("브라우저 공개키(p256dh, Base64URL)")
  private String p256dh;

  /** 구독 인증 비밀값(Base64URL). */
  @Column(name = "auth", nullable = false, length = 100)
  @Comment("구독 인증값(auth, Base64URL)")
  private String auth;

  /** 구독한 브라우저 User-Agent. */
  @Column(name = "user_agent", length = 300)
  @Comment("브라우저 User-Agent")
  private String userAgent;

  /** 레코드 상태. ACTIVE / CANCELLED. */
  @Column(name = "status", nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  @Comment("레코드 상태(ACTIVE/CANCELLED)")
  private RecordStatus status = RecordStatus.ACTIVE;

  /** 소프트 삭제 시각. */
  @Column(name = "deleted_at")
  @Comment("소프트 삭제 시각")
  private LocalDateTime deletedAt;

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false, updatable = false)
  @Comment("생성 시각")
  private LocalDateTime createdAt;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at", nullable = false)
  @Comment("마지막 수정 시각")
  private LocalDateTime updatedAt;

  /** 마지막 발송 성공 시각. */
  @Column(name = "last_success_at")
  @Comment("마지막 발송 성공 시각")
  private LocalDateTime lastSuccessAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  /** 소프트 삭제한다. */
  public void cancel() {
    status = RecordStatus.CANCELLED;
    deletedAt = LocalDateTime.now();
  }
}
