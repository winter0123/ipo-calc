package com.vibeaiteam.brokerintegration.domain.model.credential;

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
import lombok.ToString;

/**
 * 증권계좌 API 인증정보 JPA 엔티티.
 *
 * <p>증권사 조회 전용 API 연동에 필요한 앱키/앱시크릿/접근토큰을 계좌별로 암호화 저장한다. securities_accounts와 1:1 관계(계좌당 앱키 1세트라는 외부
 * 제약과 정합). 계정 소유권 검증이 필수인 계정 소유 엔티티다.
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

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 연동한 증권계좌 ID(securities_accounts.id). 계좌당 하나. */
  @Column(name = "securities_account_id", nullable = false)
  private Long securitiesAccountId;

  /** 증권사 구분값. KIS / KIWOOM / LS. */
  @Column(name = "api_provider", nullable = false, length = 50)
  private String apiProvider;

  /** 앱키(암호화 저장). 화면에 다시 보여 주지 않는다. */
  @Column(name = "app_key_encrypted", nullable = false)
  private String appKeyEncrypted;

  /** 앱시크릿(암호화 저장). 화면에 다시 보여 주지 않는다. */
  @Column(name = "app_secret_encrypted", nullable = false)
  private String appSecretEncrypted;

  /** 증권사 접근토큰 캐시(암호화 저장). 만료되면 서버가 다시 발급. */
  @Column(name = "access_token_encrypted")
  private String accessTokenEncrypted;

  /** 접근토큰 만료 시각. */
  @Column(name = "token_expires_at")
  private LocalDateTime tokenExpiresAt;

  /** 키 발급일(증권사 서비스 신청일). 한국투자증권 사용기간 1년 만료 안내 계산에 쓴다. */
  @Column(name = "key_issued_on", nullable = false)
  @Builder.Default
  private LocalDate keyIssuedOn = LocalDate.now();

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
