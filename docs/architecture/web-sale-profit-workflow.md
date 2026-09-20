# 아키텍처 설계: 웹 매도·비용·수익 확인 워크플로우 (Web Sale Profit Workflow)

> **프로젝트**: React + TypeScript, FSD 아키텍처(app→pages→widgets→features→entities→shared), TanStack Query
> **범위**: 프론트엔드 아키텍처 설계 (3개 화면: 매도 등록/비용 등록/수익 확인)
> **의존 문서**: `docs/spec/web-sale-profit-workflow.md`, `docs/architecture/web-subscription-workflow.md`
> **API 계약**: D-W15-005

---

## FSD 레이어 구조 및 폴더 배치

### 레이어 구조 개요

Epic 13(web-account-setup) 및 Epic 14(web-subscription-workflow)의 FSD 아키텍처 패턴을 그대로 따르며, Epic 13/14에서 이미 만든 `entities`와 `shared` 컴포넌트를 중복 없이 재사용한다.

### pages 레이어 — 화면 배치

3개 화면을 `pages` 레이어에 경로별로 배치한다:

```
app/pages/
├── SaleRegisterPage/
│   └── SaleRegisterPage.tsx          # /sales/register
├── CostRegisterPage/
│   └── CostRegisterPage.tsx          # /costs/register
└── ProfitViewPage/
    └── ProfitViewPage.tsx            # /profits/view
```

- 각 페이지 컴포넌트는 선언적 UI 표현에만 집중하며, 비즈니스 로직을 누적하지 않는다.
- `SaleRegisterPage`는 배정 선택 드롭다운과 매도 등록 폼을 포함한다.
- `CostRegisterPage`는 청약 선택 드롭다운과 비용 등록 폼을 포함한다.
- `ProfitViewPage`는 매도건별/투자자별 두 조회 모드를 탭 또는 토글로 전환하며 하나의 화면에 배치한다.

### features 레이어 — 기능 단위 컴포넌트

각 화면의 폼과 상호작용 로직을 `features` 레이어에 기능 단위로 분리한다:

```
app/features/
├── sale-register/
│   ├── SaleRegisterForm/
│   │   ├── SaleRegisterForm.tsx
│   │   ├── SaleRegisterForm.module.css
│   │   └── ReferenceQuantity.tsx     # 참고 표시(남은 수량) 읽기 전용 컴포넌트
│   ├── useSaleRegister.ts            # 매도 등록 mutation 훅
│   └── useAllocationOptions.ts       # 배정 드롭다운 조회 훅
├── cost-register/
│   ├── CostRegisterForm/
│   │   ├── CostRegisterForm.tsx
│   │   └── CostRegisterForm.module.css
│   ├── useCostRegister.ts            # 비용 등록 mutation 훅
│   └── useSubscriptionOptions.ts     # 청약 드롭다운 조회 훅
└── profit-view/
    ├── ProfitTable/
    │   ├── ProfitTable.tsx
    │   ├── ProfitTable.module.css
    │   ├── ProfitRow.tsx             # 단건 수익 행 컴포넌트
    │   └── ProfitSummaryRow.tsx      # 합계 행 컴포넌트(투자자별 조회)
    ├── ProfitQueryToggle.tsx         # 매도건별/투자자별 전환 토글
    ├── useSaleProfit.ts              # 매도건별 수익 조회 query 훅
    └── useInvestorProfits.ts         # 투자자별 수익 조회 query 훅
```

