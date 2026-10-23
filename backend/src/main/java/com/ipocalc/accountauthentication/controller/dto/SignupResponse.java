package com.ipocalc.accountauthentication.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 회원가입 응답 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {

  private Long accountId;
  private String email;
  private String name;
  private String nickname;

  /** 관리자 승인이 끝나 바로 로그인할 수 있으면 true, 승인 대기면 false. */
  private boolean approved;
}
