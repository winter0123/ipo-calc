# 웹 조회·대시보드 — SF-1 작업 초안 (web-inquiry-dashboard SF-1 task draft)

> **게이트 문구**: 이 초안은 Epic 11·Epic 12가 main에 병합 확인된 뒤에만 frontend 팀장에게 전달한다. 전달 전 PR #157 병합 여부와 ui/ 실제 파일 상태를 재확인할 것.

---

## 1. SF-1 범위 요약 (breakdown.md SF-1 섹션 인용)

**범위**: 투자 내역 화면의 상단 필터 바(Investor ID, Securities Account ID, IPO ID 3개 필드와 초기화 버튼)와 빈 결과 시 표시되는 EmptyState 위젯을 구현한다. 필터 바는 사이드바가 아닌 화면 상단에 배치하며(D-W16-006), Epic 14의 필터 버튼 그룹 패턴을 재사용한다. 모든 필터는 선택 사항이며, 빈 값이면 해당 필터가 적용되지 않는다. EmptyState 위젯은 "해당 조건에 해당하는 기록이 없습니다." 안내 메시지와 필터 초기화 버튼을 표시한다(D-W16-007).

**커버하는 AC**: AC-020(반응형), AC-019(접근성), AC-021(네트워크 오류), AC-018(참고 표시), AC-017(필터 초기화 동작), AC-016(필터 UI가 상단 필터 바 형태), AC-015(필터 항목 정확성). E2E 시나리오 2/3/6/7/12/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 투자 내역 화면 관련 시나리오).

**선행 SF**: 없음 (첫 번째 SF). Epic 13/14/15가 `shared`(client, validators, Button, Input, FormField, ErrorMessage, Spinner)와 `entities/investor`, `entities/securities-account`, `entities/securities-firm`, `entities/ipo`를 이미 제공한다.

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `ui/src/features/investment-history/FilterBar/FilterBar.tsx` (+ `.module.css`) — 상단 필터 바 (3개 select/input 필드 + 초기화 버튼)
- `ui/src/features/investment-history/FilterBar/useInvestmentHistoryFilters.ts` — 필터 상태 관리 훅 (useState + queryKey 관리)
- `ui/src/widgets/EmptyState/EmptyState.tsx` (+ `.module.css`) — 빈 상태 안내 메시지 + 필터 초기화 버튼 (D-W16-007)
- `ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` (+ `.module.css`) — 화면 배치 (필터 바 + 결과 영역 placeholder)
- `ui/src/App.tsx` — `/investments` 라우트 추가 (if not already present from Epic 11)

