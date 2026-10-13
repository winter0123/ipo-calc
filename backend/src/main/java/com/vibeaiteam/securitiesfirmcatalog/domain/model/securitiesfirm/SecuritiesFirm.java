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

/** 증권사 JPA 엔티티. */
@Entity
@Table(name = "securities_firms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesFirm {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 증권사 이름. 예: 한국투자증권. */
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  /** 외부 기관 코드(증권사 식별용). 선택. */
  @Column(name = "external_code", length = 255)
  private String externalCode;

  /** 사업자등록번호. 선택. */
  @Column(name = "business_registration_number", length = 255)
  private String businessRegistrationNumber;

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

  /** 매도 내역 자동 가져오기(조회 API) 지원 여부. 현재 한국투자증권·키움·LS만 true. */
  @Column(name = "api_supported", nullable = false)
  @Builder.Default
  private Boolean apiSupported = false;

  /** 연동 구현 구분값. KIS / KIWOOM / LS. 미지원이면 null. */
  @Column(name = "api_provider", length = 50)
  private String apiProvider;

  /** API 인증 방식. 예: APP_KEY_SECRET(앱키+시크릿). */
  @Column(name = "api_auth_type", length = 50)
  private String apiAuthType;

  /** 조회 전용 여부. 주문 기능은 쓰지 않으므로 항상 true. */
  @Column(name = "api_readonly_only", nullable = false)
  @Builder.Default
  private Boolean apiReadonlyOnly = true;

  /** 증권사 API 쪽 식별 코드. 선택. */
  @Column(name = "api_external_code", length = 255)
  private String apiExternalCode;
}
