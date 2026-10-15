package com.ipocalc.investormanagement.service.ownership;

import java.util.function.Function;

/**
 * 소유권 검증에서 던질 도메인 예외를 만드는 방법(없음 / 타 계정 소유).
 *
 * <p>도메인마다 예외 클래스(예: {@code CostNotFoundException})가 다르므로, 검증 로직은 한 곳에 두고 예외만 이 객체로 넘긴다. 메시지는 검증
 * 단계가 정하므로 모든 도메인에서 같은 문구가 나온다.
 *
 * @param notFound 대상이 없을 때의 예외 생성자(메시지를 받는다)
 * @param forbidden 타 계정 소유일 때의 예외 생성자(메시지를 받는다)
 */
public record OwnershipErrors(
    Function<String, ? extends RuntimeException> notFound,
    Function<String, ? extends RuntimeException> forbidden) {

  /** 투자자를 찾지 못했을 때의 표준 메시지. */
  public static final String INVESTOR_NOT_FOUND = "투자자를 찾을 수 없습니다.";

  /** 타 계정 소유일 때의 표준 메시지. */
  public static final String NOT_OWNER = "계정 소유권이 없습니다.";

  /**
   * 없음 예외를 만든다.
   *
   * @param message 메시지
   * @return 도메인의 NotFound 예외
   */
  public RuntimeException notFound(String message) {
    return notFound.apply(message);
  }

  /**
   * 권한 없음 예외를 만든다.
   *
   * @param message 메시지
   * @return 도메인의 Forbidden 예외
   */
  public RuntimeException forbidden(String message) {
    return forbidden.apply(message);
  }
}
