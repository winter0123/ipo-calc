# 아키텍처 설계: 웹 조회·대시보드 (Web Inquiry Dashboard)

> **프로젝트**: React + TypeScript, FSD 아키텍처(app→pages→widgets→features→entities→shared), TanStack Query
> **범위**: 프론트엔드 아키텍처 설계 (2개 화면: 투자 내역 조회, 대시보드)
> **의존 문서**: `docs/spec/web-inquiry-dashboard.md`, `docs/work-log/web-inquiry-dashboard.md`
> **API 계약**: D-W16-004

---

## FSD 레이어 구조 및 폴더 배치

### 레이어 구조 개요

Epic 13(web-account-setup), 14(web-subscription-workflow), 15(web-sale-profit-workflow)의 FSD 아키텍처 패턴을 그대로 따르며, 기존 `entities`와 `shared` 컴포넌트를 중복 없이 재사용한다. 본 Epic에서 추가할 엔티티는 `dashboard` 하나이며, 그 외 모든 도메인 타입과 공통 컴포넌트는 기존 것을 재사용한다.

### pages 레이어 — 화면 배치

2개 화면을 `pages` 레이어에 경로별로 배치한다:

```
app/pages/
├── InvestmentHistoryPage/
│   └── InvestmentHistoryPage.tsx     # /investments
└── DashboardPage/
│   └── DashboardPage.tsx             # /dashboard
```

- `InvestmentHistoryPage`: 필터 바 + 투자 내역 목록/상세를 렌더링하는 선언적 UI 컴포넌트. 비즈니스 로직을 누적하지 않는다.
- `DashboardPage`: 탭 전환 + 카드/테이블을 렌더링하는 선언적 UI 컴포넌트. 기본 탭은 "전체"로 설정된다.

### widgets 레이어 — 복합 UI 위젯

페이지 수준에서 재사용 가능한 복합 위젯을 `widgets` 레이어에 배치한다:

```
app/widgets/
├── InvestmentHistoryTable/
│   ├── InvestmentHistoryTable.tsx
│   ├── InvestmentHistoryTable.module.css
│   ├── InvestmentHistoryRow.tsx
│   └── InvestmentHistoryDetail.tsx
├── DashboardCardGroup/
│   ├── DashboardCardGroup.tsx
│   ├── DashboardCardGroup.module.css
│   └── DashboardCard.tsx
└── EmptyState/
    ├── EmptyState.tsx
    └── EmptyState.module.css
```

- `InvestmentHistoryTable`: 목록 행과 상세 정보를 중첩하여 표시하는 위젯. `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시하며, 상세 확장 시 `allocation`과 `sales[]`를 중첩 표시한다.
- `DashboardCardGroup`: 5개 카드(totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount)를 그리드로 배치한다.
- `DashboardCard`: 개별 지표 카드. 레이블과 값을 표시하며, 수익률(%)은 퍼센트 형식, 참여 건수는 정수, 금액은 숫자에 원 화폐 단위를 포함한다.
- `EmptyState`: 빈 결과 안내 메시지 + 필터 초기화 버튼을 표시하는 재사용 가능한 위젯. D-W16-007에 따라 기존 패턴을 재사용한다.

### features 레이어 — 기능 단위 컴포넌트 및 훅

각 화면의 기능과 데이터 조회 로직을 `features` 레이어에 기능 단위로 분리한다:

```
app/features/
├── investment-history/
│   ├── FilterBar/
│   │   ├── FilterBar.tsx
│   │   └── FilterBar.module.css
│   ├── useInvestmentHistory.ts
│   └── useInvestmentHistoryDetail.ts
└── dashboard/
    ├── DashboardTabs/
    │   ├── DashboardTabs.tsx
    │   └── DashboardTabs.module.css
    ├── useDashboardSummary.ts
    ├── useDashboardByInvestor.ts
    ├── useDashboardBySecuritiesFirm.ts
    └── useDashboardByPeriod.ts
