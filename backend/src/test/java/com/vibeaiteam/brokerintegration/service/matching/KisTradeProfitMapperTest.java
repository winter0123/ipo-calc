package com.vibeaiteam.brokerintegration.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitResponse;
import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link KisTradeProfitMapper} 단위 테스트. 픽스처는 실응답 형태를 따른 더미 값이다. */
class KisTradeProfitMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static String row(
      String dt, String pdno, String sllQty, String sllAmt, String fee, String tax) {
    return """
        {"trad_dt":"%s","pdno":"%s","prdt_name":"더미","trad_dvsn_name":"현금","loan_dt":"",
         "hldg_qty":"0","pchs_unpr":"0","buy_qty":"10","buy_amt":"1000","sll_pric":"0",
         "sll_qty":"%s","sll_amt":"%s","rlzt_pfls":"0","pfls_rt":"0.00000000",
         "fee":"%s","tl_tax":"%s","loan_int":"0"}"""
        .formatted(dt, pdno, sllQty, sllAmt, fee, tax);
  }

  private static KisPeriodTradeProfit profit(String... rows) throws Exception {
    String body =
        "{\"rt_cd\":\"0\",\"msg_cd\":\"KIOK0510\",\"msg1\":\"ok\",\"output1\":["
            + String.join(",", rows)
            + "],\"output2\":{\"sll_qty_smtl\":\"1\"},"
            + "\"ctx_area_fk100\":\"\",\"ctx_area_nk100\":\"\"}";
    KisPeriodTradeProfitResponse r = JSON.readValue(body, KisPeriodTradeProfitResponse.class);
    return new KisPeriodTradeProfit(r.output1(), r.output2());
  }

  @Test
  void ac1830_매도행을_변환하고_영숫자_pdno를_보존한다() throws Exception {
    List<BrokerSaleSummary> out =
        KisTradeProfitMapper.toSaleSummaries(
            profit(row("20261001", "0123A4", "5", "5000", "12", "34")));

    assertThat(out)
        .containsExactly(
            new BrokerSaleSummary(LocalDate.of(2026, 10, 1), "0123A4", 5, 5000, 12, 34));
  }

  @Test
  void ac1831_매수만_있는_행은_제외한다() throws Exception {
    List<BrokerSaleSummary> out =
        KisTradeProfitMapper.toSaleSummaries(
            profit(
                row("20261001", "005930", "0", "0", "0", "0"),
                row("20261001", "005931", "", "", "", ""),
                row("20261001", "005932", "1", "100", "0", "0")));

    assertThat(out).extracting(BrokerSaleSummary::stockCode).containsExactly("005932");
  }

  @Test
  void ac1832_같은_일자_종목은_합산한다() throws Exception {
    List<BrokerSaleSummary> out =
        KisTradeProfitMapper.toSaleSummaries(
            profit(
                row("20261001", "005930", "3", "300", "1", "2"),
                row("20261001", "005930", "4", "400", "10", "20"),
                row("20261002", "005930", "1", "100", "0", "0")));

    assertThat(out)
        .containsExactly(
            new BrokerSaleSummary(LocalDate.of(2026, 10, 1), "005930", 7, 700, 11, 22),
            new BrokerSaleSummary(LocalDate.of(2026, 10, 2), "005930", 1, 100, 0, 0));
  }

  @Test
  void ac1833_결정적_순서이고_빈_수수료세금은_0이다() throws Exception {
    KisPeriodTradeProfit in =
        profit(
            row("20261002", "000020", "1", "10", "", ""),
            row("20261001", "000030", "1", "10", "", ""),
            row("20261001", "000010", "1", "10", "", ""));
    int detailCount = in.details().size();

    List<BrokerSaleSummary> first = KisTradeProfitMapper.toSaleSummaries(in);
    List<BrokerSaleSummary> second = KisTradeProfitMapper.toSaleSummaries(in);

    assertThat(first).isEqualTo(second);
    assertThat(first)
        .extracting(BrokerSaleSummary::stockCode)
        .containsExactly("000010", "000030", "000020");
    assertThat(first).allSatisfy(s -> assertThat(s.fee() + s.tax()).isZero());
    assertThat(in.details()).hasSize(detailCount);
  }

  @Test
  void ac1834_해석_불가_값은_invalid_response이고_원본값을_싣지_않는다() throws Exception {
    List<KisPeriodTradeProfit> bad =
        List.of(
            profit(row("20261001", "005930", "12x34", "100", "0", "0")),
            profit(row("20261001", "005930", "1", "99zz", "0", "0")),
            profit(row("2026-99", "005930", "1", "100", "0", "0")),
            profit(row("20261001", "", "1", "100", "0", "0")),
            profit(row("20261001", "005930", "1", "100", "7q", "0")));
    List<String> raw = List.of("12x34", "99zz", "2026-99", "7q");

    for (KisPeriodTradeProfit p : bad) {
      assertThatThrownBy(() -> KisTradeProfitMapper.toSaleSummaries(p))
          .isInstanceOfSatisfying(
              BrokerApiException.class,
              e -> {
                assertThat(e.getStatusCode()).isEqualTo("invalid_response");
                raw.forEach(v -> assertThat(e.getMessage()).doesNotContain(v));
              });
    }
  }
}
