package com.ipocalc.ipocatalog.domain.model.ipo;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공모주 JPA 엔티티. */
@Entity
@Table(name = "ipos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ipo {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 회사명. */
  @Column(name = "company_name", nullable = false, length = 255)
  private String companyName;

  /** 공모주 이름(화면 표시용 종목명). */
  @Column(name = "ipo_name", nullable = false, length = 255)
  private String ipoName;

  /** 종목코드(6자리). 상장 전이면 null일 수 있다. 증권사 매도 내역 매칭에 쓴다. */
  @Column(name = "stock_code", length = 255)
  private String stockCode;

  /** DART 고유번호(8자리). 공시 기반 일정 동기화에 쓴다. */
  @Column(name = "corp_code", length = 8)
  private String corpCode;

  /** 회차 표시. 예: 1회차. 선택. */
  @Column(name = "round_label", length = 255)
  private String roundLabel;

  /** 희망 공모가 하단(원). */
  @Column(name = "lower_band_price")
  private Long lowerBandPrice;

  /** 희망 공모가 상단(원). */
  @Column(name = "upper_band_price")
  private Long upperBandPrice;

  /** 확정 공모가(원). */
  @Column(name = "fixed_price")
  private Long fixedPrice;

  /** 상장일 시초가(원). 금융위 주식시세 API로 상장 다음 영업일 이후 채운다. 모르면 null. */
  @Column(name = "listing_open_price")
  private Long listingOpenPrice;

  /** 청약 시작일. */
  @Column(name = "subscription_start_date", nullable = false)
  private LocalDate subscriptionStartDate;

  /** 청약 종료일. */
  @Column(name = "subscription_end_date", nullable = false)
  private LocalDate subscriptionEndDate;

  /** 상장일. 대시보드·수익 확인의 기간(월·연) 판단 기준. */
  @Column(name = "listing_date")
  private LocalDate listingDate;

  /** 배정 발표일. */
  @Column(name = "allotment_date")
  private LocalDate allotmentDate;

  /** 환불일(증거금 반환일). */
  @Column(name = "refund_date")
  private LocalDate refundDate;

  /** 공모 주식 수(주). */
  @Column(name = "offering_share_count")
  private Long offeringShareCount;

  /** 레코드 상태. ACTIVE(사용 중) / CANCELLED(삭제·취소됨). 물리 삭제는 하지 않는다. */
  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  private RecordStatus status = RecordStatus.ACTIVE;

  /** 자동 동기화 출처. OPENDART / OPENDART+USTOCKPLUS, 관리자가 직접 등록했으면 null. */
  @Column(name = "sync_source", length = 20)
  private String syncSource;

  /** 마지막 자동 동기화 시각. */
  @Column(name = "last_synced_at")
  private LocalDateTime lastSyncedAt;

  /** 소프트 삭제 시각. null이면 살아 있는 레코드. */
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** 상장일 시초가를 저장한다. null이면 기존 값을 지운다. */
  public void updateListingOpenPrice(Long listingOpenPrice) {
    this.listingOpenPrice = listingOpenPrice;
  }
}