```

- `FilterBar`: 투자자 ID, 증권계좌 ID, 공모주 ID 3개 필드와 초기화 버튼을 포함하는 상단 필터 바. D-W16-006에 따라 사이드바가 아닌 화면 상단에 배치하며, Epic 14의 필터 버튼 그룹 패턴을 재사용한다. 모든 필터는 선택 사항이며, 빈 값이면 해당 필터가 적용되지 않는다.
- `useInvestmentHistory`: `GET /investment-history`를 조회하는 query 훅. 필터 파라미터(investorId, securitiesAccountId, ipoId)를 조건으로 전달한다.
- `useInvestmentHistoryDetail`: `GET /investment-history/{subscriptionId}`를 조회하는 query 훅. subscriptionId가 존재할 때만 활성화된다.
- `DashboardTabs`: 전체/투자자별/증권사별/월별/연도별 5개 탭을 전환하는 컴포넌트. D-W16-005에 따라 동일 화면 내에서 탭 전환이 이루어지며, 현재 활성 탭에 `aria-selected="true"`를 적용한다.
- `useDashboardSummary`: `GET /dashboard/summary`를 조회하는 query 훅.
- `useDashboardByInvestor`: `GET /dashboard/by-investor`를 조회하는 query 훅.
- `useDashboardBySecuritiesFirm`: `GET /dashboard/by-securities-firm`를 조회하는 query 훅.
- `useDashboardByPeriod`: `GET /dashboard/by-period?unit=month|year`를 조회하는 query 훅. `unit` 파라미터와 선택적 `year`/`month` 파라미터를 전달한다.

### entities 레이어 — 도메인 타입 및 API 클라이언트

Epic 13/14/15에서 이미 만든 엔티티를 그대로 재사용하며, 대시보드 API 응답 타입만 추가한다:

```
app/entities/
├── investor/                          # Epic 13에서 이미 존재 — 재사용
│   ├── types.ts
│   └── api-client.ts
├── securities-account/                # Epic 13에서 이미 존재 — 재사용
│   ├── types.ts
│   └── api-client.ts
├── securities-firm/                   # Epic 13에서 이미 존재 — 재사용 (참고용)
│   ├── types.ts
│   └── api-client.ts
├── ipo/                               # Epic 13에서 이미 존재 — 재사용
│   ├── types.ts
│   └── api-client.ts
├── subscription/                      # Epic 14에서 이미 존재 — 재사용
│   ├── types.ts                       # Subscription 타입 정의 (allocation, sales[] 필드 포함)
│   └── api-client.ts                  # GET /investment-history, GET /investment-history/{subscriptionId} 호출
├── allocation/                        # Epic 14에서 이미 존재 — 재사용
│   ├── types.ts
│   └── api-client.ts
├── sale/                              # Epic 15에서 이미 존재 — 재사용
│   ├── types.ts
│   └── api-client.ts
├── cost/                              # Epic 15에서 이미 존재 — 재사용
│   ├── types.ts
│   └── api-client.ts
├── profit/                            # Epic 15에서 이미 존재 — 재사용
│   ├── types.ts
│   └── api-client.ts
└── dashboard/                         # 신규 — 대시보드 API 응답 DTO 타입
    ├── types.ts                       # DashboardSummary, DashboardByInvestor, DashboardBySecuritiesFirm, DashboardByPeriod 타입 정의
    └── api-client.ts                  # GET /dashboard/summary, GET /dashboard/by-investor, GET /dashboard/by-securities-firm, GET /dashboard/by-period 호출
