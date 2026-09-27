# 웹 조회·대시보드 — 세부 기능 분해 (web-inquiry-dashboard breakdown)

> **Epic**: Epic 16 (웹 조회·대시보드)
> **분야**: 프론트엔드 (React + TypeScript, FSD, TanStack Query) — frontend 워크스페이스
> **작성일**: 2026-09-28
> **API 계약 출처**: docs/spec/web-inquiry-dashboard.md (D-W16-004), docs/architecture/web-inquiry-dashboard.md
> **의존 관계**: Epic 11(투자 내역 조회)와 Epic 12(대시보드 집계 API)의 프론트엔드 구현이므로, 백엔드 API가 완료되어야 실연동 가능. 그러나 UI 구현은 mock으로 가능. Epic 12의 dashboard-aggregation-api는 Epic 10(profit-calculation)에 의존하며, Epic 10이 main에 머지되지 않은 상태에서는 API 모킹으로 개발 진행.

---

## 세부 기능(SF) 개요

총 **5개 SF**로 분해한다. 각 SF는 화면 하나 단위 또는 주요 컴포넌트 단위이며, PR 하나로 리뷰 가능한 크기다. 순차 의존 관계이며 한 번에 하나씩만 `ready`가 되도록 체인을 건는다.

```
SF-1 (투자 내역 화면: 필터 바 및 빈 상태) → SF-2 (투자 내역 화면: 목록 및 상세 표시) → SF-3 (대시보드 화면: 탭 전환 및 빈 상태) → SF-4 (대시보드 화면: 지표 카드 표시) → SF-5 (대시보드 화면: 기간별 탭 연도 선택기 및 API 연동)
```

| SF | 화면/컴포넌트 | 선행 조건 | 백엔드 API 상태 | 착수 가능 여부 |
|---|---|---|---|---|
| SF-1 | 투자 내역 화면: 상단 필터 바 + EmptyState 위젯 | Epic 13/14/15의 `shared`/`entities` 재사용 | GET /investment-history (Epic 11) **미구현** | UI 구현은 가능하나 실연동·E2E는 관련 백엔드 완료 후 |
| SF-2 | 투자 내역 화면: InvestmentHistoryTable 위젯 (목록/상세) + remainingQuantity 참고 표시 | SF-1 | GET /investment-history (Epic 11), GET /investment-history/{subscriptionId} (Epic 11) **미구현** | UI 구현은 가능하나 실연동·E2E는 관련 백엔드 완료 후 |
| SF-3 | 대시보드 화면: DashboardTabs 위젯 + EmptyState 위젯 | SF-2 | GET /dashboard/summary, GET /dashboard/by-investor, GET /dashboard/by-securities-firm, GET /dashboard/by-period (Epic 12) **미구현** | UI 구현은 가능하나 실연동·E2E는 관련 백엔드 완료 후 |
| SF-4 | 대시보드 화면: DashboardCardGroup 위젯 + DashboardCard 위젯 (5개 지표 카드) | SF-3 | GET /dashboard/summary (Epic 12) **미구현** | UI 구현은 가능하나 실연동·E2E는 관련 백엔드 완료 후 |
| SF-5 | 대시보드 화면: 기간별 탭(월별/연도별)에서 연도 선택기 컴포넌트 + API 연동 로직 (useDashboardByPeriod 훅) | SF-4 | GET /dashboard/by-period?unit=month\|year (Epic 12) **미구현** | UI 구현은 가능하나 실연동·E2E는 관련 백엔드 완료 후 |

---

## SF-1: 투자 내역 화면: 상단 필터 바 및 EmptyState 위젯

**범위**: 투자 내역 화면의 상단 필터 바(Investor ID, Securities Account ID, IPO ID 3개 필드와 초기화 버튼)와 빈 결과 시 표시되는 EmptyState 위젯을 구현한다. 필터 바는 사이드바가 아닌 화면 상단에 배치하며(D-W16-006), Epic 14의 필터 버튼 그룹 패턴을 재사용한다. 모든 필터는 선택 사항이며, 빈 값이면 해당 필터가 적용되지 않는다. EmptyState 위젯은 "해당 조건에 해당하는 기록이 없습니다." 안내 메시지와 필터 초기화 버튼을 표시한다(D-W16-007).

