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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 배정 JPA 엔티티. */
@Entity
@Table(name = "allocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Allocation {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 배정 결과가 나온 청약 ID(subscriptions.id). 청약당 배정은 하나. */
  @Column(name = "subscription_id", nullable = false)
  private Long subscriptionId;

  /** 실제로 배정받은 수량(주). 청약 수량 이하, 이미 판 수량 이상. */
  @Column(name = "allocated_quantity", nullable = false)
  private Long allocatedQuantity;

  /** 배정일. */
  @Column(name = "allocated_at", nullable = false)
  private LocalDateTime allocatedAt;

  /** 배정 방식. 예: 비례배정 / 추첨배정(균등) / 선착순. 선택. */
  @Column(name = "allocation_method")
  private String allocationMethod;

  /** 배정 단가(원, 보통 확정 공모가). 취득금액·총 투자금 계산 기준. 선택. */
  @Column(name = "allocation_price")
  private Long allocationPrice;

  /** 비고. 선택. */
  @Column(name = "note")
  private String note;

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
