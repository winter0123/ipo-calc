# 웹 조회·대시보드 — SF-5 작업 초안 (web-inquiry-dashboard SF-5 task draft)

> **게이트 문구(2026-09-28 갱신)**: **PR #157은 이미 main에 병합**되었고, **PR #168(Epic 16 SF-1)·PR #162(Epic 11 SF-3)·PR #167(Epic 12 SF-1)도 병합 완료**다. 따라서 "PR #157 병합 여부 재확인" 게이트는 **사라졌다**. ui/ 실제 파일 상태는 본 문서 5절 "드리프트 점검"에 `origin/main` 기준으로 대조해 반영했다. 남은 게이트는 **Epic 12 SF-2~4(`/summary`, `/by-investor`, `/by-securities-firm`, `/by-period`) 병합 확인**뿐이며, 병합되면 mock이 아닌 실연동으로 검증해야 한다.
>
> **확인 명령**: `git log origin/main --oneline -- backend/src/main/java/com/vibeaiteam/dashboard/controller/DashboardController.java`

---

## 1. SF-5 범위 요약 (breakdown.md SF-5 섹션 인용)

**범위**: 대시보드 화면의 기간별 탭(월별/연도별)에서 연도 선택기 컴포넌트를 구현하고, `useDashboardByPeriod` 훅을 통해 API 연동 로직을 완성한다. 연도 선택기는 `Input` 또는 `Select` 컴포넌트를 재사용하여 사용자가 원하는 연도를 입력할 수 있도록 한다. spec은 월별 탭 선택 시 기본 최신 월을 조회하고 필요시 연도 선택기를 함께 제공한다고 명시하므로, 이 SF는 연도 선택기 컴포넌트만 구현하고 월은 현재 월로 고정한다(월 선택기는 spec에 명시가 없으므로 범위에 포함하지 않는다). 월별/연도별 두 탭 모두에서 이 연도 선택기를 재사용해 사용자가 특정 연도의 데이터를 조회할 수 있게 한다.

**커버하는 AC**: AC-008(기간 파라미터 유효성 검사), AC-004(월별 집계 정상), AC-005(연도별 집계 정상), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 9/10/11/12/13/14/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 기간별 탭 관련 시나리오).

