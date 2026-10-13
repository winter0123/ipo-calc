package com.vibeaiteam.accountauthentication.util.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockCodesTest {

  @Test
  void normalizesWhitespaceCaseAndPrefix() {
    assertThat(StockCodes.normalize(" A005930 ")).isEqualTo("005930");
    assertThat(StockCodes.normalize("a005930")).isEqualTo("005930");
    assertThat(StockCodes.normalize("0013y0")).isEqualTo("0013Y0");
  }

  @Test
  void padsNumericCodesToSixDigits() {
    assertThat(StockCodes.normalize("5930")).isEqualTo("005930");
  }

  @Test
  void keepsAlphanumericPreListingCodesAndReturnsNullForBlank() {
    assertThat(StockCodes.normalize("0013Y0")).isEqualTo("0013Y0");
    assertThat(StockCodes.normalize("  ")).isNull();
    assertThat(StockCodes.normalize(null)).isNull();
  }
}
