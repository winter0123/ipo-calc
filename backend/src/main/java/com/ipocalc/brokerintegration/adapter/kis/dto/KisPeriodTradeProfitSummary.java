package com.ipocalc.brokerintegration.adapter.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 기간별 매매손익 현황 조회 응답의 {@code output2} 요약 항목.
 *
 * <p><b>실계좌로 실제 응답을 확인하기 전이라 필드명은 문서 기준 추정이며 틀릴 수 있다.</b> 전부 nullable {@code String}으로 받고 알 수 있는
 * 필드는 무시한다. 특히 {@code prsm_tlex_smtl}(추정제비용합계)의 의미는 실계좌 확인이 필요하다(research 게이트 1). 실계좌 조회로 확정되면 이
 * 클래스 필드를 조정한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisPeriodTradeProfitSummary(
    @JsonProperty("sll_qty_smtl") String sllQtySmtl,
    @JsonProperty("sll_tr_amt_smtl") String sllTrAmtSmtl,
    @JsonProperty("sll_fee_smtl") String sllFeeSmtl,
    @JsonProperty("sll_tltx_smtl") String sllTltxSmtl,
    @JsonProperty("tot_rlzt_pfls") String totRlztPfls,
    @JsonProperty("prsm_tlex_smtl") String prsmTlexSmtl) {}
