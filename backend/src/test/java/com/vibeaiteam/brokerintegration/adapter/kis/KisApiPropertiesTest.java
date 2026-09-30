package com.vibeaiteam.brokerintegration.adapter.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link KisApiProperties} 테스트. 허용되지 않은 호스트의 base-url은 거부되어야 한다(research 4.3-C). */
class KisApiPropertiesTest {

  @Test
  void allowedHostWithPort_passesValidation() {
    KisApiProperties properties = withBaseUrl("https://openapi.koreainvestment.com:9443");

    assertThatCode(properties::validateBaseUrl).doesNotThrowAnyException();
    assertThat(properties.getBaseUrl()).isEqualTo("https://openapi.koreainvestment.com:9443");
  }

  @Test
  void allowedHostWithoutPort_passesValidation() {
    KisApiProperties properties = withBaseUrl("https://openapi.koreainvestment.com");

    assertThatCode(properties::validateBaseUrl).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://evil.example.com:9443",
        "https://api.openapi.koreainvestment.com:9443",
        "https://openapi.koreainvestment.com.evil.example.com:9443",
        "http://openapi.koreainvestment.com:9443",
        "https://127.0.0.1:9443",
        "https://localhost:9443"
      })
  void disallowedBaseUrl_isRejected(String baseUrl) {
    KisApiProperties properties = withBaseUrl(baseUrl);

    assertThatThrownBy(properties::validateBaseUrl).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void malformedBaseUrl_isRejected() {
    KisApiProperties properties = withBaseUrl("not-a-valid-url");

    assertThatThrownBy(properties::validateBaseUrl)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("형식이 올바르지 않습니다");
  }

  @Test
  void defaultBaseUrl_isTheRealTradingHost() {
    // 기본값이 설정 파일에 있는 실제 거래 호스트이고, 모의투자 호스트가 아니다.
    assertThat(new KisApiProperties().getBaseUrl())
        .isEqualTo("https://openapi.koreainvestment.com:9443");
  }

  private KisApiProperties withBaseUrl(String baseUrl) {
    KisApiProperties properties = new KisApiProperties();
    properties.setBaseUrl(baseUrl);
    return properties;
  }
}
