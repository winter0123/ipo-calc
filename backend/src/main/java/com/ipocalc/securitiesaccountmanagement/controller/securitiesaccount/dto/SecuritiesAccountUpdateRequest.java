package com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 증권계좌 수정 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesAccountUpdateRequest {

  private String accountName;

  private Long securitiesFirmId;

  /** 계좌번호(선택). 미전달=무변경, 공백=400(번호 지우기 미지원), 유효값=정규화+암호화 교체 저장(기존 번호도 허용). */
  private String accountNumber;
}
