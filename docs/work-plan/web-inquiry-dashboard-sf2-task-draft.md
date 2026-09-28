# 웹 조회·대시보드 — SF-2 작업 초안 (web-inquiry-dashboard SF-2 task draft)

> **게이트 문구**: 이 초안은 Epic 11·Epic 12가 main에 병합 확인된 뒤에만 frontend 팀장에게 전달한다. 전달 전 PR #157 병합 여부와 ui/ 실제 파일 상태를 재확인할 것.
>
> **참고: Epic 12는 backend가 SF-1~4를 한 번에 구현 중 — 병합 시점에 API 상태를 재확인할 것** (아직 병합 전이므로 API가 이미 구현됐다고 단정하지 않음)

---

## 1. SF-2 범위 요약 (breakdown.md SF-2 섹션 인용)

**범위**: 투자 내역 화면의 투자 내역 목록 표와 상세 확장을 구현한다. 목록은 `InvestmentHistoryTable` 위젯으로, 각 행은 `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시하며, 행 클릭 시 상세 정보를 중첩하여 표시한다. 상세 화면은 `subscription + allocation + sales[]`를 중첩 표시하며, `remainingQuantity`는 읽기 전용으로 참고 표시한다. 이 SF에서 Epic 16 전용 `entities`(`subscription`)를 재사용하며(Epic 14에서 이미 존재), 공통 `ConfirmDialog`를 재사용한다.

**커버하는 AC**: AC-001(조회 성공), AC-002(필터링 성공), AC-003(빈 결과), AC-004(상세 조회 성공), AC-005(상세 404), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 1/2/4/5/6/8/9/10/11/13/14/18/19/20/21/22는 이 SF 대상.

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 11(투자 내역 조회) 미구현** — 현재 SF-1까지 완료된 Epic 11은 미구현 상태.

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx` (+ `.module.css`) — 목록 행과 상세 정보를 중첩하여 표시하는 위젯
- `widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx` — 단일 투자 내역 행 컴포넌트
- `widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx` — 선택된 구독의 상세 정보 표시 컴포넌트 (subscription + allocation + sales[] 중첩)
- `features/investment-history/useInvestmentHistory.ts` — `GET /investment-history`를 조회하는 `useQuery` 훅 (filters를 queryKey에 포함)
- `features/investment-history/useInvestmentHistoryDetail.ts` — `GET /investment-history/{subscriptionId}`를 조회하는 `useQuery` 훅 (subscriptionId가 존재할 때만 enabled)
- `pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` — 기존 파일 업데이트 (필터 바 아래에 테이블 위젯 배치)
- `shared/components/ConfirmDialog/ConfirmDialog.tsx` (+ `.module.css`) — **기존 재사용**(Epic 14에서 생성됨, 중복 생성 금지)

**사용 백엔드 API** (spec.md, architecture.md 참고):
- `GET /investment-history` — Epic 11, **미구현** (목록 조회)
- `GET /investment-history/{subscriptionId}` — Epic 11, **미구현** (상세 조회)

