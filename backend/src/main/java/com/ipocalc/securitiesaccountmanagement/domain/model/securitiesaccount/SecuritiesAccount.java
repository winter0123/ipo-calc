package com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
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

/** 증권계좌 JPA 엔티티. */
@Entity
@Table(name = "securities_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesAccount {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 계좌 주인 투자자 ID(investors.id). */
  @Column(name = "investor_id", nullable = false)
  private Long investorId;

  /** 증권사 ID(securities_firms.id). */
  @Column(name = "securities_firm_id", nullable = false)
  private Long securitiesFirmId;

  /** 계좌번호(암호화 저장). 화면에는 마스킹해서 보여 주고 수정할 수 없다. */
  @Column(name = "account_number", nullable = false, length = 255)
  private String accountNumber;

  /** 계좌 별칭. 예: 주력 계좌. 선택. */
  @Column(name = "account_name", length = 255)
  private String accountName;

  /** 이 투자자의 기본 계좌 여부. 투자자당 하나만 true. */
  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private Boolean isDefault = false;

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
