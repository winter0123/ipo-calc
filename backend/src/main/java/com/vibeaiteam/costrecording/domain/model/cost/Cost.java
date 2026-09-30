package com.vibeaiteam.costrecording.domain.model.cost;

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
 * 비용 JPA 엔티티.
 */
@Entity
@Table(name = "costs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cost {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id", nullable = false)
  private Long subscriptionId;

  @Column(name = "cost_type", nullable = false, length = 255)
  private String costType;

  @Column(name = "amount", nullable = false)
  private Long amount;

  @Column(name = "incurred_at", nullable = false)
  private LocalDateTime incurredAt;

  @Column(name = "memo", length = 255)
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
