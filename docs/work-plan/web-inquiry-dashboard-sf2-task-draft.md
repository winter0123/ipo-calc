# 웹 조회·대시보드 — SF-2 작업 초안 (web-inquiry-dashboard SF-2 task draft)

> **게이트 문구(2026-09-28 갱신)**: **PR #157은 이미 main에 병합**되었고, **PR #168(Epic 16 SF-1)·PR #162(Epic 11 SF-3)·PR #167(Epic 12 SF-1)도 병합 완료**다. 따라서 "PR #157 병합 여부 재확인" 게이트는 **사라졌다**. ui/ 실제 파일 상태는 본 문서 5절 "드리프트 점검"에 `origin/main` 기준으로 대조해 반영했다. 남은 게이트는 **Epic 12 SF-2~4(`/summary`, `/by-investor`, `/by-securities-firm`, `/by-period`) 병합 확인**뿐이며, 병합되면 mock이 아닌 실연동으로 검증해야 한다.
>
> **확인 명령**: `git log origin/main --oneline -- backend/src/main/java/com/vibeaiteam/dashboard/controller/DashboardController.java`

---

## 1. SF-2 범위 요약 (breakdown.md SF-2 섹션 인용)

**범위**: 투자 내역 화면의 투자 내역 목록 표와 상세 확장을 구현한다. 목록은 `InvestmentHistoryTable` 위젯으로, 각 행은 `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시하며, 행 클릭 시 상세 정보를 중첩하여 표시한다. 상세 화면은 `subscription + allocation + sales[]`를 중첩 표시하며, `remainingQuantity`는 읽기 전용으로 참고 표시한다. 이 SF에서 Epic 16 전용 `entities`(`subscription`)를 재사용하며(Epic 14에서 이미 존재), 공통 `ConfirmDialog`를 재사용한다.

**커버하는 AC**: AC-001(조회 성공), AC-002(필터링 성공), AC-003(빈 결과), AC-004(상세 조회 성공), AC-005(상세 404), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 1/2/4/5/6/8/9/10/11/13/14/18/19/20/21/22는 이 SF 대상.

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 11(투자 내역 조회) 전량 구현·병합 완료** — `GET /investment-history`(PR #153), `GET /investment-history/{subscriptionId}`(PR #162) 모두 main에 존재하므로 실연동 가능. (2026-09-28 드리프트 정정, 원문은 "Epic 11 미구현"이었음)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx` (+ `.module.css`) — 목록 행과 상세 정보를 중첩하여 표시하는 위젯
- `widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx` — 단일 투자 내역 행 컴포넌트
- `widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx` — 선택된 구독의 상세 정보 표시 컴포넌트 (subscription + allocation + sales[] 중첩)
- `features/investment-history/useInvestmentHistory.ts` — `GET /investment-history`를 조회하는 `useQuery` 훅 (filters를 queryKey에 포함)
- `features/investment-history/useInvestmentHistoryDetail.ts` — `GET /investment-history/{subscriptionId}`를 조회하는 `useQuery` 훅 (subscriptionId가 존재할 때만 enabled)
- `pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` — 기존 파일 업데이트 (필터 바 아래에 테이블 위젯 배치)
- `shared/components/ConfirmDialog/ConfirmDialog.tsx` (+ `.module.css`) — **기존 재사용**(Epic 14에서 생성됨, 중복 생성 금지)