**사용 백엔드 API** (spec.md, architecture.md 참고):
- `GET /investment-history` — Epic 11 SF-2, **구현·병합 완료 (PR #153)** (필터 적용용)
- `GET /investment-history/{subscriptionId}` — Epic 11 SF-3, **미구현(진행 중)** (참고용; SF-2에서 사용)

**특이사항** (breakdown.md, spec.md, architecture.md 종합):
- 필터 유효성은 서버 규칙을 대체하지 않는다: investorId/securitiesAccountId/ipoId는 텍스트/select 형태이며, 프론트에서 빈 값 필터링은 수행하지 않음(D-W16-006). 빈 값이면 해당 필터가 적용되지 않음.
- 필터 초기화 버튼 클릭 시 모든 필드를 빈 문자열로 리셋하고, `invalidateQueries({ queryKey: ['investmentHistory'] })`를 호출하여 전체 목록을 재조회한다.
- EmptyState 위젯은 `role="status"` 속성을 적용하고, 안내 메시지에 `aria-live="polite"`를 사용할 수 있다.
- GitHub Issue #134(`apiFetch`가 서버 400 메시지를 버리는 문제)에 대한 처리 방식은 별도 결정이 필요함(breakdown.md "GitHub Issue #134 처리 방식" 섹션 참조).
- Epic 14의 필터 버튼 그룹 패턴(shared/components/Button, Input, FormField 재사용)을 따른다.
- 접근성: WCAG 2.1 AA 준수, `axe-core` 검증, 모든 필드에 `<label>` 또는 `aria-label` 연결, 에러 메시지에 `aria-live="polite"`, 키보드 네비게이션 지원, `aria-invalid`/`aria-describedby` 사용.
- 반응형: 640px/768px/1024px 브레이크포인트, 모바일 우선(mobile-first), `FilterBar`는 모바일에서 필드 스택 배열, 데스크톱에서 인라인 배열.

---

## 2. frontend 팀장 전달용 `orca orchestration task-create` 명령

아래 명령은 **실제로 실행하지 말고 문서에 텍스트로만 기록**한다. frontend 팀장이 복사하여 실행할 수 있도록 `--spec` 내용에 목적/범위/비범위/읽을 파일/수정 가능 파일/검증 명령/완료 조건을 모두 구체화했다.

```bash
orca orchestration task-create \
  --task-title "SF-1: 투자 내역 화면 - 상단 필터 바(Investor ID/Securities Account ID/IPO ID 3필드 + 초기화 버튼) 및 EmptyState 위젯 구현" \
  --spec "
## 목적
Epic 16(web-inquiry-dashboard) SF-1 수행: 투자 내역 화면(/investments)의 상단 필터 바와 빈 상태(EmptyState) 위젯을 구현한다. `GET /investment-history`(목록 조회, Epic 11 SF-2)는 이미 구현·병합 완료(PR #153) 상태이므로 실연동 가능하며, `GET /investment-history/{subscriptionId}`(상세 조회, Epic 11 SF-3)만 미구현이므로 해당 훅은 mock으로 처리하고 추후 전환한다.

## 범위 (In Scope)
1. **FilterBar 컴포넌트** (`ui/src/features/investment-history/FilterBar/FilterBar.tsx` + `.module.css`)
   - 상단 필터 바 레이아웃 (사이드바 아님, D-W16-006)
   - 3개 필터 필드: investorId(text/select), securitiesAccountId(text/select), ipoId(text/select) — 모두 선택 사항, 빈 값이면 필터 미적용
   - 필터 초기화 버튼 (바의 끝에 배치)
   - Epic 14 필터 버튼 그룹 패턴 재사용: shared/components/Button, Input, FormField 사용
   - 필드별 `<label>` + `aria-label`, `aria-invalid`, `aria-describedby` 적용
   - 모바일(640px) 스택 배열, 데스크톱(1024px) 인라인 배열 반응형

2. **useInvestmentHistoryFilters 훅** (`ui/src/features/investment-history/FilterBar/useInvestmentHistoryFilters.ts`)
   - useState로 필터 상태 관리: investorId, securitiesAccountId, ipoId (초기값: 빈 문자열)
   - 필터 변경 시 useInvestmentHistory 훅의 queryKey 자동 갱신 → 재조회 트리거
   - 초기화 버튼 핸들러: 모든 필드 빈 문자열 리셋 + queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })

3. **EmptyState 위젯** (`ui/src/widgets/EmptyState/EmptyState.tsx` + `.module.css`)
   - 메시지: \"해당 조건에 해당하는 기록이 없습니다.\" (D-W16-007)
   - 필터 초기화 버튼 포함 (클릭 시 FilterBar의 초기화 로직 호출 또는 동일 invalidateQueries 수행)
   - `role=\"status\"` 속성, 안내 메시지에 `aria-live=\"polite\"` 적용
   - 기존 빈 상태 표시 패턴 재사용 (shared/components/ErrorMessage 패턴 기반)

4. **InvestmentHistoryPage** (`ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` + `.module.css`)
   - 화면 배치: FilterBar(상단) + 결과 영역 placeholder(하단)
   - 결과 영역: 초기(idle) 시 숨김, 로딩 시 Spinner, 성공 시 InvestmentHistoryTable(SF-2에서 구현) 자리, 빈 결과 시 EmptyState, 에러 시 ErrorMessage
   - 비즈니스 로직 누적하지 않음 (선언적 UI만)

5. **라우팅 추가** (`ui/src/App.tsx`)
   - `/investments` → InvestmentHistoryPage 매핑 추가 (이미 Epic 11에서 추가됐을 수 있음 — PR #157 병합 후 재확인)

## 비범위 (Out of Scope)
- 실제 화면 코드 작성 외: breakdown/spec/architecture 문서 수정 금지
- InvestmentHistoryTable, InvestmentHistoryRow, InvestmentHistoryDetail 위젯 구현 (SF-2 범위)
- useInvestmentHistory, useInvestmentHistoryDetail 훅 구현 (SF-2 범위, entities/subscription/api-client.ts의 getInvestmentHistory/getInvestmentHistoryById 사용)
- DashboardPage, DashboardTabs, DashboardCardGroup, DashboardCard 위젯 (SF-3~5 범위)
- entities/dashboard/ 신규 생성 (SF-4 범위)
- 백엔드 API 구현 및 실연동 (Epic 11/12 완료 후)
- GitHub Issue #134 공통 수정 (별도 SF-0로 분리 여부 미정 — 사용자 결정 대기)
- PR #157 병합 여부 확인 (다른 사람 담당)

## 읽을 파일 (Reference Only — 수정 금지)
- docs/breakdown/web-inquiry-dashboard.md (SF-1 섹션 전체)
- docs/spec/web-inquiry-dashboard.md (1. 투자 내역 화면, 3.1 필터 UI, 3.2 접근성, 3.3 반응형, AC-015~021)
- docs/architecture/web-inquiry-dashboard.md (FSD 레이어, FilterBar/EmptyState/InvestmentHistoryPage 설계, TanStack Query 훅 설계, 상태 관리, 라우팅, 모듈 소유권, 접근성/반응형 구현 방안)
- docs/breakdown/web-subscription-workflow.md (Epic 14 SF-1의 FilterBar/ConfirmDialog/공통 컴포넌트 패턴 참고 — shared/components/Button, Input, FormField, ErrorMessage, Spinner 재사용)
- docs/guide-frontend.md (검증 명령, 기술 기준, FSD 구조 규칙)
- ui/src/shared/components/Button/Button.tsx, Input/Input.tsx, FormField/FormField.tsx, ErrorMessage/ErrorMessage.tsx, Spinner/Spinner.tsx (기존 재사용 — 중복 생성 금지)
- ui/src/shared/api/client.ts (apiFetch — Issue #134 수정 완료 패턴 확인)
- ui/src/entities/investor/, securities-account/, ipo/ (기존 엔티티 타입 재사용)
- ui/src/App.tsx (기존 라우팅 구조 확인)

## 수정 가능 파일 (Editable Files)
- ui/src/features/investment-history/FilterBar/FilterBar.tsx (신규)
- ui/src/features/investment-history/FilterBar/FilterBar.module.css (신규)
- ui/src/features/investment-history/FilterBar/useInvestmentHistoryFilters.ts (신규)
- ui/src/widgets/EmptyState/EmptyState.tsx (신규)
- ui/src/widgets/EmptyState/EmptyState.module.css (신규)
- ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx (신규 또는 기존 업데이트)
- ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.module.css (신규 또는 기존 업데이트)
- ui/src/App.tsx (라우트 추가 — 이미 존재하면 수정 불필요)

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
   - FilterBar: 3개 필터 필드 + 초기화 버튼이 상단 바에 렌더링됨, 필터 변경 시 queryKey 갱신되어 재조회 트리거됨, 초기화 버튼 클릭 시 필드 리셋 + invalidateQueries 호출됨
   - EmptyState: 빈 결과 시 메시지 + 초기화 버튼 표시, \`role=\"status\"\` + \`aria-live=\"polite\"\` 적용됨
   - InvestmentHistoryPage: FilterBar + 결과 영역 placeholder 배치됨, 라우트 `/investments` 접근 가능
   - 모든 필드에 `<label>`/`aria-label`, `aria-invalid`, `aria-describedby` 적용됨

2. **접근성 통과**
   - \`axe-core\` 스캔 시 WCAG 2.1 AA 위반 없음 (투자 내역 화면)
   - 탭/필드 키보드 네비게이션(Tab, Enter, Escape) 정상 동작

3. **반응형 통과**
   - 640px/768px/1024px에서 레이아웃 정상 (모바일: 필드 스택, 데스크톱: 인라인)

4. **코드 품질 통과**
   - 위 검증 명령 5개(test, lint, typecheck, format:check, build) 모두 PASS
   - ESLint/Prettier/TypeScript strict 준수, 2칸 들여쓰기, 탭 금지
   - FSD 의존 방향 준수: app → pages → widgets → features → entities → shared (하위→상위 참조 금지)

5. **문서/상태 갱신**
   - docs/current-status.md에 SF-1 완료 반영
   - docs/work-log/web-inquiry-dashboard.md에 작업 로그 append
   - PR 생성 및 리뷰 요청 (병합은 Master 승인 후)

6. **Mock 데이터 검증**
    - `GET /investment-history`(목록)는 실연동 가능하므로 실제 API로 필터링/초기화/빈 상태 플로우 확인
    - `GET /investment-history/{subscriptionId}`(상세)는 미구현 상태에서 mock 데이터로 상세 조회 플로우 수동 확인 완료
"
  --parent <EPIC_16_TASK_ID> \
  --json
```

> **참고**: `--parent <EPIC_16_TASK_ID>`는 Epic 16 최상위 태스크 ID로 치환하여 실행할 것. `--deps`는 없음(SF-1이 첫 번째이므로).

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
- `typecheck`만으로 끝나지 않고 `pnpm run build`(내부에서 `tsc -b` 실행)도 필수 — 프로젝트 참조 하위 타입 오류를 놓치는 사례가 있었음(guide-frontend.md 27행 참고)
- 소스 루트: `ui/` (frontend 작업공간명과 혼동 금지 — 소스는 `ui/`에 있음)
- FSD 레이어: `ui/src/{app,pages,widgets,features,entities,shared}/` — `src` 바로 아래 형제로 둠, `src/app/` 아래 중첩 금지
- 의존 방향: `app → pages → widgets → features → entities → shared`

---

## 4. 참고 사항 (작업자 인수인계용)

- **PR #157 현황**: Epic 16 컴포넌트/라우팅 실측 점검 결과가 담긴 PR로, `/dashboard` 라우트가 이미 investor 목록 페이지를 가리키고 있어 재작성 필요, `/investments` 라우트 없음. 이 초안에는 코드로 확인하지 말고 \"PR #157 병합 후 실제 파일 상태 재확인 필요\" 문구만 상단 게이트에 남김.
- **GitHub Issue #134**: `apiFetch`가 서버 400 메시지를 버리는 문제. breakdown.md에 별도 SF-0로 분리할지 각 폼 SF에 포함할지 범위 밖으로 미룰지 사용자 결정 대기 중. 결정 전까지는 현재 패턴(`shared/api/client.ts`의 수정된 `apiFetch` + 폼의 `getFormErrorMessage()` 패턴)대로 구현하되, 추후 일괄 수정 가능성 염두.
- **Epic 14 필터 버튼 그룹 패턴**: `web-subscription-workflow.md` SF-1에서 `shared/components/Button`, `Input`, `FormField`, `ErrorMessage`, `Spinner`, `ConfirmDialog`를 신규 생성하여 재사용 기반 마련함. SF-1에서는 이들을 **중복 생성하지 않고 그대로 재사용**함.
- **Mock 데이터**: 백엔드 API 미구현 상태에서 `useInvestmentHistory` 훅은 mock 데이터 반환하도록 임시 구현하고, 실연동은 Epic 11 완료 후 전환. architecture.md의 `useInvestmentHistory` 설계(enabled: true, queryKey에 filters 포함, select: 그대로 반환, staleTime: 0) 준수.
- **공유 엔티티**: `entities/investor`, `securities-account`, `securities-firm`, `ipo`, `subscription`는 Epic 13/14/15에서 이미 구현됨 — 수정 금지, 타입만 import하여 사용.
- **EmptyState 위젯 재사용**: SF-3(대시보드 빈 상태)에서도 동일 위젯 재사용 예정 — `widgets/EmptyState/`에 배치하여 공통화.

---

## 5. 드리프트 점검 (2026-09-28, ui/ 실제 코드 대조) — 참고용

**이 초안은 이미 구현·병합 완료(PR #168)되어 더 이상 실행하지 않는다.** 다만 SF-2~5 인수인계 전에 알아둘 사실이 있어 기록한다.

- **라우팅 갭 #3 → 해소됨**: 이 초안 작성 시점에는 "`/investments` 라우트 없음"이 사실이었으나, PR #168로 `App.tsx`에 `<Route path="/investments" element={<InvestmentHistoryPage />} />`가 추가되어 해소되었다.
- **Epic 11 SF-3 완료**: 원문 "`GET /investment-history/{subscriptionId}`(상세 조회, Epic 11 SF-3)만 미구현이므로 해당 훅은 mock으로 처리"는 더 이상 사실이 아니다. PR #162로 구현·병합되어 실연동 가능하다.
- **함수명 오기**: 원문 "`getInvestmentHistory/getInvestmentHistoryById` 사용"에서 상세 조회의 실제 함수명은 **`getSubscriptionDetail`**이다(`git show origin/main:ui/src/entities/subscription/api-client.ts`).
- **shared 컴포넌트 부재**: 원문이 "PR #157 병합 후 실제 파일 상태 재확인 필요"만 남긴 부분은, `ui/src/shared/components/`에 `ConfirmDialog`만 있고 `Button`/`Input`/`FormField`/`ErrorMessage`/`Spinner`는 생성 이력이 없다는 사실로 정정한다.

> 상세 드리프트 목록은 SF-2 초안의 5절 "드리프트 점검"에 있다.

---

**문서 버전**: 1.1 (초안, 참고용 드리프트 메모 추가)  
**작성일**: 2026-09-28  
**담당**: planning 그룹 → frontend 팀장 전달 대기