**특이사항** (breakdown.md, spec.md, architecture.md 종합):
- 목록 행은 `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시한다(D-W16-004).
- 상세 화면은 `subscriptionId`, `investorName`, `securitiesAccountId`, `ipoId`, `allocation.allocatedQuantity`, `allocation.status`, `sales[].saleId`, `sales[].quantity`, `sales[].price`, `sales[].amount`, `sales[].saleDate`, `remainingQuantity`를 표시한다(D-W16-004).
- `remainingQuantity`는 읽기 전용으로 참고 표시하며, `aria-readonly="true"` 속성을 적용한다(D-W16-004).
- 배정(allocation)과 매도(sales)는 중첩하여 표시한다(D-W16-004).
- 에러 처리: 404는 "해당 구독을 찾을 수 없습니다."를 표시하고, 500/네트워크 오류는 일반적인 에러 메시지를 표시한다(D-W16-004).
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(breakdown.md "GitHub Issue #134 처리 방식" 섹션 참조).
- `entities/subscription/`의 `Subscription` 타입(Epic 14에서 이미 구현, allocation/sales[] 포함)과 `api-client.ts`의 `getInvestmentHistory`/`getInvestmentHistoryById` 함수를 재사용한다.
- 접근성: WCAG 2.1 AA 준수, `axe-core` 검증, 모든 필드에 `<label>` 또는 `aria-label` 연결, 에러 메시지에 `aria-live="polite"`, 키보드 네비게이션 지원, `aria-invalid`/`aria-describedby` 사용, `remainingQuantity`에 `aria-readonly="true"`.
- 반응형: 640px/768px/1024px 브레이크포인트, 모바일 우선(mobile-first), `InvestmentHistoryTable`은 모바일에서 전체 폭 100%, 데스크톱에서 적절히 제한.
- FSD 의존 방향 준수: `pages` → `widgets` → `features` → `entities` → `shared`.

---

## 2. frontend 팀장 전달용 `orca orchestration task-create` 명령

아래 명령은 **실제로 실행하지 말고 문서에 텍스트로만 기록**한다. frontend 팀장이 복사하여 실행할 수 있도록 `--spec` 내용에 목적/범위/비범위/읽을 파일/수정 가능 파일/검증 명령/완료 조건을 모두 구체화했다.

```bash
orca orchestration task-create \
  --task-title "SF-2: 투자 내역 화면 - InvestmentHistoryTable 위젯(목록/상세), useInvestmentHistory/useInvestmentHistoryDetail 훅 구현" \
  --spec "
## 목적
Epic 16(web-inquiry-dashboard) SF-2 수행: 투자 내역 화면(/investments)의 목록 표(InvestmentHistoryTable)와 상세 확장(InvestmentHistoryDetail) 위젯, 그리고 TanStack Query 훅(useInvestmentHistory, useInvestmentHistoryDetail)을 구현한다. SF-1(FilterBar, EmptyState, InvestmentHistoryPage 기본 배치)는 완료된 것으로 가정하고, 필터 바 아래에 테이블 위젯을 배치한다. 백엔드 API(GET /investment-history, GET /investment-history/{subscriptionId})는 Epic 11 SF-2/3에서 구현 예정이며 현재 미구현 상태이므로 mock 데이터로 검증하고, 실연동은 Epic 11 완료 후 전환한다.

## 범위 (In Scope)
1. **InvestmentHistoryTable 위젯** (`ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx` + `.module.css`)
   - 목록 뷰: 각 행을 InvestmentHistoryRow로 렌더링
   - 상세 확장: 선택된 행의 상세 정보를 InvestmentHistoryDetail로 중첩 표시
   - 상태 관리: 선택된 subscriptionId를 로컬 상태로 관리, 선택 시 useInvestmentHistoryDetail 훅 활성화
   - 빈 결과/로딩/에러 상태: EmptyState(재사용), Spinner(shared), ErrorMessage(shared) 위젯으로 처리
   - 모바일(640px) 단일 컬럼, 태블릿/데스크톱에서 적절한 폭 제한 반응형

2. **InvestmentHistoryRow 컴포넌트** (`ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx`)
   - 단일 행 렌더링: subscriptionId, investorName, securitiesAccount, ipoName, status 표시
   - 행 클릭 시 onSelect 콜백 호출 (부모에서 상세 확장 트리거)
   - 키보드 접근성: Tab으로 포커스 가능, Enter/Space로 선택, `role=\"row\"`, `tabIndex=0`

3. **InvestmentHistoryDetail 컴포넌트** (`ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx` + `.module.css`)
   - 상세 정보 중첩 표시: subscriptionId, investorName, securitiesAccountId, ipoId, allocation(allocatedQuantity, status), sales[](saleId, quantity, price, amount, saleDate), remainingQuantity
   - remainingQuantity는 읽기 전용으로 `aria-readonly=\"true\"` 적용
   - 배정(allocation)과 매도(sales[])를 시각적으로 중첩 그룹화하여 표시
   - 닫기 버튼 또는 빈 영역 클릭 시 상세 접기 (선택된 subscriptionId 초기화)