```

- `subscription/`: Epic 14에서 이미 구현된 `Subscription` 타입에 `allocation` 필드와 `sales[]` 배열이 포함되어 있다. 투자 내역 목록/상세 조회에 그대로 재사용한다. `api-client.ts`의 `getInvestmentHistory` 함수는 투자 내역 목록 조회에, `getInvestmentHistoryById` 함수는 상세 조회에 사용한다.
- `dashboard/`: 신규 엔티티. 대시보드 4개 API의 응답 타입만 정의한다. `DashboardSummary` 타입은 `totalInvestmentAmount`, `totalPreTaxProfit`, `totalAfterTaxProfit`, `totalProfitRate`, `participationCount` 필드를 포함한다. `DashboardByPeriod` 타입은 `unit`, `data[]` 등 기간별 집계 응답 필드를 포함한다.
- `entities/dashboard/api-client.ts`: TanStack Query의 `queryFn`으로 사용되는 함수를 내보낸다. `shared/api/client.ts`를 기반으로 한다.
- **중복 생성 금지**: `investor`, `securities-account`, `securities-firm`, `ipo`, `subscription`, `allocation`, `sale`, `cost`, `profit` 엔티티는 변경하지 않는다.

### shared 레이어 — 공통 컴포넌트 재사용

Epic 13/14/15에서 이미 만든 `shared` 컴포넌트를 중복 생성하지 않고 그대로 재사용한다:

```
app/shared/
├── components/
│   ├── Button/                        # Epic 13/14/15에서 이미 존재 — 재사용
│   ├── Input/                         # Epic 13/14/15에서 이미 존재 — 재사용
│   ├── FormField/                     # Epic 13/14/15에서 이미 존재 — 재사용
│   ├── ErrorMessage/                  # Epic 13/14/15에서 이미 존재 — 재사용
│   ├── Spinner/                       # Epic 13/14/15에서 이미 존재 — 재사용
│   └── ConfirmDialog/                 # Epic 14/15에서 이미 존재 — 재사용
│       ├── ConfirmDialog.tsx
│       └── ConfirmDialog.module.css
├── lib/
│   ├── format-hyphen.ts               # Epic 13/14/15에서 이미 존재 — 재사용
│   └── validators.ts                  # Epic 13/14/15에서 이미 존재 — 재사용
└── api/
    ├── client.ts                      # Epic 13/14/15에서 이미 존재 — 재사용
    └── interceptors.ts                # Epic 13/14/15에서 이미 존재 — 재사용
