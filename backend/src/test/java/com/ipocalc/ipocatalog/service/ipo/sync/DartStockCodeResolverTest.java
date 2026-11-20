package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 월별 수집과 자동 재점검이 함께 사용하는 DART 코드 선택 규칙. */
class DartStockCodeResolverTest {
  private static final Clock CLOCK =
      Clock.fixed(
          LocalDate.of(2026, 11, 20).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
          ZoneId.of("Asia/Seoul"));
  private final OpenDartCorpDisclosureClient client = mock(OpenDartCorpDisclosureClient.class);

  private OpenDartDisclosureItem filing(String receipt, String code) {
    return OpenDartDisclosureItem.builder()
        .corpCode("01025617")
        .corpName("하나에어로")
        .rceptNo(receipt)
        .stockCode(code)
        .reportNm("[발행조건확정]증권신고서(지분증권)")
        .build();
  }

  @Test
  void usesHistoricalCodeWhenLatestFilingCodeIsMissing() {
    OpenDartDisclosureItem latest = filing("20261109000163", "");
    OpenDartDisclosureItem original = filing("20261008000649", "A271160");

    String code =
        DartStockCodeResolver.resolve(
            client,
            CLOCK,
            latest,
            List.of(original, latest),
            LocalDate.of(2026, 11, 10),
            null,
            false);

    assertThat(code).isEqualTo("271160");
    verify(client, never()).getCompanyOverview("01025617");
  }

  @Test
  void doesNotFetchCompanyOverviewForNewIpoWithoutAnyFilingCode() {
    OpenDartDisclosureItem latest = filing("20261109000163", "");

    assertThat(
            DartStockCodeResolver.resolve(
                client, CLOCK, latest, List.of(latest), LocalDate.of(2026, 11, 10), null, false))
        .isNull();
    verify(client, never()).getCompanyOverview("01025617");
  }

  @Test
  void conflictingHistoricalCodesDoNotAssignEitherCode() {
    OpenDartDisclosureItem latest = filing("20261109000163", "");
    String code =
        DartStockCodeResolver.resolve(
            client,
            CLOCK,
            latest,
            List.of(filing("20261008000649", "271160"), filing("20261101000001", "999999"), latest),
            LocalDate.of(2026, 11, 10),
            null,
            false);

    assertThat(code).isNull();
    verify(client, never()).getCompanyOverview("01025617");
  }

  @Test
  void olderCompanyFromDifferentCorpCodeCannotContaminateCode() {
    OpenDartDisclosureItem latest = filing("20261109000163", "");
    OpenDartDisclosureItem unrelated =
        OpenDartDisclosureItem.builder().corpCode("00000001").stockCode("999999").build();

    assertThat(
            DartStockCodeResolver.resolve(
                client,
                CLOCK,
                latest,
                List.of(unrelated, filing("20261008000649", "271160")),
                LocalDate.of(2026, 11, 10),
                null,
                false))
        .isEqualTo("271160");
  }
}
