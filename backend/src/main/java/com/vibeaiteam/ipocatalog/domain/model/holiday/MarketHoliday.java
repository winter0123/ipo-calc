package com.vibeaiteam.ipocatalog.domain.model.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 시장 휴장일 엔티티.
 *
 * <p>한국거래소(KRX) 휴장일을 저장한다. 한국천문연구원 특일 정보 API에서 조회한 법정 공휴일과 KRX 고유 규칙(근로자의 날, 연말 휴장일)을 합쳐 관리한다.
 */
@Entity
@Table(name = "market_holidays")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketHoliday {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 휴장일 (yyyy-MM-dd). 중복 불가. */
  @Column(name = "holiday_date", nullable = false, unique = true)
  private LocalDate holidayDate;

  /** 휴장일 이름 (예: "신정", "설날 연휴", "근로자의 날"). */
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  /** 출처: SEED(초기 데이터), KASI_API(한국천문연구원 API), RULE(KRX 규칙 보완). */
  @Column(name = "source", nullable = false, length = 20)
  private String source;

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