```

- **중복 생성 금지**: `FormField`, `ErrorMessage`, `Button`, `Spinner`, `Input`, `ConfirmDialog`, `format-hyphen.ts`, `validators.ts`, `client.ts`, `interceptors.ts`는 기존 것을 그대로 사용한다.
- `ErrorMessage`: 에러 메시지를 인라인으로 표시할 때 재사용한다. 에러 발생 시 첫 번째 에러 필드로 자동 포커스를 지원한다.
- `Spinner`: 로딩 상태에서 결과 영역에 표시한다.
- `EmptyState` 위젯은 `features` 또는 `widgets`에 위치하며, `shared/components/ErrorMessage` 패턴을 기반으로 한다.

---

## TanStack Query 훅 설계

### API 호출 구성

각 API를 TanStack Query의 `useQuery`로 구성한다. 본 Epic은 읽기 전용 조회이므로 `useMutation`은 없다:

| 기능 | 훅 | API | queryKey | 비고 |
|------|-----|-----|----------|------|
| 투자 내역 목록 | `useInvestmentHistory` | `GET /investment-history` | `['investmentHistory', filters]` | filters: { investorId?, securitiesAccountId?, ipoId? } |
| 투자 내역 상세 | `useInvestmentHistoryDetail` | `GET /investment-history/{subscriptionId}` | `['investmentHistoryDetail', subscriptionId]` | subscriptionId가 있을 때만 enabled |
| 대시보드 전체 | `useDashboardSummary` | `GET /dashboard/summary` | `['dashboardSummary']` | 기본 탭 "전체" |
| 대시보드 투자자별 | `useDashboardByInvestor` | `GET /dashboard/by-investor` | `['dashboardByInvestor']` | 탭 "투자자별" |
| 대시보드 증권사별 | `useDashboardBySecuritiesFirm` | `GET /dashboard/by-securities-firm` | `['dashboardBySecuritiesFirm']` | 탭 "증권사별" |
| 대시보드 기간별 | `useDashboardByPeriod` | `GET /dashboard/by-period?unit=month\|year` | `['dashboardByPeriod', unit, year?, month?]` | 탭 "월별"/"연도별" |

### useInvestmentHistory (useQuery)

- `queryFn`: `entities/subscription/api-client.ts`의 `getInvestmentHistory` 함수를 전달한다.
- `queryKey`: `['investmentHistory', filters]` 형태. filters 객체에 investorId, securitiesAccountId, ipoId를 포함한다.
- `enabled`: 항상 활성화. 필터 없이 호출하면 전체 목록이 반환된다.
- `select`: 응답 데이터를 가공하지 않고 그대로 반환한다. 빈 배열(`[]`)도 정상 응답으로 처리한다.
- `staleTime`: 기본값(0). 필터 변경 시 최신 데이터를 보장한다.

### useInvestmentHistoryDetail (useQuery)

- `queryFn`: `entities/subscription/api-client.ts`의 `getInvestmentHistoryById` 함수를 전달한다. `GET /investment-history/{subscriptionId}`를 호출한다.
- `queryKey`: `['investmentHistoryDetail', subscriptionId]` 형태.
- `enabled`: `subscriptionId`가 존재할 때만 조회를 활성화한다.
- `select`: 응답에 포함된 `allocation`과 `sales[]`를 그대로 반환한다.

### useDashboardSummary (useQuery)

- `queryFn`: `entities/dashboard/api-client.ts`의 `getDashboardSummary` 함수를 전달한다.
- `queryKey`: `['dashboardSummary']`.
- `enabled`: true (기본 탭 "전체" 로드 시 자동 호출).
- `staleTime`: 기본값(0). 탭 전환 시 최신 데이터를 보장한다.

### useDashboardByInvestor (useQuery)

- `queryFn`: `entities/dashboard/api-client.ts`의 `getDashboardByInvestor` 함수를 전달한다.
- `queryKey`: `['dashboardByInvestor']`.
- `enabled`: 탭이 "투자자별"일 때만 활성화.

### useDashboardBySecuritiesFirm (useQuery)

- `queryFn`: `entities/dashboard/api-client.ts`의 `getDashboardBySecuritiesFirm` 함수를 전달한다.
- `queryKey`: `['dashboardBySecuritiesFirm']`.
- `enabled`: 탭이 "증권사별"일 때만 활성화.

### useDashboardByPeriod (useQuery)

- `queryFn`: `entities/dashboard/api-client.ts`의 `getDashboardByPeriod` 함수를 전달한다. `GET /dashboard/by-period?unit=month|year`를 호출한다.
- `queryKey`: `['dashboardByPeriod', unit, year?, month?]` 형태.
- `enabled`: 탭이 "월별" 또는 "연도별"일 때만 활성화.
- `month`/`year` 파라미터: 월별 탭에서 기본 최신 월을 사용하며, 필요시 연도 선택기를 통해 전달한다.

### 캐시 무효화 방향

- 본 Epic은 읽기 전용 조회 화면이므로, `useMutation`에 의한 캐시 무효화는 필요하지 않다.
- `invalidateQueries`는 필드 초기화 시점에 사용된다: 필터 초기화 버튼 클릭 시 `['investmentHistory']` 쿼리를 무효화하여 전체 목록을 재조회한다.

---

## 상태 관리 설계

### 필터 상태 (투자 내역 화면)

- `FilterBar` 컴포넌트가 `useState<FilterState>`로 필터 값을 관리한다:
  - `investorId: string` (기본값: `''`)
  - `securitiesAccountId: string` (기본값: `''`)
  - `ipoId: string` (기본값: `''`)
- 필터 변경 시 `useInvestmentHistory`의 `queryKey`가 변경되어 자동 재조회가 발생한다.
- 초기화 버튼 클릭 시 모든 필터를 빈 문자열로 리셋하고, `invalidateQueries({ queryKey: ['investmentHistory'] })`를 호출하여 전체 목록을 재조회한다.

### 탭 선택 상태 (대시보드 화면)

- `DashboardPage`가 `useState<'all' | 'investor' | 'securities-firm' | 'month' | 'year'>`로 현재 탭을 관리한다.
- 기본 탭은 `"all"`로 설정된다.
- 탭 전환 시 현재 탭을 갱신하고, 해당 `useQuery` 훅의 `enabled`가 `true`가 되어 자동으로 API를 호출한다.
- 탭 전환 시 로딩 상태가 표시된다.

### 빈 결과/에러 상태 처리

| 화면 | 상태 | 처리 방식 |
|------|------|-----------|
| 투자 내역 목록 | 초기(idle) | 필터 바만 표시, 결과 영역 숨김 |
| 투자 내역 목록 | 로딩 | 결과 영역에 `Spinner` 표시 |
| 투자 내역 목록 | 성공(데이터 존재) | `InvestmentHistoryTable` 표시 |
| 투자 내역 목록 | 빈 결과(200 + `[]`) | `EmptyState` 표시 — "해당 조건에 해당하는 기록이 없습니다." + 필터 초기화 버튼 |
| 투자 내역 목록 | 에러(500/네트워크) | `ErrorMessage` 표시 — 서버 오류 또는 네트워크 연결 확인 메시지 |
| 투자 내역 상세 | 성공(200) | `InvestmentHistoryDetail` 표시 — subscription + allocation + sales[] 중첩 |
| 투자 내역 상세 | 404 | `ErrorMessage` 표시 — "해당 구독을 찾을 수 없습니다." |
| 대시보드 | 초기/로딩 | `DashboardCardGroup` 영역에 `Spinner` 표시, 현재 탭 비활성화 |
| 대시보드 | 성공(데이터 존재) | 5개 `DashboardCard` 표시 |
| 대시보드 | 빈 결과(모든 값 0 또는 빈 배열) | `EmptyState` 표시 — "해당 조건에 해당하는 기록이 없습니다." + 필터 초기화 버튼 |
| 대시보드 | 에러(500/네트워크) | `ErrorMessage` 표시 |

- `useQuery`의 `isLoading` 상태를 기반으로 로딩 표시를 제어한다.
- `useQuery`의 `isError` 상태를 기반으로 에러 메시지를 표시한다. 네트워크 오류와 500 에러를 구분하여 메시지를 다르게 표시한다.
- `useQuery`의 `data`가 `[]`이거나 모든 지표 값이 0인지 확인하여 빈 결과 상태를 판단한다.

---

## 라우팅 설계

### 경로 배치

```
/app
├── /investments   → InvestmentHistoryPage   # 투자 내역 화면
└── /dashboard     → DashboardPage           # 대시보드 화면
```

- `react-router-dom`을 사용하며, Epic 13/14/15에서 이미 설치가 확인되었다.
- 네비게이션 링크로 투자 내역 화면과 대시보드 화면 간 이동이 가능하다.
- 투자 내역 조회 완료 후 동일 화면에 결과가 표시되며, 별도 이동이 필요하지 않다.
- 대시보드 조회 완료 후 동일 화면에 결과가 표시되며, 별도 이동이 필요하지 않다.

---

## 에러 처리 설계

### 에러 유형별 처리 (spec의 에러 처리 표 그대로)

| 에러 유형 | 필드 | 인라인 에러 메시지 | 처리 위치 |
|-----------|------|-------------------|-----------|
| 404 Not Found (상세 구독 미존재) | subscriptionId | "해당 구독을 찾을 수 없습니다." | `InvestmentHistoryDetail` 영역 |
| 500 Internal Server Error | 전체 화면 | "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." | `ErrorMessage` 컴포넌트 |
| 네트워크 오류 | 전체 화면 | "네트워크 연결을 확인해 주세요." | `ErrorMessage` 컴포넌트 |

- `useQuery`의 `error` 객체를 통해 에러 타입을 판별한다.
- 404 에러: `useInvestmentHistoryDetail`에서 발생하며, `ErrorMessage`에 "해당 구독을 찾을 수 없습니다."를 표시한다.
- 500 에러: 모든 `useQuery`에서 발생할 수 있으며, 전역 에러 처리 또는 각 훅의 `onError`에서 `ErrorMessage`을 표시한다.
- 네트워크 오류: `useQuery`의 `isError`와 `error` 객체의 네트워크 관련 속성을 통해 판별한다.
- 에러 메시지에 `aria-live="polite"` 속성을 적용하여 스크린 리더가 공지하도록 한다.
- 색상만으로 에러를 전달하지 않으며, 아이콘/텍스트를 병행한다.

---

## 접근성 구현 방안 (WCAG 2.1 AA)

### 검증 도구

- `axe-core` 브라우저 확장/CI 통합을 단일 채택한다.

### 필수 구현 사항

- **레이블**: 모든 필드(`investorId`, `securitiesAccountId`, `ipoId`)와 탭에 `<label>` 또는 `aria-label`을 연결한다.
- **에러 메시지**: 에러 메시지 영역에 `aria-live="polite"` 속성을 적용한다.
- **포커스 관리**: 에러 발생 시 첫 번째 에러 필드로 자동 포커스를 이동한다.
- **색상 전달**: 색상만으로 에러를 전달하지 않으며, 아이콘과 텍스트를 병행한다.
- **키보드 네비게션**: Tab, Enter, Escape 키 지원. 필터 입력 필드와 탭 모두 키보드로 접근 가능해야 한다.
- **aria-invalid / aria-describedby**: 필드별 에러 상태에 `aria-invalid` 및 `aria-describedby` 속성을 사용한다.
- **탭 전환**: 현재 활성 탭에 `aria-selected="true"`, 비활성 탭에 `aria-selected="false"`를 적용한다. `role="tablist"`, `role="tab"` 속성을 사용한다.
- **빈 결과 메시지**: 빈 결과 안내 메시지에 `role="status"` 속성을 적용한다.
- **읽기 전용 표시**: `remainingQuantity` 등 읽기 전용 필드에 `aria-readonly="true"`를 적용한다.

---

## 반응형 구현 방안

### 브레이크포인트 (D-W16-002)

| 브레이크포인트 | 최소 너비 | 적용 대상 |
|---------------|-----------|-----------|
| 모바일 | 640px | 카드/테이블 전체 폭 100%, 단일 컬럼 레이아웃 |
| 태블릿 | 768px | 2열 레이아웃 가능(카드 그룹화) |
| 데스크톱 | 1024px | 전체 폭 제한, 양쪽 여백 확보 |

### CSS 미디어 쿼리

```css
/* 모바일 우선(mobile-first) 기본 스타일 */
.dashboard-card-group { display: flex; flex-direction: column; }
.investment-history-table { width: 100%; }

