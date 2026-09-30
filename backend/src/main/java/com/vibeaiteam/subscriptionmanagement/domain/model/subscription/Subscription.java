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

/**
 * 청약 JPA 엔티티.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "investor_id", nullable = false)
  private Long investorId;

  @Column(name = "securities_account_id", nullable = false)
  private Long securitiesAccountId;

  @Column(name = "ipo_id", nullable = false)
  private Long ipoId;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "deposit_amount", nullable = false)
  private Long depositAmount;

  @Column(name = "subscribed_at", nullable = false)
  private LocalDateTime subscribedAt;

  @Column(name = "memo")
  private String memo;

  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  private RecordStatus status = RecordStatus.ACTIVE;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
