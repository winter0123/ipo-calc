package com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenDART 기업개황({@code /api/company.json}) 응답 중 쓰는 필드만.
 *
 * @param status 결과 코드(000 정상)
 * @param ceoName 대표이사
 * @param establishedOn 설립일(yyyyMMdd)
 * @param address 주소
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenDartCompanyOverview(
    String status,
    @JsonProperty("ceo_nm") String ceoName,
    @JsonProperty("est_dt") String establishedOn,
    @JsonProperty("adres") String address) {}