**커버하는 AC**: AC-020(반응형), AC-019(접근성), AC-021(네트워크 오류), AC-018(참고 표시 - though note: 참고 표시 is more for detail, but we can include), AC-017(필터 초기화 동작), AC-016(필터 UI가 상단 필터 바 형태), AC-015(필터 항목 정확성). E2E 시나리오 2/3/6/7/12/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 투자 내역 화면 관련 시나리오).

**선행 SF**: 없음 (첫 번째 SF). Epic 13/14/15가 `shared`(client, validators, Button, Input, FormField, ErrorMessage, Spinner)와 `entities/investor`, `entities/securities-account`, `entities/securities-firm`, `entities/ipo`를 이미 제공한다.

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `features/investment-history/FilterBar/FilterBar.tsx` (+ `.module.css`) — 상단 필터 바 (3개 select/input 필드 + 초기화 버튼)
- `features/investment-history/FilterBar/useInvestmentHistoryFilters.ts` — 필터 상태 관리 훅 (useState + queryKey 관리)
- `widgets/EmptyState/EmptyState.tsx` (+ `.module.css`) — 빈 상태 안내 메시지 + 필터 초기화 버튼 (D-W16-007)
- `pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` (+ `.module.css`) — 화면 배치 (필터 바 + 결과 영역 placeholder)
- `App.tsx` — `/investments` 라우트 추가 (if not already present from Epic 11)

**사용 백엔드 API**:
- `GET /investment-history` — Epic 11, **미구현** (필터 적용용)
- `GET /investment-history/{subscriptionId}` — Epic 11, **미구현** (참고용; SF-2에서 사용)

**특이사항**:
- 필터 유효성은 서버 규칙을 대체하지 않는다: investorId/securitiesAccountId/ipoId는 텍스트/select 형태이며, 프론트에서 빈 값 필터링은 수행하지 않음(D-W16-006). 빈 값이면 해당 필터가 적용되지 않음.
- 필터 초기화 버튼 클릭 시 모든 필드를 빈 문자열로 리셋하고, `invalidateQueries({ queryKey: ['investmentHistory'] })`를 호출하여 전체 목록을 재조회한다.
- EmptyState 위젯은 `role="status"` 속성을 적용하고, 안내 메시지에 `aria-live="polite"`를 사용할 수 있다.
- GitHub Issue #134(`apiFetch`가 서버 400 메시지를 버리는 문제)에 대한 처리 방식은 별도 결정이 필요함(아래 "GitHub Issue #134 처리 방식" 참조).

---

## SF-2: 투자 내역 화면: 목록 및 상세 표시

