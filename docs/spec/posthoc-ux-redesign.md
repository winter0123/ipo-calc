# 사후 등록 UX/UI 전면 개선 사양서 (Posthoc UX Redesign Specification)

> **프로젝트**: React + TypeScript, FSD 아키텍처(app→pages→widgets→features→entities→shared), TanStack Query
> **범위**: 화면(screen) 단위 프론트엔드 기능 명세
> **API 계약 출처**: 기존 API 재사용 (GET /investment-history, GET /investors, GET /securities-accounts)

---

## 목차

1. [개요](#1-개요)
2. [용어 정의](#2-용어-정의)
3. [세부 기능별 화면 명세](#3-세부-기능별-화면-명세)
   - [3.1 SF-1: 투자 내역 조회 화면 — 계좌 선택형 재설계](#31-sf-1-투자-내역-조회-화면---계좌-선택형-재설계)
   - [3.2 SF-2: 공모주 일정 달력 뷰](#32-sf-2-공모주-일정-달력-뷰)
   - [3.3 SF-3: 대시보드 월별 탭 UX 개선](#33-sf-3-대시보드-월별-탭-ux-개선)
   - [3.4 SF-4: 공통 레이아웃 컴포넌트(`MainLayout`) 도입](#34-sf-4-공통-레이아웃-컴포넌트mainlayout-도입)
4. [수용 기준 (Acceptance Criteria)](#4-수용-기준-ac-)
5. [E2E 테스트 범위](#5-e2e-테스트-범위)

---

## 1. 개요

이 specification은 Epic 19(사후 등록 UX/UI 전면 개선)의 기능을 정의한다. 현재 투자 내역 조회 화면에서 투자자 ID, 증권계좌 ID, IPO ID를 직접 숫자로 입력하는 방식 대신, 로그인 사용자가 등록한 투자자 및 증권계좌 목록에서 드롭다운 선택 방식으로 변경하여 사용성을 개선한다.

백엔드 API 변경은 없으며, 프론트엔드만 드롭다운 선택값을 숫자 ID로 변환하여 기존 API와 호환되도록 구현한다.

## 2. 용어 정의

| 용어 | 정의 |
|------|------|
| 투자자 | 사용자가 등록한 투자자(개인 또는 법인) |
| 증권계좌 | 투자자가 특정 증권사에 개설한 거래 계좌 |
| IPO | Initial Public Offering (공모주) |
| 드롭다운 체인 | 투자자 선택 시 해당 투자자의 증권계좌 목록이 동적으로 로드되는 연쇄형 드롭다운 |
| 빈 상태 | 등록된 투자자/증권계좌가 0건인 상태 |

## 3. 세부 기능별 화면 명세

### 3.1 SF-1: 투자 내역 조회 화면 — 계좌 선택형 재설계

#### 화면 목적
사용자는 투자자 선택 드롭다운과 증권계좌 선택 드롭다운을 통해 투자 내역을 조회하고, 투자자/증권계좌/공모주별로 필터링하여 목록을 확인한다. 이 화면은 읽기 전용 조회 화면이다.

#### 사용하는 API
- `GET /investment-history` — 투자 내역 목록 조회(필터 적용)
  - 쿼리 파라미터: `investorId?`, `securitiesAccountId?`, `ipoId?`, `page?`, `size?` (선택 필터)
  - 응답 200: InvestmentHistoryResponse 객체 (content 필드에 InvestmentHistoryItem[] 배열 포함, 페이지네이션 정보: totalElements, totalPages, number, size; 쿼리 파라미터: page?, size?)
  - 응답 500: 서버 오류
- `GET /investors` — 투자자 목록 조회
  - 응답 200: InvestorResponse[] 배열
  - 응답 500: 서버 오류
- `GET /securities-accounts?investorId=` — 특정 투자자의 증권계좌 목록 조회
  - 쿼리 파라미터: `investorId` (선택)
  - 응답 200: SecuritiesAccount[] 배열
  - 응답 500: 서버 오류
  - 응답 404: 투자자를 찾을 수 없음

#### 재사용할 기존 코드
- `ui/src/entities/investor/api-client.ts`: `fetchInvestors()` 함수 (InvestorResponse[] 반환)
- `ui/src/entities/securities-account/api-client.ts`: `fetchSecuritiesAccounts()` 함수 (SecuritiesAccount[] 반환, investorId 쿼리 파라미터를 받지 않으므로 동적 로드를 위해 확장 필요)  
- `ui/src/entities/subscription/api-client.ts`: `getInvestmentHistory(filters)` 함수 (InvestmentHistoryResponse 반환)
- `ui/src/entities/investor/types.ts`: `InvestorResponse` 타입
- `ui/src/entities/securities-account/types.ts`: `SecuritiesAccount` 타입
- `ui/src/entities/subscription/types.ts`: `InvestmentHistoryResponse`, `InvestmentHistoryItem`, `InvestmentHistoryFilters` 타입
- `ui/src/features/investment-history/useInvestmentHistory.ts`: `useInvestmentHistory(filters)` 훅 (기존 TanStack Query useQuery 훅 사용)
- 투자자별 증권계좌 목록을 동적으로 로드하는 로직과 투자자 목록용 훅이 없으므로 신규 훅 필요: `features/investment-history/ 아래 useInvestorList, useSecuritiesAccountList`

#### 필터 항목 (상단 필터 바)

| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| investorId | select | 아니오 | 투자자 선택 드롭다운 — 로그인 사용자의 투자자 목록 |
| securitiesAccountId | select | 아니오 | 증권계좌 선택 드롭다운 — 선택된 투자자의 증권계좌 목록 |
| ipoId | select | 아니오 | 공모주 선택 드롭다운 (선택 사항) |

- 모든 필터는 선택 사항이며, 빈 값이면 해당 필터가 적용되지 않음
- 필터 바는 화면 상단에 위치
- 필터 초기화 버튼 제공

#### 표시 항목

목록 화면 표시 항목:

| 필드명 | 설명 | 출처 |
|--------|------|------|
| id | 구독 ID | API 응답 (InvestmentHistoryItem.id) |
| investorName | 투자자명 | API 응답 (InvestmentHistoryItem.investorName) |
| securitiesFirmName | 증권사명 | API 응답 (InvestmentHistoryItem.securitiesFirmName) |
| ipoName | 공모주명 | API 응답 (InvestmentHistoryItem.ipoName) |
| subscriptionStatus | 상태 | API 응답 (InvestmentHistoryItem.subscriptionStatus) |

상세 화면 표시 항목 (`GET /investment-history/{subscriptionId}`):

| 필드명 | 설명 | 출처 |
|--------|------|------|
| id | 구독 ID | API 응답 |
| investorName | 투자자명 | API 응답 |
| securitiesFirmName | 증권사명 | API 응답 |
| ipoId | 공모주 ID | API 응답 |
| ipoName | 공모주명 | API 응답 |
| ipoFixedPrice | 공모주 확정가 | API 응답 |
| subscribedAt | 청약일 | API 응답 |
| subscriptionQuantity | 청약 수량 | API 응답 |
| subscriptionStatus | 청약 상태 | API 응답 |
| allocation.allocatedQuantity | 배정 수량 | API 응답 |
| allocation.allocationStatus | 배정 상태 | API 응답 |
| sales[].id | 매도 ID | API 응답 |
| sales[].quantity | 매도 수량 | API 응답 |
| sales[].price | 매도 단가 | API 응답 |
| sales[].amount | 매도 금액 | API 응답 |
| sales[].soldAt | 매도 일시 | API 응답 |
| remainingQuantity | 남은 수량 | API 응답 |

- 배정(allocation)과 매도(sales)는 중첩하여 표시
- remainingQuantity는 읽기 전용으로 참고 표시

#### 화면 상태

| 상태 | 설명 | UI 동작 |
|------|------|---------|
| 초기 (idle) | 화면 로드 시, 필터 대기 상태 | 투자자 드롭다운만 활성화, 결과 영역 숨김 |
| 투자자 로딩 중 | 투자자 목록 로딩 중 | 투자자 드롭다운에 로딩 표시 |
| 증권계좌 로딩 중 | 증권계좌 목록 로딩 중 | 증권계좌 드롭다운에 로딩 표시 |
| 로딩 (loading) | 투자 내역 API 호출 중 | 결과 영역에 스피너 표시 |
| 성공 (success) | 200 응답 수신, 데이터 존재 | 투자 내역 목록/상세 표시 |
| 투자자 0명 | `GET /investors` 응답이 빈 배열(`[]`) | "등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요." 안내 메시지 + 투자자 등록 페이지 링크 제공 |
| 조회결과 0건 | `GET /investment-history` 응답의 content 배열이 빈 (`[]`) | "해당 조건에 해당하는 기록이 없습니다." 안내 메시지 + 필터 초기화 버튼 제공 |
| 에러 (error) | 500/네트워크 오류, 상세 조회 시 404 | 에러 메시지 표시 |

#### 에러 처리

| 에러 유형 | 필드 | 인라인 에러 메시지 |
|-----------|------|-------------------|
| 404 Not Found (투자자 미존재) | investorId | "투자자를 찾을 수 없습니다." |
| 500 Internal Server Error | 전체 화면 | "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." |
| 네트워크 오류 | 전체 화면 | "네트워크 연결을 확인해 주세요." |

#### 빈 상태 처리

- 등록된 투자자가 0건인 경우 "등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요." 안내 메시지를 표시한다
- 투자자 등록 페이지로 이동하는 링크를 함께 제공한다
- 별도의 특수 컴포넌트 없이 기존 빈 상태 표시 패턴을 재사용한다

#### 성공 후 이동 경로
- 투자 내역 조회 완료 후 동일 화면에 결과가 표시됨 (이동 불필요)
- 상세 구독 조회 완료 후 동일 화면에 상세 정보가 표시됨
- 다른 화면으로 이동하려면 네비게이션 링크로 대시보드 화면으로 이동 가능

#### 접근성 및 반응형
- 모든 드롭다운에 `<label>` 또는 `aria-label` 요소 연결
- 에러 메시지에 `aria-live="polite"` 속성 적용
- 포커스 관리: 에러 발생 시 첫 번째 에러 필드로 자동 포커스
- 색상만으로 에러를 전달하지 않음(아이콘/텍스트 병행)
- 키보드 네비게이션 지원(Tab, Enter, Escape)
- `aria-invalid` 및 `aria-describedby` 속성 사용
- 드롭다운 탭 전환 시 현재 활성 옵션에 `aria-selected="true"`, 비활성 옵션에 `aria-selected="false"` 적용
- 빈 결과 안내 메시지에 `role="status"` 속성 적용
- 반응형 브레이크포인트: 640px(모바일), 768px(태블릿), 1024px(데스크톱)
- 모바일 우선(mobile-first) 접근
- 드롭다운 터치 대상 최소 44×44px 보장

### 3.2 SF-2: 공모주 일정 달력 뷰
개요(갱신 2026-09-30: `GET /ipo-schedule`는 폐기되어 기존 `GET /ipos`를 사용하며 배정·환불일은 제외하고 구현됨): Epic 17의 `GET /ipo-schedule` API에 의존하는 신규 페이지로, 공모주 일정을 달력 형태로 표시한다. 로그인 사용자의 계좌 등록 여부와 무관하게 전체 공모주 일정을 보여준다. 세부 사양은 Epic 17의 spec을 참조한다.

### 3.3 SF-3: 대시보드 월별 탭 UX 개선
개요: 기존 대시보드 월별 탭에 전월/다음월 이동 버튼 추가 및 현재 선택된 월을 더 명확히 표시한다. 세부 사양은 docs/breakdown/posthoc-ux-redesign.md의 SF-3 섹션을 참조한다.

### 3.4 SF-4: 공통 레이아웃 컴포넌트(`MainLayout`) 도입
개요: 모든 페이지를 `<header>` + `<main>` + (선택적 `<footer>`) 구조로 감싸는 공통 레이아웃 컴포넌트를 도입하여 접근성 위반(landmark-one-main, region)을 근본적으로 해결한다. 세부 사양은 docs/breakdown/posthoc-ux-redesign.md의 SF-4 섹션을 참조한다.

---

## 4. 수용 기준 (Acceptance Criteria)

### AC-1901: 투자자 드롭다운 표시
**Given** 로그인 후 투자 내역 화면이 로드됨  
**When** `GET /investors` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 투자자 목록이 드롭다운에 표시된다  
**And** 각 항목에 투자자 ID와 이름이 포함된다

### AC-1902: 증권계좌 연쇄 선택
**Given** 투자자 선택 드롭다운에서 특정 투자자가 선택됨  
**When** `GET /securities-accounts?investorId={selectedInvestorId}` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 해당 투자자의 증권계좌 목록이 증권계좌 드롭다운에 표시된다  
**And** 이전 선택된 증권계좌가 초기화된다

### AC-1903: 투자자만 선택해도 조회 가능
**Given** 투자자 선택 드롭다운에서 투자자가 선택되었으나 증권계좌는 선택되지 않음  
**When** `GET /investment-history?investorId={selectedInvestorId}&securitiesAccountId=` 요청을 보냄  
**Then** 200 응답을 받음  
**And** 해당 투자자의 전체 투자 내역이 표시된다(증권계좌 미선택 = 전체 조회)

### AC-1904: 투자자 0명 빈 상태 처리
**Given** `GET /investors` 응답이 빈 배열(`[]`)임  
**When** 투자 내역 화면이 로드됨  
**Then** 빈 상태 화면이 표시됨  
**And** "등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요." 안내 메시지가 표시됨  
**And** 투자자 등록 페이지로 이동하는 링크가 제공됨

### AC-1905: 페이지네이션·빈 상태·에러 처리 회귀 없음
**Given** 기존 투자 내역 조회 기능이 정상 동작함  
**When** 새로운 드롭다운 필터로 투자 내역을 조회함  
**Then** 기존 페이지네이션이 정상 동작함  
**And** 빈 결과 시 적절한 안내가 표시됨 (투자자 0명 또는 조회결과 0건)  
**And** 오류 시 적절한 에러 메시지가 표시됨

---

## 5. E2E 테스트 범위

### 5.1 기본 플로우 (Happy Path)

#### 시나리오 1: 로그인 -> 투자자 목록 로드 -> 증권계좌 연쇄 로드 -> 투자 내역 조회
1. 로그인 후 투자 내역 화면으로 이동
2. `GET /investors` 호출 후 투자자 목록이 드롭다운에 표시되는지 확인
3. 투자자 선택 시 `GET /securities-accounts?investorId={selectedId}` 호출 후 해당 투자자의 증권계좌 목록이 표시되는지 확인
4. 증권계좌 선택 후 투자 내역 조회 시도
5. `GET /investment-history?investorId={selectedId}&securitiesAccountId={selectedAccountId}` 호출 확인
6. 투자 내역 목록이 올바르게 표시되는지 확인

#### 시나리오 2: 투자자만 선택해도 조회 가능
1. 투자자 선택 드롭다운에서 투자자 선택
2. 증권계좌 선택 드롭다운은 비워둔 상태 유지
3. 투자 내역 조회 시도
4. `GET /investment-history?investorId={selectedId}&securitiesAccountId=` 호출 확인
5. 해당 투자자의 전체 투자 내역이 표시되는지 확인

#### 시나리오 3: 투자자 0명 빈 상태 처리
1. `GET /investors` 모킹하여 빈 배열 반환
2. 투자 내역 화면 로드
3. 빈 상태 화면이 표시되는지 확인
4. "등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요." 메시지가 표시되는지 확인
5. 투자자 등록 페이지 링크가 제공되는지 확인

### 5.2 빈 결과 케이스

#### 시나리오 4: 투자 내역 빈 결과
1. 특정 투자자와 증권계좌로 필터 설정
2. `GET /investment-history`에 해당 조건으로 호출 시 빈 배열 반환하도록 모킹
3. 빈 결과 화면이 표시되는지 확인
4. "해당 조건에 해당하는 기록이 없습니다." 안내 메시지가 표시되는지 확인
5. 필터 초기화 버튼이 제공되는지 확인
6. 필터 초기화 버튼 클릭 후 필터가 초기화되고 전체 목록이 다시 표시되는지 확인

### 5.3 에러 케이스

#### 시나리오 5: 투자자 목록 로드 실패
1. `GET /investors` 호출 시 500 오류 반환하도록 모킹
2. 에러 메시지가 표시되는지 확인
3. "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." 메시지가 표시되는지 확인

#### 시나리오 6: 네트워크 오류
1. 네트워크 차단 상태에서 투자 내역 화면 로드 시도
2. "네트워크 연결을 확인해 주세요." 메시지가 표시되는지 확인

### 5.4 드롭다운 상호작용 테스트

#### 시나리오 7: 투자자 변경 시 증권계좌 초기화
1. 첫 번째 투자자 선택 및 해당 증권계좌 목록 로드
2. 특정 증권계좌 선택
3. 두 번째 투자자 선택
4. 증권계좌 드롭다운이 초기화되고 해당 두 번째 투자자의 증권계좌 목록으로 업데이트되는지 확인

#### 시나리오 8: 공모주 필터 연동
1. 공모주 선택 드롭다운이 존재하고 옵션이 표시되는지 확인 (사용 가능한 경우)
2. 공모주 선택 후 투자 내역 조회 시 `ipoId` 파라미터가 포함되는지 확인

### 5.5 접근성 테스트

#### 시나리오 9: axe-core 접근성 검증
1. 투자 내역 화면에서 `axe-core` 스캔 수행
2. WCAG 2.1 AA 위반 사항이 없는지 검증
3. `aria-live`, `aria-invalid`, `aria-describedby`, `aria-selected` 속성 올바른 적용 확인

### 5.6 반응형 테스트

#### 시나리오 10: 다양한 화면 크기
1. 640px(모바일), 768px(태블릿), 1024px(데스크톱)에서 각 화면 렌더링 확인
2. 드롭다운이 각 브레이크포인트에 맞게 표시되는지 확인
3. 모바일에서는 단일 컬럼, 태블릿에서는 적절한 간격, 데스크톱에서는 전체 레이아웃이 적용되는지 확인

### 5.7 터치 대상 크기 테스트

#### 시나리오 11: 드롭다운 터치 대상
1. 모든 드롭다운 옵션의 터치 대상이 최소 44×44px인지 확인
2. 모바일 환경에서 드롭다운이 충분히 큰 터치 영역을 가지는지 확인

---