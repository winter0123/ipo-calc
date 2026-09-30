package com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm;

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
 * 증권사 JPA 엔티티.
 */
@Entity
@Table(name = "securities_firms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesFirm {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "external_code", length = 255)
  private String externalCode;

  @Column(name = "business_registration_number", length = 255)
  private String businessRegistrationNumber;

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

  @Column(name = "api_supported", nullable = false)
  @Builder.Default
  private Boolean apiSupported = false;

  @Column(name = "api_provider", length = 50)
  private String apiProvider;

  @Column(name = "api_auth_type", length = 50)
  private String apiAuthType;

  @Column(name = "api_readonly_only", nullable = false)
  @Builder.Default
  private Boolean apiReadonlyOnly = true;

  @Column(name = "api_external_code", length = 255)
  private String apiExternalCode;
}