- `SaleRegisterForm`: 매도 등록 폼 UI와 제출 로직을 캡슐화한다. 배정 드롭다운 데이터는 entities 레이어의 `allocation` 엔티티에서 조회한다.
- `ReferenceQuantity`: 선택된 배정의 `remainingQuantity`를 읽기 전용으로 참고 표시하는 컴포넌트. Epic 14의 `ReferenceQuantity` 패턴을 재사용한다.
- `CostRegisterForm`: 비용 등록 폼 UI와 제출 로직을 캡슐화한다. 청약 드롭다운 데이터는 entities 레이어의 `subscription` 엔티티에서 조회한다.
- `ProfitTable`, `ProfitRow`, `ProfitSummaryRow`: 수익 조회 결과를 표시하는 테이블 컴포넌트. API 응답 필드를 그대로 표시한다(CalculationTrace 없음, D-W15-001).
- `ProfitQueryToggle`: 매도건별/투자자별 조회 모드를 전환하는 토글 컴포넌트.
- `useSaleProfit`, `useInvestorProfits`: 각각 `GET /sales/{saleId}/profit` 및 `GET /investors/{investorId}/profits`를 조회하는 query 훅.

### entities 레이어 — 도메인 타입 및 API 클라이언트

Epic 13/14에서 이미 만든 엔티티를 그대로 재사용하고, 본 워크플로우 전용 응답 DTO 타입만 추가한다:

```
app/entities/
├── investor/                          # Epic 13에서 이미 존재 — 재사용
│   ├── types.ts                       # Investor 타입 정의
│   └── api-client.ts                  # GET /investors 호출
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
│   ├── types.ts                       # Subscription 타입 정의 (allocation 필드 포함)
│   └── api-client.ts                  # POST /subscriptions, GET /investment-history 호출
├── allocation/                        # Epic 14에서 이미 존재 — 재사용
│   ├── types.ts                       # Allocation 타입 정의
│   └── api-client.ts                  # POST /allocations 호출
├── sale/                              # 신규 — 응답 DTO 타입만 (저장 엔티티 아님)
│   ├── types.ts                       # Sale 타입 정의 (POST /sales 요청/응답)
│   └── api-client.ts                  # POST /sales 호출
├── cost/                              # 신규 — 응답 DTO 타입만 (저장 엔티티 아님)
│   ├── types.ts                       # Cost 타입 정의 (POST /costs 요청/응답)
│   └── api-client.ts                  # POST /costs 호출
└── profit/                            # 신규 — 응답 DTO 타입만 (저장 엔티티 아님)
    ├── types.ts                       # SaleProfit, InvestorProfit 타입 정의
    └── api-client.ts                  # GET /sales/{saleId}/profit, GET /investors/{investorId}/profits 호출
```

- `sale/types.ts`: `POST /sales` 요청 및 응답에 해당하는 TypeScript 인터페이스를 정의한다. `allocationId`, `quantity`, `price`, `amount`, `saleDate`, `fee`, `tax`, `otherCost` 필드를 포함한다.
- `cost/types.ts`: `POST /costs` 요청 및 응답에 해당하는 TypeScript 인터페이스를 정의한다. `subscriptionId`, `costType`, `amount`, `incurredAt`, `memo` 필드를 포함한다.
- `profit/types.ts`: `GET /sales/{saleId}/profit` 및 `GET /investors/{investorId}/profits` 응답에 해당하는 TypeScript 인터페이스를 정의한다. `saleAmount`, `matchedAcquisitionAmount`, `matchedCostAmount`, `saleFee`, `saleTax`, `saleOtherCost`, `preTaxProfit`, `afterTaxProfit` 필드를 포함한다.
- `investor`, `securities-account`, `securities-firm`, `ipo` 엔티티는 Epic 13에서 이미 구현되어 있으므로 그대로 재사용한다.
- `subscription`, `allocation` 엔티티는 Epic 14에서 이미 구현되어 있으므로 그대로 재사용한다. `subscription/api-client.ts`의 `getInvestmentHistory` 함수는 매도 등록 화면의 배정 조회에 사용된다.
- **주의**: `sale`, `cost`, `profit` 엔티티는 API 응답 DTO 타입만 정의한다. 서버에 저장되는 도메인 엔티티가 아니며, 별도의 저장 인프라(CalculationTrace 등)를 설계하지 않는다.

### shared 레이어 — 공통 컴포넌트 재사용

