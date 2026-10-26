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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** 증권계좌 JPA 엔티티. */
@Entity
@Table(name = "securities_accounts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SecuritiesAccount {
  /** Updates the securities account details. */
  public void changeDetails(Long securitiesFirmId, String accountName, LocalDateTime now) {
    if (securitiesFirmId != null) {
      this.securitiesFirmId = securitiesFirmId;
    }
    if (accountName != null) {
      this.accountName = accountName;
    }
    this.updatedAt = now;
  }

  /** Deactivates the credential. */
  public void deactivate(LocalDateTime now) {
    this.status = RecordStatus.INACTIVE;
    this.deletedAt = now;
    this.isDefault = false;
    this.updatedAt = now;
  }

  /** Marks this securities account as the default. */
  public void makeDefault(LocalDateTime now) {
    this.isDefault = true;
    this.updatedAt = now;
  }

  /** Removes the default flag from this securities account. */
  public void clearDefault(LocalDateTime now) {
    this.isDefault = false;
    this.updatedAt = now;
  }

  /** Replaces the encrypted account number. */
  public void rotateAccountNumber(String encryptedAccountNumber) {
    this.accountNumber = encryptedAccountNumber;
  }

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Comment("증권계좌 고유 식별자")
  private Long id;

  /** 계좌 주인 투자자 ID(investors.id). */
  @Column(name = "investor_id", nullable = false)
  @Comment("투자자 ID")
  private Long investorId;

  /** 증권사 ID(securities_firms.id). */
  @Column(name = "securities_firm_id", nullable = false)
  @Comment("증권사 ID")
  private Long securitiesFirmId;

  /** 계좌번호(암호화 저장). 화면에는 마스킹해서 보여 주고 수정할 수 없다. */
  @Column(name = "account_number", nullable = false, length = 255)
  @Comment("계좌번호(암호화)")
  private String accountNumber;

  /** 계좌 별칭. 예: 주력 계좌. 선택. */
  @Column(name = "account_name", length = 255)
  @Comment("계좌 별칭")
  private String accountName;

  /** 이 투자자의 기본 계좌 여부. 투자자당 하나만 true. */
  @Column(name = "is_default", nullable = false)
  @Builder.Default
  @Comment("기본 계좌 여부")
  private Boolean isDefault = false;

  /** 레코드 상태. ACTIVE(사용 중) / CANCELLED(삭제·취소됨). 물리 삭제는 하지 않는다. */
  @Column(name = "status", nullable = false, length = 10)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Comment("레코드 상태(ACTIVE/INACTIVE/CANCELLED)")
  private RecordStatus status = RecordStatus.ACTIVE;

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
}
