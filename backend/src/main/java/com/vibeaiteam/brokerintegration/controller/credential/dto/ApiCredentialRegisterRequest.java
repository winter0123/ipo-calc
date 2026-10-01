package com.vibeaiteam.brokerintegration.controller.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 브로커 API 인증정보 등록 요청 DTO.
 *
 * <p>앱키·앱시크릿은 요청 시점에만 평문으로 전달되며 응답 DTO·엔티티 로그에는 절대 포함하지 않는다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCredentialRegisterRequest {

  /** 브로커 어댑터 식별자(예: KIS). */
  @NotBlank(message = "apiProvider는 필수 입력값입니다.")
  private String apiProvider;

  /** 브로커 발급 앱키. */
  @NotBlank(message = "appKey는 필수 입력값입니다.")
  @Size(max = 512, message = "appKey가 너무 깁니다.")
  private String appKey;

  /** 브로커 발급 앱시크릿. */
  @NotBlank(message = "appSecret은 필수 입력값입니다.")
  @Size(max = 512, message = "appSecret이 너무 깁니다.")
  private String appSecret;

  /** 증권사 키 발급일(선택). 없으면 오늘로 본다. */
  private LocalDate keyIssuedOn;
}
