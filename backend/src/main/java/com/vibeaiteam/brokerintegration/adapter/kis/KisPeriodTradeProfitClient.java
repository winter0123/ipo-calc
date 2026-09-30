package com.vibeaiteam.brokerintegration.adapter.kis;

import com.vibeaiteam.brokerintegration.adapter.BrokerQueryPort;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitDetail;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitResponse;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitSummary;
import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * KIS 기간별 매매손익 현황 조회({@code TTTC8715R}) 클라이언트.
 *
 * <p>{@link BrokerQueryPort}를 구현하며 GET만 사용한다. 주문 관련 경로·메서드는 존재하지 않는다. 허용 경로는 클래스 상수
 * {@link #PROFIT_PATH} 하나뿐이라 호출자 입력이 URI에 들어가지 않는다(research 4.3-C).
 */
public class KisPeriodTradeProfitClient implements BrokerQueryPort {

  private static final Logger log = LoggerFactory.getLogger(KisPeriodTradeProfitClient.class);

  /** 유일하게 호출을 허용하는 조회 경로. */
  private static final String PROFIT_PATH =
      "/uapi/domestic-stock/v1/trading/inquire-period-trade-profit";

  private static final String TR_ID = "TTTC8715R";
  private static final String CUSTTYPE = "P";
  private static final String SORT_DVSN = "00";
  private static final String PDNO = "";
  private static final String CBLC_DVSN = "00";
  private static final String CTX_AREA_FK100 = "";
  private static final String CTX_AREA_NK100 = "";
  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int READ_TIMEOUT_MS = 15_000;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final RestClient restClient;

  /**
   * 생성자 (테스트용 — RestClient를 직접 주입).
   *
   * @param restClient 이미 base-url과 requestFactory가 설정된 RestClient
   */
  public KisPeriodTradeProfitClient(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * 생성자 (운영용 — base-url과 타임아웃을 설정한 RestClient를 만든다).
   *
   * @param restClientBuilder RestClient 빌더
   * @param baseUrl KIS API base-url
   */
  public KisPeriodTradeProfitClient(RestClient.Builder restClientBuilder, String baseUrl) {
    this(
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(createRequestFactory())
            .build());
  }

  private static SimpleClientHttpRequestFactory createRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout(READ_TIMEOUT_MS);
    return factory;
  }

  @Override
  public KisPeriodTradeProfit queryPeriodTradeProfit(
      Long securitiesAccountId,
      KisCredentials credentials,
      KisAccessToken token,
      LocalDate from,
      LocalDate to) {

    String inqrStrtDt = from.format(DATE_FORMATTER);
    String inqrEndDt = to.format(DATE_FORMATTER);

    try {
      ResponseEntity<KisPeriodTradeProfitResponse> entity =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(PROFIT_PATH)
                          .queryParam("CANO", credentials.cano())
                          .queryParam("ACNT_PRDT_CD", credentials.acntPrdtCd())
                          .queryParam("SORT_DVSN", SORT_DVSN)
                          .queryParam("INQR_STRT_DT", inqrStrtDt)
                          .queryParam("INQR_END_DT", inqrEndDt)
                          .queryParam("PDNO", PDNO)
                          .queryParam("CBLC_DVSN", CBLC_DVSN)
                          .queryParam("CTX_AREA_FK100", CTX_AREA_FK100)
                          .queryParam("CTX_AREA_NK100", CTX_AREA_NK100)
                          .build())
              .header("authorization", "Bearer " + token.token())
              .header("appkey", credentials.appKey())
              .header("appsecret", credentials.appSecret())
              .header("tr_id", TR_ID)
              .header("custtype", CUSTTYPE)
              .retrieve()
              .toEntity(KisPeriodTradeProfitResponse.class);

      HttpStatusCode status = entity.getStatusCode();
      KisPeriodTradeProfitResponse response = entity.getBody();
      logAudit(securitiesAccountId, status.value());

      if (response == null) {
        throw new BrokerApiException("null_response", "KIS 기간별 매매손익 조회 응답 본문이 비어 있습니다.");
      }
      if (!response.isSuccess()) {
        throw new BrokerApiException(
            "kis_api_error",
            "KIS 기간별 매매손익 조회 실패: rt_cd="
                + response.rtCd()
                + ", msg_cd="
                + response.msgCd()
                + ", msg1="
                + response.msg1());
      }

      List<KisPeriodTradeProfitDetail> details =
          response.output1() == null ? List.of() : response.output1();
      return new KisPeriodTradeProfit(details, response.output2());
    } catch (BrokerApiException e) {
      throw e;
    } catch (RuntimeException e) {
      // 예외 메시지에 앱키/시크릿/토큰/CANO가 실리지 않도록 원인 예외의 타입만 남긴다.
      log.error("KIS 기간별 매매손익 조회 중 오류: securitiesAccountId={}", securitiesAccountId, e);
      throw new BrokerApiException(
          "query_failed",
          "KIS 기간별 매매손익 조회 중 오류 발생: " + e.getClass().getSimpleName(),
          e);
    }
  }

  /**
   * 호출 감사 로그를 남긴다(시각은 로그 프레임워크가 붙인다). 계좌 ID·HTTP 메서드·경로(쿼리스트링 제외)·상태코드만 기록하고
   * 앱키·앱시크릿·토큰·계좌번호(CANO)는 기록하지 않는다. 쿼리스트링에 CANO가 들어가므로 URI 전체를 남기지 않는다.
   *
   * @param securitiesAccountId 증권계좌 ID
   * @param statusCode HTTP 상태코드
   */
  private void logAudit(Long securitiesAccountId, int statusCode) {
    log.info(
        "KIS 브로커 조회 감사: securitiesAccountId={}, method=GET, path={}, status={}",
        securitiesAccountId,
        PROFIT_PATH,
        statusCode);
  }
}