@media (min-width: 640px) {
  /* 모바일 기본 스타일 유지 */
}

@media (min-width: 768px) {
  .dashboard-card-group { display: grid; grid-template-columns: repeat(2, 1fr); }
  .investment-history-table { width: 100%; }
}

@media (min-width: 1024px) {
  .dashboard-card-group { display: grid; grid-template-columns: repeat(5, 1fr); }
  .investment-history-table { max-width: 100%; margin: 0 auto; }
}
```

- CSS 미디어 쿼리는 `@media (min-width: 640px)`, `@media (min-width: 768px)`, `@media (min-width: 1024px)`를 사용한다.
- 모바일 우선(mobile-first) 접근을 따른다.
- D-W16-002에 따라 640/768/1024 브레이크포인트를 사용하며, 조사 문서에서 제안한 다른 값(`<768px`/`768-1024px`/`>1024px`)은 사용하지 않는다.
- `DashboardCardGroup`은 모바일에서 단일 열, 태블릿에서 2열, 데스크톱에서 5열(카드 수)로 변경된다.
- `InvestmentHistoryTable`은 모바일에서 전체 폭 100%, 데스크톱에서 적절히 제한된다.
- `FilterBar`는 모바일에서 필드가 스택 배열되고, 데스크톱에서 인라인 배열이 된다.

---

## 컴포넌트 설계

### 필터 바 컴포넌트 (FilterBar)

- 위치: `app/features/investment-history/FilterBar/`
- Epic 14의 필터 버튼 그룹 패턴을 재사용한다.
- 상단 필터 바에 투자자 ID, 증권계좌 ID, 공모주 ID 3개 필드를 배치한다.
- 필터 초기화 버튼은 필터 바의 끝에 배치한다(D-W16-006).
- 모든 필터는 선택 사항이며, 빈 값이면 해당 필터가 적용되지 않는다.
- 사이드바 패턴을 사용하지 않는다(D-W16-006).
- `Input` 컴포넌트와 `Button` 컴포넌트를 `shared/components/`에서 재사용한다.
- 필드에 `aria-label`, `aria-invalid`, `aria-describedby` 속성을 적용한다.

### 목록/상세 테이블 컴포넌트 (InvestmentHistoryTable)

- 위치: `app/widgets/InvestmentHistoryTable/`
- 목록 뷰: `InvestmentHistoryRow`가 각 구독의 `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시한다.
- 상세 뷰: `InvestmentHistoryDetail`가 선택된 구독의 `subscription + allocation + sales[]`를 중첩하여 표시한다. `remainingQuantity`는 읽기 전용으로 참고 표시한다.
- Epic 15의 `ProfitTable`/`ProfitRow` 패턴을 참조하여 구성한다.
- 배정(allocation)과 매도(sales)는 중첩하여 표시한다.