Epic 13/14에서 이미 만든 `shared` 컴포넌트를 중복 생성하지 않고 그대로 재사용한다:

```
app/shared/
├── components/
│   ├── Button/                        # Epic 13/14에서 이미 존재 — 재사용
│   ├── Input/                         # Epic 13/14에서 이미 존재 — 재사용
│   ├── FormField/                     # Epic 13/14에서 이미 존재 — 재사용
│   ├── ErrorMessage/                  # Epic 13/14에서 이미 존재 — 재사용
│   ├── Spinner/                       # Epic 13/14에서 이미 존재 — 재사용
│   └── ConfirmDialog/                 # Epic 14에서 이미 존재 — 재사용
│       ├── ConfirmDialog.tsx
│       └── ConfirmDialog.module.css
├── lib/
│   ├── format-hyphen.ts               # Epic 13/14에서 이미 존재 — 재사용
│   └── validators.ts                  # Epic 13/14에서 이미 존재 — 재사용
└── api/
    ├── client.ts                      # Epic 13/14에서 이미 존재 — 재사용
    └── interceptors.ts                # Epic 13/14에서 이미 존재 — 재사용
```

- **중복 생성 금지**: `FormField`, `ErrorMessage`, `Button`, `Spinner`, `Input`, `ConfirmDialog`, `format-hyphen.ts`, `validators.ts`, `client.ts`, `interceptors.ts`는 Epic 13/14에서 이미 구현된 것을 그대로 사용한다. 이들을 복사하거나 새로 만들지 않는다.
- **ConfirmDialog**: spec의 AC-023에 명시된 수정/삭제 확인 다이얼로그에 `shared/components/ConfirmDialog/`를 재사용한다. Epic 14에서 이미 생성되었으므로 새로 만들지 않는다.

---

## TanStack Query 사용 설계

### API 호출 구성

각 API를 TanStack Query의 `useQuery` 또는 `useMutation`으로 구성한다:

| 기능 | 훅 | 타입 | 비고 |
|------|-----|------|------|
| 매도 등록 | `useSaleRegister` | `useMutation` | `POST /sales` |
| 비용 등록 | `useCostRegister` | `useMutation` | `POST /costs` |
| 매도건별 수익 조회 | `useSaleProfit` | `useQuery` | `GET /sales/{saleId}/profit` |
| 투자자별 수익 조회 | `useInvestorProfits` | `useQuery` | `GET /investors/{investorId}/profits` |
| 배정 옵션 조회 | `useAllocationOptions` | `useQuery` | `GET /investment-history/{subscriptionId}` — 매도 등록 드롭다운용 |
| 청약 옵션 조회 | `useSubscriptionOptions` | `useQuery` | `GET /investment-history` — 비용 등록 드롭다운용 |

### useSaleRegister (useMutation)

- `mutationFn`: `entities/sale/api-client.ts`의 `createSale` 함수를 전달한다.
- `onSuccess`: 성공 시 `queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })`로 투자 내역 쿼리를 무효화한 뒤, `router.push('/investments')`로 투자 내역 화면(Epic 11)으로 이동한다. 매도 등록 성공 후 투자 내역 화면에서 새 매도가 즉시 표시되어야 하므로, Epic 14의 캐시 무효화 패턴을 그대로 따른다.
- `onError`: 400/404/409 에러를 필드별 에러 메시지로 매핑한다. 404는 `allocationId` 필드에 "배정을 찾을 수 없습니다."를, 409는 전체 폼에 서버 응답 메시지를 그대로 표시한다. 400(누적매도수량>배정수량)은 `quantity` 필드 아래 서버 에러 메시지를 인라인으로 표시한다.
- `onSettled`: 로딩 상태를 해제한다.

### useCostRegister (useMutation)

