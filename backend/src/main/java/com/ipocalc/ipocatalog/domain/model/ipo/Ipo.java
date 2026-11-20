package com.ipocalc.ipocatalog.domain.model.ipo;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
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
      this.lastVerifiedRefundReceiptNo = null;
    }
    if (subscriptionEndDate != null) {
      this.lastImportedSubscriptionEndDate = null;
    }
    this.listingDate = listingDate;
    this.refundDate = refundDate;
    this.subscriptionEndDate = subscriptionEndDate;
  }

  /** DART로 확인한 코드는 비어 있는 자동 수집 IPO에만 연결한다. 기존 값은 덮어쓰지 않는다. */
  public void fillMissingStockCode(String verifiedStockCode) {
    if (this.stockCode == null && verifiedStockCode != null) {
      this.stockCode = verifiedStockCode;
    }
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
    // 환불일 자체를 DART 원문에서 검증했을 때만 uStockPlus의 정정을 막는다.
    // 청약일 등 다른 일정의 최신 접수번호는 환불일 출처를 증명하지 않는다.
    if (refund != null
        && (this.refundDate == null || this.lastVerifiedRefundReceiptNo == null)
        && canAutoOverwrite(this.refundDate, this.lastImportedRefundDate)) {
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
    // 관리자가 가격을 수동 변경하면 과거 자동 출처를 제거해 이후 자동 갱신으로 덮어쓰지 않는다.
    if (!java.util.Objects.equals(this.lowerBandPrice, lower)
        || !java.util.Objects.equals(this.upperBandPrice, upper)) {
      this.bandPriceSourceReceiptNo = null;
      this.bandPriceParserVersion = null;
    }
    if (!java.util.Objects.equals(this.fixedPrice, fixed)) {
      this.fixedPriceSourceReceiptNo = null;
      this.fixedPriceParserVersion = null;
    }
    this.companyName = companyName;
    this.ipoName = ipoName;
    this.stockCode = stockCode;
    this.roundLabel = roundLabel;
    if (!java.util.Objects.equals(this.subscriptionStartDate, start)) {
      this.lastImportedSubscriptionStartDate = null;
    }
    if (!java.util.Objects.equals(this.subscriptionEndDate, end)) {
      this.lastImportedSubscriptionEndDate = null;
    }
    if (!java.util.Objects.equals(this.listingDate, listing)) {
      this.lastImportedListingDate = null;
    }
    if (!java.util.Objects.equals(this.offeringShareCount, shares)) {
      this.lastImportedOfferingShareCount = null;
    }
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
   * OpenDART 동기화 시 가격 필드를 제외한 필드만 반영한다. 가격은 별도 메서드 {@link
   * #applyVerifiedOfferingTerms(DartOfferingTerms, LocalDateTime)}에서 검증 후 적용한다.
   *
   * <p>{@code source_receipt_no}는 철회 매칭 보호를 위해 건드리지 않는다.
   *
   * @param companyName 회사명
   * @param shares 공모 주식 수
   * @param start 청약 시작일
   * @param end 청약 종료일
   * @param allotment 배정일
   * @param refund 환불일
   * @param source 동기화 출처
   * @param now 수정 시각
   */
  public void syncFromOpenDart(
      String companyName,
      Long shares,
      LocalDate start,
      LocalDate end,
      LocalDate allotment,
      LocalDate refund,
      String source,
      LocalDateTime now) {
    // 이름 수정 여부를 추적하는 별도 출처가 없으므로 기존 이름은 보존한다.
    if (this.companyName == null || this.companyName.isBlank()) {
      this.companyName = companyName;
    }
    if (this.ipoName == null || this.ipoName.isBlank()) {
      this.ipoName = companyName;
    }
    if (shares != null
        && (this.offeringShareCount == null
            || (this.lastImportedOfferingShareCount != null
                && this.lastImportedOfferingShareCount.equals(this.offeringShareCount)))) {
      this.offeringShareCount = shares;
      this.lastImportedOfferingShareCount = shares;
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

  /** Deactivates the credential. */
  public void deactivate(LocalDateTime now) {
    this.status = RecordStatus.INACTIVE;
    this.deletedAt = now;
    this.updatedAt = now;
  }

  /**
   * 원문에서 검증된 공모 조건(밴드·확정가)을 적용한다. 수동 레코드(syncSource == null)에는 호출하지 않는다.
   *
   * @param terms 원문 파싱 결과
   * @param now 수정 시각
   * @return 실제 변경이 있었으면 true
   */
  public boolean applyVerifiedOfferingTerms(DartOfferingTerms terms, LocalDateTime now) {
    if (this.syncSource == null) {
      // 수동 레코드는 자동 가격 적용 안 함
      return false;
    }

    boolean changed = false;

    // 가격 항목마다 최신 출처를 별도로 확인한다.
    if (terms.hasValidBand()
        && isVerifiedSourceCurrent(
            terms.sourceReceiptNo(),
            terms.parserVersion(),
            this.bandPriceSourceReceiptNo,
            this.bandPriceParserVersion,
            this.lowerBandPrice == null && this.upperBandPrice == null)) {
      if (!terms.lowerBandPrice().equals(this.lowerBandPrice)
          || !terms.upperBandPrice().equals(this.upperBandPrice)
          || !java.util.Objects.equals(terms.sourceReceiptNo(), this.bandPriceSourceReceiptNo)
          || !java.util.Objects.equals(terms.parserVersion(), this.bandPriceParserVersion)) {
        this.lowerBandPrice = terms.lowerBandPrice();
        this.upperBandPrice = terms.upperBandPrice();
        this.bandPriceSourceReceiptNo = terms.sourceReceiptNo();
        this.bandPriceParserVersion = terms.parserVersion();
        changed = true;
      }
    }

    if (terms.hasConfirmedFixedPrice()
        && isVerifiedSourceCurrent(
            terms.sourceReceiptNo(),
            terms.parserVersion(),
            this.fixedPriceSourceReceiptNo,
            this.fixedPriceParserVersion,
            this.fixedPrice == null)) {
      if (!terms.fixedPrice().equals(this.fixedPrice)
          || !java.util.Objects.equals(terms.sourceReceiptNo(), this.fixedPriceSourceReceiptNo)
          || !java.util.Objects.equals(terms.parserVersion(), this.fixedPriceParserVersion)) {
        this.fixedPrice = terms.fixedPrice();
        this.fixedPriceSourceReceiptNo = terms.sourceReceiptNo();
        this.fixedPriceParserVersion = terms.parserVersion();
        changed = true;
      }
    }

    // 밴드 또는 확정가 하나만 유효한 경우 다른 정상 값은 지우지 않음 (기존 값 유지)

    if (changed) {
      this.lastSyncedAt = now;
      this.updatedAt = now;
    }

    return changed;
  }

  /** 접수번호가 명확한 원문만 기존 자동 가격을 갱신한다. 출처 없는 기존 수동 가능 가격은 변경하지 않고, 같은 접수번호의 새 파서 버전만 허용한다. */
  private static boolean isVerifiedSourceCurrent(
      String incomingReceipt,
      int incomingVersion,
      String storedReceipt,
      Integer storedVersion,
      boolean emptyUntrackedValue) {
    if (incomingReceipt == null || incomingReceipt.isBlank()) {
      return false;
    }
    if (storedReceipt == null || storedReceipt.isBlank()) {
      return emptyUntrackedValue;
    }
    int order = incomingReceipt.compareTo(storedReceipt);
    return order > 0 || (order == 0 && (storedVersion == null || incomingVersion >= storedVersion));
  }

  /** 주식 수만 갱신한다 (가격 필드 변경 없음). 기존 {@link #syncBandAndShares}를 대체하며, 가격은 건드리지 않는다. */
  public void syncShareCountOnly(Long offeringShareCount, LocalDateTime now) {
    if (offeringShareCount != null) {
      this.offeringShareCount = offeringShareCount;
      this.updatedAt = now;
    }
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

  /** 밴드 가격 출처 공시 접수번호 (원문 파서가 검증한 경우만). */
  @Column(name = "band_price_source_receipt_no", length = 20)
  @Comment("밴드 가격 출처 공시 접수번호")
  private String bandPriceSourceReceiptNo;

  /** 밴드 가격 파서 버전. */
  @Column(name = "band_price_parser_version")
  @Comment("밴드 가격 파서 버전")
  private Integer bandPriceParserVersion;

  /** 확정가 출처 공시 접수번호 (원문 파서가 검증한 경우만). */
  @Column(name = "fixed_price_source_receipt_no", length = 20)
  @Comment("확정가 출처 공시 접수번호")
  private String fixedPriceSourceReceiptNo;

  /** 확정가 파서 버전. */
  @Column(name = "fixed_price_parser_version")
  @Comment("확정가 파서 버전")
  private Integer fixedPriceParserVersion;

  /** 자동 수집이 마지막으로 반영한 청약 시작일. 출처 불명·수동 입력이면 null로 보호한다. */
  @Column(name = "last_imported_subscription_start_date")
  private LocalDate lastImportedSubscriptionStartDate;

  /** 자동 수집이 마지막으로 반영한 청약 종료일. 출처 불명·수동 입력이면 null로 보호한다. */
  @Column(name = "last_imported_subscription_end_date")
  private LocalDate lastImportedSubscriptionEndDate;

  /** 자동 수집이 마지막으로 확인한 주식 수. 수동 변경 및 기존 출처 불명 값은 보존한다. */
  @Column(name = "last_imported_offering_share_count")
  private Long lastImportedOfferingShareCount;

  /** 확인된 원문 일정에 적용된 최신 공시 접수번호. */
  @Column(name = "last_verified_schedule_receipt_no", length = 20)
  private String lastVerifiedScheduleReceiptNo;

  /** DART 원문에서 환불일을 실제로 검증하고 반영한 접수번호. 청약일 출처와 독립적이다. */
  @Column(name = "last_verified_refund_receipt_no", length = 20)
  private String lastVerifiedRefundReceiptNo;

  /** DART가 확인한 환불일이 실제 저장되었을 때만 환불일 출처를 남긴다. */
  public void recordVerifiedRefundReceipt(String receipt, LocalDate verifiedRefund) {
    if (receipt == null
        || receipt.isBlank()
        || verifiedRefund == null
        || !verifiedRefund.equals(refundDate)
        || !verifiedRefund.equals(lastImportedRefundDate)
        || (lastVerifiedRefundReceiptNo != null
            && receipt.compareTo(lastVerifiedRefundReceiptNo) < 0)) {
      return;
    }
    lastVerifiedRefundReceiptNo = receipt;
  }

  /** 확인된 DS006 수량에 적용된 최신 공시 접수번호. */
  @Column(name = "last_verified_share_receipt_no", length = 20)
  private String lastVerifiedShareReceiptNo;

  /** 검증된 공시 접수번호가 이전 원문보다 새로울 때만 일정 갱신을 허용한다. */
  public boolean mayApplyVerifiedSchedule(String incomingReceipt) {
    return incomingReceipt != null
        && !incomingReceipt.isBlank()
        && (lastVerifiedScheduleReceiptNo == null
            || incomingReceipt.compareTo(lastVerifiedScheduleReceiptNo) >= 0);
  }

  /** 유효한 일정 원문을 처리한 접수번호를 기록한다. 원본 공시 식별자와 분리한다. */
  public void recordVerifiedScheduleReceipt(String incomingReceipt) {
    if (mayApplyVerifiedSchedule(incomingReceipt)) {
      this.lastVerifiedScheduleReceiptNo = incomingReceipt;
    }
  }

  /** 접수번호별 최신성 및 수동값 보존 규칙을 동시에 적용하여 공모주식 수를 기록한다. */
  public void applyVerifiedShareCount(Long shares, String incomingReceipt) {
    if (shares == null
        || shares <= 0
        || incomingReceipt == null
        || incomingReceipt.isBlank()
        || (lastVerifiedShareReceiptNo != null
            && incomingReceipt.compareTo(lastVerifiedShareReceiptNo) < 0)) {
      return;
    }
    if (offeringShareCount == null
        || (lastImportedOfferingShareCount != null
            && lastImportedOfferingShareCount.equals(offeringShareCount))) {
      offeringShareCount = shares;
      lastImportedOfferingShareCount = shares;
      lastVerifiedShareReceiptNo = incomingReceipt;
    }
  }

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
