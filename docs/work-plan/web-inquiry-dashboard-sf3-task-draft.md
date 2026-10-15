# 웹 조회·대시보드 — SF-3 작업 초안 (web-inquiry-dashboard SF-3 task draft)

> **게이트 문구(2026-09-28 갱신)**: **PR #157은 이미 main에 병합**되었고, **PR #168(Epic 16 SF-1)·PR #162(Epic 11 SF-3)·PR #167(Epic 12 SF-1)도 병합 완료**다. 따라서 "PR #157 병합 여부 재확인" 게이트는 **사라졌다**. ui/ 실제 파일 상태는 본 문서 5절 "드리프트 점검"에 `origin/main` 기준으로 대조해 반영했다. 남은 게이트는 **Epic 12 SF-2~4(`/summary`, `/by-investor`, `/by-securities-firm`, `/by-period`) 병합 확인**뿐이며, 병합되면 mock이 아닌 실연동으로 검증해야 한다.
>
> **확인 명령**: `git log origin/main --oneline -- backend/src/main/java/com/ipocalc/dashboard/controller/DashboardController.java`

---

## 1. SF-3 범위 요약 (breakdown.md SF-3 섹션 인용)

**범위**: 대시보드 화면의 탭 전환 컴포넌트(DashboardTabs)와 빈 결과 시 표시되는 EmptyState 위젯을 구현한다. 탭은 전체/투자자별/증권사별/월별/연도별 5개 탭을 표시하며(D-W16-005), 동일 화면 내에서 탭 전환이 이루어지며, 현재 활성 탭에 `aria-selected="true"`를 적용한다. EmptyState 위젯은 SF-1과 동일하게 "해당 조건에 해당하는 기록이 없습니다." 안내 메시지와 필터 초기화 버튼을 표시한다(D-W16-007). note: 대시보드의 필터 초기화 버튼은 탭 선택 상태를 유지하면서 현재 탭의 데이터를 다시 조회하는 역할을 한다.

**커버하는 AC**: AC-012(대시보드 탭 전환 동작), AC-011(대시보드 기본 탭 자동 호출), AC-010(대시보드 차트 미도입), AC-009(접근성 검증 통과), AC-008(반응형 레이아웃), AC-007(대시보드 탭 전환 동작), AC-006(대시보드 기본 탭), AC-005(대시보드 총투자금 정의), AC-004(대시보드 차트 미도입), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 12/13/14/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 대시보드 화면 관련 시나리오).

