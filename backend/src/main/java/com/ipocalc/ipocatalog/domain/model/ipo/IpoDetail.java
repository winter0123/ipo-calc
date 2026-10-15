package com.ipocalc.ipocatalog.domain.model.ipo;

import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSnapshot.Financial;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSnapshot.FloatStep;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSnapshot.Underwriter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 공모주 상세 정보 JPA 엔티티. DART 공시 스냅샷이며 공모주당 1행이고 재동기화 때 덮어쓴다. */
@Entity
@Table(name = "ipo_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoDetail {

  /** 공모주 ID(ipos.id). PK이자 FK. */
  @Id
  @Column(name = "ipo_id")
  private Long ipoId;

  /** 값을 가져온 공시의 접수번호(14자리). */
  @Column(name = "source_rcept_no", length = 14)
  private String sourceRceptNo;

  /** 값을 가져온 공시 이름. 예: [기재정정]투자설명서. */
  @Column(name = "source_report_name", length = 200)
  private String sourceReportName;

  /** 저장 당시 DART 공시 파서 버전. 파서가 바뀌면 같은 공시도 다시 파싱한다. */
  @Column(name = "parser_version", nullable = false)
  private int parserVersion;

  /** 공시에서 마지막으로 가져온 시각. */
  @Column(name = "synced_at")
  private LocalDateTime syncedAt;

  /** 대표이사 이름. */
  @Column(name = "ceo_name", length = 200)
  private String ceoName;

  /** 설립일. */
  @Column(name = "established_on")
  private LocalDate establishedOn;

  /** 본점 주소. */
  @Column(name = "address", length = 500)
  private String address;

  /** 액면가(원). */
  @Column(name = "par_value")
  private Long parValue;

  /** 자본금(원, 요약재무 최근 기). */
  @Column(name = "capital_stock")
  private Long capitalStock;

  /** 최대주주 이름. */
  @Column(name = "largest_shareholder_name", length = 200)
  private String largestShareholderName;

  /** 최대주주 공모 전 지분율(%). */
  @Column(name = "largest_shareholder_ratio", precision = 7, scale = 2)
  private BigDecimal largestShareholderRatio;

  /** 수요예측 기관 경쟁률(X:1의 X). */
  @Column(name = "institutional_competition_rate", precision = 12, scale = 2)
  private BigDecimal institutionalCompetitionRate;

  /** 의무보유확약 비율(%). */
  @Column(name = "lock_up_commitment_ratio", precision = 7, scale = 2)
  private BigDecimal lockUpCommitmentRatio;

  /** 요약 재무 배열(공시 순서, 금액은 원 단위). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "financials", nullable = false)
  @Builder.Default
  private List<Financial> financials = new ArrayList<>();

  /** 유통가능 물량 일정 배열(누적 주식수·비율). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "float_schedule", nullable = false)
  @Builder.Default
  private List<FloatStep> floatSchedule = new ArrayList<>();

  /** 인수회사 배열(이름·역할·인수 주식수). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "underwriters", nullable = false)
  @Builder.Default
  private List<Underwriter> underwriters = new ArrayList<>();

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
