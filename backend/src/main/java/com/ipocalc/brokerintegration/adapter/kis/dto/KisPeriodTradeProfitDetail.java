package com.ipocalc.brokerintegration.adapter.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 기간별 매매손익 현황 조회 응답의 {@code output1} 상세 항목.
 *
 * <p><b>실계좌로 실제 응답을 확인하기 전이라 필드명은 문서 기준 추정이며 틀릴 수 있다.</b> 전부 nullable {@code String}으로 받고 알 수 있는
 * 필드는 무시한다. 실계좌 조회로 확정되면 이 클래스 필드를 조정한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisPeriodTradeProfitDetail(
    @JsonProperty("trad_dt") String tradDt,
    @JsonProperty("pdno") String pdno,
    @JsonProperty("prdt_name") String prdtName,
    @JsonProperty("trad_dvsn_name") String tradDvsnName,
    @JsonProperty("buy_qty") String buyQty,
    @JsonProperty("buy_amt") String buyAmt,
    @JsonProperty("sll_pric") String sllPric,
    @JsonProperty("sll_qty") String sllQty,
    @JsonProperty("sll_amt") String sllAmt,
    @JsonProperty("rlzt_pfls") String rlztPfls,
    @JsonProperty("pfls_rt") String pflsRt,
    @JsonProperty("fee") String fee,
    @JsonProperty("tl_tax") String tlTax) {}
