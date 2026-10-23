package com.ipocalc.accountauthentication.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 회원가입 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

  @NotBlank(message = "이메일은 필수입니다")
  @Email(message = "이메일 형식이 올바르지 않습니다")
  private String email;

  @NotBlank(message = "비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
  private String password;

  @Size(max = 50, message = "이름은 최대 50자까지 가능합니다")
  private String name;

  @Size(max = 50, message = "닉네임은 최대 50자까지 가능합니다")
  private String nickname;

  /** 이전 클라이언트 호환용 필드. 본인 투자자 표시 이름에는 nickname을 사용한다. */
  @Size(max = 50, message = "표시 이름은 최대 50자까지 가능합니다")
  private String displayName;

  /** 계정 주인과의 관계. 비우면 SELF. */
  @Pattern(regexp = "SELF", message = "회원가입 투자자 관계는 본인(SELF)이어야 합니다")
  @Size(max = 20, message = "관계는 최대 20자까지 가능합니다")
  private String relationship;
}
