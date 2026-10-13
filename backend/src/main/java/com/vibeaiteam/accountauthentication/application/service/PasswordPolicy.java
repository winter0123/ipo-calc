package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.exception.ValidationFailedException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** 가입·비밀번호 변경이 함께 쓰는 비밀번호 정책: 8자 이상, 영문·숫자·특수문자 각 1개 이상, bcrypt 한계인 72바이트 이하. */
final class PasswordPolicy {

  /** bcrypt는 72바이트 넘는 부분을 무시(또는 거부)하므로 입력 자체를 막는다. */
  static final int MAX_BYTES = 72;

  private static final Pattern PATTERN =
      Pattern.compile(
          "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$");

  private PasswordPolicy() {}

  /** 정책을 어기면 해당 필드에 대한 {@link ValidationFailedException}을 던진다. */
  static void validate(String field, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new ValidationFailedException(field, "비밀번호는 72바이트(영문 기준 72자)를 넘을 수 없습니다");
    }
    if (!PATTERN.matcher(password).matches()) {
      throw new ValidationFailedException(
          field, "비밀번호는 최소 8자 이상이며 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다");
    }
  }
}