**사용 백엔드 API** (spec.md, architecture.md 참고):
- `GET /investment-history` — Epic 11 SF-2, **구현·병합 완료 (PR #153)** (목록 조회)
- `GET /investment-history/{subscriptionId}` — Epic 11 SF-3, **구현·병합 완료 (PR #162)** (상세 조회)

> 2026-09-28 드리프트 정정: 원문은 두 API 모두 "Epic 11, **미구현**"이었다. 두 API는 main에 존재하므로 mock이 아니라 실연동으로 개발한다.

**특이사항** (breakdown.md, spec.md, architecture.md 종합):
- 목록 행·상세 화면의 실제 필드 계약은 병합된 백엔드 DTO(`backend/.../InvestmentHistoryDto.java`)를 따른다: `id`, `subscribedAt`, `ipoId`, `ipoName`, `ipoFixedPrice`, `subscriptionQuantity`, `subscriptionStatus`, `allocation`(nullable), `sales[]`, `remainingQuantity`(nullable). (2026-09-28 드리프트 정정 — 원문이 D-W16-004 기준으로 `sales[].saleId`/`sales[].price`/`sales[].saleDate`/`allocation.status` 및 행의 `investorName`/`securitiesAccount`를 요구했지만, 실제 응답 DTO에 이 필드들은 존재하지 않는다. `investorName`·`securitiesAccount` 표시가 AC이면 백엔드 계약 확장 또는 별도 조회가 필요하므로 착수 전에 사용자 결정을 받을 것. 아래 "드리프트 점검" 절 참조.)
- `remainingQuantity`는 읽기 전용으로 참고 표시하며, `aria-readonly="true"` 속성을 적용한다(D-W16-004).
- 배정(allocation)과 매도(sales)는 중첩하여 표시한다(D-W16-004).
- 에러 처리: 404는 "해당 구독을 찾을 수 없습니다."를 표시하고, 500/네트워크 오류는 일반적인 에러 메시지를 표시한다(D-W16-004).
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(breakdown.md "GitHub Issue #134 처리 방식" 섹션 참조).
- `entities/subscription/`의 `InvestmentHistoryItem`/`AllocationSummary`/`SaleSummary`/`InvestmentHistoryResponse` 타입(Epic 11에서 선반영됨)과 `api-client.ts`의 `getInvestmentHistory`/`getSubscriptionDetail` 함수를 재사용한다. (2026-09-28 드리프트 정정 — 원문은 "`Subscription` 타입(Epic 14에서 이미 구현, allocation/sales[] 포함)"이었으나, `Subscription` 타입에는 allocation/sales가 없고 allocation·sales를 담은 타입은 `InvestmentHistoryItem`이다. 또한 상세 조회 함수 실명은 `getInvestmentHistoryById`가 아니라 `getSubscriptionDetail`이다.)
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
Epic 16(web-inquiry-dashboard) SF-2 수행: 투자 내역 화면(/investments)의 목록 표(InvestmentHistoryTable)와 상세 확장(InvestmentHistoryDetail) 위젯, 그리고 TanStack Query 훅(useInvestmentHistory, useInvestmentHistoryDetail)을 구현한다. SF-1(FilterBar, EmptyState, InvestmentHistoryPage 기본 배치)은 이미 main에 병합 완료(PR #168)된 것으로 가정하고, 필터 바 아래에 테이블 위젯을 배치한다. 백엔드 API(GET /investment-history=PR #153, GET /investment-history/{subscriptionId}=PR #162)는 이미 구현·병합 완료이므로 mock이 아니라 **실연동**으로 개발한다.

## 범위 (In Scope)
1. **InvestmentHistoryTable 위젯** (`ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx` + `.module.css`)
   - 목록 뷰: 각 행을 InvestmentHistoryRow로 렌더링
   - 상세 확장: 선택된 행의 상세 정보를 InvestmentHistoryDetail로 중첩 표시
   - 상태 관리: 선택된 subscriptionId를 로컬 상태로 관리, 선택 시 useInvestmentHistoryDetail 훅 활성화
   - 빈 결과/로딩/에러 상태: 빈 결과는 EmptyState(재사용), 로딩·에러는 인라인 `<p>` / `<p role="alert">` (shared Spinner·ErrorMessage는 미존재)
   - 모바일(640px) 단일 컬럼, 태블릿/데스크톱에서 적절한 폭 제한 반응형

2. **InvestmentHistoryRow 컴포넌트** (`ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx`)
   - 단일 행 렌더링: **실제 응답 DTO 기준** `id`(구독ID), `subscribedAt`, `ipoName`, `subscriptionQuantity`, `subscriptionStatus`, `remainingQuantity` 표시. `investorName`/`securitiesAccount`는 `GET /investment-history` 응답에 해당 필드가 없으므로 표시하지 않는다(표시 요구가 남으면 백엔드 계약 확장 또는 별도 조회가 필요하므로 착수 전 사용자 결정)
   - 행 클릭 시 onSelect 콜백 호출 (부모에서 상세 확장 트리거)
   - 키보드 접근성: Tab으로 포커스 가능, Enter/Space로 선택, `role=\"row\"`, `tabIndex=0`

3. **InvestmentHistoryDetail 컴포넌트** (`ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx` + `.module.css`)
   - 상세 정보 중첩 표시(실제 DTO 필드명 기준): `id`, `subscribedAt`, `ipoId`, `ipoName`, `ipoFixedPrice`, `subscriptionQuantity`, `subscriptionStatus`, `allocation`(nullable: `id`, `allocatedQuantity`, `allocationPrice`, `allocationStatus`), `sales[]`(`id`, `quantity`, `amount`, `fee`, `tax`, `otherCost`, `status`, `soldAt`), `remainingQuantity`
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
   - queryFn: entities/subscription/api-client.ts의 **getSubscriptionDetail** 함수 사용 (함수 실명 확인: `git show origin/main:ui/src/entities/subscription/api-client.ts` → `getSubscriptionDetail(subscriptionId: number): Promise<InvestmentHistoryItem>`)
   - enabled: subscriptionId가 존재할 때만 true (상세 확장 시에만 조회)
   - select: allocation과 sales[] 포함 전체 응답 반환
   - 404 에러 시 별도 처리: "해당 구독을 찾을 수 없습니다." 메시지 표시 (InvestmentHistoryPage.tsx의 `getErrorMessage()` 인라인 패턴을 따를 것 — shared ErrorMessage 컴포넌트는 존재하지 않음)

6. **InvestmentHistoryPage 업데이트** (`ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` + `.module.css`)
   - 기존 SF-1 배치(FilterBar + 결과 영역 placeholder) 아래에 InvestmentHistoryTable 배치
   - 현재 SF-1 코드가 \"투자 내역 {n}건 조회됨 (테이블은 다음 SF에서 구현)\" placeholder를 그리는 부분을 InvestmentHistoryTable로 교체
   - 결과 영역 상태 분기: idle(숨김) → loading(인라인 `<p>` 로딩 문구) → success(InvestmentHistoryTable) → empty(EmptyState 재사용) → error(인라인 `<p role="alert">`)
   - **shared Spinner/ErrorMessage 컴포넌트는 존재하지 않으므로** 새 컴포넌트를 임의로 만들지 말고 InvestmentHistoryPage.tsx의 기존 인라인 패턴을 재사용한다
   - 상세 404 에러는 InvestmentHistoryDetail 영역 내 인라인 `<p role="alert">`로 표시

7. **entities/subscription 재사용** (수정 금지, import만)
   - ui/src/entities/subscription/types.ts — `InvestmentHistoryItem`(allocation, sales[], remainingQuantity 포함), `AllocationSummary`, `SaleSummary`, `InvestmentHistoryResponse`, `InvestmentHistoryFilters` 타입. **`Subscription` 타입은 allocation/sales를 담지 않으므로 사용하지 않는다**
   - ui/src/entities/subscription/api-client.ts — `getInvestmentHistory`, `getSubscriptionDetail` 함수

## 비범위 (Out of Scope)
- 실제 화면 코드 작성 외: breakdown/spec/architecture 문서 수정 금지
- FilterBar, EmptyState, useInvestmentHistoryFilters 훅 구현 (SF-1 범위 — main에 이미 병합 완료, PR #168)
- DashboardPage, DashboardTabs, DashboardCardGroup, DashboardCard 위젯 (SF-3~5 범위)
- entities/dashboard/ 신규 생성 (SF-4 범위)
- 백엔드 API 구현 (Epic 11 SF-2/SF-3 모두 완료 — 실연동은 이 SF에서 수행)
- GitHub Issue #134 공통 수정 (별도 SF-0로 분리 여부 미정 — 사용자 결정 대기)
- PR #157 병합 여부 확인 (다른 사람 담당)

## 읽을 파일 (Reference Only — 수정 금지)
- docs/breakdown/web-inquiry-dashboard.md (SF-2 섹션 전체)
- docs/spec/web-inquiry-dashboard.md (1. 투자 내역 화면, 4. AC-001~005, 019~021, 5. E2E 시나리오 1/2/4/5/6/8/9/10/11/13/14/18/19/20/21/22)
- docs/architecture/web-inquiry-dashboard.md (FSD 레이어, InvestmentHistoryTable/Row/Detail 설계, TanStack Query 훅 설계, entities/subscription 재사용, 상태 관리, 접근성/반응형 구현 방안)
- docs/guide-frontend.md (검증 명령, 기술 기준, FSD 구조 규칙)
- ui/src/widgets/EmptyState/EmptyState.tsx (SF-1에서 생성됨 — 재사용, main에 존재)
- ui/src/shared/components/ConfirmDialog/ConfirmDialog.tsx (기존 재사용 — 중복 생성 금지)
- ui/src/shared/api/client.ts (apiFetch — Issue #134 수정 완료 패턴 확인)
- ui/src/entities/subscription/types.ts, api-client.ts (기존 엔티티 타입/함수 재사용 — 수정 금지)
- ui/src/features/investment-history/FilterBar/FilterBar.tsx, useInvestmentHistoryFilters.ts (SF-1 산출물 — 재사용, main에 존재)
- ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx (SF-1 산출물 — 업데이트 대상, main에 존재)
- backend/src/main/java/com/vibeaiteam/investmentqueryapi/dto/investmenthistory/InvestmentHistoryDto.java (실제 응답 DTO 필드 확인용)

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
   - InvestmentHistoryRow: 실제 DTO 기준(`id`, `subscribedAt`, `ipoName`, `subscriptionQuantity`, `subscriptionStatus`, `remainingQuantity`) 표시, 키보드 선택 가능
   - InvestmentHistoryDetail: allocation + sales[] 중첩 표시(실제 DTO 필드명), remainingQuantity에 aria-readonly=\"true\" 적용
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

6. **실연동 검증**
   - GET /investment-history(목록, PR #153)와 GET /investment-history/{subscriptionId}(상세, PR #162)는 **모두 구현·병합 완료**이므로 mock이 아닌 실 API로 필터링/상세 조회/빈 상태/에러 플로우를 검증한다
   - 실연동 착수 전 `backend/src/main/java/com/vibeaiteam/investmentqueryapi/dto/investmenthistory/InvestmentHistoryDto.java`와 `ui/src/entities/subscription/types.ts`의 필드 정합을 다시 확인할 것
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

- **라우팅 갭 #3 (2026-09-28 재확인 → 해소됨)**: `useSaleRegister.ts:13`의 `navigate('/investments')`가 가리키는 `/investments` 라우트는 PR #157 시점에는 없었으나, **PR #168(Epic 16 SF-1, main 병합 완료)로 `App.tsx`에 `path="/investments"` → `InvestmentHistoryPage`가 추가되어 해소되었다.** 이 문서 초안 작성 당시의 "`/investments` 라우트 없음" 기록은 더 이상 사실이 아니다.
- **비용 등록 화면의 navigate 부재**: `useCostRegister.ts`의 `onSuccess`는 `invalidateQueries({ queryKey: ['costs'] })`만 호출하고 `navigate()`가 **전혀 없다**(매도 등록과 달리). 따라서 라우팅 갭이 아니라 "성공 후 이동 없음" 별도 이슈이며, 깨진 링크는 아니다. Epic 16 범위 밖이므로 별도 티켓으로 다룰 것을 권고한다.
- **shared 컴포넌트 실제 목록**: `ui/src/shared/components/`에는 **`ConfirmDialog` 하나만 존재**한다. `Button`/`Input`/`FormField`/`ErrorMessage`/`Spinner`는 저장소 전체 이력(`git log --all -- ui/src/shared/components/Button ...`)에 생성 기록이 **하나도 없다**. 본 문서가 가리키는 해당 경로들은 import 시 빌드 실패하므로 새 컴포넌트 생성 여부를 워커가 임의로 결정하지 말고 Master/frontend 팀장에게 확인할 것.
- **GitHub Issue #134**: `apiFetch`가 서버 400 메시지를 버리는 문제. breakdown.md에 별도 SF-0로 분리할지 각 폼 SF에 포함할지 범위 밖으로 미룰지 사용자 결정 대기 중. 결정 전까지는 현재 패턴(`shared/api/client.ts`의 수정된 `apiFetch` + 폼의 `getFormErrorMessage()` 패턴)대로 구현하되, 추후 일괄 수정 가능성 염두.
- **Epic 14 필터 버튼 그룹 패턴**: `web-subscription-workflow.md` SF-1에서 **`shared/components/ConfirmDialog`만** 생성되어 재사용 기반이 마련됐다. `Button`/`Input`/`FormField`/`ErrorMessage`/`Spinner`는 생성되지 않았으므로, 이들이 필요하면 SF-2에서 신규 생성하거나 화면 인라인 마크업(`<input>`, `<p role="alert">`)을 쓰거나, 어느 쪽으로 할지 먼저 결정해야 한다.
- **실연동**: `useInvestmentHistory`, `useInvestmentHistoryDetail` 훅은 mock이 아니라 실제 API를 호출한다(Epic 11 SF-2/SF-3 모두 병합 완료). architecture.md의 훅 설계(enabled, queryKey, select, staleTime) 준수.
- **공유 엔티티**: `entities/subscription`는 Epic 11/14에서 구현됨 — 수정 금지, 타입/함수만 import하여 사용. `entities/investor`, `securities-account`, `ipo`도 Epic 13에서 구현됨.
- **EmptyState 위젯 재사용**: SF-1에서 생성된 `widgets/EmptyState/`를 그대로 재사용함.
- **SF-1 산출물 의존**: FilterBar, useInvestmentHistoryFilters, EmptyState, InvestmentHistoryPage(기본 배치)는 **PR #168로 main에 이미 병합 완료**되어 있다. "완료된 것으로 가정"이 아니라 실제 존재하는 파일이다.

---

## 5. 드리프트 점검 (2026-09-28, ui/ 실제 코드 대조)

**대조 기준**: `origin/main`의 실제 파일(`git show origin/main:<path>`) + `git ls-tree -r origin/main -- ui/src`. 본 문서의 워크트리는 `winter0123/epic16-sf2-5-task-draft` 브랜치로 origin/main보다 **3커밋 뒤처져 있어**(PR #157·#167·#168 미반영) 판단은 항상 origin/main을 기준으로 내렸다.

**필수 검증 명령 결과** (`grep -rn "navigate(" ui/src --include="*.tsx" | grep -i "investments\|dashboard"`):

```text
ui/src/features/investor-register/InvestorRegisterForm.tsx:27:        navigate('/dashboard')
ui/src/features/securities-account-register/SecuritiesAccountRegisterForm.tsx:39:          navigate('/dashboard')
ui/src/features/login/LoginForm.tsx:20:        navigate('/dashboard')
```

> 이 명령은 `--include="*.tsx"`라 `useSaleRegister.ts`(`.ts`)의 `navigate('/investments')`를 **잡지 못한다**. `.ts`까지 포함한 결과는 아래 별도 명령으로 확인해야 한다.
>
> ```text
> $ grep -rn "navigate(" ui/src --include="*.tsx" --include="*.ts" | grep -i "investments\|dashboard"
> ui/src/features/sale-register/useSaleRegister.ts:13:      navigate('/investments')
> ui/src/features/investor-register/InvestorRegisterForm.tsx:27:        navigate('/dashboard')
> ui/src/features/securities-account-register/SecuritiesAccountRegisterForm.tsx:39:          navigate('/dashboard')
> ui/src/features/login/LoginForm.tsx:20:        navigate('/dashboard')
> ```
>
> `.ts`를 포함해도 `navigate('/investments')`가 1건뿐이어서, 검색 인자만 믿고 "이동 로직이 없다"고 결론 내리면 안 된다.

### 라우팅 갭 #3 결론: **해소됨**

| 시점 | `/investments` | 근거 |
|---|---|---|
| 본 문서 초안 작성(PR #166) 시점 | 없음 | `App.tsx`에 경로 없음 |
| `origin/main` 현재 | **있음** | `App.tsx`의 `<Route path="/investments" element={<InvestmentHistoryPage />} />` (PR #168) |

- `/dashboard`는 처음부터 `DashboardPage`(투자자 목록 조회 화면)을 가리키고 있다. Epic 16 SF-3에서 DashboardTabs로 재작성할 대상이다.
- `useSaleRegister.ts:13`의 `navigate('/investments')`는 이제 유효한 라우트를 가리킨다. `navigate` 호출 자체는 **`useSaleRegister.ts`(.ts) 안에 있고 컴포넌트 파일이 아니다**는 점에 주의.
- `useCostRegister.ts`에는 `navigate`가 없어 비용 등록 성공 후 이동이 없다. 깨진 링크가 아니라 **부재**이므로 라우팅 갭 결론과 분리한다.

### 발견한 드리프트 목록 (원문 인용 → 정정)

| # | 위치 | 원문 인용 | 사실 | 정정 |
|---|---|---|---|---|
| 1 | 1절 선행 SF | "**백엔드 선행 조건: Epic 11(투자 내역 조회) 미구현** — 현재 SF-1까지 완료된 Epic 11은 미구현 상태." | `InvestmentHistoryController`에 `@GetMapping`과 `@GetMapping("/{subscriptionId}")` 모두 존재(PR #153, #162) | 본문 수정 완료 — 실연동 가능 |
| 2 | 1절 사용 백엔드 API | "- `GET /investment-history` — Epic 11, **미구현** (목록 조회)<br>- `GET /investment-history/{subscriptionId}` — Epic 11, **미구현** (상세 조회)" | 두 API 모두 main에 존재 | 본문 수정 완료 |
| 3 | 1절 특이사항 | "목록 행은 `subscriptionId`, `investorName`, `securitiesAccount`, `ipoName`, `status`를 표시한다(D-W16-004)." | `InvestmentHistoryDto`에 `investorName`/`securitiesAccount`/`status` 필드 **없음**. 실제 필드: `id`, `subscribedAt`, `ipoId`, `ipoName`, `ipoFixedPrice`, `subscriptionQuantity`, `subscriptionStatus`, `allocation`, `sales`, `remainingQuantity` | 본문 수정 완료. 단 `investorName` 표시 요구는 **백엔드 계약 확장 필요 → 사용자 결정 대상** |
| 4 | 1절 특이사항 | "상세 화면은 ... `sales[].saleId`, `sales[].quantity`, `sales[].price`, `sales[].amount`, `sales[].saleDate`, `allocation.status` ..." | `SaleSummaryDto`: `id`, `quantity`, `amount`, `fee`, `tax`, `otherCost`, `status`, `soldAt`. `AllocationSummaryDto`: `id`, `allocatedQuantity`, `allocationPrice`, `allocationStatus`. `price`/`saleDate`/`saleId` 필드 없음 | 본문 수정 완료 (실제 필드명 명시) |
| 5 | 1절 특이사항 | "`entities/subscription/`의 `Subscription` 타입(Epic 14에서 이미 구현, allocation/sales[] 포함)" | `Subscription` 타입에 allocation/sales 없음. allocation·sales는 `InvestmentHistoryItem`에 있음 | 본문 수정 완료 |
| 6 | 2절 spec 5번 | "queryFn: entities/subscription/api-client.ts의 getInvestmentHistoryById 함수 사용" | 실제 함수명 `getSubscriptionDetail` | 본문 수정 완료 |
| 7 | 2절 spec 7번 | "ui/src/entities/subscription/api-client.ts — getInvestmentHistory, **getInvestmentHistoryById** 함수" | 동일 | 본문 수정 완료 |
| 8 | 2절 spec 6번 | "loading(Spinner) → ... → error(ErrorMessage)" | shared `Spinner`/`ErrorMessage` 컴포넌트 **미존재**(생성 이력 0) | 본문 수정 완료 — 기존 인라인 패턴 사용 |
| 9 | 2절 읽을 파일 | "ui/src/shared/components/Button/Button.tsx, Input/Input.tsx, FormField/FormField.tsx, ErrorMessage/ErrorMessage.tsx, Spinner/Spinner.tsx, ConfirmDialog/ConfirmDialog.tsx (기존 재사용 — 중복 생성 금지)" | 5개 중 4개 파일 없음. `ConfirmDialog`만 존재 | 본문 수정 완료 |
| 10 | 2절 비범위 | "백엔드 API 구현 및 실연동 (Epic 11 완료 후)" | Epic 11 완료 | 본문 수정 완료 — 실연동을 이 SF에서 수행 |
| 11 | 2절 완료 조건 6번 | "GET /investment-history(목록)와 GET /investment-history/{subscriptionId}(상세)는 모두 **미구현이므로 mock 데이터로** ..." | 둘 다 구현 완료 | 본문 수정 완료 — 실연동 검증으로 대체 |
| 12 | 4절 참고 | "`/dashboard` 라우트가 이미 investor 목록 페이지를 가리키고 있어 재작성 필요, **`/investments` 라우트 없음**" | `/investments`는 PR #168로 추가됨 | 본문 수정 완료 (해소됨으로 결론) |
| 13 | 4절 참고 | "Epic 14 필터 버튼 그룹 패턴: ... `Button`, `Input`, `FormField`, `ErrorMessage`, `Spinner`, `ConfirmDialog`를 **신규 생성하여** 재사용 기반 마련함" | `ConfirmDialog`만 생성됨 | 본문 수정 완료 |

### 착수 전 확인이 필요한 미해결 사항 (사용자 결정)

1. **투자자명/증권사 표시 여부**: `GET /investment-history` 응답에 `investorName`/`securitiesAccount`가 없다. spec/breakdown의 표시 요구를 유지하려면 백엔드 DTO 확장(Epic 11 후속) 또는 프런트에서 `GET /investors`·`GET /securities-accounts`를 조합 호출해야 한다. **Epic 16 SF-2 착수 전에 결정 필요.**
2. **shared 공용 컴포넌트 정책**: `Button`/`Input`/`FormField`/`ErrorMessage`/`Spinner`가 없어 SF-2에서 신규 생성할지, 화면 인라인 마크업으로 갈지 확정 필요. 이번에도 워커가 임의 결정하면 `shared`가 분해된다.
3. **비용 등록 성공 후 이동**: `useCostRegister`에 navigate가 없다. Epic 16 범위 밖 별도 티켓으로 분리할지, SF-2에서 함께 처리할지 결정 필요.

---

**문서 버전**: 1.1 (초안 + 2026-09-28 드리프트 점검 반영)  
**작성일**: 2026-09-28  
**드리프트 점검일**: 2026-09-28 (ui/ 실제 코드 대조, 대조 기준 `origin/main`)  
**담당**: planning 그룹 → frontend 팀장 전달 대기