### 대시보드 탭 컴포넌트 (DashboardTabs)

- 위치: `app/features/dashboard/DashboardTabs/`
- 전체/투자자별/증권사별/월별/연도별 5개 탭을 표시한다.
- Epic 15의 `ProfitQueryToggle` 패턴을 참조하되, 탭(Tab) 형태로 구현한다(토글이 아닌 탭).
- 탭 전환 시 `aria-selected` 속성을 갱신한다.
- 탭 선택 시 해당 `useQuery` 훅의 `enabled`가 `true`가 되어 API를 호출한다.

### 카드 컴포넌트 (DashboardCard / DashboardCardGroup)

- 위치: `app/widgets/DashboardCardGroup/`, `app/widgets/DashboardCard/`
- 5개 카드를 그리드로 배치한다: totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount.
- 각 카드에 레이블과 값을 표시한다.
- 수익률(%)은 퍼센트 형식, 참여 건수는 정수, 금액은 숫자에 원 화폐 단위를 포함한다.
- 차트 라이브러리를 사용하지 않는다(D-W16-003) — 순수 숫자 카드와 테이블만 사용한다.
- 총투자금은 배정금액 단일 기준으로 정의되며, 선택 옵션이 존재하지 않는다(D-W16-001).

### 빈 상태 컴포넌트 (EmptyState)