**선행 SF**: SF-2 (필수). **백엔드 선행 조건: Epic 12(대시보드 집계 API)의 엔드포인트가 아직 main에 없음** — `DashboardController` 클래스·DTO 4종·`DashboardAggregationService`는 SF-1로 이미 병합(PR #167)됐으나, `@GetMapping` 엔드포인트 자체는 SF-2(`/summary`)·SF-3(`/by-investor`, `/by-securities-firm`)·SF-4(`/by-period`)에서 붙는다. (2026-09-28 드리프트 정정, 원문은 "Epic 12 미구현"이었음)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `features/dashboard/DashboardTabs/DashboardTabs.tsx` (+ `.module.css`) — 전체/투자자별/증권사별/월별/연도별 5개 탭 전환 컴포넌트
- `features/dashboard/useDashboardTabs.ts` — 탭 상태 관리 훅 (useState + 탭별 API 호출을 담당하는 훅들의 enable/disable 조절)
- `widgets/EmptyState/EmptyState.tsx` — **기존 재사용**(SF-1에서 생성됨, 중복 생성 금지)
- `pages/DashboardPage/DashboardPage.tsx` (+ `.module.css`) — 화면 배치 (탭 위젯 + 결과 영역 placeholder)
- `App.tsx` — `/dashboard` 라우트는 **이미 존재**(`<Route path="/dashboard" element={<DashboardPage />} />`). 라우트 추가는 불필요하고 `DashboardPage.tsx` 재작성만 하면 된다. (2026-09-28 드리프트 정정, 원문은 "`/dashboard` 라우트 추가 (if not already present)"이었음)

**사용 백엔드 API** (spec.md, architecture.md 참고):
- `GET /dashboard/summary` — Epic 12 SF-2, **아직 main에 없음** (이번 세션에 backend가 SF-2~4를 완료 예정 → 착수 직전 재확인)
- `GET /dashboard/by-investor` — Epic 12 SF-3, **아직 main에 없음**
- `GET /dashboard/by-securities-firm` — Epic 12 SF-3, **아직 main에 없음**
- `GET /dashboard/by-period?unit=month|year` — Epic 12 SF-4, **아직 main에 없음**

> 2026-09-28 드리프트 정정: 원문은 4개 API 모두 "Epic 12, **미구현**"이었다. Epic 12 SF-1 기반 계층은 병합(#167)됐고 나머지는 진행 중이므로, 표현을 "미구현"에서 "Epic 12 SF-2~4에서 구현 예정"으로 바꾼다.

**특이사항** (breakdown.md, spec.md, architecture.md 종합):
- 탭 전환은 동일 화면 내에서 이루어지며, 별도 화면 이동이 없다(D-W16-005).
- 기본 탭은 "전체"로 설정되며, 화면 로드 시 `GET /dashboard/summary`를 자동 호출한다(D-W16-005, AC-011).
- 탭 선택 시 해당 API를 호출하여 결과를 현재 화면에 갱신하며, 전환 시 로딩 상태를 표시한다(D-W16-005, AC-012).
- 월별/연도별 탭에서 `unit` 파라미터를 적절히 전달하며, 월별 탭 선택 시 기본 최신 월을 조회하고, 필요시 연도 선택기를 함께 제공한다(D-W16-005).
- 빈 결과 처리: 모든 지표 값이 0이거나 데이터가 없는 배열인 경우 EmptyState 위젯을 표시한다(D-W16-007, AC-011).
- 필터 초기화 버튼은 EmptyState 위젯에 포함되어 있으며, 클릭 시 현재 탭의 데이터를 다시 조회한다(D-W16-007).
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(breakdown.md "GitHub Issue #134 처리 방식" 섹션 참조).
- 접근성: WCAG 2.1 AA 준수, `axe-core` 검증, 탭에 `role="tablist"`, `role="tab"`, 활성 탭 `aria-selected="true"`, 비활성 `aria-selected="false"`, 빈 결과 메시지에 `role="status"`.
- 반응형: 640px/768px/1024px 브레이크포인트, 모바일 우선(mobile-first), `DashboardTabs`는 모바일에서 스크롤 가능한 탭 바, 데스크톱에서 인라인 배치.
- FSD 의존 방향 준수: `pages` → `widgets` → `features` → `entities` → `shared`.

---

## 2. frontend 팀장 전달용 `orca orchestration task-create` 명령

아래 명령은 **실제로 실행하지 말고 문서에 텍스트로만 기록**한다. frontend 팀장이 복사하여 실행할 수 있도록 `--spec` 내용에 목적/범위/비범위/읽을 파일/수정 가능 파일/검증 명령/완료 조건을 모두 구체화했다.

```bash
orca orchestration task-create \
  --task-title "SF-3: 대시보드 화면 - DashboardTabs 위젯(5개 탭 전환), useDashboardTabs 훅, DashboardPage 배치, EmptyState 재사용" \
  --spec "
## 목적
Epic 16(web-inquiry-dashboard) SF-3 수행: 대시보드 화면(/dashboard)의 탭 전환(DashboardTabs)과 탭 상태 관리 훅(useDashboardTabs), 대시보드 페이지 배치(DashboardPage)를 구현한다. SF-1에서 생성된 EmptyState 위젯을 재사용한다. `/dashboard` 라우트는 이미 존재하므로 App.tsx 수정이 필요 없고 DashboardPage.tsx만 재작성한다. 백엔드 API(GET /dashboard/summary, /by-investor, /by-securities-firm, /by-period)는 Epic 12 SF-2~4에서 구현 예정이며 아직 main에 없으므로 mock 데이터로 검증하고, 실연동은 Epic 12 SF-2~4 병합 후 전환한다.

## 범위 (In Scope)
1. **DashboardTabs 컴포넌트** (`ui/src/features/dashboard/DashboardTabs/DashboardTabs.tsx` + `.module.css`)
   - 5개 탭: 전체, 투자자별, 증권사별, 월별, 연도별
   - 탭 전환 시 onChange 콜백으로 현재 탭 상태 변경 알림
   - 활성 탭: \`aria-selected=\"true\"\`, 비활성 탭: \`aria-selected=\"false\"\`
   - \`role=\"tablist\"\` 컨테이너, 각 탭 \`role=\"tab\"\` 속성 적용
   - 키보드 네비게이션: 좌/우 화살표로 탭 이동, Enter/Space로 선택, Home/End로 첫/마지막 탭 이동
   - 모바일(640px): 탭 바 가로 스크롤 가능(overflow-x: auto), 탭 최소 폭 확보
   - 데스크톱(1024px): 탭 인라인 배치, 적절한 간격
   - Epic 15의 ProfitQueryToggle 패턴 참조하되 탭(Tab) 형태로 구현

2. **useDashboardTabs 훅** (`ui/src/features/dashboard/useDashboardTabs.ts`)
   - 탭 상태 관리: useState<'all' | 'investor' | 'securities-firm' | 'month' | 'year'> (기본값: 'all')
   - 탭별 API 훅 매핑: 각 탭에 해당하는 useQuery 훅(useDashboardSummary, useDashboardByInvestor, useDashboardBySecuritiesFirm, useDashboardByPeriod)의 enabled 제어
   - 현재 탭 반환: currentTab, setCurrentTab
   - 탭 변경 시 이전 탭의 쿼리는 캐시 유지(enabled: false), 새 탭의 쿼리는 enabled: true로 자동 호출
   - 로딩 상태 통합: 현재 탭의 isLoading 반환

3. **DashboardPage** (`ui/src/pages/DashboardPage/DashboardPage.tsx` + `.module.css`)
   - 화면 배치: DashboardTabs(상단) + 결과 영역(하단)
   - **기존 DashboardPage를 대체**: 현재는 `GET /investors`(investor api-client)로 투자자 목록과 "증권계좌 등록" 링크를 렌더링하는 화면이다. 이 내용을 제거하고 탭 + 결과 영역으로 재작성한다. `fetchInvestors`/`Link to={/securities-accounts/register/${investor.id}}` 블록이 사라지는 것을 정상으로 본다.
   - 결과 영역 상태 분기:
     - idle: 초기 로드 전(첫 탭 'all'이므로 바로 loading 전환)
     - loading: 현재 탭의 isLoading 시 인라인 `<p>` 로딩 문구 표시 (**shared `Spinner` 컴포넌트는 존재하지 않음** — `InvestmentHistoryPage.tsx`의 `{isLoading && <p className={styles.loading}>...` 패턴을 따를 것)
     - success: 데이터 존재 시 DashboardCardGroup(SF-4에서 구현) 또는 테이블 자리 표시
     - empty: 모든 지표 0 또는 빈 배열 시 EmptyState(재사용) 표시 — 메시지 + 필터 초기화 버튼(현재 탭 재조회)
     - error: 500/네트워크 오류 시 인라인 `<p role="alert">` 표시 (**shared `ErrorMessage` 컴포넌트는 존재하지 않음** — `InvestmentHistoryPage.tsx`의 `getErrorMessage()` 패턴을 따를 것)
   - 비즈니스 로직 누적하지 않음 (선언적 UI만)
   - 네비게이션 링크: 투자 내역 화면(/investments)으로 이동 링크 포함 — **PR #168로 `/investments` 라우트가 이미 존재하므로 유효한 링크다**

4. **App.tsx 라우팅**
   - `/dashboard` → DashboardPage 매핑은 **이미 존재**하므로 수정 불필요. 파일 자체를 수정 가능 목록에서 빼도 된다.

5. **EmptyState 재사용** (수정 금지)
   - ui/src/widgets/EmptyState/EmptyState.tsx — SF-1에서 생성됨, 그대로 import하여 사용
   - 대시보드용: 필터 초기화 버튼 클릭 시 현재 탭의 invalidateQueries 호출 (useDashboardTabs 훅을 통해 현재 탭별 queryKey 무효화)

## 비범위 (Out of Scope)
- 실제 화면 코드 작성 외: breakdown/spec/architecture 문서 수정 금지
- DashboardCardGroup, DashboardCard 위젯 구현 (SF-4 범위)
- useDashboardSummary, useDashboardByInvestor, useDashboardBySecuritiesFirm, useDashboardByPeriod 훅 구현 (SF-4 범위)
- entities/dashboard/ 신규 생성 (SF-4 범위)
- PeriodSelector 연도 선택기 컴포넌트 (SF-5 범위)
- 백엔드 API 구현 및 실연동 (Epic 12 완료 후)
- GitHub Issue #134 공통 수정 (별도 SF-0로 분리 여부 미정 — 사용자 결정 대기)
- PR #157 병합 여부 확인 (다른 사람 담당)
- InvestmentHistoryTable, FilterBar 등 투자 내역 화면 컴포넌트 (SF-1은 PR #168로 실제 병합 완료, SF-2 완료 가정)

## 읽을 파일 (Reference Only — 수정 금지)
- docs/breakdown/web-inquiry-dashboard.md (SF-3 섹션 전체)
- docs/spec/web-inquiry-dashboard.md (2. 대시보드 화면, 3.2 접근성, 3.3 반응형, AC-004~012, 019~021, 5. E2E 시나리오 12~22)
- docs/architecture/web-inquiry-dashboard.md (FSD 레이어, DashboardTabs/useDashboardTabs 설계, DashboardPage 배치, EmptyState 재사용, 상태 관리, 접근성/반응형 구현 방안)
- docs/guide-frontend.md (검증 명령, 기술 기준, FSD 구조 규칙)
- ui/src/widgets/EmptyState/EmptyState.tsx (SF-1 산출물 — 재사용, main에 존재)
- ui/src/features/profit-view/ProfitQueryToggle.tsx (Epic 15의 실제 토글 패턴 — SF-3이 참조해야 할 정본 경로)
- ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx (로딩/에러 인라인 렌더링 패턴 정본 — shared Spinner/ErrorMessage가 없으므로 대신 참고)
- ui/src/shared/api/client.ts (apiFetch 패턴 확인)
- ui/src/features/dashboard/ (SF-4에서 생성될 훅들 — 현재는 없음, 인터페이스만 참고)
- backend/src/main/java/com/ipocalc/dashboard/dto/ (SummaryDto, InvestorAggregationDto, SecuritiesFirmAggregationDto, PeriodAggregationDto — Epic 12 SF-1로 이미 병합됨)

## 수정 가능 파일 (Editable Files)
- ui/src/features/dashboard/DashboardTabs/DashboardTabs.tsx (신규)
- ui/src/features/dashboard/DashboardTabs/DashboardTabs.module.css (신규)
- ui/src/features/dashboard/useDashboardTabs.ts (신규)
- ui/src/pages/DashboardPage/DashboardPage.tsx (기존 재작성 — 현재 투자자 목록 화면)
- ui/src/pages/DashboardPage/DashboardPage.module.css (기존 업데이트)
- ~~ui/src/App.tsx~~ — **`/dashboard` 라우트가 이미 존재하므로 수정 불필요. 워커가 App.tsx를 건드리지 말 것.**

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
   - DashboardTabs: 5개 탭 렌더링, 탭 클릭/키보드로 전환, 활성 탭 aria-selected 적용, 로딩 상태 표시
   - useDashboardTabs: 탭 상태 관리, 탭별 쿼리 enabled 제어, 현재 탭 로딩 상태 반환
   - DashboardPage: DashboardTabs + 결과 영역 배치, 상태별 분기 렌더링, EmptyState 재사용(필터 초기화 버튼 → 현재 탭 재조회)
   - 라우트 \`/dashboard\` 접근 가능, 네비게이션 링크로 투자 내역 화면 이동 가능

2. **접근성 통과**
   - \`axe-core\` 스캔 시 WCAG 2.1 AA 위반 없음 (대시보드 화면)
   - 탭 키보드 네비게이션(←/→, Home/End, Enter/Space) 정상 동작
   - role=\"tablist\"/\"tab\", aria-selected 올바르게 적용됨
   - 빈 결과 메시지에 role=\"status\" 적용됨

3. **반응형 통과**
   - 640px/768px/1024px에서 레이아웃 정상 (모바일: 탭 바 스크롤, 데스크톱: 인라인)
   - 탭 텍스트 잘림 없이 표시

4. **코드 품질 통과**
   - 위 검증 명령 5개(test, lint, typecheck, format:check, build) 모두 PASS
   - ESLint/Prettier/TypeScript strict 준수, 2칸 들여쓰기, 탭 금지
   - FSD 의존 방향 준수: app → pages → widgets → features → entities → shared

5. **문서/상태 갱신**
   - docs/current-status.md에 SF-3 완료 반영
   - docs/work-log/web-inquiry-dashboard.md에 작업 로그 append
   - PR 생성 및 리뷰 요청 (병합은 Master 승인 후)

6. **Mock 데이터 검증**
   - 4개 대시보드 API 엔드포인트는 아직 main에 없으므로(Epic 12 SF-2~4 진행 중) mock 데이터로 탭 전환/로딩/빈 상태/에러 플로우 수동 확인 완료
   - 각 탭별 mock 데이터: summary(5개 지표), by-investor(투자자별 배열), by-securities-firm(증권사별 배열), by-period(unit=month/year 기간별 배열)
   - 실연동은 Epic 12(SF-2~4) 병합 후 전환 예정. **Epic 12 병합이 확인되면 이 SF를 mock이 아닌 실연동으로 다시 검증할 것.**
"
  --parent <EPIC_16_TASK_ID> \
  --deps '["<SF_2_TASK_ID>"]' \
  --json
```

> **참고**: `--parent <EPIC_16_TASK_ID>`는 Epic 16 최상위 태스크 ID로, `--deps`는 SF-2 태스크 ID로 치환하여 실행할 것.

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

- **라우팅 갭 #3 (2026-09-28 재확인 → 해소됨)**: 원문은 "`/dashboard` 라우트가 이미 investor 목록 페이지를 가리키고 있어 재작성 필요, **`/investments` 라우트 없음**"이었다. `/investments`는 **PR #168(Epic 16 SF-1)로 main에 추가되어 해소**되었다. `/dashboard`가 투자자 목록을 가리킨다는 점은 여전히 사실이며, 이 SF에서 그 내용을 제거하는 것이 목표다.
- **GitHub Issue #134**: `apiFetch`가 서버 400 메시지를 버리는 문제. breakdown.md에 별도 SF-0로 분리할지 각 폼 SF에 포함할지 범위 밖으로 미룰지 사용자 결정 대기 중. 결정 전까지는 현재 패턴(`shared/api/client.ts`의 수정된 `apiFetch` + 폼의 `getFormErrorMessage()` 패턴)대로 구현하되, 추후 일괄 수정 가능성 염두.
- **shared 컴포넌트 실제 목록**: `ui/src/shared/components/`에는 `ConfirmDialog`만 있다. `Button`/`Spinner`/`ErrorMessage`/`FormField`/`Input`은 생성 이력이 없다. 로딩·에러는 `InvestmentHistoryPage.tsx`의 인라인 `<p>` 패턴을 따른다.
- **EmptyState 위젯 재사용**: SF-1에서 생성된 `widgets/EmptyState/`를 그대로 재사용함. 대시보드에서는 필터 초기화 버튼이 "현재 탭 재조회" 역할로 동작하도록 useDashboardTabs 훅과 연동.
- **Epic 15 ProfitQueryToggle 패턴**: 실제 정본은 `ui/src/features/profit-view/ProfitQueryToggle.tsx`(main에 존재, Epic 15 SF-3 수익 확인 화면과 함께 병합). SF-3에서는 이를 탭(Tab) 형태로 변형하여 구현함.
- **Mock 데이터**: 4개 대시보드 엔드포인트가 아직 main에 없으므로 각 탭별 mock 데이터로 검증. architecture.md의 `useDashboardSummary`/`ByInvestor`/`BySecuritiesFirm`/`ByPeriod` 설계(enabled 제어, queryKey, staleTime) 준수.
- **SF-4 훅 인터페이스 선행 정의**: SF-3에서 useDashboardTabs가 호출할 SF-4 훅들의 인터페이스(이름, 반환 타입, enabled 조건)를 사전에 맞춰두어야 함. architecture.md의 TanStack Query 훅 설계 표 참고. **Epic 12 SF-1로 백엔드 DTO 4종(SummaryDto, InvestorAggregationDto, SecuritiesFirmAggregationDto, PeriodAggregationDto)이 이미 병합**됐으므로, SF-4의 `entities/dashboard/types.ts`는 이 DTO 필드를 그대로 선반영해야 한다.
- **SF-1~2 산출물과 독립**: 대시보드 화면은 투자 내역 화면과 별도 페이지이므로 SF-1~2 산출물과 파일 충돌 없음. 단 `/investments`로 가는 네비게이션 링크는 SF-1 산출물에 의존한다(PR #168로 준비 완료).

---

## 5. 드리프트 점검 (2026-09-28, ui/ 실제 코드 대조)

**대조 기준**: `origin/main`의 실제 파일(`git show origin/main:<path>`). 본 문서의 워크트리는 origin/main보다 뒤처져 있어(PR #157·#167·#168 미반영) 판단은 항상 origin/main 기준이다.

**필수 검증 명령 결과** (`grep -rn "navigate(" ui/src --include="*.tsx" | grep -i "investments\|dashboard"`):

```text
ui/src/features/investor-register/InvestorRegisterForm.tsx:27:        navigate('/dashboard')
ui/src/features/securities-account-register/SecuritiesAccountRegisterForm.tsx:39:          navigate('/dashboard')
ui/src/features/login/LoginForm.tsx:20:        navigate('/dashboard')
```

> 이 명령은 `--include="*.tsx"`라 `useSaleRegister.ts`(.ts)의 `navigate('/investments')`를 잡지 못한다. `.ts`를 포함하면 1건이 추가된다(세부 내용은 SF-2 초안의 드리프트 점검 절 참조).

### 라우팅 갭 #3 결론: **해소됨** (`/investments`는 PR #168로 추가됨)

- `/dashboard` → `DashboardPage`(현재: 투자자 목록 조회) — 라우트는 존재하고, 이 SF의 대상은 라우트 추가가 아니라 **화면 내용 재작성**이다.

### 발견한 드리프트 목록 (원문 인용 → 정정)

| # | 위치 | 원문 인용 | 사실 | 정정 |
|---|---|---|---|---|
| 1 | 1절 선행 SF | "**백엔드 선행 조건: Epic 12(대시보드 집계 API) 미구현**" | `DashboardController`/`DashboardAggregationService`/DTO 4종은 PR #167로 병합됨. `@GetMapping` 엔드포인트만 SF-2~4에서 추가 예정 | 본문 수정 완료 |
| 2 | 1절 산출물 | "`App.tsx` — `/dashboard` 라우트 추가 (if not already present)" | 라우트 이미 존재 | 본문 수정 완료 (추가 불필요) |
| 3 | 1절 사용 API | 4개 모두 "Epic 12, **미구현**" | SF-1 기반 계층은 병합, 엔드포인트는 진행 중 | 본문 수정 완료 |
| 4 | 2절 spec 3번 | "loading: 현재 탭의 isLoading 시 **Spinner(shared)** 표시" / "error: ... **ErrorMessage(shared)** 표시" | 두 shared 컴포넌트 미존재 | 본문 수정 완료 — 인라인 `<p>` 패턴 |
| 5 | 2절 spec 3번 | "App.tsx 라우팅 추가 - `/dashboard` → DashboardPage 매핑 추가 (이미 Epic 11/PR #157에서 추가됐을 수 있음 — 병합 후 재확인)" | 이미 존재 | 본문 수정 완료 |
| 6 | 2절 읽을 파일 | "ui/src/shared/components/**Button/Button.tsx, Spinner/Spinner.tsx, ErrorMessage/ErrorMessage.tsx** (기존 재사용)" | 3개 파일 모두 없음 | 본문 수정 완료 |
| 7 | 2절 수정 가능 파일 | "ui/src/pages/DashboardPage/DashboardPage.tsx (신규)" / ".module.css (신규)" | 이미 존재하는 파일의 재작성 | 본문 수정 완료 |
| 8 | 4절 참고 | "**`/investments` 라우트 없음**" | PR #168로 존재 | 본문 수정 완료 (해소됨) |
| 9 | 4절 참고 | "Epic 15의 ProfitQueryToggle 패턴 참조" | 실제 파일 `ui/src/features/profit-view/ProfitQueryToggle.tsx` 존재 — 참조는 유효 | 정본 경로 명시 보강 |

### 착수 전 확인 필요 (사용자 결정)

1. **Epic 12 SF-2~4 병합 시점**: 이번 세션에 backend가 완료한다는 전제이므로, 병합 확인 후에는 mock이 아닌 **실연동으로 SF-3~5를 검증**해야 한다. 병합 여부는 `git log origin/main --oneline -- backend/src/main/java/com/ipocalc/dashboard/controller/DashboardController.java`로 확인한다.
2. **shared 공용 컴포넌트 정책**: SF-3이 첫 진입점이므로 `Button`/`Spinner`/`ErrorMessage`를 여기서 만들지 않으면 SF-4/SF-5까지 화면마다 인라인 마크업이 중복된다.

---

**문서 버전**: 1.1 (초안 + 2026-09-28 드리프트 점검 반영)  
**작성일**: 2026-09-28  
**드리프트 점검일**: 2026-09-28 (ui/ 실제 코드 대조, 대조 기준 `origin/main`)  
**담당**: planning 그룹 → frontend 팀장 전달 대기