**범위**: 투자 내역 화면의 투자 내역 목록 표와 상세 확장을 구현한다. 목록은 `InvestmentHistoryTable` 위젯으로, 각 행은 `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시하며, 행 클릭 시 상세 정보를 중첩하여 표시한다. 상세 화면은 `subscription + allocation + sales[]`를 중첩 표시하며, `remainingQuantity`는 읽기 전용으로 참고 표시한다. 이 SF에서 Epic 16 전용 `entities`(`subscription`)를 재사용하며(Epic 14에서 이미 존재), 공통 `ConfirmDialog`를 재사용한다.

**커버하는 AC**: AC-001(조회 성공), AC-002(필터링 성공), AC-003(빈 결과), AC-004(상세 조회 성공), AC-005(상세 404), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 1/2/4/5/6/8/9/10/11/13/14/18/19/20/21/22는 이 SF 대상.

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 11(투자 내역 조회) 미구현** — 현재 SF-1까지 완료된 Epic 11은 미구현 상태.

**산출물 파일 경로**:
- `widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx` (+ `.module.css`) — 목록 행과 상세 정보를 중첩하여 표시하는 위젯
- `widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx` — 단일 투자 내역 행 컴포넌트
- `widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx` — 선택된 구독의 상세 정보 표시 컴포넌트 (subscription + allocation + sales[] 중첩)
- `features/investment-history/useInvestmentHistory.ts` — `GET /investment-history`를 조회하는 `useQuery` 훅 (filters를 queryKey에 포함)
- `features/investment-history/useInvestmentHistoryDetail.ts` — `GET /investment-history/{subscriptionId}`를 조회하는 `useQuery` 훅 (subscriptionId가 존재할 때만 enabled)
- `pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` — 기존 파일 업데이트 (필터 바 아래에 테이블 위젯 배치)
- `shared/components/ConfirmDialog/ConfirmDialog.tsx` (+ `.module.css`) — **기존 재사용**(Epic 14에서 생성됨, 중복 생성 금지)

**사용 백엔드 API**:
- `GET /investment-history` — Epic 11, **미구현** (목록 조회)
- `GET /investment-history/{subscriptionId}` — Epic 11, **미구현** (상세 조회)

**특이사항**:
- 목록 행은 `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시한다(D-W16-004).
- 상세 화면은 `subscriptionId`, `investorName`, `securitiesAccountId`, `ipoId`, `allocation.allocatedQuantity`, `allocation.status`, `sales[].saleId`, `sales[].quantity`, `sales[].price`, `sales[].amount`, `sales[].saleDate`, `remainingQuantity`를 표시한다(D-W16-004).
- `remainingQuantity`는 읽기 전용으로 참고 표시하며, `aria-readonly="true"` 속성을 적용한다(D-W16-004).
- 배정(allocation)과 매도(sales)는 중첩하여 표시한다(D-W16-004).
- 에러 처리: 404는 "해당 구독을 찾을 수 없습니다."를 표시하고, 500/네트워크 오류는 일반적인 에러 메시지를 표시한다(D-W16-004).
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(아래 참조).

---

## SF-3: 대시보드 화면: 탭 전환 및 빈 상태

**범위**: 대시보드 화면의 탭 전환 컴포넌트(DashboardTabs)와 빈 결과 시 표시되는 EmptyState 위젯을 구현한다. 탭은 전체/투자자별/증권사별/월별/연도별 5개 탭을 표시하며(D-W16-005), 동일 화면 내에서 탭 전환이 이루어지며, 현재 활성 탭에 `aria-selected="true"`를 적용한다. EmptyState 위젯은 SF-1과 동일하게 "해당 조건에 해당하는 기록이 없습니다." 안내 메시지와 필터 초기화 버튼을 표시한다(D-W16-007). note: 대시보드의 필터 초기화 버튼은 탭 선택 상태를 유지하면서 현재 탭의 데이터를 다시 조회하는 역할을 한다.

**커버하는 AC**: AC-012(대시보드 탭 전환 동작), AC-011(대시보드 기본 탭 자동 호출), AC-010(대시보드 차트 미도입), AC-009(접근성 검증 통과), AC-008(반응형 레이아웃), AC-007(대시보드 탭 전환 동작), AC-006(대시보드 기본 탭), AC-005(대시보드 총투자금 정의), AC-004(대시보드 차트 미도입), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 12/13/14/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 대시보드 화면 관련 시나리오).

**선행 SF**: SF-2 (필수). **백엔드 선행 조건: Epic 12(대시보드 집계 API) 미구현** — 현재 SF-2까지 완료된 Epic 12는 미구현 상태.