- 위치: `app/widgets/EmptyState/`
- "해당 조건에 해당하는 기록이 없습니다." 안내 메시지와 필터 초기화 버튼을 표시한다(D-W16-007).
- `ErrorMessage`/빈 상태 표시 패턴을 재사용한다. 별도의 특수 컴포넌트 없이 기존 패턴을 따른다.
- 빈 결과 메시지에 `role="status"` 속성을 적용한다.

---

## 모듈 소유권 (FSD 레이어별 폴더 경로)

| FSD 레이어 | 폴더 경로 | 소유 콘텐츠 | 의존 관계 | Epic 13/14/15 공유 여부 |
|---|---|---|---|---|
| **app** | `app/` | 최상위 진입점, 레이아웃 | pages, widgets, features, entities, shared 모두 참조 가능 | — |
| **pages** | `app/pages/*Page/` | 화면 컴포넌트 (InvestmentHistoryPage, DashboardPage) | features, widgets, entities, shared 참조 | 독립 |
| **widgets** | `app/widgets/` | 복합 UI 위젯 (InvestmentHistoryTable, DashboardCardGroup, EmptyState) | features, entities, shared 참조 | 독립 |
| **features** | `app/features/*/` | 필터 바, 탭, 커스텀 훅 (investment-history, dashboard) | entities, shared 참조 | 독립 |
| **entities** | `app/entities/*/` | 도메인 타입, API 클라이언트 | shared 참조 | `investor`, `securities-account`, `securities-firm`, `ipo`, `subscription`, `allocation`, `sale`, `cost`, `profit` **Epic 13/14/15와 공유**. `dashboard` **신규(응답 DTO 타입만)** |
| **shared** | `app/shared/` | 공통 컴포넌트, 유틸 함수, API 클라이언트, 인터셉터 | 없음 (독립) | **Epic 13/14/15과 공유** (Button, Input, FormField, ErrorMessage, Spinner, ConfirmDialog, validators, client 등 — 중복 생성 금지) |

### 공유 엔티티 상세

- `entities/investor/`: Epic 13에서 이미 구현. 투자자 드롭다운에 재사용.
- `entities/securities-account/`: Epic 13에서 이미 구현.
- `entities/securities-firm/`: Epic 13에서 이미 구현. 참고용.
- `entities/ipo/`: Epic 13에서 이미 구현.
- `entities/subscription/`: Epic 14에서 이미 구현. 투자 내역 목록/상세 조회에 재사용. `api-client.ts`의 `getInvestmentHistory` 및 `getInvestmentHistoryById` 함수를 사용한다.
- `entities/allocation/`: Epic 14에서 이미 구현.
- `entities/sale/`, `entities/cost/`, `entities/profit/`: Epic 15에서 이미 구현.
- `entities/dashboard/`: 신규. 대시보드 4개 API의 응답 DTO 타입과 API 클라이언트.

---

## 의존성

### Epic 13/14/15에서 이미 확인된 라이브러리 재사용

