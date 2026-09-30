package com.vibeaiteam.brokerintegration.domain.model.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.ToString;

/**
 * 증권계좌 API 인증정보 JPA 엔티티.
 *
 * <p>증권사 조회 전용 API 연동에 필요한 앱키/앱시크릿/접근토큰을 계좌별로 암호화 저장한다.
 * securities_accounts와 1:1 관계(계좌당 앱키 1세트라는 외부 제약과 정합).
 * 계정 소유권 검증이 필수인 계정 소유 엔티티다.
 */
@Entity
@Table(name = "securities_account_api_credentials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"appKeyEncrypted", "appSecretEncrypted", "accessTokenEncrypted"})
public class SecuritiesAccountApiCredential {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "securities_account_id", nullable = false)
  private Long securitiesAccountId;

  @Column(name = "api_provider", nullable = false, length = 50)
  private String apiProvider;

  @Column(name = "app_key_encrypted", nullable = false)
  private String appKeyEncrypted;

  @Column(name = "app_secret_encrypted", nullable = false)
  private String appSecretEncrypted;

  @Column(name = "access_token_encrypted")
  private String accessTokenEncrypted;

  @Column(name = "token_expires_at")
  private LocalDateTime tokenExpiresAt;

  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  private String status = "ACTIVE";

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}