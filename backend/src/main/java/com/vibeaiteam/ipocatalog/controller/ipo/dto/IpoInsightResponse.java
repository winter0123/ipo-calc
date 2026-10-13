package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import com.vibeaiteam.ipocatalog.service.ipo.detail.IpoDetailSnapshot.Financial;
import com.vibeaiteam.ipocatalog.service.ipo.detail.IpoDetailSnapshot.FloatStep;
import com.vibeaiteam.ipocatalog.service.ipo.detail.IpoDetailSnapshot.Underwriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공모주 상세 화면의 부가 정보(DART 공시에서 가져와 DB에 저장한 값). 아직 없는 값은 null, 배열은 빈 배열이다.
 *
 * @param syncedAt 공시에서 마지막으로 가져온 시각
 * @param sourceReportName 출처 공시 이름
 * @param sourceUrl 출처 공시 주소(https://dart.fss.or.kr/ 만)
 * @param ceoName 대표이사
 * @param establishedOn 설립일
 * @param address 본점 주소
 * @param parValue 액면가(원)
 * @param capitalStock 자본금(원)
 * @param largestShareholderName 최대주주 이름
 * @param largestShareholderRatio 최대주주 공모 전 지분율(%)
 * @param institutionalCompetitionRate 수요예측 기관 경쟁률(예: 1096.56 → 1096.56:1)
 * @param lockUpCommitmentRatio 의무보유확약 비율(%)
 * @param financials 요약 재무(공시 순서, 최근 기 먼저)
 * @param floatSchedule 유통가능 물량 일정(누적 주식수·비율)
 * @param underwriters 인수회사
 */
public record IpoInsightResponse(
    LocalDateTime syncedAt,
    String sourceReportName,
    String sourceUrl,
    String ceoName,
    LocalDate establishedOn,
    String address,
    Long parValue,
    Long capitalStock,
    String largestShareholderName,
    BigDecimal largestShareholderRatio,
    BigDecimal institutionalCompetitionRate,
    BigDecimal lockUpCommitmentRatio,
    List<Financial> financials,
    List<FloatStep> floatSchedule,
    List<Underwriter> underwriters) {}