| 라이브러리 | 용도 | 확인 상태 |
|---|---|---|
| **@tanstack/react-query** | 서버 상태 관리 | Epic 13/14/15에서 확인 — 설치됨 |
| **react-router-dom** | 라우팅 | Epic 13/14/15에서 확인 — 설치됨 |
| **react-hook-form** 또는 `useState` | 폼 상태 관리 | Epic 13/14/15에서 확인 — 설치 여부에 따라 결정 |
| **axios** 또는 **fetch** | HTTP 클라이언트 | Epic 13/14/15에서 확인 — 설치 여부에 따라 결정 |

### 신규 확인 필요 항목

| 라이브러리 | 용도 | 확인 방법 | 미설치 시 대안 |
|---|---|---|---|
| — | 특별히 신규 설치 필요한 라이브러리 없음 | — | — |

- 본 워크플로우는 Epic 13/14/15에서 이미 확립된 스택을 그대로 사용한다. 추가 라이브러리 설치가 필요한 요소가 없다.
- 스타일링 방식(CSS Modules, Tailwind 등)은 Epic 13/14/15에서 이미 결정된 것을 따른다.
- `axe-core`는 이미 Epic 13에서 접근성 검증 도구로 도입된 것으로 확인한다.

---

## 리스크

| 리스크 | 설명 | 완화 방안 |
|---|---|---|
| **투자 내역 상세 404 처리** | `GET /investment-history/{subscriptionId}`에서 404가 발생할 때 UI가 올바르게 처리되지 않을 수 있음 | `useInvestmentHistoryDetail`의 `onError` 또는 `useQuery`의 `error` 처리에서 404를 판별하고 `ErrorMessage`에 "해당 구독을 찾을 수 없습니다."를 표시 |
| **대시보드 빈 결과 판별** | `GET /dashboard/summary`에서 모든 값이 0인 경우와 데이터가 없는 경우를 구분하기 어려움 | `useDashboardSummary`의 `select` 또는 `data` 확인 로직에서 모든 필드가 0인지 또는 배열이 비어있는지 판별하여 `EmptyState`를 표시 |
| **기간별 탭에서 연도/월 파라미터 관리** | `useDashboardByPeriod`에서 `unit`, `year`, `month` 파라미터를 올바르게 전달해야 함 | `queryKey`에 모든 파라미터를 포함하고, `DashboardTabs`에서 탭 상태에 따라 파라미터를 결정한다. 월별 탭에서 기본 최신 월을 사용한다 |
| **필터 초기화 시 쿼리 무효화** | 필터 초기화 후 `invalidateQueries`가 올바르게 동작하지 않을 수 있음 | `FilterBar`의 초기화 버튼 클릭 핸들러에서 `queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })`를 명시적으로 호출 |
| **Epic 13/14/15 shared 컴포넌트 변경 영향** | `FormField`, `ErrorMessage`, `ConfirmDialog` 등을 수정하면 본 에픽에도 영향을 미침 | shared 컴포넌트는 안정적인 인터페이스를 유지하도록 관리. 변경 시 회귀 테스트 필수 |
| **대시보드 API 보안 필터링** | 서버가 이미 계정 소유권 필터링(SecurityUtils)을 적용했지만, 프론트에서 예상치 못한 데이터가 반환될 수 있음 | `useDashboardSummary` 등에서 응답 데이터를 검증하고, 예상 필드가 없는 경우 에러 처리 |
| **investment-history 목록 대량 데이터** | 필터 없이 조회 시 목록이 많아지면 렌더링 성능 저하 가능 | 페이지네이션 또는 가상 스크롤 도입을 추후 검토. 현재는 전체 조회로 설계 |

---

## 참고

- **API 계약**: D-W16-004에 명시된 확정된 계약만 사용하며, 추가 경로를 추측하지 않는다.
- **프로젝트 아키텍처**: React + TypeScript, FSD(Feature-Sliced Design), TanStack Query (AGENTS.md 14장 참고)
- **코드 스타일**: ESLint + Prettier + `tsc --strict`, 2칸 들여쓰기, 탭 금지 (AGENTS.md 14장 프론트엔드 규칙)
- **금지사항**: 코드 작성 금지, API 경로 추측 금지(D-W16-004만 사용), 총투자금 선택 옵션 UI 설계 금지(D-W16-001), 차트 라이브러리 사용 설계 금지(D-W16-003), 사이드바 필터 설계 금지(D-W16-006)
- **의존 문서**: `docs/spec/web-inquiry-dashboard.md`, `docs/work-log/web-inquiry-dashboard.md`
