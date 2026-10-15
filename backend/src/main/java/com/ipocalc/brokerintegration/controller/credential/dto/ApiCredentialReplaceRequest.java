package com.ipocalc.brokerintegration.controller.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 연결된 증권사 API 키를 새 키로 교체하는 요청. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCredentialReplaceRequest {

  @NotBlank(message = "appKey는 필수 입력값입니다.")
  @Size(max = 512, message = "appKey가 너무 깁니다.")
  private String appKey;

  @NotBlank(message = "appSecret은 필수 입력값입니다.")
  @Size(max = 512, message = "appSecret이 너무 깁니다.")
  private String appSecret;

  /** 새 키의 발급일(선택). 없으면 오늘로 본다. */
  private LocalDate keyIssuedOn;
}
