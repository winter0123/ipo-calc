package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DS001 공시검색 응답 개별 항목 DTO. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenDartDisclosureItem {

  /** 회사고유번호 (8자리) */
  @JsonProperty("corp_code")
  private String corpCode;

  /** 기업명 */
  @JsonProperty("corp_name")
  private String corpName;

  /** 종목코드 (6자리, 없을 수 있음) */
  @JsonProperty("stock_code")
  private String stockCode;

  /** 법인구분 (Y: 유가, K: 코스닥, N: 코넥스, E: 기타) */
  @JsonProperty("corp_cls")
  private String corpCls;

  /** 보고서명 */
  @JsonProperty("report_nm")
  private String reportNm;

  /** 접수번호 (14자리) */
  @JsonProperty("rcept_no")
  private String rceptNo;

  /** 제출인명 */
  @JsonProperty("flr_nm")
  private String flrNm;

  /** 접수일자 (YYYYMMDD) */
  @JsonProperty("rcept_dt")
  private String rceptDt;

  /** 비고 */
  private String rm;

  /**
   * 정정·발행조건확정 공시인지 판별.
   *
   * @return {@code reportNm}이 {@code [기재정정]} 또는 {@code [발행조건확정]}으로 시작하면 {@code true}
   */
  public boolean isCorrectionOrFinalized() {
    return reportNm != null && (reportNm.startsWith("[기재정정]") || reportNm.startsWith("[발행조건확정]"));
  }

  /**
   * 정정·발행조건확정 접두어 제거한 정규화된 보고서명 반환.
   *
   * @return 접두어 제거된 보고서명 (예: {@code "[기재정정]증권신고서(지분증권)"} → {@code "증권신고서(지분증권)"})
   */
  public String getNormalizedReportName() {
    if (reportNm == null) {
      return null;
    }
    if (reportNm.startsWith("[기재정정]")) {
      return reportNm.substring("[기재정정]".length());
    }
    if (reportNm.startsWith("[발행조건확정]")) {
      return reportNm.substring("[발행조건확정]".length());
    }
    return reportNm;
  }

  /**
   * 지분증권 증권신고서인지 판별 (정정/확정 공시도 포함하여 정규화 후 확인).
   *
   * @return 정규화된 보고서명에 {@code "증권신고서(지분증권)"}이 포함되면 {@code true}
   */
  public boolean isEquitySecuritiesReport() {
    String normalized = getNormalizedReportName();
    return normalized != null && normalized.contains("증권신고서(지분증권)");
  }
}
