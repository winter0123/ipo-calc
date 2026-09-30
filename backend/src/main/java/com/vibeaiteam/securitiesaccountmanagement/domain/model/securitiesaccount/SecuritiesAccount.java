package com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount;

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
 * 증권계좌 JPA 엔티티.
 */
@Entity
@Table(name = "securities_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "investor_id", nullable = false)
  private Long investorId;

  @Column(name = "securities_firm_id", nullable = false)
  private Long securitiesFirmId;

  @Column(name = "account_number", nullable = false, length = 255)
  private String accountNumber;

  @Column(name = "account_name", length = 255)
  private String accountName;

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private Boolean isDefault = false;

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
