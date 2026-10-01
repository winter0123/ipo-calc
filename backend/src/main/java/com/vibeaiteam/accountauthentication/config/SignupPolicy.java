package com.vibeaiteam.accountauthentication.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 가입 모드 설정({@code app.signup.mode}, 환경변수 {@code APP_SIGNUP_MODE}).
 *
 * <p>값이 OPEN·APPROVAL·CLOSED가 아니면 기동을 실패시킨다.
 */
@Component
public class SignupPolicy {

  private final SignupMode mode;

  /**
   * 설정값으로 생성한다.
   *
   * @param mode 가입 모드 문자열(대소문자 무시)
   */
  public SignupPolicy(@Value("${app.signup.mode:APPROVAL}") String mode) {
    this.mode = SignupMode.valueOf(mode.trim().toUpperCase());
  }

  /**
   * 현재 가입 모드를 돌려준다.
   *
   * @return 가입 모드
   */
  public SignupMode mode() {
    return mode;
  }
}