- `mutationFn`: `entities/cost/api-client.ts`의 `createCost` 함수를 전달한다.
- `onSuccess`: 성공 시 비용 목록 화면으로 이동한다. 해당 화면의 목록 쿼리를 `invalidateQueries`로 무효화한다.
- `onError`: 400/404 에러를 필드별 에러 메시지로 매핑한다. 404는 `subscriptionId` 필드에 "청약을 찾을 수 없습니다."를, 400은 해당 필드 아래 서버 에러 메시지를 인라인으로 표시한다.
- `onSettled`: 로딩 상태를 해제한다.

### useSaleProfit (useQuery)

- `queryFn`: `entities/profit/api-client.ts`의 `getSaleProfit` 함수를 전달한다. `GET /sales/{saleId}/profit`을 호출한다.
- `queryKey`: `['saleProfit', saleId]` 형태로 구성한다.
- `enabled`: `saleId`가 존재할 때만 조회를 활성화한다. saleId가 입력되지 않았거나 선택되지 않았으면 조회하지 않는다.
- `staleTime`: 기본값(0) 또는 짧은 시간으로 설정한다. 수익 조회 결과는 자주 변경되지 않지만, 매도 등록 후 최신 데이터를 보장해야 하므로 `invalidateQueries`로 명시적으로 무효화하는 것이 안전하다.

### useInvestorProfits (useQuery)

- `queryFn`: `entities/profit/api-client.ts`의 `getInvestorProfits` 함수를 전달한다. `GET /investors/{investorId}/profits`를 호출한다.
- `queryKey`: `['investorProfits', investorId]` 형태로 구성한다.
- `enabled`: `investorId`가 존재할 때만 조회를 활성화한다.
- `staleTime`: 기본값(0) 또는 짧은 시간으로 설정한다.

### 캐시 무효화 방향

- **매도 등록 성공 후**: `['investmentHistory']` 쿼리 무효화 → 투자 내역 화면(Epic 11)에서 새 매도가 즉시 표시됨. 이는 Epic 14의 `useAllocationRegister`에서 `['investmentHistory']`를 무효화한 패턴과 동일하다.
- **비용 등록 성공 후**: 비용 목록 쿼리 키를 무효화하여 목록에 새 비용이 즉시 표시되도록 한다.
- **수익 조회 후**: 별도 무효화 불필요. `useSaleProfit`/`useInvestorProfits`는 조회 자체가 최신 데이터를 가져오므로, `staleTime`을 짧게 설정하거나 필요 시 `invalidateQueries`로 무효화한다.

### 수익 조회 캐시와 D-W15-001의 관계

spec D-W15-001은 "서버가 CalculationTrace를 저장하지 않는다"는 것을 의미하며, "프론트가 TanStack Query 캐시를 사용하면 안 된다"는 뜻이 아니다. 구분:

- **D-W15-001의 의미**: 서버가 계산 과정 메타데이터(CalculationTrace)를 영속 저장하지 않으므로, 프론트에 "계산 이력 보기" 화면이나 "계산 근거" UI를 설계하지 않는다. API 응답 필드(saleAmount, matchedAcquisitionAmount, etc.)를 그대로 표시하면 된다.
- **TanStack Query 캐시**: 프론트가 서버로부터 받은 API 응답을 브라우저 메모리에 일시적으로 캐싱하는 것은 TanStack Query의 표준 동작이다. 이는 서버의 영속 저장과 무관하며, `staleTime` 설정과 `invalidateQueries`로 제어한다.
- **결론**: `useSaleProfit`과 `useInvestorProfits`에 TanStack Query의 표준 `staleTime` 캐시를 사용하는 것은 허용된다. 다만 매도 등록 후에는 `invalidateQueries`로 수익 쿼리를 무효화하여 항상 최신 데이터를 표시하도록 한다.

---

## 매도 등록 화면의 참고 표시(remainingQuantity) 구현 방향

### Epic 14의 useSubscriptionDetail 패턴 재사용