**산출물 파일 경로**:
- `features/dashboard/DashboardTabs/DashboardTabs.tsx` (+ `.module.css`) — 전체/투자자별/증권사별/월별/연도별 5개 탭 전환 컴포넌트
- `features/dashboard/useDashboardTabs.ts` — 탭 상태 관리 훅 (useState + 탭별 API 호출을 담당하는 훅들의 enable/disable 조절)
- `widgets/EmptyState/EmptyState.tsx` — **기존 재사용**(SF-1에서 생성됨, 중복 생성 금지)
- `pages/DashboardPage/DashboardPage.tsx` (+ `.module.css`) — 화면 배치 (탭 위젯 + 결과 영역 placeholder)
- `App.tsx` — `/dashboard` 라우트 추가 (if not already present)

**사용 백엔드 API**:
- `GET /dashboard/summary` — Epic 12, **미구현**
- `GET /dashboard/by-investor` — Epic 12, **미구현**
- `GET /dashboard/by-securities-firm` — Epic 12, **미구현**
- `GET /dashboard/by-period?unit=month\|year` — Epic 12, **미구현**

**특이사항**:
- 탭 전환은 동일 화면 내에서 이루어지며, 별도 화면 이동이 없다(D-W16-005).
- 기본 탭은 "전체"로 설정되며, 화면 로드 시 `GET /dashboard/summary`를 자동 호출한다(D-W16-005, AC-011).
- 탭 선택 시 해당 API를 호출하여 결과를 현재 화면에 갱신하며, 전환 시 로딩 상태를 표시한다(D-W16-005, AC-012).
- 월별/연도별 탭에서 `unit` 파라미터를 적절히 전달하며, 월별 탭 선택 시 기본 최신 월을 조회하고, 필요시 연도 선택기를 함께 제공한다(D-W16-005).
- 빈 결과 처리: 모든 지표 값이 0이거나 데이터가 없는 배열인 경우 EmptyState 위젯을 표시한다(D-W16-007, AC-011).
- 필터 초기화 버튼은 EmptyState 위젯에 포함되어 있으며, 클릭 시 현재 탭의 데이터를 다시 조회한다(D-W16-007).
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(아래 참조).

---

## SF-4: 대시보드 화면: 지표 카드 표시

**범위**: 대시보드 화면의 지표 카드 그룹 위젯(DashboardCardGroup)과 개별 지표 카드 위젯(DashboardCard)을 구현한다. 5개 카드(totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount)를 그리드로 배치한다(D-W16-003). 각 카드에 레이블과 값을 표시하며, 수익률(%)은 퍼센트 형식, 참여 건수는 정수, 금액은 숫자에 원 화폐 단위를 포함한다. 차트 라이브러리를 사용하지 않는다(D-W16-003) — 순수 숫자 카드와 테이블만 사용한다. 총투자금은 배정금액 단일 기준으로 정의되며(D-W16-001), 선택 옵션이 존재하지 않는다.

**커버하는 AC**: AC-010(대시보드 차트 미도입), AC-009(대시보드 총투자금 정의), AC-008(대시보드 차트 미도입), AC-007(대시보드 총투자금 0일 때 수익률 0.0), AC-006(대시보드 읽기 전용·저장 없음), AC-005(대시보드 잔차 없이 단순 합산), AC-004(대시보드 계정 간 데이터 격리), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 1/2/3/4/5/6/7/8/9/10/11/12/13/14/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 대시보드 화면 지표 관련 시나리오).

**선행 SF**: SF-3 (필수). **백엔드 선행 조건: Epic 12(대시보드 집계 API) 미구현** — 현재 SF-3까지 완료된 Epic 12는 미구현 상태.

