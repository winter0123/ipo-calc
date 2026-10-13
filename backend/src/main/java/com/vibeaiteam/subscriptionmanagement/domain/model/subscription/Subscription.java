package com.vibeaiteam.subscriptionmanagement.domain.model.subscription;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 청약 JPA 엔티티. */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 청약한 투자자 ID(investors.id). */
  @Column(name = "investor_id", nullable = false)
  private Long investorId;

  /** 청약에 쓴 증권계좌 ID(securities_accounts.id). */
  @Column(name = "securities_account_id", nullable = false)
  private Long securitiesAccountId;

  /** 청약한 공모주 ID(ipos.id). */
  @Column(name = "ipo_id", nullable = false)
  private Long ipoId;

  /** 청약 수량(주). */
  @Column(name = "quantity", nullable = false)
  private Long quantity;

  /** 청약 증거금(원). 기본은 공모가 × 수량 × 50%. */
  @Column(name = "deposit_amount", nullable = false)
  private Long depositAmount;

  /** 청약일. 화면에서는 날짜만 받고 09:00을 붙여 저장한다. */
  @Column(name = "subscribed_at", nullable = false)
  private LocalDateTime subscribedAt;

  /** 메모. 선택. */
  @Column(name = "memo")
  private String memo;

  /** 레코드 상태. ACTIVE(사용 중) / CANCELLED(삭제·취소됨). 물리 삭제는 하지 않는다. */
  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  private RecordStatus status = RecordStatus.ACTIVE;

  /** 소프트 삭제 시각. null이면 살아 있는 레코드. */
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