매도 등록 화면에서 배정의 `remainingQuantity`를 참고 표시하기 위해, Epic 14의 `useSubscriptionDetail` 패턴을 재사용한다.

### 조회 방향 — subscriptionId 기반

- `GET /investment-history/{subscriptionId}` API(Epic 11에서 이미 구현)를 호출하여 해당 청약의 배정 정보를 조회한다.
- 응답에 `allocation` 필드가 포함되며, `allocation.allocatedQuantity`, `allocation.sales[]`, `allocation.remainingQuantity`를 참고 표시에 사용한다.

### subscriptionId 확보 방향

매도 등록 화면의 사용자 흐름:
1. **투자자 선택**: 투자자 드롭다운에서 투자자를 선택한다. `entities/investor`에서 조회한 투자자 목록을 사용한다.
2. **청약 목록 조회**: 선택된 투자자의 청약 목록을 `GET /investment-history`로 조회한다. 이 목록에 각 청약의 `subscriptionId`가 포함된다.
3. **청약 선택**: 사용자가 청약을 선택하면 `subscriptionId`가 확정된다.
4. **배정 조회**: `GET /investment-history/{subscriptionId}`를 호출하여 해당 청약의 배정 목록과 `remainingQuantity`를 가져온다.
5. **배정 선택**: 드롭다운에서 배정을 선택하고, 선택된 배정의 `remainingQuantity`를 `ReferenceQuantity` 컴포넌트에 전달한다.

### ReferenceQuantity 컴포넌트

- `features/sale-register/SaleRegisterForm/ReferenceQuantity.tsx`에 읽기 전용 컴포넌트로 구현한다.
- Epic 14의 `ReferenceQuantity` 컴포넌트 패턴을 그대로 재사용한다.
- `<input type="number" value={remainingQuantity} readOnly disabled />` 또는 유사한 읽기 전용 UI로 표시한다.
- 라벨: "남은 수량 (참고)"
- `aria-readonly="true"` 속성을 적용하여 접근성 기준을 충족한다.

### UX 보조

- `quantity` 입력 시 `remainingQuantity`를 초과하는 값을 입력하면 경고 스타일을 적용할 수 있으나, 최종 검증은 서버가 수행한다.
- 서버 400 응답(누적매도수량>배정수량) 시 서버 에러 메시지를 `quantity` 필드 아래 인라인 에러로 그대로 표시한다.

---

## 수익 확인 화면의 조회 UI 구조

### 단일 화면 내 두 조회 모드

`ProfitViewPage`는 하나의 화면에서 매도건별 조회와 투자자별 조회를 모두 수용한다. 두 모드는 **탭(Tab)** 또는 **토글(Toggle)**로 전환한다.

### UI 구조

```
ProfitViewPage
├── ProfitQueryToggle          # 매도건별 / 투자자별 전환 토글
├── 조회 입력 영역
│   ├── 매도건별 모드: saleId 입력 또는 드롭다운
│   └── 투자자별 모드: investorId 입력 또는 드롭다운
├── 로딩 상태 (Spinner)
├── 결과 영역
│   ├── 매도건별: ProfitTable (단건 행)
│   └── 투자자별: ProfitTable (다중 행 + ProfitSummaryRow 합계 행)
└── 에러 메시지 영역
```

### 구현 방향

- `ProfitQueryToggle` 컴포넌트가 `useState<'sale' | 'investor'>`로 현재 모드를 관리한다.
- 모드 전환 시 현재 조회 결과를 초기화하고, 새 모드의 입력 필드를 리셋한다.
- `useSaleProfit`과 `useInvestorProfits`를 각각 독립적으로 사용하며, 현재 활성 모드에 해당하는 훅만 `enabled: true`로 설정한다.
- 탭 전환 시 `invalidateQueries`를 통해 상대방 모드의 캐시를 무효화할 필요는 없다. 두 쿼리의 `queryKey`가 다르므로 독립적으로 캐시가 관리된다.
- 수익이 음수(손실)인 경우 `preTaxProfit` 및 `afterTaxProfit` 필드에 빨간색 또는 마이너스 기호를 적용한다.

