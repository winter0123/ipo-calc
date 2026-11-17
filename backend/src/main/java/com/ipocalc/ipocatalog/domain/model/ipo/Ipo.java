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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** 공모주 JPA 엔티티. */
@Entity
@Table(name = "ipos")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Ipo {
  /** Corrects the IPO schedule dates. 수동/정정 반영이므로 수집 원본 추적값을 무효화한다. */
  public void correctSchedule(
      LocalDate listingDate, LocalDate refundDate, LocalDate subscriptionEndDate) {
    if (listingDate != null) {
      this.lastImportedListingDate = null;
    }
    if (refundDate != null) {
      this.lastImportedRefundDate = null;
    }
    if (subscriptionEndDate != null) {
      this.lastImportedSubscriptionEndDate = null;
    }
    this.listingDate = listingDate;
    this.refundDate = refundDate;
    this.subscriptionEndDate = subscriptionEndDate;
  }

  /** 보조 일정의 빈 필드와 수집 상장일·환불일 정정을 반영하고 수동 값은 보존한다. */
  public boolean mergeSupplementalSchedule(
      String stockCode,
      LocalDate allotment,
      LocalDate refund,
      LocalDate listing,
      String source,
      LocalDateTime now) {
    boolean changed = false;
    if (stockCode != null && !stockCode.equals(this.stockCode)) {
      this.stockCode = stockCode;
      changed = true;
    }
    if (this.allotmentDate == null && allotment != null) {
      this.allotmentDate = allotment;
      changed = true;
    }
    if (canAutoOverwrite(this.refundDate, this.lastImportedRefundDate) && refund != null) {
      if (!refund.equals(this.refundDate)) {
        this.refundDate = refund;
        this.lastImportedRefundDate = refund;
        changed = true;
      }
    }
    if (listing != null
        && (this.listingDate == null || this.listingDate.equals(this.lastImportedListingDate))
        && !listing.equals(this.listingDate)) {
      this.listingDate = listing;
      this.listingOpenPrice = null;
      this.lastImportedListingDate = listing;
      changed = true;
    }
    if (changed) {
      this.syncSource = source;
      this.lastSyncedAt = now;
      this.updatedAt = now;
    }
    return changed;
  }

  /** Updates the entity details and modification time. */
  public void updateDetails(
      String companyName,
      String ipoName,
      String stockCode,
      String roundLabel,
      LocalDate start,
      LocalDate end,
      Long lower,
      Long upper,
      Long fixed,
      LocalDate listing,
      Long shares,
      LocalDateTime now) {
    this.companyName = companyName;
    this.ipoName = ipoName;
    this.stockCode = stockCode;
    this.roundLabel = roundLabel;
    this.subscriptionStartDate = start;
    this.subscriptionEndDate = end;
    this.lowerBandPrice = lower;
    this.upperBandPrice = upper;
    this.fixedPrice = fixed;
    this.listingDate = listing;
    this.offeringShareCount = shares;
    this.updatedAt = now;
  }

  /**
   * OpenDART 동기화 시 공모가 변경 사항만 반영한다.
   *
   * <p>정정·발행조건확정 공시가 아니어서 전체 갱신을 보류하더라도, 밴드 가격과 공모 주식 수는 최신 공시 값으로 갱신한다. {@code source_receipt_no}는
   * 철회 매칭 보호를 위해 건드리지 않는다. {@code fixedPrice}는 호출자가 별도로 결정해 넘기며, 확정 공시가 아니면 기존 값을 보존한다.
   *
   * @param lowerBandPrice 희망 공모가 하단
   * @param upperBandPrice 희망 공모가 상단
   * @param offeringShareCount 공모 주식 수
   * @param now 수정 시각
   */
  public void syncBandAndShares(
      Long lowerBandPrice, Long upperBandPrice, Long offeringShareCount, LocalDateTime now) {
    if (lowerBandPrice != null) {
      this.lowerBandPrice = lowerBandPrice;
    }
    if (upperBandPrice != null) {
      this.upperBandPrice = upperBandPrice;
    }
    if (offeringShareCount != null) {
      this.offeringShareCount = offeringShareCount;
    }
    this.updatedAt = now;
  }

  /** Deactivates the credential. */
  public void deactivate(LocalDateTime now) {
    this.status = RecordStatus.INACTIVE;
    this.deletedAt = now;
    this.updatedAt = now;
  }

  /** Applies IPO fields from an OpenDART synchronization. 청약/환불 일정은 수집 원본 추적값을 따른다. */
  public void syncFromOpenDart(
      String companyName,
      Long offerPrice,
      Long confirmedPrice,
      Long shares,
      LocalDate start,
      LocalDate end,
      LocalDate allotment,
      LocalDate refund,
      String source,
      LocalDateTime now) {
    this.companyName = companyName;
    this.ipoName = companyName;
    boolean isAutoSync = this.syncSource != null;
    if (offerPrice != null) {
      this.lowerBandPrice = offerPrice;
      this.upperBandPrice = offerPrice;
    }
    if (confirmedPrice != null) {
      // 수동 레코드(syncSource == null): fixedPrice 없으면만 설정(수동 입력 보호)
      // 자동 동기화 레코드(syncSource != null): 확정공모 공시 왔으면 무조건 업데이트
      if (!isAutoSync) {
        if (this.fixedPrice == null) {
          this.fixedPrice = confirmedPrice;
        }
      } else {
        this.fixedPrice = confirmedPrice;
      }
    }
    if (shares != null) {
      this.offeringShareCount = shares;
    }
    if (start != null
        && canAutoOverwrite(this.subscriptionStartDate, this.lastImportedSubscriptionStartDate)) {
      this.subscriptionStartDate = start;
      this.lastImportedSubscriptionStartDate = start;
    }
    if (end != null
        && canAutoOverwrite(this.subscriptionEndDate, this.lastImportedSubscriptionEndDate)) {
      this.subscriptionEndDate = end;
      this.lastImportedSubscriptionEndDate = end;
    }
    if (allotment != null) {
      this.allotmentDate = allotment;
    }
    if (refund != null && canAutoOverwrite(this.refundDate, this.lastImportedRefundDate)) {
      this.refundDate = refund;
      this.lastImportedRefundDate = refund;
    }
    this.syncSource = source;
    this.lastSyncedAt = now;
    this.updatedAt = now;
  }

  /** 수집 원본(수집기 저장 시점) 규칙: 현재 값이 없거나 추적 값과 같으면 자동 정정을 허용한다. */
  private static boolean canAutoOverwrite(LocalDate current, LocalDate tracked) {
    return current == null || (tracked != null && tracked.equals(current));
  }

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Comment("공모주 고유 식별자")
  private Long id;

  /** 회사명. */
  @Column(name = "company_name", nullable = false, length = 255)
  @Comment("회사명")
  private String companyName;

  /** 공모주 이름(화면 표시용 종목명). */
  @Column(name = "ipo_name", nullable = false, length = 255)
  @Comment("공모주 표시명")
  private String ipoName;

  /** 종목코드(6자리). 상장 전이면 null일 수 있다. 증권사 매도 내역 매칭에 쓴다. */
  @Column(name = "stock_code", length = 255)
  @Comment("종목코드(6자리)")
  private String stockCode;

  /** DART 고유번호(8자리). 공시 기반 일정 동기화에 쓴다. */
  @Column(name = "corp_code", length = 8)
  @Comment("DART 고유번호(8자리)")
  private String corpCode;

  /** 회차 표시. 예: 1회차. 선택. */
  @Column(name = "round_label", length = 255)
  @Comment("회차 표시(예: 1회차)")
  private String roundLabel;

  /** 희망 공모가 하단(원). */
  @Column(name = "lower_band_price")
  @Comment("희망 공모가 하단(원)")
  private Long lowerBandPrice;

  /** 희망 공모가 상단(원). */
  @Column(name = "upper_band_price")
  @Comment("희망 공모가 상단(원)")
  private Long upperBandPrice;

  /** 확정 공모가(원). */
  @Column(name = "fixed_price")
  @Comment("확정 공모가(원)")
  private Long fixedPrice;

  /** 상장일 시초가(원). 금융위 주식시세 API로 상장 다음 영업일 이후 채운다. 모르면 null. */
  @Column(name = "listing_open_price")
  @Comment("상장일 시초가(원)")
  private Long listingOpenPrice;

  /** 청약 시작일. */
  @Column(name = "subscription_start_date", nullable = false)
  @Comment("청약 시작일")
  private LocalDate subscriptionStartDate;

  /** 청약 종료일. */
  @Column(name = "subscription_end_date", nullable = false)
  @Comment("청약 종료일")
  private LocalDate subscriptionEndDate;

  /** 상장일. 대시보드·수익 확인의 기간(월·연) 판단 기준. */
  @Column(name = "listing_date")
  @Comment("상장일")
  private LocalDate listingDate;

  /** 배정 발표일. */
  @Column(name = "allotment_date")
  @Comment("배정 발표일")
  private LocalDate allotmentDate;

  /** 환불일(증거금 반환일). */
  @Column(name = "refund_date")
  @Comment("환불일(증거금 반환일)")
  private LocalDate refundDate;

  /** 공모 주식 수(주). */
  @Column(name = "offering_share_count")
  @Comment("공모 주식 수(주)")
  private Long offeringShareCount;

  /** 레코드 상태. ACTIVE(사용 중) / CANCELLED(삭제·취소됨). 물리 삭제는 하지 않는다. */
  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Comment("레코드 상태(ACTIVE/INACTIVE/CANCELLED)")
  private RecordStatus status = RecordStatus.ACTIVE;

  /** 자동 동기화 출처. OPENDART / OPENDART+USTOCKPLUS, 관리자가 직접 등록했으면 null. */
  @Column(name = "sync_source", length = 20)
  @Comment("자동 동기화 출처")
  private String syncSource;

  /** 마지막으로 보조 일정에서 반영한 상장일. 기존 값의 출처가 불명확하면 null로 보호한다. */
  @Column(name = "last_imported_listing_date")
  private LocalDate lastImportedListingDate;

  /** 철회된 공모의 접수번호. 같은 공시의 재수집으로 복원하지 않는다. */
  @Column(name = "withdrawn_receipt_no", length = 20)
  private String withdrawnReceiptNo;

  /** 이 행의 데이터를 만든 최신 DS006 접수번호(자동 수집 행만). 철회 점검 시 원공시 매칭에 쓴다. */
  @Column(name = "source_receipt_no", length = 20)
  @Comment("이 행의 데이터를 만든 최신 DS006 접수번호")
  private String sourceReceiptNo;

  /** 자동 수집이 마지막으로 반영한 청약 시작일. 출처 불명·수동 입력이면 null로 보호한다. */
  @Column(name = "last_imported_subscription_start_date")
  private LocalDate lastImportedSubscriptionStartDate;

  /** 자동 수집이 마지막으로 반영한 청약 종료일. 출처 불명·수동 입력이면 null로 보호한다. */
  @Column(name = "last_imported_subscription_end_date")
  private LocalDate lastImportedSubscriptionEndDate;

  /** 자동 수집이 마지막으로 반영한 환불일. 출처 불명·수동 입력이면 null로 보호한다. */
  @Column(name = "last_imported_refund_date")
  private LocalDate lastImportedRefundDate;

  /** 관리자 입력은 보조 일정의 자동 정정 대상에서 제외한다. */
  public void protectListingDate() {
    this.lastImportedListingDate = null;
  }

  /** 관리자 입력(청약 시작일)은 자동 정정 대상에서 제외한다. */
  public void protectSubscriptionStartDate() {
    this.lastImportedSubscriptionStartDate = null;
  }

  /** 관리자 입력(청약 종료일)은 자동 정정 대상에서 제외한다. */
  public void protectSubscriptionEndDate() {
    this.lastImportedSubscriptionEndDate = null;
  }

  /** 자동 수집이 이 행에 반영한 DS006 접수번호를 기록한다. */
  public void recordSyncSourceReceipt(String receiptNo) {
    this.sourceReceiptNo = receiptNo;
  }

  /** 해당 공모를 철회하고 이력은 보존한다. */
  public void withdraw(String receiptNo, LocalDateTime now) {
    this.withdrawnReceiptNo = receiptNo;
    deactivate(now);
  }

  /** 마지막 자동 동기화 시각. */
  @Column(name = "last_synced_at")
  @Comment("마지막 자동 동기화 시각")
  private LocalDateTime lastSyncedAt;

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

  /** 상장일 시초가를 저장한다. null이면 기존 값을 지운다. */
  public void updateListingOpenPrice(Long listingOpenPrice) {
    this.listingOpenPrice = listingOpenPrice;
  }
}
