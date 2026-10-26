package com.ipocalc.allocationmanagement.domain.model.allocation;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** 배정 JPA 엔티티. */
@Entity
@Table(name = "allocations")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Allocation {
  /** Updates the entity details and modification time. */
  public void updateDetails(
      Long subscriptionId,
      Long quantity,
      LocalDateTime allocatedAt,
      String method,
      Long price,
      String note,
      LocalDateTime now) {
    if (subscriptionId != null) {
      this.subscriptionId = subscriptionId;
    }
    if (quantity != null) {
      this.allocatedQuantity = quantity;
    }
    if (allocatedAt != null) {
      this.allocatedAt = allocatedAt;
    }
    if (method != null) {
      this.allocationMethod = method;
    }
    if (price != null) {
      this.allocationPrice = price;
    }
    if (note != null) {
      this.note = note;
    }
    this.updatedAt = now;
  }

  /** Cancels the record using a soft delete. */
  public void cancel(LocalDateTime now) {
    this.status = RecordStatus.CANCELLED;
    this.deletedAt = now;
    this.updatedAt = now;
  }

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Comment("배정 고유 식별자")
  private Long id;

  /** 배정 결과가 나온 청약 ID(subscriptions.id). 청약당 배정은 하나. */
  @Column(name = "subscription_id", nullable = false)
  @Comment("청약 ID")
  private Long subscriptionId;

  /** 실제로 배정받은 수량(주). 청약 수량 이하, 이미 판 수량 이상. */
  @Column(name = "allocated_quantity", nullable = false)
  @Comment("배정 수량(주)")
  private Long allocatedQuantity;

  /** 배정일. */
  @Column(name = "allocated_at", nullable = false)
  @Comment("배정일")
  private LocalDateTime allocatedAt;

  /** 배정 방식. 예: 비례배정 / 추첨배정(균등) / 선착순. 선택. */
  @Column(name = "allocation_method")
  @Comment("배정 방식(비례/추첨/선착순 등)")
  private String allocationMethod;

  /** 배정 단가(원, 보통 확정 공모가). 취득금액·총 투자금 계산 기준. 선택. */
  @Column(name = "allocation_price")
  @Comment("배정 단가(원)")
  private Long allocationPrice;

  /** 비고. 선택. */
  @Column(name = "note")
  @Comment("비고")
  private String note;

  /** 레코드 상태. ACTIVE(사용 중) / CANCELLED(삭제·취소됨). 물리 삭제는 하지 않는다. */
  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Comment("레코드 상태(ACTIVE/CANCELLED)")
  private RecordStatus status = RecordStatus.ACTIVE;

  /** 소프트 삭제 시각. null이면 살아 있는 레코드. */
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
}
