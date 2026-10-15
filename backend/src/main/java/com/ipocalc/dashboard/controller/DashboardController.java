package com.ipocalc.dashboard.controller;

import com.ipocalc.dashboard.dto.InvestorAggregationDto;
import com.ipocalc.dashboard.dto.PeriodAggregationDto;
import com.ipocalc.dashboard.dto.SecuritiesFirmAggregationDto;
import com.ipocalc.dashboard.dto.SummaryDto;
import com.ipocalc.dashboard.exception.DashboardValidationException;
import com.ipocalc.dashboard.service.DashboardAggregationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 집계 API 컨트롤러 전체/투자자별/증권사별/기간별 실현수익 집계를 제공한다. 모든 엔드포인트는 읽기 전용 GET이며, 계산 결과를 저장하지 않는다
 * (D-D-002). 계정 소유권 검증: 로그인한 Account가 소유한 Investor의 데이터만 집계 대상으로 삼는다 (AC-013).
 *
 * <p>실제 엔드포인트는 SF-2(/summary), SF-3(/by-investor, /by-securities-firm), SF-4(/by-period)에서 각각 구현한다.
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardAggregationService dashboardAggregationService;

  /**
   * 전체 실현수익 집계 조회 현재 계정 소유 ACTIVE Sale 전체를 대상으로 총투자금, 총세전/세후수익, 수익률, 참여건수를 반환한다. 매도 기록이 없으면 모든 필드
   * 0인 SummaryDto를 반환한다 (AC-006).
   *
   * @return 전체 집계 결과
   */
  @GetMapping("/summary")
  public SummaryDto getSummary() {
    return dashboardAggregationService.getSummary();
  }

  /**
   * 투자자별 실현수익 집계 조회 현재 계정 소유 ACTIVE Sale을 투자자별로 그룹핑해 총투자금, 총세전/세후수익, 수익률, 참여건수를 반환한다. investorId
   * 오름차순으로 정렬된다 (AC-002). 매도 기록이 없으면 빈 리스트를 반환한다.
   *
   * @return 투자자별 집계 결과 리스트
   */
  @GetMapping("/by-investor")
  public List<InvestorAggregationDto> getByInvestor() {
    return dashboardAggregationService.getByInvestor();
  }

  /**
   * 증권사별 실현수익 집계 조회 현재 계정 소유 ACTIVE Sale을 증권사별로 그룹핑해 총투자금, 총세전/세후수익, 수익률, 참여건수를 반환한다.
   * securitiesFirmId 오름차순으로 정렬된다 (AC-003). 매도 기록이 없으면 빈 리스트를 반환한다.
   *
   * @return 증권사별 집계 결과 리스트
   */
  @GetMapping("/by-securities-firm")
  public List<SecuritiesFirmAggregationDto> getBySecuritiesFirm() {
    return dashboardAggregationService.getBySecuritiesFirm();
  }

  /**
   * 기간별 실현수익 집계 조회 현재 계정 소유 ACTIVE Sale을 매도일자 기준으로 기간별(month/year) 그룹핑해 총투자금, 총세전/세후수익, 수익률, 참여건수를
   * 반환한다. period 오름차순으로 정렬된다 (AC-004, AC-005). unit 파라미터는 month 또는 year만 허용하며, 그 외 값은 400 Bad
   * Request (AC-008). 매도 기록이 없으면 빈 리스트를 반환한다.
   *
   * @param unit 기간 단위 (month: "YYYY-MM", year: "YYYY")
   * @param year 선택. 지정하면 해당 연도 매도만 집계한다.
   * @param month 선택(1~12). unit=month이고 year가 있을 때 해당 월 매도만 집계한다.
   * @return 기간별 집계 결과 리스트
   * @throws DashboardValidationException unit이 month/year가 아닌 경우
   */
  @GetMapping("/by-period")
  public List<PeriodAggregationDto> getByPeriod(
      @RequestParam String unit,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    if (!"month".equals(unit) && !"year".equals(unit)) {
      throw new DashboardValidationException("unit은 month 또는 year여야 합니다.");
    }
    if (year != null && (year < 1900 || year > 9999)) {
      throw new DashboardValidationException("year는 1900~9999 범위여야 합니다.");
    }
    if (month != null && (month < 1 || month > 12)) {
      throw new DashboardValidationException("month는 1~12 범위여야 합니다.");
    }
    return dashboardAggregationService.getByPeriod(unit, year, month);
  }
}