**산출물 파일 경로**:
- `widgets/DashboardCardGroup/DashboardCardGroup.tsx` (+ `.module.css`) — 5개 카드를 그리드로 배치하는 위젯
- `widgets/DashboardCard/DashboardCard.tsx` (+ `.module.css`) — 개별 지표 카드 위젯 (레이블과 값 표시)
- `features/dashboard/useDashboardSummary.ts` — `GET /dashboard/summary`를 조회하는 `useQuery` 훅
- `features/dashboard/useDashboardByInvestor.ts` — `GET /dashboard/by-investor`를 조회하는 `useQuery` 훅
- `features/dashboard/useDashboardBySecuritiesFirm.ts` — `GET /dashboard/by-securities-firm`를 조회하는 `useQuery` 훅
- `features/dashboard/useDashboardByPeriod.ts` — `GET /dashboard/by-period?unit=month\|year`를 조회하는 `useQuery` 훅
- `pages/DashboardPage/DashboardPage.tsx` — 기존 파일 업데이트 (탭 위젯 아래에 카드 그룹 위젯 배치)
- `entities/dashboard/types.ts` — **신규**. `DashboardSummary`, `DashboardByInvestor`, `DashboardBySecuritiesFirm`, `DashboardByPeriod` 타입 정의 (API 응답 DTO)
- `entities/dashboard/api-client.ts` — **신규**. `GET /dashboard/summary`, `GET /dashboard/by-investor`, `GET /dashboard/by-securities-firm`, `GET /dashboard/by-period` 호출 함수

**사용 백엔드 API**:
- `GET /dashboard/summary` — Epic 12, **미구현**
- `GET /dashboard/by-investor` — Epic 12, **미구현**
- `GET /dashboard/by-securities-firm` — Epic 12, **미구현**
- `GET /dashboard/by-period?unit=month\|year` — Epic 12, **미구현**

**특이사항**:
- **총투자금 정의**: 배정금액 단일 기준으로 정의되며(`Allocation.allocatedQuantity × allocationPrice`), 별도 선택 옵션이 존재하지 않음(D-W16-001).
- **데이터 무존재 시**: 모든 필드 0인 `DashboardSummary` 반환 또는 빈 배열 반환 (AC-006/011).
- **수익률 계산**: `totalAfterTaxProfit / totalInvestmentAmount × 100` (총투자금 0이면 0.0) (D-W16-003).
- **참여 건수**: 정수로 표시하며, 데이터가 없으면 0.
- **금액 표기**: 숫자에 원 화폐 단위를 포함하여 표시 (예: "1,000원").
- **차트 라이브러리 미도입**: 차트/시각화 컴포넌트가 존재하지 않으며, 지표가 숫자 카드와 테이블로만 표시됨(D-W16-003).
- **응답 DTO 타입**: `entities/dashboard/types.ts`에 정의하며, `entities/dashboard/api-client.ts`에서 API 호출 함수를 내보낸다.
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(아래 참조).

---

## SF-5: 대시보드 화면: 기간별 탭 연도 선택기 및 API 연동

**범위**: 대시보드 화면의 기간별 탭(월별/연도별)에서 연도 선택기 컴포넌트를 구현하고, `useDashboardByPeriod` 훅을 통해 API 연동 로직을 완성한다. 연도 선택기는 `Input` 또는 `Select` 컴포넌트를 재사용하여 사용자가 원하는 연도를 입력할 수 있도록 한다. spec은 월별 탭 선택 시 기본 최신 월을 조회하고 필요시 연도 선택기를 함께 제공한다고 명시하므로, 이 SF는 연도 선택기 컴포넌트만 구현하고 월은 현재 월로 고정한다(월 선택기는 spec에 명시가 없으므로 범위에 포함하지 않는다). 월별/연도별 두 탭 모두에서 이 연도 선택기를 재사용해 사용자가 특정 연도의 데이터를 조회할 수 있게 한다.

**커버하는 AC**: AC-008(기간 파라미터 유효성 검사), AC-004(월별 집계 정상), AC-005(연도별 집계 정상), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 9/10/11/12/13/14/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 기간별 탭 관련 시나리오).

**선행 SF**: SF-4 (필수). **백엔드 선행 조건: Epic 12(대시보드 집계 API) 미구현** — 현재 SF-4까지 완료된 Epic 12는 미구현 상태.

