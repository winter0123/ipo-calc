package com.vibeaiteam.brokerintegration.adapter;

import com.vibeaiteam.brokerintegration.service.matching.BrokerSaleSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * 증권사별 매도 내역 조회 포트(조회 전용).
 *
 * <p>증권사마다 다른 조회 방식을 숨기고 일자×종목 매도 집계({@link BrokerSaleSummary})만 돌려준다. 주문 관련 메서드는 선언하지 않으며 {@code
 * BrokerReadOnlyArchitectureTest}가 메서드명과 선언 개수를 고정한다.
 */
public interface BrokerSaleSource {

  /**
   * 이 소스가 담당하는 증권사 식별자.
   *
   * @return 인증정보의 {@code apiProvider} 값 (예: KIS, KIWOOM, LS)
   */
  String provider();

  /**
   * 기간 안의 매도 내역을 조회한다.
   *
   * @param context 호출 시점의 인증정보·토큰·조회 대상 종목
   * @param from 조회 시작일 (포함)
   * @param to 조회 종료일 (포함)
   * @return 일자·종목별 매도 집계
   */
  List<BrokerSaleSummary> fetchSales(BrokerFetchContext context, LocalDate from, LocalDate to);

  /**
   * 한 번에 조회할 수 있는 최대 기간(일, 시작일~종료일 차이).
   *
   * @return 최대 기간
   */
  default int maxRangeDays() {
    return 3650;
  }

  /**
   * 최대 기간을 넘었을 때 사용자에게 보여 줄 안내 문구.
   *
   * @return 안내 문구
   */
  default String rangeLimitMessage() {
    return "조회 기간은 최대 10년까지입니다.";
  }
}
