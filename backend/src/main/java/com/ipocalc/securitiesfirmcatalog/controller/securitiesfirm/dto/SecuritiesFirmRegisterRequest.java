package com.ipocalc.securitiesfirmcatalog.controller.securitiesfirm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 증권사 등록 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesFirmRegisterRequest {

  private String name;

  private String externalCode;

  private String businessRegistrationNumber;
}