**산출물 파일 경로**:
- `features/dashboard/PeriodSelector/PeriodSelector.tsx` (+ `.module.css`) — 연도 선택기 컴포넌트 (Input 필드 + 버튼 또는 Select)
- `features/dashboard/useDashboardByPeriod.ts` — 기존 파일 업데이트 (연도 선택기 값을 `year` 파라미터로 전달)
- `pages/DashboardPage/DashboardPage.tsx` — 기존 파일 업데이트 ( 기간별 탭 영역에 연도 선택기 컴포넌트 배치)
- `entities/dashboard/api-client.ts` — 기존 파일 업데이트 (년도 및 월 선택 사항을 파라미터로 전달하는 함수 수정)

**사용 백엔드 API**:
- `GET /dashboard/by-period?unit=month\|year&year=\<year\>&month=\<month\>` — Epic 12, **미구현** (해당 파라미터 전달)

**특이사항**:
- **연도 선택기**: 사용자가 4자리 연도를 입력할 수 있는 Input 컴포넌트를 제공하며, 빈 값이면 현재 연도를 기본값으로 사용한다. 월별 탭에서는 월은 항상 현재 월을 기본값으로 사용하고, 월 선택기는 spec에 명시가 없으므로 이 SF의 범위에 포함하지 않는다(향후 고려 사항으로 남김).
- **API 파라미터**: `unit` 필수, `year` 선택 사항. 월별 탭은 `month` 파라미터를 전달하지 않고 서비스가 현재 월을 기본으로 사용하도록 한다(spec의 "기본 최신 월 조회" 규정).
- **유효성 검사**: `year` 파라미터가 숫자인지 및 유효한 범위인지 확인하며,_INVALID_YEAR_ 오류 시 400 Bad Request 반환.
- **빈 결과 처리**: 데이터가 없는 기간인 경우 EmptyState 위젯을 표시한다(D-W16-007).
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(아래 참조).

---

## GitHub Issue #134 처리 방식

