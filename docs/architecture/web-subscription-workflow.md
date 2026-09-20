# 아키텍처 설계: 웹 IPO·청약·배정 워크플로우 (Web Subscription Workflow)

> **프로젝트**: React + TypeScript, FSD 아키텍처(app→pages→widgets→features→entities→shared), TanStack Query
> **범위**: 프론트엔드 아키텍처 설계 (3개 화면: 청약 등록/청약 목록/배정 등록)
> **의존 문서**: `docs/spec/web-subscription-workflow.md`, `docs/architecture/web-account-setup.md`
> **API 계약**: D-W14-003

---

## FSD 레이어 구조 및 폴더 배치

### 레이어 구조 개요

Epic 13(web-account-setup)의 FSD 아키텍처 패턴을 그대로 따르며, Epic 13에서 이미 만든 `entities`와 `shared` 컴포넌트를 중복 없이 재사용한다.

### pages 레이어 — 화면 배치

3개 화면을 `pages` 레이어에 경로별로 배치한다:

```
app/pages/
├── SubscriptionRegisterPage/
│   └── SubscriptionRegisterPage.tsx    # /subscriptions/register
├── SubscriptionListPage/
│   └── SubscriptionListPage.tsx        # /subscriptions
└── AllocationRegisterPage/
    └── AllocationRegisterPage.tsx      # /allocations/register
```

- 각 페이지 컴포넌트는 선언적 UI 표현에만 집중하며, 비즈니스 로직을 누적하지 않는다.
- 페이지 간 네비게이션은 TanStack Query의 mutation 성공 콜백 또는 라우터 네비게이션으로 처리한다.
- `AllocationRegisterPage`는 URL 파라미터로 `subscriptionId`를 받는다.

### features 레이어 — 기능 단위 컴포넌트

각 화면의 폼과 상호작용 로직을 `features` 레이어에 기능 단위로 분리한다:

```
app/features/
├── subscription-register/
│   ├── SubscriptionRegisterForm/
│   │   ├── SubscriptionRegisterForm.tsx
│   │   └── SubscriptionRegisterForm.module.css
│   └── useSubscriptionRegister.ts     # 청약 등록 mutation 훅
├── subscription-list/
│   ├── SubscriptionList/
│   │   ├── SubscriptionList.tsx
│   │   ├── SubscriptionCard.tsx       # 개별 청약 카드 컴포넌트
│   │   └── SubscriptionList.module.css
│   ├── SubscriptionFilter.tsx         # 미배정/배정완료/전체 필터 버튼 그룹
│   └── useInvestmentHistory.ts        # 청약 목록 조회 query 훅
└── allocation-register/
    ├── AllocationRegisterForm/
    │   ├── AllocationRegisterForm.tsx
    │   ├── AllocationRegisterForm.module.css
    │   └── ReferenceQuantity.tsx       # 참고 표시(청약수량) 읽기 전용 컴포넌트
    ├── useAllocationRegister.ts        # 배정 등록 mutation 훅
    └── useSubscriptionDetail.ts        # 청약 상세 조회 query 훅 (참고 표시용)
```

- `SubscriptionRegisterForm`: 청약 등록 폼 UI와 제출 로직을 캡슐화한다. 투자자/증권계좌/공모주 드롭다운 데이터는 entities 레이어의 `investor`, `securities-account`, `ipo` 엔티티에서 조회한다.
- `SubscriptionList`, `SubscriptionCard`: 청약 목록과 개별 카드를 표시한다. 필터링은 `SubscriptionFilter` 컴포넌트가 담당하며, 필터 상태를 `useInvestmentHistory` 훅에 전달한다.
- `AllocationRegisterForm`: 배정 등록 폼 UI와 제출 로직을 캡슐화한다. `ReferenceQuantity` 컴포넌트가 읽기 전용 청약수량을 표시한다.
- `useSubscriptionDetail`: 배정 등록 화면 진입 시 `subscriptionId`로 `GET /investment-history/{subscriptionId}`를 조회하여 청약수량을 참고 표시한다. Epic 11에서 이미 구현된 API를 재사용한다.

### entities 레이어 — 도메인 타입 및 API 클라이언트

Epic 13에서 이미 만든 `investor`, `securities-account`, `securities-firm` 엔티티를 그대로 재사용하고, 본 워크플로우 전용 엔티티를 추가한다:

```
app/entities/
├── investor/                          # Epic 13에서 이미 존재 — 재사용
│   ├── types.ts                       # Investor 타입 정의
│   └── api-client.ts                  # GET /investors 호출
├── securities-account/                # Epic 13에서 이미 존재 — 재사용
│   ├── types.ts                       # SecuritiesAccount 타입 정의
│   └── api-client.ts                  # GET /securities-accounts 호출
├── securities-firm/                   # Epic 13에서 이미 존재 — 재사용 (참고용)
│   ├── types.ts
│   └── api-client.ts
├── ipo/
│   ├── types.ts                       # Ipo 타입 정의
│   └── api-client.ts                  # GET /ipos 호출
├── subscription/
│   ├── types.ts                       # Subscription 타입 정의 (allocation 필드 포함)
│   └── api-client.ts                  # POST /subscriptions, GET /investment-history 호출
└── allocation/
    ├── types.ts                       # Allocation 타입 정의
    └── api-client.ts                  # POST /allocations 호출
```

- `subscription/types.ts`: API 응답 구조와 일치하는 TypeScript 인터페이스를 정의한다. `allocation` 필드가 `null`이면 미배정, 아니면 배정완료 상태를 나타낸다.
- `allocation/types.ts`: `Allocation` 타입을 정의한다.
- `subscription/api-client.ts`: `POST /subscriptions`(청약 등록)과 `GET /investment-history`(청약 목록 조회)를 호출한다.
- `allocation/api-client.ts`: `POST /allocations`(배정 등록)을 호출한다.
- `investor`, `securities-account` 엔티티는 Epic 13에서 이미 구현되어 있으므로 `api-client.ts`의 조회 함수를 그대로 호출한다.

### shared 레이어 — 공통 컴포넌트 재사용

Epic 13에서 이미 만든 `shared` 컴포넌트를 중복 생성하지 않고 그대로 재사용한다:

```
app/shared/
├── components/
│   ├── Button/                        # Epic 13에서 이미 존재 — 재사용
│   ├── Input/                         # Epic 13에서 이미 존재 — 재사용
│   ├── FormField/                     # Epic 13에서 이미 존재 — 재사용
│   ├── ErrorMessage/                  # Epic 13에서 이미 존재 — 재사용
│   ├── Spinner/                       # Epic 13에서 이미 존재 — 재사용
│   └── ConfirmDialog/                 # 신규 — 수정/삭제 확인 다이얼로그 공통 컴포넌트
│       ├── ConfirmDialog.tsx
│       └── ConfirmDialog.module.css
├── lib/
│   ├── format-hyphen.ts               # Epic 13에서 이미 존재 — 재사용
│   └── validators.ts                  # Epic 13에서 이미 존재 — 재사용
└── api/
    ├── client.ts                      # Epic 13에서 이미 존재 — 재사용
    └── interceptors.ts                # Epic 13에서 이미 존재 — 재사용
```

- **중복 생성 금지**: `FormField`, `ErrorMessage`, `Button`, `Spinner`, `Input`, `format-hyphen.ts`, `validators.ts`, `client.ts`, `interceptors.ts`는 Epic 13에서 이미 구현된 것을 그대로 사용한다. 이들을 복사하거나 새로 만들지 않는다.
- **ConfirmDialog 신규 생성**: spec의 AC-017에 명시된 수정/삭제 확인 다이얼로그를 `shared/components/ConfirmDialog/`에 공통 컴포넌트로 신설한다. props: `isOpen`, `title`, `message`, `onConfirm`, `onCancel`. 확인/취소 버튼 포함.

---

## TanStack Query 사용 설계

### API 호출 구성

각 API를 TanStack Query의 `useQuery` 또는 `useMutation`으로 구성한다:

| 기능 | 훅 | 타입 | 비고 |
|------|-----|------|------|
| 청약 등록 | `useSubscriptionRegister` | `useMutation` | `POST /subscriptions` |
| 청약 목록 조회 | `useInvestmentHistory` | `useQuery` | `GET /investment-history` (필터 파라미터 포함) |
| 청약 상세 조회 | `useSubscriptionDetail` | `useQuery` | `GET /investment-history/{subscriptionId}` — 배정 등록 시 참고 표시용 |
| 배정 등록 | `useAllocationRegister` | `useMutation` | `POST /allocations` |

### useSubscriptionRegister (useMutation)

- `mutationFn`: `entities/subscription/api-client.ts`의 `createSubscription` 함수를 전달한다.
- `onSuccess`: 성공 시 `queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })`로 청약 목록 쿼리를 무효화한 뒤, `router.push('/subscriptions')`로 청약 목록 화면으로 이동한다.
- `onError`: 400/404/409 에러를 필드별 에러 메시지로 매핑한다. 404는 해당 필드(투자자/증권계좌/공모주) 아래 "~을/를 찾을 수 없습니다"를, 409는 전체 폼에 "이미 동일한 조건의 청약이 존재합니다."를 표시한다.
- `onSettled`: 로딩 상태를 해제한다.

