package com.ipocalc.salemanagement.domain.model.sale;

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

/** 매도 JPA 엔티티. */
@Entity
@Table(name = "sales")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sale {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 매도한 배정 ID(allocations.id). 한 배정을 여러 번 나눠 팔 수 있다. */
  @Column(name = "allocation_id", nullable = false)
  private Long allocationId;

  /** 매도 수량(주). */
  @Column(name = "quantity", nullable = false)
  private Long quantity;

  /** 매도 단가(원). */
  @Column(name = "price")
  private Long price;

  /** 매도 금액(원) = 매도 단가 × 수량. */
  @Column(name = "amount")
  private Long amount;

  /** 매도일. 화면에서는 날짜만 받고 09:00을 붙여 저장한다. */
  @Column(name = "sale_date", nullable = false)
  private LocalDateTime saleDate;

  /** 공모주 수수료(원, 청약 수수료 등. 기본 2,000원). 매도 입력 개편(2026-10-02) 전에 저장된 행은 매도 수수료 의미. */
  @Column(name = "fee")
  private Long fee;

  /** 세금 및 기타 수수료(원, 증권거래세+매도 수수료 등). 개편 전에 저장된 행은 세금만. */
  @Column(name = "tax")
  private Long tax;

  /** 기타 비용(원). 값이 있으면 수익 계산에서 빼지만, 지금 화면은 입력받지 않는다(API로만 들어옴). */
  @Column(name = "other_cost")
  private Long otherCost;

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
