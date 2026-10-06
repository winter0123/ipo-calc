package com.vibeaiteam.accountauthentication.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 비밀번호 변경 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeRequest {

  @NotBlank(message = "현재 비밀번호는 필수입니다")
  private String currentPassword;

  @NotBlank(message = "새 비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
  private String newPassword;

  @NotBlank(message = "비밀번호 확인은 필수입니다")
  private String confirmPassword;
}