### useInvestmentHistory (useQuery)

- `queryFn`: `entities/subscription/api-client.ts`의 `getInvestmentHistory` 함수를 전달한다. 필터 파라미터(투자자 ID 등)를 `queryKey`에 포함시켜 필터별로 별도 캐시를 유지한다.
- `queryKey`: `['investmentHistory', { investorId, ...filters }]` 형태로 구성한다. 필터가 변경되면 자동으로 재조회된다.
- `staleTime`: 목록 데이터의 신선도를 유지하기 위해 `0`(기본값) 또는 짧은 시간으로 설정한다. 청약 등록/배정 등록 후 `invalidateQueries`로 즉시 무효화한다.

### useSubscriptionDetail (useQuery)

- `queryFn`: `entities/subscription/api-client.ts`의 `getSubscriptionDetail` 함수를 전달한다. `GET /investment-history/{subscriptionId}`를 호출한다.
- `queryKey`: `['subscriptionDetail', subscriptionId]` 형태로 구성한다.
- `enabled`: `subscriptionId`가 존재할 때만 조회를 활성화한다. URL 파라미터가 없으면 조회하지 않는다.
- `staleTime`: 참고 표시용이므로 짧은 시간 또는 `0`으로 설정한다.

### useAllocationRegister (useMutation)

- `mutationFn`: `entities/allocation/api-client.ts`의 `createAllocation` 함수를 전달한다.
- `onSuccess`: 성공 시 `queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })`로 청약 목록 쿼리를 무효화한 뒤, `router.push('/subscriptions')`로 청약 목록 화면으로 이동한다. 배정 상태가 "배정완료"로 갱신되어야 한다.
- `onError`: 400/404/409 에러를 필드별 에러 메시지로 매핑한다. 400(배정수량>청약수량)은 서버 응답 메시지를 그대로 표시한다. 409는 "이미 동일한 청약에 대한 배정이 존재합니다."를 표시한다.
- `onSettled`: 로딩 상태를 해제한다.

### 캐시 무효화 방향

- **청약 등록 성공 후**: `['investmentHistory']` 쿼리 무효화 → 청약 목록 화면에서 새 청약이 즉시 표시됨.
- **배정 등록 성공 후**: 동일하게 `['investmentHistory']` 쿼리 무효화 → 목록에서 해당 청약의 `allocation` 필드가 업데이트되어 "배정완료"로 표시됨.
- 무효화는 항상 목록 조회 쿼리 키 전체를 타겟으로 하며, 특정 항목만 갱신하는 로직은 사용하지 않는다. 서버에서 최신 데이터를 다시 가져오도록 한다.

---

## 청약 목록 화면의 필터링 상태 관리

### 필터 상태 관리 방향 — 로컬 state 권장

필터 상태(미배정/배정완료/전체)는 **URL 쿼리 파라미터가 아닌 로컬 state로 관리**한다.

근거:
1. 필터는 일시적인 탐색 상태이므로 영구 보존이 필요하지 않다.
2. URL 파라미터로 관리하면 뒤로 가기/앞으로 가기 동작이 필터 상태를 예상치 못하게 변경할 수 있다.
3. Epic 13의 로그인/회원가입 흐름과 마찬가지로, 이 화면의 핵심 동작은 필터링이 아니라 청약 등록/배정 등록이므로 URL 공유가 필요한 대상이 아니다.

구현:
- `SubscriptionFilter` 컴포넌트에서 `useState<'all' | 'unallocated' | 'allocated'>`로 필터 상태를 관리한다.
- 필터 값이 변경되면 `useInvestmentHistory`의 필터 파라미터로 전달하여 쿼리를 재조회한다.

### subscriptionId 전달 방향 — URL 파라미터

미배정 청약의 "배정 입력" 버튼 클릭 시 `subscriptionId`를 **URL 파라미터**로 전달한다.

- 이동 경로: `/allocations/register?subscriptionId={id}`
- `AllocationRegisterPage`에서 `useSearchParams`로 `subscriptionId`를 추출한다.
- `subscriptionId`가 없으면 청약 목록 화면으로 리다이렉트한다.