4. **useInvestmentHistory 훅** (`ui/src/features/investment-history/useInvestmentHistory.ts`)
   - queryKey: ['investmentHistory', filters] (filters: { investorId?, securitiesAccountId?, ipoId? })
   - queryFn: entities/subscription/api-client.ts의 getInvestmentHistory 함수 사용
   - enabled: 항상 true (필터 없이도 전체 조회 가능)
   - select: 응답 그대로 반환 (빈 배열도 정상 처리)
   - staleTime: 0 (필터 변경 시 최신 데이터 보장)
   - FilterBar의 필터 상태 변경 시 queryKey 자동 갱신 → 재조회 트리거

5. **useInvestmentHistoryDetail 훅** (`ui/src/features/investment-history/useInvestmentHistoryDetail.ts`)
   - queryKey: ['investmentHistoryDetail', subscriptionId]
   - queryFn: entities/subscription/api-client.ts의 getInvestmentHistoryById 함수 사용
   - enabled: subscriptionId가 존재할 때만 true (상세 확장 시에만 조회)
   - select: allocation과 sales[] 포함 전체 응답 반환
   - 404 에러 시 별도 처리: ErrorMessage에 \"해당 구독을 찾을 수 없습니다.\" 표시

6. **InvestmentHistoryPage 업데이트** (`ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` + `.module.css`)
   - 기존 SF-1 배치(FilterBar + 결과 영역 placeholder) 아래에 InvestmentHistoryTable 배치
   - 결과 영역 상태 분기: idle(숨김) → loading(Spinner) → success(InvestmentHistoryTable) → empty(EmptyState) → error(ErrorMessage)
   - 상세 404 에러는 InvestmentHistoryDetail 영역 내 ErrorMessage로 표시

7. **entities/subscription 재사용** (수정 금지, import만)
   - ui/src/entities/subscription/types.ts — Subscription 타입 (allocation, sales[] 포함)
   - ui/src/entities/subscription/api-client.ts — getInvestmentHistory, getInvestmentHistoryById 함수

## 비범위 (Out of Scope)
- 실제 화면 코드 작성 외: breakdown/spec/architecture 문서 수정 금지
- FilterBar, EmptyState, useInvestmentHistoryFilters 훅 구현 (SF-1 범위, 이미 완료 가정)
- DashboardPage, DashboardTabs, DashboardCardGroup, DashboardCard 위젯 (SF-3~5 범위)
- entities/dashboard/ 신규 생성 (SF-4 범위)
- 백엔드 API 구현 및 실연동 (Epic 11 완료 후)
- GitHub Issue #134 공통 수정 (별도 SF-0로 분리 여부 미정 — 사용자 결정 대기)
- PR #157 병합 여부 확인 (다른 사람 담당)