---

## 모듈 소유권 (FSD 레이어별 폴더 경로)

| FSD 레이어 | 폴더 경로 | 소유 콘텐츠 | 의존 관계 | Epic 13/14 공유 여부 |
|---|---|---|---|---|
| **app** | `app/` | 최상위 진입점, 레이아웃 | pages, widgets, features, entities, shared 모두 참조 가능 | — |
| **pages** | `app/pages/*Page/` | 화면 컴포넌트 (SaleRegisterPage, CostRegisterPage, ProfitViewPage) | features, entities, shared 참조 | 독립 |
| **widgets** | `app/widgets/` | 복합 UI 위젯 (해당 시) | features, entities, shared 참조 | 독립 |
| **features** | `app/features/*/` | 폼 컴포넌트, 커스텀 훅 (sale-register, cost-register, profit-view) | entities, shared 참조 | 독립 |
| **entities** | `app/entities/*/` | 도메인 타입, API 클라이언트 | shared 참조 | `investor`, `securities-account`, `securities-firm`, `ipo`, `subscription`, `allocation` **Epic 13/14와 공유**. `sale`, `cost`, `profit` **신규(응답 DTO 타입만)** |
| **shared** | `app/shared/` | 공통 컴포넌트, 유틸 함수, API 클라이언트, 인터셉터 | 없음 (독립) | **Epic 13/14과 공유** (Button, Input, FormField, ErrorMessage, Spinner, ConfirmDialog, validators, client 등 — 중복 생성 금지) |

### 공유 엔티티 상세

- `entities/investor/`: Epic 13에서 이미 구현. 투자자 드롭다운에 재사용.
- `entities/securities-account/`: Epic 13에서 이미 구현.
- `entities/securities-firm/`: Epic 13에서 이미 구현. 참고용.
- `entities/ipo/`: Epic 13에서 이미 구현.
- `entities/subscription/`: Epic 14에서 이미 구현. 비용 등록의 청약 드롭다운 조회 및 `GET /investment-history` 호출에 재사용.
- `entities/allocation/`: Epic 14에서 이미 구현. 매도 등록의 배정 드롭다운 조회에 재사용. `subscription/api-client.ts`의 `getInvestmentHistory` 함수가 allocation 데이터도 포함하므로, 매도 등록 화면의 배정 조회는 이 함수를 통해 처리한다.
- `entities/sale/`: 신규. `POST /sales` 요청/응답 DTO 타입과 API 클라이언트.
- `entities/cost/`: 신규. `POST /costs` 요청/응답 DTO 타입과 API 클라이언트.
- `entities/profit/`: 신규. 수익 조회 API 응답 DTO 타입과 API 클라이언트.

---

## 의존성

### Epic 13/14에서 이미 확인된 라이브러리 재사용

| 라이브러리 | 용도 | 확인 상태 |
|---|---|---|
| **@tanstack/react-query** | 서버 상태 관리 | Epic 13/14에서 확인 — 설치됨 |
| **react-router-dom** | 라우팅 | Epic 13/14에서 확인 — 설치됨 |
| **react-hook-form** 또는 `useState` | 폼 상태 관리 | Epic 13/14에서 확인 — 설치 여부에 따라 결정 |
| **axios** 또는 **fetch** | HTTP 클라이언트 | Epic 13/14에서 확인 — 설치 여부에 따라 결정 |

### 신규 확인 필요 항목

| 라이브러리 | 용도 | 확인 방법 | 미설치 시 대안 |
|---|---|---|---|
| — | 특별히 신규 설치 필요한 라이브러리 없음 | — | — |

- 본 워크플로우는 Epic 13/14에서 이미 확립된 스택을 그대로 사용한다. 추가 라이브러리 설치가 필요한 요소가 없다.
- 스타일링 방식(CSS Modules, Tailwind 등)은 Epic 13/14에서 이미 결정된 것을 따른다.

