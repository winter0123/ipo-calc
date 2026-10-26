package com.ipocalc.investormanagement.domain.model.investor;

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

/** 투자자 JPA 엔티티. */
@Entity
@Table(name = "investors")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Investor {
  /** Updates the investor profile. */
  public void updateProfile(
      String displayName, String relationship, boolean updateRelationship, LocalDateTime now) {
    if (displayName != null) {
      this.displayName = displayName;
    }
    if (updateRelationship) {
      this.relationship = relationship;
    }
    this.updatedAt = now;
  }

  /** Deactivates the credential. */
  public void deactivate(LocalDateTime now) {
    this.status = RecordStatus.INACTIVE;
    this.deletedAt = now;
    this.updatedAt = now;
  }

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Comment("투자자 고유 식별자")
  private Long id;

  /** 이 투자자를 등록한 로그인 계정 ID(accounts.id). 소유권 판단 기준. */
  @Column(name = "account_id", nullable = false)
  @Comment("계정 ID(소유권 판단 기준)")
  private Long accountId;

  /** 화면에 보이는 투자자 이름(최대 50자). 예: 본인, 아내. */
  @Column(name = "display_name", nullable = false, length = 50)
  @Comment("투자자 표시명")
  private String displayName;

  /** 계정 주인과의 관계. SELF/PARTNER/FRIEND/PARENT/CHILD/OTHER이며, 기존 SPOUSE 값도 호환한다. */
  @Column(name = "relationship", length = 20)
  @Comment("계정 주인과의 관계(SELF/PARTNER 등)")
  private String relationship;

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