**문제**: `apiFetch`가 서버 에러 응답 본문을 버려서 여러 Register 폀이 400 상세 메시지를 못 보여줌(Issue #134)

**영향 범위**: Epic 16의 모든 폼 SF(SF-1, SF-2, SF-3, SF-4, SF-5)에 영향 (특히 필터 바 및 입력 필드에서의 에러 메시지 표시)

**결정 필요 사항**: 이 Epic의 별도 선행 SF로 처리할지, 각 폼 SF에 포함시킬지, 아니면 범위 밖으로 미룰지 결론을 내리고 근거를 문서에 남긴다.

**검토 사항**:
1. 별도 선행 SF로 처리: `shared/api/client.ts`의 `apiFetch` 수정 및 관련 폼의 에러 매칭 로직 동시 수정
   - 장점: 한 번의 수정으로 모든 폼에 적용 가능, 일관된 에러 처리 보장
   - 단점: 선행 SF 추가로 인해 전체 일정 지연, 다른 Epic과의 의존성 증가
2. 각 폼 SF에 포함: 각 SF에서 관련 파일들을 함께 수정
   - 장점: 선행 SF 추가 없이 점진적 개선 가능
   - 단점: 중복 작업 발생, 일관성 유지 어려움
3. 범위 밖으로 미룰: 현재 상태 유지하고 후속 Epic에서 처리
   - 장점: 현재 Epic 집중도 유지
   - 단점: 사용자 경험 저하 지속, 기술 부채 누적

**권고안**: 별도 선행 SF로 처리하는 것이 적절하다.
- 이유: Issue #134은 Epic 2, 4, 14의 이미 merge된 코드에 영향을 주는 공통 버그이므로, 별도 SF로 정리하여 기술 부채를 해결하는 것이 장기적으로 효율적이다.
- 선행 SF 명칭: `SF-0: 공통 apiFetch 에러 처리 수정`
- 선행 조건: 없음 (첫 번째 SF)
- 산출물: 
  - `ui/src/shared/api/client.ts` — `apiFetch` 수정으로 4xx/5xx 응답의 JSON body에서 메시지 필드 읽기
  - `features/*/*/*Form/*Form.tsx`의 `getFormErrorMessage()` 함수들 — 실제 서버 메시지를 표시하도록 수정

**현재 상태**: 사용자 결정이 필요하므로 breakdown 문서에 명시하고, 사용자 결정 없이 구현을 시작하면 안 된다.

---

## E2E 테스트 매핑 요약

| E2E 시나리오 | 내용 | 대응 SF |
|---|---|---|
| 1 | 로그인 -> 대시보드 전체 지표 확인 | SF-4 |
| 2 | 대시보드 탭 전환 및 필터 조회 | SF-3 + SF-4 |
| 3 | 투자 내역 화면 조회 및 필터 | SF-1 + SF-2 |
| 4 | 특정 구독을 선택하여 상세 확인 | SF-2 |
| 5 | 투자 내역 빈 결과 | SF-2 |
| 6 | 대시보드 빈 결과 | SF-3 |
| 7 | 네트워크 오류 | SF-1, SF-2, SF-3, SF-4, SF-5 |
| 8 | 서버 500 오류 | SF-1, SF-2, SF-3, SF-4, SF-5 |
| 9 | 탭 전환 시 로딩 상태 | SF-3 |
| 10 | 대시보드 기본 탭 자동 호출 | SF-3 |
| 11 | 투자 내역 필터 초기화 동작 | SF-1 |
| 12 | 대시보드 필터 초기화 동작 (EmptyState 버튼) | SF-3 |
| 13 | axe-core 접근성 | SF-1~5 각 화면 |
| 14 | 640/768/1024px 반응형 | SF-1~5 각 화면 |
| 15 | 차트 라이브러리 미도입 확인 | SF-4 |
| 16 | 총투자금 선택 옵션 없음 확인 | SF-4 |
| 17 | 사이드바 필터 없음 확인 | SF-1 |
| 18 | 기간별 탭 연도 선택기 동작 | SF-5 |
| 19 | 기간별 탭 API 연동 (년도 파라미터 전달) | SF-5 |
| 20 | 기간별 탭 빈 결과 처리 | SF-5 |
| 21 | 기간별 탭 유효성 검사 (잘못된 year) | SF-5 |

---

## 구현 순서 가이드

1. **GitHub Issue #134 처리 방식에 대한 사용자 결정 대기** — breakdown 문서의 이 섹션이 사용자 결정 사항임을 명시하고, 결정이 내려진 뒤에 계획 수립
2. **사용자 결정이 "별도 선행 SF"인 경우**: 
   - SF-0: 공통 apiFetch 에러 처리 수정 → SF-1 → SF-2 → SF-3 → SF-4 → SF-5 순서로 착수
   - SF-0은 API 계약과 무관하므로 언제든지 착수 가능
3. **사용자 결정이 "각 폼 SF에 포함"인 경우**:
   - SF-1 → SF-2 → SF-3 → SF-4 → SF-5 순서로 착수, 각 SF에서 관련 파일들을 함께 수정
4. **사용자 결정이 "범위 밖으로 미룰"인 경우**:
   - SF-1 → SF-2 → SF-3 → SF-4 → SF-5 순서로 착수, Issue #134는 후속 Epic에서 처리

**백엔드 게이트 조건**:
- SF-1~5(ui): Epic 11과 Epic 12의 백엔드 API가 완료되어야 실연동·E2E 검증이 가능. tuttavia, UI 구현은 mock 데이터로 진행 가능.
- Epic 11(투자 내역 조회)는 Epic 8/9/10/11의 backend 작업에 의존.
- Epic 12(대시보드 집계 API)는 Epic 10의 backend 작업에 의존하며, Epic 10이 main에 머지되지 않은 상태에서는 API 모킹으로 개발 진행.

**planning 역할**: 
- Epic 16 breakdown 작성 완료 후 즉시 Epic 17 breakdown 시작 가능 (if applicable)
- frontend는 Epic 16 breakdown 승인 후 화면 구조·mock·테스트 계획을 시작하되, 실제 API 연동은 E11/E12 완료 뒤 진행.