근거:
- Epic 13에서 투자자 ID 전파 시 sessionStorage/router state/url 파라미터를 검토했으나, subscriptionId는 페이지 새로고침 시에도 유지되어야 하므로 URL 파라미터가 가장 안정적이다.
- `useSubscriptionDetail` 훅에서 `subscriptionId`를 쿼리 키로 사용하므로, URL에 노출되어도 문제가 없다(민감한 정보가 아니며, 접근 제어는 백엔드가 수행).

---

## 참고 표시(referenceQuantity) 구현 방향

### 배정 등록 화면 진입 시 청약 상세 조회

- `AllocationRegisterPage`는 URL 파라미터로 받은 `subscriptionId`로 `useSubscriptionDetail` 훅을 호출한다.
- `useSubscriptionDetail`은 `GET /investment-history/{subscriptionId}`를 조회한다. 이 API는 Epic 11에서 이미 구현된 것을 재사용한다.
- 응답에서 `subscriptionQuantity`(청약수량) 필드를 추출하여 `ReferenceQuantity` 컴포넌트에 전달한다. (Master 수정: investment-query-api spec의 실제 필드명은 `quantity`가 아니라 `subscriptionQuantity`)

### ReferenceQuantity 컴포넌트

- `features/allocation-register/AllocationRegisterForm/ReferenceQuantity.tsx`에 읽기 전용 컴포넌트로 구현한다.
- `<input type="number" value={quantity} readOnly disabled />` 또는 유사한 읽기 전용 UI로 표시한다.
- 라벨: "청약수량 (참고)"
- `aria-readonly="true"` 속성을 적용하여 접근성 기준을 충족한다.

### UX 보조

- `allocatedQuantity` 입력 시 `referenceQuantity`를 초과하는 값을 입력하면 경고 스타일을 적용할 수 있으나, 최종 검증은 서버가 수행한다.
- 서버 400 응답(배정수량>청약수량) 시 서버 에러 메시지를 인라인 에러로 그대로 표시한다.

---

## 확인 다이얼로그 컴포넌트

### ConfirmDialog 공통 컴포넌트

- `shared/components/ConfirmDialog/`에 신규 생성한다.
- `shared` 레이어에 위치하므로 어떤 레이어에서든 재사용 가능하다.

### 인터페이스

```typescript
interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}
```

### 구현 방향

- `isOpen`이 `true`일 때 모달 형태로 표시된다.
- `title`: "정말로 수정하시겠습니까?" 또는 "정말로 삭제하시겠습니까?"
- `message`: 변경 전/후 값 요약 또는 "논리 삭제" 안내문
- 확인/취소 버튼을 포함하며, `Escape` 키로도 닫을 수 있다.
- `aria-modal="true"`와 `role="dialog"`를 적용하여 접근성을 충족한다.
- 포커스 트랩: 다이얼로그가 열리면 내부 버튼으로 포커스가 이동하고, 닫히면 트리거 버튼으로 포커스가 복귀한다.

### 사용 위치

- 수정/삭제 동작이 필요한 모든 기능에서 `ConfirmDialog`를 import하여 사용한다.
- 현재 spec에는 청약/배정의 수정/삭제 확인이 명시되어 있으므로, 해당 기능 구현 시 사용한다.

---

## 모듈 소유권 (FSD 레이어별 폴더 경로)

| FSD 레이어 | 폴더 경로 | 소유 콘텐츠 | 의존 관계 | Epic 13 공유 여부 |
|---|---|---|---|---|
| **app** | `app/` | 최상위 진입점, 레이아웃 | pages, widgets, features, entities, shared 모두 참조 가능 | — |
| **pages** | `app/pages/*Page/` | 화면 컴포넌트 (SubscriptionRegisterPage, SubscriptionListPage, AllocationRegisterPage) | features, entities, shared 참조 | 독립 |
| **widgets** | `app/widgets/` | 복합 UI 위젯 (해당 시) | features, entities, shared 참조 | 독립 |
| **features** | `app/features/*/` | 폼 컴포넌트, 커스텀 훅 (subscription-register, subscription-list, allocation-register) | entities, shared 참조 | 독립 |
| **entities** | `app/entities/*/` | 도메인 타입, API 클라이언트 | shared 참조 | `investor`, `securities-account` **Epic 13과 공유**, `subscription`, `allocation`, `ipo` 신규 |
| **shared** | `app/shared/` | 공통 컴포넌트, 유틸 함수, API 클라이언트, 인터셉터 | 없음 (독립) | **Epic 13과 공유** (Button, Input, FormField, ErrorMessage, Spinner, validators, client 등 — 중복 생성 금지) |

### 공유 엔티티 상세