**선행 SF**: SF-4 (필수). **백엔드 선행 조건: Epic 12의 대시보드 엔드포인트가 아직 main에 없음** — `DashboardController`·DTO 4종·`DashboardAggregationService`는 SF-1로 이미 병합(PR #167)됐고, `@GetMapping`은 SF-2~4에서 붙는다. 이 SF가 쓰는 `/by-period`는 SF-4 범위다. (2026-09-28 드리프트 정정, 원문은 "Epic 12 미구현"이었음)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `features/dashboard/PeriodSelector/PeriodSelector.tsx` (+ `.module.css`) — 연도 선택기 컴포넌트 (Input 필드 + 버튼 또는 Select)
- `features/dashboard/useDashboardByPeriod.ts` — 기존 파일 업데이트 (연도 선택기 값을 `year` 파라미터로 전달)
- `pages/DashboardPage/DashboardPage.tsx` — 기존 파일 업데이트 (기간별 탭 영역에 연도 선택기 컴포넌트 배치)
- `entities/dashboard/api-client.ts` — 기존 파일 업데이트 (년도 및 월 선택 사항을 파라미터로 전달하는 함수 수정)

**사용 백엔드 API** (spec.md, architecture.md 참고):
- `GET /dashboard/by-period?unit=month|year&year=<year>&month=<month>` — Epic 12 SF-4, **아직 main에 없음** (이번 세션에 backend가 SF-2~4 완료 예정 → 착수 직전 재확인)

> 2026-09-28 드리프트 정정: 원문은 "Epic 12, **미구현**"이었다. SF-1 기반 계층은 병합(#167)됐고 엔드포인트는 진행 중이므로 표현을 바꿨다. `PeriodAggregationDto`(PR #167 병합)가 이 API의 응답 DTO 정본이다.

**특이사항** (breakdown.md, spec.md, architecture.md 종합):
- **연도 선택기**: 사용자가 4자리 연도를 입력할 수 있는 Input 컴포넌트를 제공하며, 빈 값이면 현재 연도를 기본값으로 사용한다. 월별 탭에서는 월은 항상 현재 월을 기본값으로 사용하고, 월 선택기는 spec에 명시가 없으므로 이 SF의 범위에 포함하지 않는다(향후 고려 사항으로 남김).
- **API 파라미터**: `unit` 필수, `year` 선택 사항. 월별 탭은 `month` 파라미터를 전달하지 않고 서비스가 현재 월을 기본으로 사용하도록 한다(spec의 "기본 최신 월 조회" 규정).
- **유효성 검사**: `year` 파라미터가 숫자인지 및 유효한 범위인지 확인하며, INVALID_YEAR 오류 시 400 Bad Request 반환.
- **빈 결과 처리**: 데이터가 없는 기간인 경우 EmptyState 위젯을 표시한다(D-W16-007).
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(아래 참조).

---

## 2. frontend 팀장 전달용 `orca orchestration task-create` 명령

아래 명령은 **실제로 실행하지 말고 문서에 텍스트로만 기록**한다. frontend 팀장이 복사하여 실행할 수 있도록 `--spec` 내용에 목적/범위/비범위/읽을 파일/수정 가능 파일/검증 명령/완료 조건을 모두 구체화했다.

```bash
orca orchestration task-create \
  --task-title "SF-5: 대시보드 화면 - PeriodSelector 연도 선택기 컴포넌트, useDashboardByPeriod 훅 연도 파라미터 연동, DashboardPage 기간별 탭 영역 배치, entities/api-client 업데이트" \
  --spec "
## 목적
Epic 16(web-inquiry-dashboard) SF-5 수행: 대시보드 화면(/dashboard)의 기간별 탭(월별/연도별)에서 사용할 연도 선택기(PeriodSelector) 컴포넌트를 구현하고, useDashboardByPeriod 훅을 연도 파라미터를 전달하도록 업데이트하며, DashboardPage의 기간별 탭 결과 영역에 연도 선택기를 배치한다. entities/dashboard/api-client.ts의 getDashboardByPeriod 함수도 year/month 파라미터를 전달하도록 업데이트한다. SF-4(DashboardCardGroup, DashboardCard, 4개 useDashboard* 훅, entities/dashboard types/api-client, DashboardPage 카드 배치)는 완료된 것으로 가정한다. 백엔드 API(GET /dashboard/by-period)는 Epic 12 SF-4에서 구현 예정이며 현재 미구현 상태이므로 mock 데이터로 검증하고, 실연동은 Epic 12 완료 후 전환한다.

## 범위 (In Scope)
1. **PeriodSelector 컴포넌트** (\`ui/src/features/dashboard/PeriodSelector/PeriodSelector.tsx\` + \`.module.css\`)
   - 연도 입력 필드: 4자리 연도 숫자 입력 가능한 `<input type="number">` (2026-09-28 드리프트 정정 — 원문은 "Input 컴포넌트 (shared/components/Input 재사용)"이었으나 **`ui/src/shared/components/Input/`는 존재하지 않는다**. 기존 화면의 인라인 `<input>` + `styles.css` 모듈 패턴을 따를 것. 공용 Input을 새로 만들지 말 것)
   - 빈 값이면 현재 연도를 기본값으로 사용 (새 Date().getFullYear())
   - 입력 유효성: 숫자 4자리, 합리적 범위(예: 2000~현재연도+1) 검증 — 클라이언트 사이드 1차 검증, 서버 400 응답 시 에러 메시지 표시
   - 조회 버튼: Button 컴포넌트(shared 재사용) — 클릭 시 onSearch 콜백 호출, year 값 전달
   - 월별 탭에서는 \"월: 현재 월\" 표시(읽기 전용), 연도 선택기만 편집 가능
   - 연도별 탭에서는 연도 선택기만 표시
   - 접근성: Input에 \`<label>\` + \`aria-label\`, 에러 시 \`aria-invalid\`, \`aria-describedby\` 적용
   - 반응형: 모바일(640px) 전체 폭, 데스크톱(1024px) 적절한 폭 제한

2. **useDashboardByPeriod 훅 업데이트** (\`ui/src/features/dashboard/useDashboardByPeriod.ts\`)
   - 기존 queryKey: ['dashboardByPeriod', unit, year?, month?] 유지
   - year 파라미터: PeriodSelector에서 전달받은 연도 값 사용 (없으면 undefined → 서버가 기본값 처리)
   - month 파라미터: 월별 탭에서만 현재 월(기본값) 전달, 연도별 탭에서는 전달하지 않음
   - enabled: 탭이 'month' 또는 'year'일 때만 true (SF-4에서 이미 구현)
   - year 변경 시 queryKey 변경되어 자동 재조회 트리거
   - staleTime: 0 (탭 전환/연도 변경 시 최신 데이터 보장)
   - select: 응답 그대로 반환 (빈 배열도 정상 처리)

3. **DashboardPage 업데이트** (\`ui/src/pages/DashboardPage/DashboardPage.tsx\` + \`.module.css\`)
   - 기존 SF-4 배치(탭 + 카드 그룹/테이블) 유지
   - 기간별 탭('month'/'year') 활성화 시 결과 영역 상단에 PeriodSelector 컴포넌트 배치
   - PeriodSelector의 onSearch 콜백에서 useDashboardByPeriod 훅의 year 파라미터 갱신 → 재조회 트리거
   - 월별 탭: \"월: <현재 월>\" 표시(읽기 전용) + 연도 선택기 + 조회 버튼
   - 연도별 탭: 연도 선택기 + 조회 버튼
   - 빈 결과 시 EmptyState(재사용) 표시 — \"해당 조건에 해당하는 기록이 없습니다.\" + 필터 초기화 버튼(현재 탭 재조회 + 연도 선택기 초기화)
   - 에러 시 인라인 `<p role="alert">` 표시 — 네트워크/500 에러 메시지 (2026-09-28 드리프트 정정 — shared `ErrorMessage` 컴포넌트는 존재하지 않는다. `InvestmentHistoryPage.tsx`의 `getErrorMessage()` 패턴을 따를 것)
   - 비즈니스 로직 누적하지 않음 (선언적 UI만)

4. **entities/dashboard/api-client.ts 업데이트** (기존 파일 수정)
   - getDashboardByPeriod 함수 수정: unit, year?, month? 파라미터를 쿼리 스트링으로 전달
   - URL 구성: \`/dashboard/by-period?unit=\${unit}\${year ? '&year=' + year : ''}\${month ? '&month=' + month : ''}\`
   - year/month 파라미터가 undefined이면 쿼리 스트링에 포함하지 않음 (서버 기본값 사용)

## 비범위 (Out of Scope)
- 실제 화면 코드 작성 외: breakdown/spec/architecture 문서 수정 금지
- DashboardCardGroup, DashboardCard, useDashboardSummary, useDashboardByInvestor, useDashboardBySecuritiesFirm 구현 (SF-4 범위, 이미 완료 가정)
- DashboardTabs, useDashboardTabs, EmptyState 구현 (SF-3 범위, 이미 완료 가정)
- 월 선택기 컴포넌트 (spec에 명시 없음 — 향후 고려 사항)
- 백엔드 API 구현 및 실연동 (Epic 12 완료 후)
- GitHub Issue #134 공통 수정 (별도 SF-0로 분리 여부 미정 — 사용자 결정 대기)
- PR #157 병합 여부 확인 (다른 사람 담당)
- InvestmentHistoryTable, FilterBar 등 투자 내역 화면 컴포넌트 (SF-1~2 완료 가정)

## 읽을 파일 (Reference Only — 수정 금지)
- docs/breakdown/web-inquiry-dashboard.md (SF-5 섹션 전체)
- docs/spec/web-inquiry-dashboard.md (2. 대시보드 화면, 3.2 접근성, 3.3 반응형, AC-004, 005, 008, 019~021, 5. E2E 시나리오 9~22)
- docs/architecture/web-inquiry-dashboard.md (FSD 레이어, useDashboardByPeriod 설계, PeriodSelector 배치, entities/dashboard/api-client 파라미터 전달, 상태 관리, 접근성/반응형 구현 방안)
- docs/guide-frontend.md (검증 명령, 기술 기준, FSD 구조 규칙)
- ui/src/widgets/EmptyState/EmptyState.tsx (SF-1 산출물 — 재사용)
- ui/src/shared/components/Button/Button.tsx, Input/Input.tsx, FormField/FormField.tsx, ErrorMessage/ErrorMessage.tsx, Spinner/Spinner.tsx (기존 재사용 — 중복 생성 금지)
  > **2026-09-28 드리프트 정정**: 위 5개 파일은 **모두 존재하지 않는다**(저장소 전체 이력에 생성 기록 0건). `ui/src/shared/components/`에는 `ConfirmDialog`만 있다. 인라인 `<input>`/`<p role="alert">` 패턴으로 구현할 것.
- ui/src/shared/api/client.ts (apiFetch — Issue #134 수정 완료 패턴 확인)
- ui/src/features/dashboard/useDashboardByPeriod.ts (SF-4 산출물 — 업데이트 대상)
- ui/src/pages/DashboardPage/DashboardPage.tsx (SF-4 산출물 — 업데이트 대상)
- ui/src/entities/dashboard/api-client.ts (SF-4 산출물 — 업데이트 대상)
- ui/src/entities/dashboard/types.ts (SF-4 산출물 — DashboardByPeriod 타입 참고)

## 수정 가능 파일 (Editable Files)
- ui/src/features/dashboard/PeriodSelector/PeriodSelector.tsx (신규)
- ui/src/features/dashboard/PeriodSelector/PeriodSelector.module.css (신규)
- ui/src/features/dashboard/useDashboardByPeriod.ts (기존 업데이트)
- ui/src/pages/DashboardPage/DashboardPage.tsx (기존 업데이트)
- ui/src/pages/DashboardPage/DashboardPage.module.css (기존 업데이트)
- ui/src/entities/dashboard/api-client.ts (기존 업데이트)

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
   - PeriodSelector: 연도 Input(4자리 숫자) + 조회 버튼 렌더링, 빈 값 시 현재 연도 기본값, 클라이언트 유효성 검증(숫자/범위), 월별 탭에서 \"월: 현재 월\" 읽기 전용 표시
   - useDashboardByPeriod: year 파라미터 변경 시 queryKey 갱신되어 재조회 트리거, 월별 탭에서 month=현재월 전달, 연도별 탭에서 month 미전달
   - DashboardPage: 기간별 탭 활성화 시 PeriodSelector 배치, 조회 버튼 클릭 시 재조회, 빈 결과/에러 상태 분기 렌더링, EmptyState 재사용(필터 초기화 → 현재 탭 재조회 + 연도 선택기 초기화)
   - entities/dashboard/api-client.ts: getDashboardByPeriod가 year/month 파라미터를 쿼리 스트링으로 올바르게 전달

2. **접근성 통과**
   - \`axe-core\` 스캔 시 WCAG 2.1 AA 위반 없음 (대시보드 화면 기간별 탭 영역)
   - 연도 Input에 \`<label>\`/\`aria-label\`, \`aria-invalid\`, \`aria-describedby\` 적용됨
   - 빈 결과 메시지에 \`role=\"status\"\` 적용됨

3. **반응형 통과**
   - 640px/768px/1024px에서 레이아웃 정상 (PeriodSelector 모바일 전체 폭, 데스크톱 폭 제한)
   - 연도 Input 텍스트 잘림 없이 표시

4. **코드 품질 통과**
   - 위 검증 명령 5개(test, lint, typecheck, format:check, build) 모두 PASS
   - ESLint/Prettier/TypeScript strict 준수, 2칸 들여쓰기, 탭 금지
   - FSD 의존 방향 준수: app → pages → widgets → features → entities → shared

5. **문서/상태 갱신**
   - docs/current-status.md에 SF-5 완료 반영
   - docs/work-log/web-inquiry-dashboard.md에 작업 로그 append
   - PR 생성 및 리뷰 요청 (병합은 Master 승인 후)

6. **Mock 데이터 검증**
   - GET /dashboard/by-period 엔드포인트가 아직 main에 없으므로 mock 데이터로 연도 선택/월별·연도별 탭/빈 결과/에러/유효성 검사 플로우 수동 확인 완료
   - 연도 미입력(기본값 현재 연도), 연도 입력(특정 연도), 잘못된 연도(숫자 아님/범위 밖) 케이스 확인
   - 월별 탭: month=현재월 자동 전달, 연도별 탭: month 미전달 확인
   - 실연동은 Epic 12(SF-4) 완료 후 전환 예정
"
  --parent <EPIC_16_TASK_ID> \
  --deps '["<SF_4_TASK_ID>"]' \
  --json
```

> **참고**: `--parent <EPIC_16_TASK_ID>`는 Epic 16 최상위 태스크 ID로, `--deps`는 SF-4 태스크 ID로 치환하여 실행할 것.

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

- **라우팅 갭 #3 (2026-09-28 재확인 → 해소됨)**: 원문은 "`/dashboard` 라우트가 이미 investor 목록 페이지를 가리키고 있어 재작성 필요, **`/investments` 라우트 없음**"이었다. `/investments`는 **PR #168(Epic 16 SF-1)로 main에 추가되어 해소**되었다. 이 SF는 라우팅을 건드리지 않는다.
- **shared 컴포넌트 실제 목록**: `ui/src/shared/components/`에는 `ConfirmDialog`만 있다. `Button`/`Input`/`FormField`/`ErrorMessage`/`Spinner`은 생성 이력이 없다.
- **GitHub Issue #134**: `apiFetch`가 서버 400 메시지를 버리는 문제. breakdown.md에 별도 SF-0로 분리할지 각 폼 SF에 포함할지 범위 밖으로 미룰지 사용자 결정 대기 중. 결정 전까지는 현재 패턴(`shared/api/client.ts`의 수정된 `apiFetch` + 폼의 `getFormErrorMessage()` 패턴)대로 구현하되, 추후 일괄 수정 가능성 염두.
- **EmptyState 위젯 재사용**: SF-1에서 생성된 `widgets/EmptyState/`를 그대로 재사용함. 대시보드에서는 필터 초기화 버튼이 \"현재 탭 재조회\" 역할로 동작하도록 useDashboardTabs 훅과 연동. SF-5에서는 연도 선택기도 함께 초기화.
- **SF-4 산출물 의존**: useDashboardByPeriod 훅, entities/dashboard/api-client.ts, DashboardPage는 SF-4에서 생성된 것을 업데이트함.
- **월 선택기 미포함**: spec(D-W16-005)에 \"월별 탭 선택 시 기본 최신 월을 조회하고, 필요시 연도 선택기를 함께 제공한다\"고만 명시되어 월 선택기는 범위 밖. 향후 별도 고려 사항으로 남김.
- **연도 유효성 검사**: 클라이언트 1차 검증(숫자 4자리, 2000~현재연도+1 범위) + 서버 400 응답 시 에러 메시지 표시(인라인 `<p role="alert">` 패턴 — shared ErrorMessage는 미존재). INVALID_YEAR 에러 코드는 서버 계약에 따름.
- **SF-1~2 산출물과 독립**: 대시보드 화면은 투자 내역 화면과 별도 페이지이므로 SF-1~2 산출물과 파일 충돌 없음.

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

- 이 SF는 라우팅과 무관하다. 연도 선택기를 `DashboardPage` 안에 배치하는 것이 유일한 UI 작업이다.

### 발견한 드리프트 목록 (원문 인용 → 정정)

| # | 위치 | 원문 인용 | 사실 | 정정 |
|---|---|---|---|---|
| 1 | 1절 선행 SF | "**백엔드 선행 조건: Epic 12(대시보드 집계 API) 미구현**" | SF-1 기반 계층은 PR #167로 병합됨. 엔드포인트만 SF-2~4에서 추가 | 본문 수정 완료 |
| 2 | 1절 사용 API | "`GET /dashboard/by-period...` — Epic 12, **미구현**" | SF-4 진행 중. 응답 DTO(`PeriodAggregationDto`)는 이미 main에 있음 | 본문 수정 완료 |
| 3 | 2절 spec 1번 | "연도 입력 필드: 4자리 연도 숫자 입력 가능한 **Input 컴포넌트 (shared/components/Input 재사용)**" | `ui/src/shared/components/Input/` 미존재(생성 이력 0건) | 본문 수정 완료 — 인라인 `<input>` |
| 4 | 2절 spec (에러 처리) | "에러 시 **ErrorMessage(shared)** 표시" | `ErrorMessage` 미존재 | 본문 수정 완료 — 인라인 `<p role="alert">` |
| 5 | 2절 읽을 파일 | "ui/src/shared/components/Button/Button.tsx, Input/Input.tsx, FormField/FormField.tsx, ErrorMessage/ErrorMessage.tsx, Spinner/Spinner.tsx (기존 재사용 — 중복 생성 금지)" | 5개 파일 모두 없음 | 본문 수정 완료 |
| 6 | 2절 완료 조건 6번 | "GET /dashboard/by-period 미구현이므로 mock 데이터로..." | 표현만 부정확(엔드포인트만 미병합) | 본문 수정 완료 |
| 7 | 4절 참고 | "**`/investments` 라우트 없음**" | PR #168로 존재 | 본문 수정 완료 (해소됨) |
| 8 | 4절 참고 | "기존 **ErrorMessage 패턴** 재사용" | shared ErrorMessage 미존재 | 본문 수정 완료 |

### 착수 전 확인 필요 (사용자 결정)

1. **Epic 12 SF-4(`/by-period`) 병합 시점**: 병합 후에는 `year`/`month` 파라미터 계약을 `PeriodAggregationDto` 기준으로 확정하고 실연동 재검증한다.
2. **공용 Input 생성 여부**: 이 SF가 첫 Input 사용처다. 여기서 `shared/components/Input`을 새로 만들지 않으면 이후 Epic에서 인라인 `<input>`이 계속 중복된다.

---

**문서 버전**: 1.1 (초안 + 2026-09-28 드리프트 점검 반영)  
**작성일**: 2026-09-28  
**드리프트 점검일**: 2026-09-28 (ui/ 실제 코드 대조, 대조 기준 `origin/main`)  
**담당**: planning 그룹 → frontend 팀장 전달 대기