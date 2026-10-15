package com.ipocalc.brokerintegration.adapter.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * KIS 기간별 매매손익 현황 조회({@code TTTC8715R}) 응답.
 *
 * <p>{@code rt_cd}가 "0"이 아니면 호출자가 {@code BrokerApiException}으로 변환한다. {@code output1}은 배열이고 {@code
 * output2}는 단일 객체이며, 없는 경우 null이다. 알 수 없는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisPeriodTradeProfitResponse(
    @JsonProperty("rt_cd") String rtCd,
    @JsonProperty("msg_cd") String msgCd,
    @JsonProperty("msg1") String msg1,
    @JsonProperty("output1") List<KisPeriodTradeProfitDetail> output1,
    @JsonProperty("output2") KisPeriodTradeProfitSummary output2,
    @JsonProperty("ctx_area_fk100") String ctxAreaFk100,
    @JsonProperty("ctx_area_nk100") String ctxAreaNk100) {

  /**
   * 성공 응답인지 확인한다.
   *
   * @return {@code rt_cd}가 "0"이면 true
   */
  public boolean isSuccess() {
    return "0".equals(rtCd);
  }
}
