package com.vibeaiteam.ipocatalog.domain.model.ipo;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
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

/**
 * 공모주 JPA 엔티티.
 */
@Entity
@Table(name = "ipos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ipo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_name", nullable = false, length = 255)
  private String companyName;

  @Column(name = "ipo_name", nullable = false, length = 255)
  private String ipoName;

  @Column(name = "stock_code", length = 255)
  private String stockCode;

  @Column(name = "corp_code", length = 8)
  private String corpCode;

  @Column(name = "round_label", length = 255)
  private String roundLabel;

  @Column(name = "lower_band_price")
  private Long lowerBandPrice;

  @Column(name = "upper_band_price")
  private Long upperBandPrice;

  @Column(name = "fixed_price")
  private Long fixedPrice;

  @Column(name = "subscription_start_date", nullable = false)
  private LocalDate subscriptionStartDate;

  @Column(name = "subscription_end_date", nullable = false)
  private LocalDate subscriptionEndDate;

  @Column(name = "listing_date")
  private LocalDate listingDate;

  @Column(name = "allotment_date")
  private LocalDate allotmentDate;

  @Column(name = "refund_date")
  private LocalDate refundDate;

  @Column(name = "offering_share_count")
  private Long offeringShareCount;

  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  private RecordStatus status = RecordStatus.ACTIVE;

  @Column(name = "sync_source", length = 20)
  private String syncSource;

  @Column(name = "last_synced_at")
  private LocalDateTime lastSyncedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
