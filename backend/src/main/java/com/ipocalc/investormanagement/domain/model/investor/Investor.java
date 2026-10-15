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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 투자자 JPA 엔티티. */
@Entity
@Table(name = "investors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investor {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 이 투자자를 등록한 로그인 계정 ID(accounts.id). 소유권 판단 기준. */
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  /** 화면에 보이는 투자자 이름(최대 50자). 예: 본인, 아내. */
  @Column(name = "display_name", nullable = false, length = 50)
  private String displayName;

  /** 계정 주인과의 관계. SELF(본인) / SPOUSE(배우자) / PARENT(부모) / CHILD(자녀) / OTHER(기타), 없으면 null. */
  @Column(name = "relationship", length = 20)
  private String relationship;

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
