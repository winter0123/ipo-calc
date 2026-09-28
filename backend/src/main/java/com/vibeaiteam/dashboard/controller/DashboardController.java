package com.vibeaiteam.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 집계 API 컨트롤러
 * 전체/투자자별/증권사별/기간별 실현수익 집계를 제공한다.
 * 모든 엔드포인트는 읽기 전용 GET이며, 계산 결과를 저장하지 않는다 (D-D-002).
 * 계정 소유권 검증: 로그인한 Account가 소유한 Investor의 데이터만 집계 대상으로 삼는다 (AC-013).
 * 
 * 실제 엔드포인트는 SF-2(/summary), SF-3(/by-investor, /by-securities-firm), SF-4(/by-period)에서 각각 구현한다.
 * 이 SF-1에서는 클래스 골격만 정의한다.
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    // SF-1: 기반 계층만 구현 — 엔드포인트 메서드는 SF-2~4에서 추가
}