---

## 리스크

| 리스크 | 설명 | 완화 방안 |
|---|---|---|
| **배정 드롭다운 데이터 소스** | 매도 등록에서 배정 목록을 어떻게 조회할지 명확하지 않음 — `GET /investment-history/{subscriptionId}`가 allocation 정보를 포함하지만, 드롭다운에 표시할 모든 배정 목록을 어떻게 구성할지 추가 검증 필요 | `useAllocationOptions` 훅에서 `GET /investment-history`로 투자자별 청약 목록을 조회한 뒤, 각 청약의 allocation 배열을 펼쳐 드롭다운 옵션으로 구성. `subscriptionId` 선택 후 `useSubscriptionDetail` 패턴으로 상세 조회 |
| **subscriptionId → allocationId 전파** | 매도 등록에서 subscriptionId를 먼저 알아야 allocationId를 선택할 수 있는지 방향 불명확 | spec에 따르면 드롭다운에서 allocationId를 직접 선택하며, `GET /investment-history/{subscriptionId}`로 remainingQuantity를 참고 표시. 즉, 투자자 선택 → 청약 선택 → 해당 청약의 배정 목록 조회 → allocationId 선택 흐름으로 설계 |
| **수익 조회 캐시 무효화 타이밍** | 매도 등록 후 수익 조회 결과가 오래될 수 있음 | `useSaleRegister`의 `onSuccess`에서 `invalidateQueries({ queryKey: ['saleProfit'] })`와 `invalidateQueries({ queryKey: ['investorProfits'] })`를 각각 추가하여 수익 쿼리도 함께 무효화 |
| **투자자별 조회 대량 데이터** | `GET /investors/{investorId}/profits` 응답이 많아지면 렌더링 성능 저하 가능 | 페이지네이션 또는 가상 스크롤 도입을 추후 검토. 현재는 전체 조회로 설계 |
| **Epic 13/14 shared 컴포넌트 변경 영향** | Epic 13/14에서 `FormField`, `ErrorMessage`, `ConfirmDialog` 등을 수정하면 본 워크플로우에도 영향을 미침 | shared 컴포넌트는 안정적인 인터페이스를 유지하도록 관리. 변경 시 회귀 테스트 필수 |
| **profit 엔티티의 DTO만 존재** | `entities/profit/`에 저장 엔티티가 없으므로, 프론트에서 수익 계산 로직을 별도로 관리하지 않음 | spec D-W15-001에 명시된 대로 API 응답 필드만 표시. 프론트에서 계산하지 않음 |
| **락 충돌 UI 미설계** | D-W15-004에 의해 락 충돌 처리 UI를 설계할 수 없음 | 서버 409 응답 시 메시지를 그대로 인라인 표시. 락 충돌 재시도 다이얼로그를 표시하지 않음 |

---

## 참고

- **API 계약**: D-W15-005에 명시된 확정된 계약만 사용하며, 추가 경로를 추측하지 않는다.
- **프로젝트 아키텍처**: React + TypeScript, FSD(Feature-Sliced Design), TanStack Query (AGENTS.md 14장 참고)
- **코드 스타일**: ESLint + Prettier + `tsc --strict`, 2칸 들여쓰기, 탭 금지 (AGENTS.md 14장 프론트엔드 규칙)
- **금지사항**: CalculationTrace/출처 메타데이터 UI 설계 금지(D-W15-001), 취득가액 선택 옵션 UI 설계 금지(D-W15-002), 매도 순번 필드 설계 금지(D-W15-003), 락 충돌 처리 UI 설계 금지(D-W15-004), 별도 매도 목록 화면 설계 금지(D-W15-006), 저장 인프라(CalculationTrace 등) 설계 금지
- **의존 문서**: `docs/spec/web-sale-profit-workflow.md`, `docs/architecture/web-subscription-workflow.md`
