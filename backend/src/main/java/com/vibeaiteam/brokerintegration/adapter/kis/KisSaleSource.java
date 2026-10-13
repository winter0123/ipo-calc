package com.vibeaiteam.brokerintegration.adapter.kis;

import com.vibeaiteam.brokerintegration.adapter.BrokerFetchContext;
import com.vibeaiteam.brokerintegration.adapter.BrokerQueryPort;
import com.vibeaiteam.brokerintegration.adapter.BrokerSaleSource;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialValidationException;
import com.vibeaiteam.brokerintegration.service.matching.BrokerSaleSummary;
import com.vibeaiteam.brokerintegration.service.matching.KisTradeProfitMapper;
import java.time.LocalDate;
import java.util.List;

/** KIS 기간별 매매손익 조회를 {@link BrokerSaleSource}로 감싼다. 조회 동작은 {@link BrokerQueryPort} 구현이 그대로 맡는다. */
public class KisSaleSource implements BrokerSaleSource {

  private static final int CANO_LENGTH = 8;
  private static final int ACCOUNT_NUMBER_LENGTH = 10;

  private final BrokerQueryPort port;

  /**
   * 생성자.
   *
   * @param port KIS 기간별 매매손익 조회 포트
   */
  public KisSaleSource(BrokerQueryPort port) {
    this.port = port;
  }

  @Override
  public String provider() {
    return KisApiProperties.PROVIDER;
  }

  @Override
  public List<BrokerSaleSummary> fetchSales(
      BrokerFetchContext context, LocalDate from, LocalDate to) {
    String digits = context.accountNumber();
    if (digits.length() != ACCOUNT_NUMBER_LENGTH) {
      throw new ApiCredentialValidationException("계좌번호가 8자리+2자리 형식이 아니라 증권사 조회를 할 수 없습니다.");
    }
    KisCredentials credentials =
        new KisCredentials(
            context.appKey(),
            context.appSecret(),
            digits.substring(0, CANO_LENGTH),
            digits.substring(CANO_LENGTH));
    return KisTradeProfitMapper.toSaleSummaries(
        port.queryPeriodTradeProfit(
            context.securitiesAccountId(), credentials, context.token(), from, to));
  }
}
