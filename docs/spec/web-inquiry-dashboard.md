# 웹 조회·대시보드 입력 사양서 (Web Inquiry Dashboard Specification)

> **프로젝트**: React + TypeScript, FSD 아키텍처(app→pages→widgets→features→entities→shared), TanStack Query
> **범위**: 화면(screen) 단위 프론트엔드 기능 명세
> **API 계약 출처**: D-W16-004 (docs/work-log/web-inquiry-dashboard.md)

---

## 목차

1. [투자 내역 화면](#1-투자-내역-화면)
2. [대시보드 화면](#2-대시보드-화면)
3. [공통 섹션](#3-공통-섹션)
4. [수용 기준 (Acceptance Criteria)](#4-수용-기준-ac-)
5. [E2E 테스트 범위](#5-e2e-테스트-범위)

---

## 1. 투자 내역 화면

### 화면 목적
사용자는 투자 내역을 조회하고, 투자자/증권계좌/공모주별로 필터링하여 목록을 확인한다. 특정 구독(subscription)의 상세 정보를 조회하여 배정(allocation)과 매도(sales) 내역을 중첩하여 확인한다. 이 화면은 읽기 전용 조회 화면이다(D-W16-005).

### 사용하는 API
- `GET /investment-history` (D-W16-004, Epic 11) — 투자 내역 목록 조회(필터 적용)
  - 쿼리 파라미터: `investorId?`, `securitiesAccountId?`, `ipoId?` (선택 필터)
  - 응답 200: 투자 내역 목록 (조건에 맞는 기록이 없으면 빈 배열을 담아 200 OK로 응답 — 404 아님)
  - 응답 500: 서버 오류
- `GET /investment-history/{subscriptionId}` (D-W16-004, Epic 11) — 투자 내역 상세 조회
  - 응답 200: 상세 정보 (subscription + allocation + sales[])
  - 응답 404: 구독을 찾을 수 없음

### 필터 항목 (D-W16-006: 상단 필터 바)

| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| investorId | text/select | 아니오 | 투자자 ID로 목록 필터 — 선택 사항 |
| securitiesAccountId | text/select | 아니오 | 증권계좌 ID로 목록 필터 — 선택 사항 |
| ipoId | text/select | 아니오 | 공모주 ID로 목록 필터 — 선택 사항 |

- 모든 필터는 선택 사항이며, 빈 값이면 해당 필터가 적용되지 않음
- 필터 바는 화면 상단에 위치 (사이드바 아님, D-W16-006)
- 필터 초기화 버튼 제공 (D-W16-007)

### 표시 항목 (D-W16-004: subscription + allocation + sales 중첩 표시)

목록 화면 표시 항목:

| 필드명 | 설명 | 출처 |
|--------|------|------|
| subscriptionId | 구독 ID | API 응답 |
| investorName | 투자자명 | API 응답 |
| securitiesAccount | 증권계좌 | API 응답 |
| ipoName | 공모주명 | API 응답 |
| status | 상태 | API 응답 |

상세 화면 표시 항목 (GET /investment-history/{subscriptionId}):

| 필드명 | 설명 | 출처 |
|--------|------|------|
| subscriptionId | 구독 ID | API 응답 |
| investorName | 투자자명 | API 응답 |
| securitiesAccountId | 증권계좌 ID | API 응답 |
| ipoId | 공모주 ID | API 응답 |
| allocation.allocatedQuantity | 배정 수량 | API 응답 |
| allocation.status | 배정 상태 | API 응답 |
| sales[].saleId | 매도 ID | API 응답 |
| sales[].quantity | 매도 수량 | API 응답 |
| sales[].price | 매도 단가 | API 응답 |
| sales[].amount | 매도 금액 | API 응답 |
| sales[].saleDate | 매도 일시 | API 응답 |
| remainingQuantity | 남은 수량 | API 응답 |

- 배정(allocation)과 매도(sales)는 중첩하여 표시
- remainingQuantity는 읽기 전용으로 참고 표시

### 화면 상태 (D-W16-007: 빈 결과 시 안내 메시지 + 필터 초기화 버튼)

| 상태 | 설명 | UI 동작 |
|------|------|---------|
| 초기 (idle) | 화면 로드 시, 필터 대기 상태 | 필터 바만 표시, 결과 영역 숨김 |
| 로딩 (loading) | API 호출 중 | 결과 영역에 스피너 표시 |
| 성공 (success) | 200 응답 수신, 데이터 존재 | 투자 내역 목록/상세 표시 |
| 빈 결과 (empty) | 200 응답 수신, 목록이 빈 배열(`[]`) | "해당 조건에 해당하는 기록이 없습니다." 안내 메시지 + 필터 초기화 버튼 제공 (D-W16-007) |
| 에러 (error) | 500/네트워크 오류, 상세 조회 시 404 | 에러 메시지 표시 |

### 에러 처리 (D-W16-004)

| 에러 유형 | 필드 | 인라인 에러 메시지 |
|-----------|------|-------------------|
| 404 Not Found (상세 구독 미존재) | subscriptionId | "해당 구독을 찾을 수 없습니다." |
| 500 Internal Server Error | 전체 화면 | "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." |
| 네트워크 오류 | 전체 화면 | "네트워크 연결을 확인해 주세요." |

### 빈 결과 처리 (D-W16-007)
- 빈 결과 시 "해당 조건에 해당하는 기록이 없습니다." 안내 메시지를 표시한다
- 필터 초기화 버튼을 제공하여 모든 필터를 초기화하고 전체 목록을 다시 조회할 수 있게 한다
- 별도의 특수 컴포넌트 없이 기존 빈 상태 표시 패턴을 재사용한다

### 성공 후 이동 경로
- 투자 내역 조회 완료 후 동일 화면에 결과가 표시됨 (이동 불필요)
- 상세 구독 조회 완료 후 동일 화면에 상세 정보가 표시됨
- 다른 화면으로 이동하려면 네비게이션 링크로 대시보드 화면으로 이동 가능

---

## 2. 대시보드 화면

### 화면 목적
사용자는 전체 투자 요약 및 다양한 기준(전체/투자자별/증권사별/월별/연도별)의 지표를 조회한다. 카드와 테이블로 지표를 표시하며, 차트 라이브러리는 사용하지 않는다(D-W16-003). 탭 또는 토글로 집계 기준을 전환한다(D-W16-005).

### 사용하는 API
- `GET /dashboard/summary` (D-W16-004, Epic 12) — 전체 집계
  - 응답 200: 전체 지표
  - 보안 필터링: 서버가 로그인 계정 소유 데이터만 반환
- `GET /dashboard/by-investor` (D-W16-004, Epic 12) — 투자자별 집계
  - 쿼리 파라미터: `investorId?` (선택, Epic 11 패턴)
  - 응답 200: 투자자별 지표
- `GET /dashboard/by-securities-firm` (D-W16-004, Epic 12) — 증권사별 집계
  - 응답 200: 증권사별 지표
- `GET /dashboard/by-period?unit=month|year` (D-W16-004, Epic 12) — 기간별 집계
  - 쿼리 파라미터: `unit` (month 또는 year), `year?`, `month?`
  - 응답 200: 기간별 지표
- 위 API들은 이미 Master가 서버 측에서 계정 소유권 필터링(SecurityUtils)을 추가했으므로, 프론트는 별도 investorId 파라미터 없이 호출해도 로그인 계정 소유 데이터만 반환된다(D-W16-004)

### 필터 항목 (탭 전환 — D-W16-005)

| 탭명 | 설명 | API |
|------|------|-----|
| 전체 | 전체 투자 집계 조회 | `GET /dashboard/summary` |
| 투자자별 | 투자자별 그룹 집계 조회 | `GET /dashboard/by-investor` |
| 증권사별 | 증권사별 그룹 집계 조회 | `GET /dashboard/by-securities-firm` |
| 월별 | 월 단위 기간별 집계 조회 | `GET /dashboard/by-period?unit=month` |
| 연도별 | 연 단위 기간별 집계 조회 | `GET /dashboard/by-period?unit=year` |

- 탭 전환은 동일 화면 내에서 이루어지며, 별도 화면 이동이 없다
- 기본 탭은 "전체"로 설정
- 탭 선택 시 해당 API를 호출하여 결과를 표시

### 표시 항목 (D-W16-003: 카드/테이블로 지표 표시, 차트 없음)

카드로 표시할 지표:

| 필드명 | 설명 | 출처 |
|--------|------|------|
| totalInvestmentAmount | 총투자금액 | API 응답 |
| totalPreTaxProfit | 총세전수익 | API 응답 |
| totalAfterTaxProfit | 총세후수익 | API 응답 |
| totalProfitRate | 총수익률 | API 응답 |
| participationCount | 참여 건수 | API 응답 |

- 위 5개 지표를 숫자 카드로 표시 (D-W16-003: 차트 라이브러리 미도입)
- 총투자금은 배정금액 단일 기준으로 정의됨 (D-W16-001), 별도 선택 옵션 없음
- 각 카드에 레이블과 값을 표시하며, 수익률(%)은 퍼센트 형식으로 표시
- 참여 건수는 정수로 표시
- 금액은 숫자에 원 화폐 단위를 포함하여 표시

### 화면 상태 (D-W16-007: 빈 결과 시 안내 메시지 + 필터 초기화 버튼)

| 상태 | 설명 | UI 동작 |
|------|------|---------|
| 초기 (idle) | 화면 로드 시, "전체" 탭 활성화 상태 | 카드 영역에 스피너 표시, "전체" 탭 활성 |
| 로딩 (loading) | API 호출 중 | 카드 영역에 스피너 표시, 현재 탭 비활성화 |
| 성공 (success) | 200 응답 수신, 데이터 존재 | 5개 카드가 채워져 표시 |
| 빈 결과 (empty) | 200 응답 수신, 모든 값이 0(summary) 또는 빈 배열(나머지) | "해당 조건에 해당하는 기록이 없습니다." 안내 메시지 + 필터 초기화 버튼 (D-W16-007) |
| 에러 (error) | 500/네트워크 오류 | 에러 메시지 표시 |

### 에러 처리 (D-W16-004)

| 에러 유형 | 필드 | 인라인 에러 메시지 |
|-----------|------|-------------------|
| 500 Internal Server Error | 전체 화면 | "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." |
| 네트워크 오류 | 전체 화면 | "네트워크 연결을 확인해 주세요." |

### 탭 전환 동작 (D-W16-005)
- 탭을 클릭하면 해당 API를 호출하고 결과를 현재 화면에 갱신
- 탭 전환 시 로딩 상태를 표시
- 월별/연도별 탭에서 `unit` 파라미터를 적절히 전달
- 월별 탭 선택 시 기본 최신 월을 조회하고, 필요시 연도 선택기를 함께 제공

### 성공 후 이동 경로
- 대시보드 조회 완료 후 동일 화면에 결과가 표시됨 (이동 불필요)
- 네비게이션 링크로 투자 내역 화면으로 이동 가능

---

## 3. 공통 섹션

### 3.1 필터 UI — 상단 필터 바 (D-W16-006)
- 필터는 화면 상단의 필터 바로 구현한다
- 사이드바 필터 패턴을 사용하지 않는다
- 투자 내역 화면: investorId / securitiesAccountId / ipoId 필터를 상단 바에 배치
- 대시보드 화면: 탭 전환이 필터 역할을 대신하며, 별도 상단 필터 바는 없음
- 필터 초기화 버튼은 필터 바의 끝에 배치 (D-W16-007)
- 프로젝트 전체 UI 일관성을 위해 기존 필터 패턴을 재사용한다

### 3.2 접근성 기준 (WCAG 2.1 AA, D-W13-005)
- **기준**: WCAG 2.1 AA 준수
- **검증 도구**: `axe-core` 브라우저 확장/CI 통합 단일 채택
- **필수 요소**:
  - 모든 필드/탭에 `<label>` 또는 `aria-label` 요소 연결
  - 에러 메시지에 `aria-live="polite"` 속성 적용
  - 포커스 관리: 에러 발생 시 첫 번째 에러 필드로 자동 포커스
  - 색상만으로 에러를 전달하지 않음(아이콘/텍스트 병행)
  - 키보드 네비게이션 지원(Tab, Enter, Escape)
  - `aria-invalid` 및 `aria-describedby` 속성 사용
  - 탭 전환 시 현재 활성 탭에 `aria-selected="true"`, 비활성 탭에 `aria-selected="false"` 적용
  - 빈 결과 안내 메시지에 `role="status"` 속성 적용

### 3.3 반응형 브레이크포인트 (D-W13-004, D-W16-002)

| 브레이크포인트 | 최소 너비 | 적용 대상 |
|---------------|-----------|-----------|
| 모바일 | 640px | 카드/테이블 전체 폭 100%, 단일 컬럼 레이아웃 |
| 태블릿 | 768px | 2열 레이아웃 가능(카드 그룹화) |
| 데스크톱 | 1024px | 전체 폭 제한, 양쪽 여백 확보 |

- CSS 미디어 쿼리: `@media (min-width: 640px)`, `@media (min-width: 768px)`, `@media (min-width: 1024px)`
- 모바일 우선(mobile-first) 접근
- D-W16-002에 따라 640/768/1024 브레이크포인트를 사용하며, 조사 문서에서 제안한 다른 값(`<768px`/`768-1024px`/`>1024px`)은 사용하지 않는다

---

## 4. 수용 기준 (Acceptance Criteria)

### AC-001: 투자 내역 조회 성공
**Given** 필터 없이 투자 내역 화면이 로드됨  
**When** `GET /investment-history` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 투자 내역 목록이 표시됨  
**And** 각 항목에 subscriptionId, investorName, securitiesAccount, ipoName, status가 포함됨

### AC-002: 투자 내역 필터링 성공
**Given** investorId 또는 securitiesAccountId 또는 ipoId를 필터로 입력함  
**When** `GET /investment-history?investorId=...` (또는 해당 필터) 요청을 보냄  
**Then** 200 응답을 받음  
**And** 필터 조건에 맞는 투자 내역만 표시됨

### AC-003: 투자 내역 빈 결과
**Given** 해당 조건에 기록이 없음  
**When** `GET /investment-history`에 필터를 적용하여 호출함  
**Then** 빈 결과 화면이 표시됨  
**And** "해당 조건에 해당하는 기록이 없습니다." 안내 메시지가 표시됨 (D-W16-007)  
**And** 필터 초기화 버튼이 제공됨 (D-W16-007)

### AC-004: 투자 내역 상세 조회 성공
**Given** 특정 구독(subscription)을 선택함  
**When** `GET /investment-history/{subscriptionId}` 요청을 보냄  
**Then** 200 응답을 받음  
**And** subscription + allocation + sales[]가 중첩되어 표시됨  
**And** remainingQuantity가 읽기 전용으로 참고 표시됨

### AC-005: 투자 내역 상세 404
**Given** 존재하지 않는 subscriptionId가 입력됨  
**When** `GET /investment-history/{subscriptionId}` 요청을 보냄  
**Then** 404 응답을 받음  
**And** "해당 구독을 찾을 수 없습니다." 에러 메시지가 표시됨

### AC-006: 대시보드 전체 탭 조회 성공
**Given** 대시보드 화면이 "전체" 탭 상태로 로드됨  
**When** `GET /dashboard/summary` 요청을 보냄  
**Then** 200 응답을 받음  
**And** totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount가 카드로 표시됨

### AC-007: 대시보드 투자자별 탭 조회 성공
**Given** "투자자별" 탭이 선택됨  
**When** `GET /dashboard/by-investor` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 투자자별 지표가 카드/테이블로 표시됨

### AC-008: 대시보드 증권사별 탭 조회 성공
**Given** "증권사별" 탭이 선택됨  
**When** `GET /dashboard/by-securities-firm` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 증권사별 지표가 카드/테이블로 표시됨

### AC-009: 대시보드 월별 탭 조회 성공
**Given** "월별" 탭이 선택됨  
**When** `GET /dashboard/by-period?unit=month` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 월별 지표가 카드/테이블로 표시됨

### AC-010: 대시보드 연도별 탭 조회 성공
**Given** "연도별" 탭이 선택됨  
**When** `GET /dashboard/by-period?unit=year` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 연도별 지표가 카드/테이블로 표시됨

### AC-011: 대시보드 빈 결과
**Given** 해당 조건에 데이터가 없음  
**When** 대시보드 탭을 전환하여 API를 호출함  
**Then** 빈 결과 화면이 표시됨  
**And** "해당 조건에 해당하는 기록이 없습니다." 안내 메시지가 표시됨 (D-W16-007)  
**And** 필터 초기화 버튼이 제공됨 (D-W16-007)

### AC-012: 대시보드 탭 전환 동작
**Given** 대시보드 화면에서 탭을 전환함  
**When** "투자자별" 탭에서 "월별" 탭으로 전환함  
**Then** 로딩 상태가 표시됨  
**And** `GET /dashboard/by-period?unit=month` 요청이 전송됨  
**And** 해당 탭의 결과로 화면이 갱신됨

### AC-013: 대시보드 총투자금 정의 (D-W16-001)
**Given** 대시보드 전체 탭이 조회됨  
**When** totalInvestmentAmount가 표시됨  
**Then** 배정금액 단일 기준으로 표시됨  
**And** 총투자금 기준을 선택하는 옵션이 존재하지 않음 (D-W16-001)

### AC-014: 대시보드 차트 미도입 (D-W16-003)
**Given** 대시보드 화면이 렌더링됨  
**When** 화면 구성 요소를 검사함  
**Then** 차트 라이브러리(차트/시각화 컴포넌트)가 존재하지 않음  
**And** 지표가 숫자 카드와 테이블로만 표시됨 (D-W16-003)

### AC-015: 접근성 검증 통과 (WCAG 2.1 AA)
**Given** 투자 내역 화면과 대시보드 화면이 렌더링됨  
**When** `axe-core`로 접근성 검사를 수행함  
**Then** WCAG 2.1 AA 기준을 통과함  
**And** `aria-live`, `aria-invalid`, `aria-describedby`, `aria-selected` 속성이 올바르게 적용됨

### AC-016: 반응형 레이아웃
**Given** 다양한 화면 크기에서 화면이 렌더링됨  
**When** 브라우저 너비를 640px, 768px, 1024px로 변경함  
**Then** 각 브레이크포인트에 맞는 레이아웃이 적용됨  
**And** 모바일(640px)에서는 단일 컬럼, 태블릿(768px)에서는 2열, 데스크톱(1024px)에서 전체 레이아웃

### AC-017: 필터 초기화 동작 (D-W16-007)
**Given** 빈 결과 화면이 표시됨  
**When** 필터 초기화 버튼을 클릭함  
**Then** 모든 필터가 초기화됨  
**And** `GET /investment-history` (전체 조회)가 다시 전송됨  
**And** 전체 목록이 표시됨

### AC-018: 네트워크 오류 처리
**Given** 네트워크 연결이 끊어진 상태  
**When** `GET /investment-history` 또는 `GET /dashboard/summary` 요청을 보냄  
**Then** 네트워크 오류가 감지됨  
**And** "네트워크 연결을 확인해 주세요." 메시지가 표시됨

### AC-019: 서버 500 에러 처리
**Given** 서버가 500 Internal Server Error를 반환함  
**When** `GET /investment-history` 또는 대시보드 API 요청을 보냄  
**Then** 500 응답을 받음  
**And** "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." 메시지가 표시됨

### AC-020: 필터 UI가 상단 필터 바 형태 (D-W16-006)
**Given** 투자 내역 화면이 렌더링됨  
**When** 필터 영역을 검사함  
**Then** 필터가 화면 상단에 위치함  
**And** 사이드바 필터가 존재하지 않음 (D-W16-006)

### AC-021: 필터 항목 정확성
**Given** 투자 내역 화면의 필터가 표시됨  
**When** 필터 항목을 검사함  
**Then** investorId, securitiesAccountId, ipoId 세 개의 필드가 존재함  
**And** 모든 필터가 선택 사항임

### AC-022: 대시보드 기본 탭 (전체)
**Given** 대시보드 화면이 로드됨  
**When** 화면이 처음 렌더링됨  
**Then** "전체" 탭이 기본 활성화됨  
**And** `GET /dashboard/summary`가 자동 호출됨

---

## 5. E2E 테스트 범위

### 5.1 기본 플로우 (Happy Path)

#### 시나리오 1: 로그인 -> 대시보드 전체 지표 확인
1. 로그인 후 대시보드 화면으로 이동
2. "전체" 탭이 기본 활성화되어 있는지 확인
3. `GET /dashboard/summary` 호출 후 5개 카드(totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount)가 표시되는지 확인
4. 각 카드의 값이 API 응답과 일치하는지 확인

#### 시나리오 2: 대시보드 탭 전환 및 필터 조회
1. 대시보드에서 "투자자별" 탭으로 전환
2. `GET /dashboard/by-investor` 호출 확인
3. 투자자별 지표가 카드/테이블로 표시되는지 확인
4. "증권사별" 탭으로 전환 → `GET /dashboard/by-securities-firm` 호출 확인
5. "월별" 탭으로 전환 → `GET /dashboard/by-period?unit=month` 호출 확인
6. "연도별" 탭으로 전환 → `GET /dashboard/by-period?unit=year` 호출 확인

#### 시나리오 3: 투자 내역 화면 조회 및 필터
1. 투자 내역 화면으로 이동
2. 필터 없이 `GET /investment-history` 호출 후 전체 목록 확인
3. investorId 필터에 값을 입력하여 필터링된 목록 확인
4. securitiesAccountId 필터에 값을 입력하여 필터링된 목록 확인
5. ipoId 필터에 값을 입력하여 필터링된 목록 확인
6. 특정 구독을 선택하여 `GET /investment-history/{subscriptionId}` 호출 후 상세 확인
7. subscription + allocation + sales[] 중첩 표시가 올바른지 확인
8. remainingQuantity가 읽기 전용으로 표시되는지 확인

#### 시나리오 4: 대시보드에서 투자 내역 화면으로 이동
1. 대시보드 화면에서 투자 내역 화면으로 네비게이션
2. 투자 내역 화면이 올바르게 렌더링되는지 확인

### 5.2 빈 결과 케이스

#### 시나리오 5: 투자 내역 빈 결과
1. 존재하지 않는 ipoId로 투자 내역 필터링
2. 빈 결과 화면이 표시되는지 확인
3. "해당 조건에 해당하는 기록이 없습니다." 메시지가 표시되는지 확인
4. 필터 초기화 버튼이 존재하는지 확인
5. 필터 초기화 버튼 클릭 후 전체 목록이 다시 표시되는지 확인

#### 시나리오 6: 대시보드 빈 결과
1. 특정 조건에서 데이터가 없는 상태로 탭 전환
2. 빈 결과 화면이 표시되는지 확인
3. "해당 조건에 해당하는 기록이 없습니다." 메시지와 필터 초기화 버튼 확인

### 5.3 에러 케이스

#### 시나리오 7: 투자 내역 404 (상세 구독 미존재)
1. 존재하지 않는 subscriptionId로 상세 조회
2. "해당 구독을 찾을 수 없습니다." 메시지 표시 확인

#### 시나리오 8: 네트워크 오류
1. 네트워크를 차단한 상태에서 투자 내역 조회 또는 대시보드 조회 시도
2. "네트워크 연결을 확인해 주세요." 메시지 표시 확인

#### 시나리오 9: 서버 500 오류
1. 서버가 500을 반환하는 상황 시뮬레이션
2. "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." 메시지 표시 확인

### 5.4 탭 전환 동작 테스트

#### 시나리오 10: 탭 전환 시 로딩 상태
1. 대시보드에서 탭을 전환
2. 전환 시 로딩 스피너가 표시되는지 확인
3. 새로운 탭의 결과가 올바르게 표시되는지 확인

#### 시나리오 11: 대시보드 기본 탭 자동 호출
1. 대시보드 화면 진입 시 "전체" 탭이 자동으로 활성화됨 확인
2. `GET /dashboard/summary`가 자동 호출되는지 확인

### 5.5 접근성 테스트

#### 시나리오 12: axe-core 접근성 검증
1. 투자 내역 화면에서 `axe-core` 스캔 수행
2. 대시보드 화면에서 `axe-core` 스캔 수행
3. WCAG 2.1 AA 위반 사항이 없는지 검증
4. `aria-live`, `aria-invalid`, `aria-describedby`, `aria-selected` 속성 올바른 적용 확인

### 5.6 반응형 테스트

#### 시나리오 13: 다양한 화면 크기
1. 640px(모바일), 768px(태블릿), 1024px(데스크톱)에서 각 화면 렌더링 확인
2. 투자 내역 화면과 대시보드 화면 모두에서 레이아웃이 각 브레이크포인트에 맞게 적용되는지 검증

### 5.7 금지사항 검증

#### 시나리오 14: 총투자금 선택 옵션 없음 확인 (D-W16-001)
1. 대시보드 화면에서 총투자금 기준을 선택하는 UI 요소가 존재하지 않는지 확인

#### 시나리오 15: 차트 라이브러리 미도입 확인 (D-W16-003)
1. 대시보드 화면에서 차트 컴포넌트나 차트 관련 라이브러리 임포트가 존재하지 않는지 확인
2. 지표가 숫자 카드와 테이블로만 표시되는지 확인

#### 시나리오 16: 사이드바 필터 없음 확인 (D-W16-006)
1. 투자 내역 화면에서 사이드바 필터가 존재하지 않는지 확인
2. 필터가 상단 필터 바 형태로 구현되어 있는지 확인

### 5.8 테스트 환경
- **테스트 도구**: Playwright (E2E)
- **API mocking**: 실제 엔드포인트 호출 포함
- **DB 상태 변경**: 실제 DB 상태 변경을 포함한 통합 테스트
- **세션 관리**: JSESSIONID 쿠키 기반 세션 시뮬레이션
- **접근성 테스트**: axe-core 브라우저 확장
- **반응형 테스트**: 다양한 화면 너비 시뮬레이션

---

## 참고

- **API 경로**: D-W16-004에 명시된 확정된 계약만 사용하며, 추가 경로를 추측하지 않는다.
- **프로젝트 아키텍처**: React + TypeScript, FSD(Feature-Sliced Design), TanStack Query
- **문서 의존성**: `docs/research/web-inquiry-dashboard.md`, `docs/work-log/web-inquiry-dashboard.md`
- **금지사항**: 코드 작성 금지, API 경로 추측 금지(D-W16-004만 사용), 총투자금 선택 옵션 UI 설계 금지(D-W16-001), 차트 라이브러리 사용 설계 금지(D-W16-003), 사이드바 필터 설계 금지(D-W16-006)