## 읽을 파일 (Reference Only — 수정 금지)
- docs/breakdown/web-inquiry-dashboard.md (SF-2 섹션 전체)
- docs/spec/web-inquiry-dashboard.md (1. 투자 내역 화면, 4. AC-001~005, 019~021, 5. E2E 시나리오 1/2/4/5/6/8/9/10/11/13/14/18/19/20/21/22)
- docs/architecture/web-inquiry-dashboard.md (FSD 레이어, InvestmentHistoryTable/Row/Detail 설계, TanStack Query 훅 설계, entities/subscription 재사용, 상태 관리, 접근성/반응형 구현 방안)
- docs/guide-frontend.md (검증 명령, 기술 기준, FSD 구조 규칙)
- ui/src/widgets/EmptyState/EmptyState.tsx (SF-1에서 생성됨 — 재사용)
- ui/src/shared/components/Button/Button.tsx, Input/Input.tsx, FormField/FormField.tsx, ErrorMessage/ErrorMessage.tsx, Spinner/Spinner.tsx, ConfirmDialog/ConfirmDialog.tsx (기존 재사용 — 중복 생성 금지)
- ui/src/shared/api/client.ts (apiFetch — Issue #134 수정 완료 패턴 확인)
- ui/src/entities/subscription/types.ts, api-client.ts (기존 엔티티 타입/함수 재사용 — 수정 금지)
- ui/src/features/investment-history/FilterBar/FilterBar.tsx, useInvestmentHistoryFilters.ts (SF-1 산출물 — 재사용)
- ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx (SF-1 산출물 — 업데이트 대상)

## 수정 가능 파일 (Editable Files)
- ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx (신규)
- ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryTable.module.css (신규)
- ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx (신규)
- ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx (신규)
- ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryDetail.module.css (신규)
- ui/src/features/investment-history/useInvestmentHistory.ts (신규)
- ui/src/features/investment-history/useInvestmentHistoryDetail.ts (신규)
- ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx (기존 업데이트)
- ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.module.css (기존 업데이트)

## 검증 명령 (docs/guide-frontend.md 실제 명령 그대로 사용)
작업 완료 후 다음 명령을 순서대로 실행하여 모두 통과해야 함:

\`\`\`bash
cd ui
pnpm run test
pnpm run lint
pnpm run typecheck
pnpm run format:check
pnpm run build
\`\`\`

> 주의: \`typecheck\`만으로 끝내지 말고 \`pnpm run build\`(\`tsc -b\` 실행)도 필수 — 프로젝트 참조 하위 타입 오류를 놓치는 사례가 있었음.

## 완료 조건 (Definition of Done)
다음 조건을 모두 만족해야 완료로 인정:

1. **기능 완성**
   - InvestmentHistoryTable: 목록 행 렌더링, 행 클릭 시 상세 확장/접기 동작, 선택된 행 하이라이트
   - InvestmentHistoryRow: 5개 필드(subscriptionId, investorName, securitiesAccount, ipoName, status) 표시, 키보드 선택 가능
   - InvestmentHistoryDetail: subscription + allocation + sales[] 중첩 표시, remainingQuantity에 aria-readonly=\"true\" 적용
   - useInvestmentHistory: 필터 변경 시 queryKey 갱신되어 재조회 트리거, 빈 배열 정상 처리
   - useInvestmentHistoryDetail: subscriptionId 있을 때만 조회, 404 시 에러 메시지 표시
   - InvestmentHistoryPage: FilterBar + InvestmentHistoryTable 배치, 상태별 분기 렌더링(idle/loading/success/empty/error)

2. **접근성 통과**
   - \`axe-core\` 스캔 시 WCAG 2.1 AA 위반 없음 (투자 내역 화면)
   - 테이블 행 키보드 네비게이션(Tab, Enter, Escape) 정상 동작
   - remainingQuantity에 aria-readonly=\"true\" 적용됨
   - 404 에러 메시지에 aria-live=\"polite\" 적용됨

3. **반응형 통과**
   - 640px/768px/1024px에서 레이아웃 정상 (모바일: 단일 컬럼, 데스크톱: 폭 제한)
   - 테이블 가로 스크롤 방지 (모바일에서 overflow-x: auto 적용 고려)

4. **코드 품질 통과**
   - 위 검증 명령 5개(test, lint, typecheck, format:check, build) 모두 PASS
   - ESLint/Prettier/TypeScript strict 준수, 2칸 들여쓰기, 탭 금지
   - FSD 의존 방향 준수: app → pages → widgets → features → entities → shared

5. **문서/상태 갱신**
   - docs/current-status.md에 SF-2 완료 반영
   - docs/work-log/web-inquiry-dashboard.md에 작업 로그 append
   - PR 생성 및 리뷰 요청 (병합은 Master 승인 후)

6. **Mock 데이터 검증**
   - GET /investment-history(목록)와 GET /investment-history/{subscriptionId}(상세)는 모두 미구현이므로 mock 데이터로 필터링/상세 조회/빈 상태/에러 플로우 수동 확인 완료
   - 실연동은 Epic 11(SF-2, SF-3) 완료 후 전환 예정
"
  --parent <EPIC_16_TASK_ID> \
  --deps '["<SF_1_TASK_ID>"]' \
  --json
```

> **참고**: `--parent <EPIC_16_TASK_ID>`는 Epic 16 최상위 태스크 ID로, `--deps`는 SF-1 태스크 ID로 치환하여 실행할 것.

---

## 3. 검증 명령 (docs/guide-frontend.md에서 확인한 실제 명령)

```bash
cd ui
pnpm run test
pnpm run lint
pnpm run typecheck
pnpm run format:check
pnpm run build
```

- 패키지 매니저: **pnpm** (pnpm-lock.yaml 커밋, package-lock.json 생성 금지)
- `typecheck`만으로 끝나지 않고 `pnpm run build`(`tsc -b` 실행)도 필수 — 프로젝트 참조 하위 타입 오류를 놓치는 사례가 있었음(guide-frontend.md 27행 참고)
- 소스 루트: `ui/` (frontend 작업공간명과 혼동 금지 — 소스는 `ui/`에 있음)
- FSD 레이어: `ui/src/{app,pages,widgets,features,entities,shared}/` — `src` 바로 아래 형제로 둠, `src/app/` 아래 중첩 금지
- 의존 방향: `app → pages → widgets → features → entities → shared`

---

## 4. 참고 사항 (작업자 인수인계용)

- **PR #157 현황**: Epic 16 컴포넌트/라우팅 실측 점검 결과가 담긴 PR로, `/dashboard` 라우트가 이미 investor 목록 페이지를 가리키고 있어 재작성 필요, `/investments` 라우트 없음. 이 초안에는 코드로 확인하지 말고 \"PR #157 병합 후 실제 파일 상태 재확인 필요\" 문구만 상단 게이트에 남김.
- **GitHub Issue #134**: `apiFetch`가 서버 400 메시지를 버리는 문제. breakdown.md에 별도 SF-0로 분리할지 각 폼 SF에 포함할지 범위 밖으로 미룰지 사용자 결정 대기 중. 결정 전까지는 현재 패턴(`shared/api/client.ts`의 수정된 `apiFetch` + 폼의 `getFormErrorMessage()` 패턴)대로 구현하되, 추후 일괄 수정 가능성 염두.
- **Epic 14 필터 버튼 그룹 패턴**: `web-subscription-workflow.md` SF-1에서 `shared/components/Button`, `Input`, `FormField`, `ErrorMessage`, `Spinner`, `ConfirmDialog`를 신규 생성하여 재사용 기반 마련함. SF-2에서는 이들을 **중복 생성하지 않고 그대로 재사용**함.
- **Mock 데이터**: 백엔드 API 미구현 상태에서 `useInvestmentHistory`, `useInvestmentHistoryDetail` 훅은 mock 데이터 반환하도록 임시 구현하고, 실연동은 Epic 11 완료 후 전환. architecture.md의 훅 설계(enabled, queryKey, select, staleTime) 준수.
- **공유 엔티티**: `entities/subscription`는 Epic 14에서 이미 구현됨 — 수정 금지, 타입/함수만 import하여 사용. `entities/investor`, `securities-account`, `ipo`도 Epic 13에서 구현됨.
- **EmptyState 위젯 재사용**: SF-1에서 생성된 `widgets/EmptyState/`를 그대로 재사용함.
- **SF-1 산출물 의존**: FilterBar, useInvestmentHistoryFilters, EmptyState, InvestmentHistoryPage(기본 배치)는 SF-1에서 완료된 것으로 가정하고 이어받아 작업함.

---

**문서 버전**: 1.0 (초안)  
**작성일**: 2026-09-28  
**담당**: planning 그룹 → frontend 팀장 전달 대기