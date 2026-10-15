package com.ipocalc.accountauthentication.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 성공 응답 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuccessResponse {

  private String message;

  /** 메시지를 담은 성공 응답을 만든다. */
  public static SuccessResponse of(String message) {
    return SuccessResponse.builder().message(message).build();
  }
}
