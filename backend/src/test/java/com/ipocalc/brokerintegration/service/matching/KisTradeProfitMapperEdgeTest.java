package com.ipocalc.brokerintegration.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ipocalc.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import com.ipocalc.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitDetail;
import com.ipocalc.brokerintegration.exception.BrokerApiException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link KisTradeProfitMapper}의 null·공백·경계 입력 분기. */
class KisTradeProfitMapperEdgeTest {

  private static KisPeriodTradeProfitDetail d(
      String dt, String pdno, String qty, String amt, String fee, String tax) {
    return new KisPeriodTradeProfitDetail(
        dt, pdno, null, null, null, null, null, qty, amt, null, null, fee, tax);
  }

  private static KisPeriodTradeProfit of(KisPeriodTradeProfitDetail... rows) {
    return new KisPeriodTradeProfit(Arrays.asList(rows), null);
  }

  @Test
  void 응답이나_상세목록이_null이면_빈_목록() {
    assertThat(KisTradeProfitMapper.toSaleSummaries(null)).isEmpty();
    assertThat(KisTradeProfitMapper.toSaleSummaries(new KisPeriodTradeProfit(null, null)))
        .isEmpty();
  }

  @Test
  void null_행과_매도수량이_null_공백_0인_행은_건너뛴다() {
    List<BrokerSaleSummary> out =
        KisTradeProfitMapper.toSaleSummaries(
            of(
                null,
                d("20261001", "005930", null, "100", "0", "0"),
                d("20261001", "005930", "  ", "100", "0", "0"),
                d("20261001", "005930", "000", "100", "0", "0"),
                d("20261001", "005930", " 3 ", "300", null, "  ")));

    assertThat(out).hasSize(1);
    assertThat(out.get(0).quantity()).isEqualTo(3);
    assertThat(out.get(0).fee()).isZero();
    assertThat(out.get(0).tax()).isZero();
  }

  @Test
  void 일자_종목코드가_null이면_invalid_response() {
    assertThatThrownBy(
            () -> KisTradeProfitMapper.toSaleSummaries(of(d(null, "005930", "1", "1", "0", "0"))))
        .isInstanceOf(BrokerApiException.class);
    assertThatThrownBy(
            () ->
                KisTradeProfitMapper.toSaleSummaries(of(d("20261001", null, "1", "1", "0", "0"))))
        .isInstanceOf(BrokerApiException.class);
  }

  @Test
  void 매도금액이_null이거나_음수_또는_19자리면_invalid_response() {
    for (String bad : new String[] {null, "", "-5", "1234567890123456789"}) {
      assertThatThrownBy(
              () ->
                  KisTradeProfitMapper.toSaleSummaries(
                      of(d("20261001", "005930", "1", bad, "0", "0"))))
          .isInstanceOf(BrokerApiException.class);
    }
  }

  @Test
  void 합산은_수량_금액_수수료_세금_모두_더하고_일자_코드순() {
    List<BrokerSaleSummary> out =
        KisTradeProfitMapper.toSaleSummaries(
            of(
                d("20261002", "000660", "1", "10", "1", "1"),
                d("20261001", "005930", "2", "20", "2", "2"),
                d("20261001", "005930", "3", "30", "3", "3")));

    assertThat(out).extracting(BrokerSaleSummary::stockCode).containsExactly("005930", "000660");
    assertThat(out.get(0).quantity()).isEqualTo(5);
    assertThat(out.get(0).amount()).isEqualTo(50);
    assertThat(out.get(0).fee()).isEqualTo(5);
    assertThat(out.get(0).tax()).isEqualTo(5);
  }
}
