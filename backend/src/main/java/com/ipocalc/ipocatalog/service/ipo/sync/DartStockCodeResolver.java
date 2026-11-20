package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.accountauthentication.util.validation.StockCodes;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** DART 공시 이력과 기업개황에서 확인된 종목코드를 공통 규칙으로 결정한다. */
@Slf4j
final class DartStockCodeResolver {
  private DartStockCodeResolver() {}

  /**
   * 공시 이력에 유일한 코드가 있으면 최신 공시의 코드가 비어 있어도 사용한다. 서로 다른 코드가
   * 나타나면 자동 코드 연결만 보류한다. 이력이 비어 있는 최근 공모에 한해 기업개황을 보조 조회한다.
   */
  static String resolve(
      OpenDartCorpDisclosureClient client,
      Clock clock,
      OpenDartDisclosureItem filing,
      List<OpenDartDisclosureItem> history,
      LocalDate subscriptionStart,
      String existingStockCode) {
    List<String> codes =
        history.stream()
            .filter(item -> filing.getCorpCode() != null
                && filing.getCorpCode().equals(item.getCorpCode()))
            .map(OpenDartDisclosureItem::getStockCode)
            .map(DartStockCodeResolver::validatedCode)
            .filter(code -> code != null)
            .distinct()
            .toList();
    String latest = validatedCode(filing.getStockCode());
    if (latest != null && !codes.contains(latest)) {
      codes = new java.util.ArrayList<>(codes);
      codes.add(latest);
    }
    if (codes.size() > 1) {
      log.warn("DART 종목코드 충돌로 코드 연결만 보류: corpCode={}", filing.getCorpCode());
      return null;
    }
    if (codes.size() == 1) {
      return codes.get(0);
    }
    LocalDate today = LocalDate.now(clock);
    if (existingStockCode != null || subscriptionStart == null
        || subscriptionStart.isBefore(today.minusDays(120))
        || subscriptionStart.isAfter(today.plusDays(59))) {
      return null;
    }
    return client.getCompanyOverview(filing.getCorpCode())
        .map(overview -> validatedCode(overview.stockCode()))
        .orElse(null);
  }

  private static String validatedCode(String raw) {
    String code = StockCodes.normalize(raw);
    return code != null && code.matches("[0-9A-Z]{6}") ? code : null;
  }
}
