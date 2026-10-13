package com.vibeaiteam.brokerintegration.adapter;

import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 키움·LS 어댑터가 함께 쓰는 RestClient 생성, base-url 검증, 숫자·종목코드 정규화. */
public final class BrokerHttp {

  private static final String INVALID = "invalid_response";

  private BrokerHttp() {}

  /**
   * base-url과 타임아웃(연결 5초, 읽기 15초)을 설정한 RestClient를 만든다.
   *
   * @param builder RestClient 빌더
   * @param baseUrl 증권사 API base-url
   * @return 설정된 RestClient
   */
  public static RestClient newClient(RestClient.Builder builder, String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5_000);
    factory.setReadTimeout(15_000);
    return builder
        .baseUrl(baseUrl)
        .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
        .requestFactory(factory)
        .build();
  }

  /**
   * 기동 시 base-url이 https이고 허용 호스트인지 검증한다.
   *
   * @param name 증권사 이름 (오류 메시지용)
   * @param baseUrl 검사할 base-url
   * @param allowedHosts 허용 호스트
   * @throws IllegalStateException 형식·스킴·호스트가 허용되지 않은 경우
   */
  public static void validateBaseUrl(String name, String baseUrl, Set<String> allowedHosts) {
    URI uri;
    try {
      uri = URI.create(baseUrl);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(name + " API base-url 형식이 올바르지 않습니다: " + baseUrl, e);
    }
    if (uri.getHost() == null || !allowedHosts.contains(uri.getHost())) {
      throw new IllegalStateException(
          name + " API base-url의 호스트가 허용되지 않았습니다: " + uri.getHost() + ". 허용 호스트: " + allowedHosts);
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalStateException(name + " API base-url은 https만 허용됩니다.");
    }
  }

  /**
   * 부호(+/-)·앞자리 0·쉼표가 붙을 수 있는 숫자 문자열을 절댓값 BigDecimal로 바꾼다. 비어 있으면 0이다.
   *
   * @param value 증권사가 준 문자열
   * @param field 오류 메시지에 쓸 필드명
   * @return 절댓값
   * @throws com.vibeaiteam.brokerintegration.exception.BrokerApiException 숫자가 아닐 때
   */
  public static BigDecimal number(String value, String field) {
    String v = value == null ? "" : value.trim().replace(",", "");
    if (v.isEmpty()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(v).abs();
    } catch (NumberFormatException e) {
      throw new BrokerApiException(INVALID, field + " 값을 해석할 수 없다");
    }
  }

  /**
   * 숫자 문자열을 반올림한 정수 절댓값으로 바꾼다.
   *
   * @param value 증권사가 준 문자열
   * @param field 오류 메시지에 쓸 필드명
   * @return 반올림한 절댓값
   */
  public static long whole(String value, String field) {
    return round(number(value, field), field);
  }

  /**
   * BigDecimal을 반올림해 long으로 바꾼다.
   *
   * @param value 값
   * @param field 오류 메시지에 쓸 필드명
   * @return 반올림한 값
   */
  public static long round(BigDecimal value, String field) {
    try {
      return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    } catch (ArithmeticException e) {
      throw new BrokerApiException(INVALID, field + " 값이 너무 크다");
    }
  }

  /**
   * 종목코드를 6자리로 정규화한다(앞의 {@code A} 접두 제거).
   *
   * @param raw 증권사가 준 종목코드
   * @return 6자리 종목코드
   * @throws com.vibeaiteam.brokerintegration.exception.BrokerApiException 코드가 비었을 때
   */
  public static String stockCode(String raw) {
    String code = raw == null ? "" : raw.trim();
    if (code.length() == 7 && (code.charAt(0) == 'A' || code.charAt(0) == 'a')) {
      code = code.substring(1);
    }
    if (code.isEmpty()) {
      throw new BrokerApiException(INVALID, "종목코드가 비어 있다");
    }
    return code;
  }
}