- `entities/investor/`: Epic 13에서 이미 구현. 청약 등록 화면의 투자자 드롭다운에 재사용.
- `entities/securities-account/`: Epic 13에서 이미 구현. 청약 등록 화면의 증권계좌 드롭다운에 재사용.
- `entities/securities-firm/`: Epic 13에서 이미 구현. 현재 워크플로우에서는 직접 사용하지 않으나, 향후 확장을 위해 유지.
- `entities/ipo/`: 신규. 공모주 드롭다운 조회용.
- `entities/subscription/`: 신규. 청약 등록 및 목록 조회용.
- `entities/allocation/`: 신규. 배정 등록용.

---

## 의존성

### Epic 13에서 이미 확인된 라이브러리 재사용

| 라이브러리 | 용도 | 확인 상태 |
|---|---|---|
| **@tanstack/react-query** | 서버 상태 관리 | Epic 13에서 확인 — 설치됨 |
| **react-router-dom** | 라우팅 | Epic 13에서 확인 — 설치됨 |
| **react-hook-form** 또는 `useState` | 폼 상태 관리 | Epic 13에서 확인 — 설치 여부에 따라 결정 |
| **axios** 또는 **fetch** | HTTP 클라이언트 | Epic 13에서 확인 — 설치 여부에 따라 결정 |

### 신규 확인 필요 항목

| 라이브러리 | 용도 | 확인 방법 | 미설치 시 대안 |
|---|---|---|---|
| — | 특별히 신규 설치 필요한 라이브러리 없음 | — | — |

- 본 워크플로우는 Epic 13에서 이미 확립된 스택을 그대로 사용한다. 추가 라이브러리 설치가 필요한 요소가 없다.
- 스타일링 방식(CSS Modules, Tailwind 등)은 Epic 13에서 이미 결정된 것을 따른다.

---

## 리스크

| 리스크 | 설명 | 완화 방안 |
|---|---|---|
| **subscriptionId 전달 안정성** | URL 파라미터로 전달 시 사용자가 URL을 직접 조작하면 유효하지 않은 subscriptionId가 들어올 수 있음 | `useSubscriptionDetail`의 `enabled` 옵션으로 subscriptionId 존재 여부를 체크하고, 404 응답 시 청약 목록으로 리다이렉트 |
| **청약 목록 대량 조회** | `GET /investment-history` 응답이 많아지면 목록 렌더링 성능이 저하될 수 있음 | 페이지네이션 또는 가상 스크롤 도입을 추후 검토. 현재는 전체 조회로 설계 |
| **배정 등록 후 목록 갱신 타이밍** | `invalidateQueries` 후 목록이 즉시 갱신되지 않을 수 있음 | `refetchOnWindowFocus: false`와 `refetchOnMount: 'always'` 설정으로 갱신 타이밍 제어 |
| **ConfirmDialog 포커스 트랩** | 모달 내부 포커스 관리가 제대로 되지 않으면 접근성 위반 가능 | `shared/components/ConfirmDialog` 구현 시 `useRef`와 키보드 이벤트로 포커스 트랩을 구현하고 axe-core로 검증 |
| **Epic 13 shared 컴포넌트 변경 영향** | Epic 13에서 `FormField`, `ErrorMessage` 등을 수정하면 본 워크플로우에도 영향을 미침 | shared 컴포넌트는 안정적인 인터페이스를 유지하도록 관리. 변경 시 회귀 테스트 필수 |
| **GET /investment-history/{subscriptionId} 필드명 (해결됨)** | Master가 investment-query-api spec을 직접 확인한 결과, 청약수량 필드명은 `quantity`가 아니라 `subscriptionQuantity`임을 확인하고 본 문서를 수정함 | 해결됨 — `subscriptionQuantity`로 정정 완료 |

---

## 참고

- **API 계약**: D-W14-003에 명시된 확정된 계약만 사용하며, 추가 경로를 추측하지 않는다.
- **프로젝트 아키텍처**: React + TypeScript, FSD(Feature-Sliced Design), TanStack Query (AGENTS.md 14장 참고)
- **코드 스타일**: ESLint + Prettier + `tsc --strict`, 2칸 들여쓰기, 탭 금지
- **금지사항**: 최소 청약 단위 등 백엔드에 없는 규칙 신설 금지(D-W14-002), 관리자 화면 설계 금지(D-W14-006), 수정 이력 UI 설계 금지(D-W14-005)
- **의존 문서**: `docs/spec/web-subscription-workflow.md`, `docs/architecture/web-account-setup.md`
