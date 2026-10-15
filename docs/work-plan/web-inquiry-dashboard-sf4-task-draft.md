# 웹 조회·대시보드 — SF-4 작업 초안 (web-inquiry-dashboard SF-4 task draft)

> **게이트 문구(2026-09-28 갱신)**: **PR #157은 이미 main에 병합**되었고, **PR #168(Epic 16 SF-1)·PR #162(Epic 11 SF-3)·PR #167(Epic 12 SF-1)도 병합 완료**다. 따라서 "PR #157 병합 여부 재확인" 게이트는 **사라졌다**. ui/ 실제 파일 상태는 본 문서 5절 "드리프트 점검"에 `origin/main` 기준으로 대조해 반영했다. 남은 게이트는 **Epic 12 SF-2~4(`/summary`, `/by-investor`, `/by-securities-firm`, `/by-period`) 병합 확인**뿐이며, 병합되면 mock이 아닌 실연동으로 검증해야 한다.
>
> **확인 명령**: `git log origin/main --oneline -- backend/src/main/java/com/ipocalc/dashboard/controller/DashboardController.java`

---

## 1. SF-4 범위 요약 (breakdown.md SF-4 섹션 인용)

**범위**: 대시보드 화면의 지표 카드 그룹 위젯(DashboardCardGroup)과 개별 지표 카드 위젯(DashboardCard)을 구현한다. 5개 카드(totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount)를 그리드로 배치한다(D-W16-003). 각 카드에 레이블과 값을 표시하며, 수익률(%)은 퍼센트 형식, 참여 건수는 정수, 금액은 숫자에 원 화폐 단위를 포함한다. 차트 라이브러리를 사용하지 않는다(D-W16-003) — 순수 숫자 카드와 테이블만 사용한다. 총투자금은 배정금액 단일 기준으로 정의되며(D-W16-001), 선택 옵션이 존재하지 않는다.

**커버하는 AC**: AC-010(대시보드 차트 미도입), AC-009(대시보드 총투자금 정의), AC-008(대시보드 차트 미도입), AC-007(대시보드 총투자금 0일 때 수익률 0.0), AC-006(대시보드 읽기 전용·저장 없음), AC-005(대시보드 잔차 없이 단순 합산), AC-004(대시보드 계정 간 데이터 격리), AC-019(접근성), AC-020(반응형), AC-021(네트워크 오류). E2E 시나리오 1/2/3/4/5/6/7/8/9/10/11/12/13/14/15/16/17/18/19/20/21/22는 이 SF 대상 (참고: 대시보드 화면 지표 관련 시나리오).

**선행 SF**: SF-3 (필수). **백엔드 선행 조건: Epic 12의 대시보드 엔드포인트가 아직 main에 없음** — `DashboardController`·DTO 4종·`DashboardAggregationService`는 SF-1로 이미 병합(PR #167)됐고, `@GetMapping`은 SF-2(`/summary`)·SF-3(`/by-investor`, `/by-securities-firm`)·SF-4(`/by-period`)에서 붙는다. (2026-09-28 드리프트 정정, 원문은 "Epic 12 미구현"이었음)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고, FSD):
- `widgets/DashboardCardGroup/DashboardCardGroup.tsx` (+ `.module.css`) — 5개 카드를 그리드로 배치하는 위젯
- `widgets/DashboardCard/DashboardCard.tsx` (+ `.module.css`) — 개별 지표 카드 위젯 (레이블과 값 표시)
- `features/dashboard/useDashboardSummary.ts` — `GET /dashboard/summary`를 조회하는 `useQuery` 훅
- `features/dashboard/useDashboardByInvestor.ts` — `GET /dashboard/by-investor`를 조회하는 `useQuery` 훅
- `features/dashboard/useDashboardBySecuritiesFirm.ts` — `GET /dashboard/by-securities-firm`를 조회하는 `useQuery` 훅
- `features/dashboard/useDashboardByPeriod.ts` — `GET /dashboard/by-period?unit=month|year`를 조회하는 `useQuery` 훅
- `pages/DashboardPage/DashboardPage.tsx` — 기존 파일 업데이트 (탭 위젯 아래에 카드 그룹 위젯 배치)
- `entities/dashboard/types.ts` — **신규**. `DashboardSummary`, `DashboardByInvestor`, `DashboardBySecuritiesFirm`, `DashboardByPeriod` 타입 정의 (API 응답 DTO)
- `entities/dashboard/api-client.ts` — **신규**. `GET /dashboard/summary`, `GET /dashboard/by-investor`, `GET /dashboard/by-securities-firm`, `GET /dashboard/by-period` 호출 함수

**사용 백엔드 API** (spec.md, architecture.md 참고):
- `GET /dashboard/summary` — Epic 12 SF-2, **아직 main에 없음** (이번 세션에 backend가 SF-2~4 완료 예정 → 착수 직전 재확인)
- `GET /dashboard/by-investor` — Epic 12 SF-3, **아직 main에 없음**
- `GET /dashboard/by-securities-firm` — Epic 12 SF-3, **아직 main에 없음**
- `GET /dashboard/by-period?unit=month|year` — Epic 12 SF-4, **아직 main에 없음**

> 2026-09-28 드리프트 정정: 원문은 4개 API 모두 "Epic 12, **미구현**"이었다. Epic 12 SF-1 기반 계층은 병합(#167)됐고 나머지는 진행 중이므로 표현을 "Epic 12 SF-2~4에서 구현 예정"으로 바꾼다.
>
> **응답 DTO는 이미 확정되어 있다**: `backend/src/main/java/com/ipocalc/dashboard/dto/`의 `SummaryDto`, `InvestorAggregationDto`, `SecuritiesFirmAggregationDto`, `PeriodAggregationDto`(PR #167로 병합). `entities/dashboard/types.ts`는 이 4개 DTO의 필드를 그대로 선반영한다.

**특이사항** (breakdown.md, spec.md, architecture.md 종합):
- **총투자금 정의**: 배정금액 단일 기준으로 정의되며(`Allocation.allocatedQuantity × allocationPrice`), 별도 선택 옵션이 존재하지 않음(D-W16-001).
- **데이터 무존재 시**: 모든 필드 0인 `DashboardSummary` 반환 또는 빈 배열 반환 (AC-006/011).
- **수익률 계산**: `totalAfterTaxProfit / totalInvestmentAmount × 100` (총투자금 0이면 0.0) (D-W16-003).
- **참여 건수**: 정수로 표시하며, 데이터가 없으면 0.
- **금액 표기**: 숫자에 원 화폐 단위를 포함하여 표시 (예: "1,000원").
- **차트 라이브러리 미도입**: 차트/시각화 컴포넌트가 존재하지 않으며, 지표가 숫자 카드와 테이블로만 표시됨(D-W16-003).
- **응답 DTO 타입**: `entities/dashboard/types.ts`에 정의하며, `entities/dashboard/api-client.ts`에서 API 호출 함수를 내보낸다.
- GitHub Issue #134 처리 방식은 별도 결정이 필요함(breakdown.md "GitHub Issue #134 처리 방식" 섹션 참조).

---

## 2. frontend 팀장 전달용 `orca orchestration task-create` 명령

아래 명령은 **실제로 실행하지 말고 문서에 텍스트로만 기록**한다. frontend 팀장이 복사하여 실행할 수 있도록 `--spec` 내용에 목적/범위/비범위/읽을 파일/수정 가능 파일/검증 명령/완료 조건을 모두 구체화했다.

```bash
orca orchestration task-create \
  --task-title "SF-4: 대시보드 화면 - DashboardCardGroup/DashboardCard 위젯(5개 지표 카드), useDashboard* 훅 4종, entities/dashboard 신규 생성, DashboardPage 업데이트" \
  --spec "
## 목적
Epic 16(web-inquiry-dashboard) SF-4 수행: 대시보드 화면(/dashboard)의 지표 카드 그룹(DashboardCardGroup)과 개별 카드(DashboardCard) 위젯, 4개 대시보드 API용 TanStack Query 훅(useDashboardSummary, useDashboardByInvestor, useDashboardBySecuritiesFirm, useDashboardByPeriod), 그리고 entities/dashboard 신규 생성을 구현한다. SF-3(DashboardTabs, DashboardPage 기본 배치, EmptyState 재사용)는 완료된 것으로 가정하고, 탭 위젯 아래에 카드 그룹 위젯을 배치한다. 백엔드 API(GET /dashboard/summary, /by-investor, /by-securities-firm, /by-period)는 Epic 12 SF-2~4에서 구현 예정이며 아직 main에 없으므로 mock 데이터로 검증하고, 실연동은 Epic 12 SF-2~4 병합 후 전환한다. 응답 DTO 4종(SummaryDto/InvestorAggregationDto/SecuritiesFirmAggregationDto/PeriodAggregationDto)은 Epic 12 SF-1로 이미 병합되어 있으므로 타입 정의는 실 DTO 기준으로 작성한다.

## 범위 (In Scope)
1. **DashboardCardGroup 위젯** (\`ui/src/widgets/DashboardCardGroup/DashboardCardGroup.tsx\` + \`.module.css\`)
   - 5개 DashboardCard를 그리드로 배치
   - 반응형: 모바일(640px) 단일 열, 태블릿(768px) 2열, 데스크톱(1024px) 5열 (카드 수만큼)
   - 카드 순서: totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount

2. **DashboardCard 위젯** (\`ui/src/widgets/DashboardCard/DashboardCard.tsx\` + \`.module.css\`)
   - 개별 지표 카드: 레이블(제목) + 값 표시
   - 값 포맷팅:
     - totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit: 금액 숫자 + 원 화폐 단위 (예: \"1,000원\")
     - totalProfitRate: 퍼센트 형식 (소수점 1자리, 예: \"12.3%\")
     - participationCount: 정수 (예: \"5건\")
   - 총투자금 0일 때 수익률 0.0% 표시 (D-W16-003)
   - 접근성: 레이블에 \`<label>\` 또는 \`aria-label\`, 값 영역에 의미 있는 텍스트

3. **useDashboardSummary 훅** (\`ui/src/features/dashboard/useDashboardSummary.ts\`)
   - queryKey: ['dashboardSummary']
   - queryFn: entities/dashboard/api-client.ts의 getDashboardSummary 함수 사용
   - enabled: true (기본 탭 'all' 로드 시 자동 호출)
   - staleTime: 0 (탭 전환 시 최신 데이터 보장)
   - select: 응답 그대로 반환 (모든 필드 0인 경우도 정상 처리)

4. **useDashboardByInvestor 훅** (\`ui/src/features/dashboard/useDashboardByInvestor.ts\`)
   - queryKey: ['dashboardByInvestor']
   - queryFn: entities/dashboard/api-client.ts의 getDashboardByInvestor 함수 사용
   - enabled: 탭이 'investor'일 때만 true
   - staleTime: 0

5. **useDashboardBySecuritiesFirm 훅** (\`ui/src/features/dashboard/useDashboardBySecuritiesFirm.ts\`)
   - queryKey: ['dashboardBySecuritiesFirm']
   - queryFn: entities/dashboard/api-client.ts의 getDashboardBySecuritiesFirm 함수 사용
   - enabled: 탭이 'securities-firm'일 때만 true
   - staleTime: 0

6. **useDashboardByPeriod 훅** (\`ui/src/features/dashboard/useDashboardByPeriod.ts\`)
   - queryKey: ['dashboardByPeriod', unit, year?, month?]
   - queryFn: entities/dashboard/api-client.ts의 getDashboardByPeriod 함수 사용
   - enabled: 탭이 'month' 또는 'year'일 때만 true
   - staleTime: 0
   - unit 파라미터: 'month' | 'year', year/month 선택 파라미터 전달

7. **entities/dashboard 신규 생성** (수정 가능)
   - \`ui/src/entities/dashboard/types.ts\`: DashboardSummary, DashboardByInvestor, DashboardBySecuritiesFirm, DashboardByPeriod 타입 정의
     - DashboardSummary: totalInvestmentAmount(number), totalPreTaxProfit(number), totalAfterTaxProfit(number), totalProfitRate(number), participationCount(number)
     - DashboardByInvestor: investorId, investorName, totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount 배열
     - DashboardBySecuritiesFirm: securitiesFirmId, securitiesFirmName, totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount 배열
     - DashboardByPeriod: unit('month'|'year'), year?, month?, data[] (기간별 지표 배열)
   - \`ui/src/entities/dashboard/api-client.ts\`: GET /dashboard/summary, GET /dashboard/by-investor, GET /dashboard/by-securities-firm, GET /dashboard/by-period 호출 함수 내보내기
     - shared/api/client.ts의 apiFetch 기반으로 구현

8. **DashboardPage 업데이트** (\`ui/src/pages/DashboardPage/DashboardPage.tsx\` + \`.module.css\`)
   - 기존 SF-3 배치(DashboardTabs + 결과 영역 placeholder) 아래에 DashboardCardGroup 배치
   - 결과 영역 상태 분기:
     - idle: 초기 로드 전 (첫 탭 'all'이므로 바로 loading 전환)
          - loading: 현재 탭의 isLoading 시 인라인 `<p>` 로딩 문구 표시 (shared `Spinner` 컴포넌트 미존재)
     - success: 데이터 존재 시 현재 탭에 맞는 표시
       - 'all' 탭: DashboardCardGroup (5개 카드)
       - 'investor' 탭: 투자자별 테이블/카드 (DashboardCardGroup 재사용 또는 테이블 위젯 - architecture.md 참조)
       - 'securities-firm' 탭: 증권사별 테이블/카드
       - 'month'/'year' 탭: 기간별 테이블/카드
     - empty: 모든 지표 0 또는 빈 배열 시 EmptyState(재사용) 표시 — 메시지 + 필터 초기화 버튼(현재 탭 재조회)
          - error: 500/네트워크 오류 시 인라인 `<p role="alert">` 표시 (shared `ErrorMessage` 컴포넌트 미존재)
   - 비즈니스 로직 누적하지 않음 (선언적 UI만)

## 비범위 (Out of Scope)
- 실제 화면 코드 작성 외: breakdown/spec/architecture 문서 수정 금지
- DashboardTabs, useDashboardTabs 훅 구현 (SF-3 범위, 이미 완료 가정)
- EmptyState 위젯 신규 생성 (SF-1에서 생성됨 — 재사용)
- PeriodSelector 연도 선택기 컴포넌트 (SF-5 범위)
- 백엔드 API 구현 및 실연동 (Epic 12 완료 후)
- GitHub Issue #134 공통 수정 (별도 SF-0로 분리 여부 미정 — 사용자 결정 대기)
- PR #157 병합 여부 확인 (다른 사람 담당)
- InvestmentHistoryTable, FilterBar 등 투자 내역 화면 컴포넌트 (SF-1~2 완료 가정)

## 읽을 파일 (Reference Only — 수정 금지)
- docs/breakdown/web-inquiry-dashboard.md (SF-4 섹션 전체)
- docs/spec/web-inquiry-dashboard.md (2. 대시보드 화면, 3.2 접근성, 3.3 반응형, AC-004~010, 019~021, 5. E2E 시나리오 1~22)
- docs/architecture/web-inquiry-dashboard.md (FSD 레이어, DashboardCardGroup/Card 설계, TanStack Query 훅 설계 4종, entities/dashboard 신규, 상태 관리, 접근성/반응형 구현 방안)
- docs/guide-frontend.md (검증 명령, 기술 기준, FSD 구조 규칙)
- ui/src/widgets/EmptyState/EmptyState.tsx (SF-1 산출물 — 재사용)
- ui/src/shared/components/Button/Button.tsx, Spinner/Spinner.tsx, ErrorMessage/ErrorMessage.tsx (기존 재사용 — 중복 생성 금지)
  > **2026-09-28 드리프트 정정**: 위 3개 파일은 **존재하지 않는다**(저장소 전체 이력에 생성 기록 0건). `ui/src/shared/components/`에는 `ConfirmDialog`만 있다. 로딩/에러는 `ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx`의 인라인 `<p>` 패턴을 따른다.
- ui/src/shared/api/client.ts (apiFetch — Issue #134 수정 완료 패턴 확인)
- ui/src/features/dashboard/DashboardTabs/DashboardTabs.tsx, useDashboardTabs.ts (SF-3 산출물 — 재사용)
- ui/src/pages/DashboardPage/DashboardPage.tsx (SF-3 산출물 — 업데이트 대상)

## 수정 가능 파일 (Editable Files)
- ui/src/widgets/DashboardCardGroup/DashboardCardGroup.tsx (신규)
- ui/src/widgets/DashboardCardGroup/DashboardCardGroup.module.css (신규)
- ui/src/widgets/DashboardCard/DashboardCard.tsx (신규)
- ui/src/widgets/DashboardCard/DashboardCard.module.css (신규)
- ui/src/features/dashboard/useDashboardSummary.ts (신규)
- ui/src/features/dashboard/useDashboardByInvestor.ts (신규)
- ui/src/features/dashboard/useDashboardBySecuritiesFirm.ts (신규)
- ui/src/features/dashboard/useDashboardByPeriod.ts (신규)
- ui/src/entities/dashboard/types.ts (신규)
- ui/src/entities/dashboard/api-client.ts (신규)
- ui/src/pages/DashboardPage/DashboardPage.tsx (기존 업데이트)
- ui/src/pages/DashboardPage/DashboardPage.module.css (기존 업데이트)

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
   - DashboardCardGroup: 5개 카드 그리드 배치, 반응형(모바일 1열/태블릿 2열/데스크톱 5열) 정상
   - DashboardCard: 레이블+값 표시, 금액(원 단위), 수익률(%), 참여 건수(정수) 포맷팅 올바름, 총투자금 0일 때 수익률 0.0%
   - useDashboardSummary: 기본 탭 'all'에서 자동 호출, 응답 그대로 반환
   - useDashboardByInvestor: 'investor' 탭에서만 호출, 투자자별 데이터 표시
   - useDashboardBySecuritiesFirm: 'securities-firm' 탭에서만 호출, 증권사별 데이터 표시
   - useDashboardByPeriod: 'month'/'year' 탭에서만 호출, unit/year/month 파라미터 전달
   - entities/dashboard: 4개 응답 DTO 타입 + 4개 API 호출 함수 정의 완료
   - DashboardPage: DashboardTabs + DashboardCardGroup 배치, 탭별 상태 분기 렌더링(idle/loading/success/empty/error), EmptyState 재사용(필터 초기화 → 현재 탭 재조회)

2. **접근성 통과**
   - \`axe-core\` 스캔 시 WCAG 2.1 AA 위반 없음 (대시보드 화면)
   - 카드 레이블에 \`<label>\`/\`aria-label\` 적용됨
   - 빈 결과 메시지에 \`role=\"status\"\` 적용됨

3. **반응형 통과**
   - 640px/768px/1024px에서 레이아웃 정상 (모바일: 단일 컬럼, 태블릿: 2열, 데스크톱: 5열)
   - 카드 텍스트 잘림 없이 표시

4. **코드 품질 통과**
   - 위 검증 명령 5개(test, lint, typecheck, format:check, build) 모두 PASS
   - ESLint/Prettier/TypeScript strict 준수, 2칸 들여쓰기, 탭 금지
   - FSD 의존 방향 준수: app → pages → widgets → features → entities → shared

5. **문서/상태 갱신**
   - docs/current-status.md에 SF-4 완료 반영
   - docs/work-log/web-inquiry-dashboard.md에 작업 로그 append
   - PR 생성 및 리뷰 요청 (병합은 Master 승인 후)

6. **Mock 데이터 검증**
   - 4개 대시보드 엔드포인트가 아직 main에 없으므로(Epic 12 SF-2~4 진행 중) mock 데이터로 탭별 표시/로딩/빈 상태/에러 플로우 수동 확인 완료
   - 각 탭별 mock 데이터: summary(5개 지표), by-investor(투자자별 배열), by-securities-firm(증권사별 배열), by-period(unit=month/year 기간별 배열)
   - 총투자금 0 → 수익률 0.0%, 금액 원 단위 표기, 참여 건수 정수 표기 확인
   - 차트 라이브러리 임포트 없음 확인
   - 실연동은 Epic 12(SF-2~4) 병합 후 전환 예정. **병합이 확인되면 mock이 아닌 실연동으로 재검증할 것.**
"
  --parent <EPIC_16_TASK_ID> \
  --deps '["<SF_3_TASK_ID>"]' \
  --json
```

> **참고**: `--parent <EPIC_16_TASK_ID>`는 Epic 16 최상위 태스크 ID로, `--deps`는 SF-3 태스크 ID로 치환하여 실행할 것.

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

- **라우팅 갭 #3 (2026-09-28 재확인 → 해소됨)**: 원문은 "`/dashboard` 라우트가 이미 investor 목록 페이지를 가리키고 있어 재작성 필요, **`/investments` 라우트 없음**"이었다. `/investments`는 **PR #168(Epic 16 SF-1)로 main에 추가되어 해소**되었다. 이 SF는 `App.tsx`를 건드리지 않고 `DashboardPage.tsx`에 카드 그룹만 얹는다.
- **shared 컴포넌트 실제 목록**: `ui/src/shared/components/`에는 `ConfirmDialog`만 있다. `Button`/`Spinner`/`ErrorMessage`/`FormField`/`Input`은 생성 이력이 없다. 로딩·에러는 `InvestmentHistoryPage.tsx`의 인라인 `<p>` 패턴을 따른다.
- **GitHub Issue #134**: `apiFetch`가 서버 400 메시지를 버리는 문제. breakdown.md에 별도 SF-0로 분리할지 각 폼 SF에 포함할지 범위 밖으로 미룰지 사용자 결정 대기 중. 결정 전까지는 현재 패턴(`shared/api/client.ts`의 수정된 `apiFetch` + 폼의 `getFormErrorMessage()` 패턴)대로 구현하되, 추후 일괄 수정 가능성 염두.
- **EmptyState 위젯 재사용**: SF-1에서 생성된 `widgets/EmptyState/`를 그대로 재사용함. 대시보드에서는 필터 초기화 버튼이 \"현재 탭 재조회\" 역할로 동작하도록 useDashboardTabs 훅과 연동.
- **SF-3 훅 인터페이스 선행 정의**: SF-3에서 useDashboardTabs가 호출할 SF-4 훅들의 인터페이스(이름, 반환 타입, enabled 조건)를 사전에 맞춰두었음. architecture.md의 TanStack Query 훅 설계 표 참고.
- **SF-1~2 산출물과 독립**: 대시보드 화면은 투자 내역 화면과 별도 페이지이므로 SF-1~2 산출물과 파일 충돌 없음.
- **차트 라이브러리 금지**: D-W16-003에 따라 차트/시각화 라이브러리 임포트·사용 금지. architecture.md의 \"리스크\" 섹션에 명시된 대로 순수 숫자 카드와 테이블만 구현.
- **entities/dashboard 신규 생성**: 기존 `entities/investor`, `securities-account`, `subscription` 등은 수정 금지. `dashboard` 엔티티만 신규로 추가하며 응답 DTO 타입과 API 클라이언트만 포함.
- **응답 DTO 정본**: 타입은 backend의 `dashboard/dto/` 4종(`SummaryDto`, `InvestorAggregationDto`, `SecuritiesFirmAggregationDto`, `PeriodAggregationDto`)을 그대로 선반영할 것 — PR #167로 이미 병합되어 있어 필드를 추측하지 않는다.

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

- 이 SF는 `/investments`를 건드리지 않는다. 라우팅 갭이 해소되었으므로 SF-2~5 사이에 남은 라우팅 작업은 없다.

### 발견한 드리프트 목록 (원문 인용 → 정정)

| # | 위치 | 원문 인용 | 사실 | 정정 |
|---|---|---|---|---|
| 1 | 1절 선행 SF | "**백엔드 선행 조건: Epic 12(대시보드 집계 API) 미구현**" | SF-1 기반 계층(컨트롤러·서비스·DTO 4종)은 PR #167로 병합됨. 엔드포인트만 SF-2~4에서 추가 | 본문 수정 완료 |
| 2 | 1절 사용 API | 4개 모두 "Epic 12, **미구현**" | SF-2~4 진행 중 | 본문 수정 완료 + DTO 정본 경로 명시 |
| 3 | 2절 spec 목적 | "백엔드 API(...)는 Epic 12 SF-1~4에서 구현 예정이며 현재 미구현 상태" | SF-1은 이미 병합됨 | 본문 수정 완료 |
| 4 | 2절 spec 8번 | "loading: 현재 탭의 isLoading 시 **Spinner(shared)** 표시" / "error: ... **ErrorMessage(shared)** 표시" | 두 shared 컴포넌트 미존재 | 본문 수정 완료 — 인라인 `<p>` 패턴 |
| 5 | 2절 읽을 파일 | "ui/src/shared/components/Button/Button.tsx, Spinner/Spinner.tsx, ErrorMessage/ErrorMessage.tsx (기존 재사용 — 중복 생성 금지)" | 3개 파일 모두 없음 | 본문 수정 완료 |
| 6 | 2절 완료 조건 6번 | "4개 대시보드 API 모두 미구현이므로 mock 데이터로" | SF-1은 병합, 엔드포인트만 진행 중 | 본문 수정 완료 |
| 7 | 4절 참고 | "**`/investments` 라우트 없음**" | PR #168로 존재 | 본문 수정 완료 (해소됨) |
| 8 | 1절 산출물 | "`entities/dashboard/types.ts` — **신규**. ... (API 응답 DTO)" | DTO가 이미 main에 있으므로 필드를 추측하지 않고 그대로 옮겨야 함 | 보강(정본 경로 명시) |

### 착수 전 확인 필요 (사용자 결정)

1. **Epic 12 SF-2~4 병합 시점**: 병합 확인 후에는 `entities/dashboard/types.ts`를 실 DTO 기준으로 확정하고 mock이 아닌 실연동으로 재검증한다.
2. **shared 공용 컴포넌트 정책**: `Button`/`Spinner`/`ErrorMessage`를 여기서 만들지 않으면 SF-5까지 인라인 마크업이 중복된다.

---

**문서 버전**: 1.1 (초안 + 2026-09-28 드리프트 점검 반영)  
**작성일**: 2026-09-28  
**드리프트 점검일**: 2026-09-28 (ui/ 실제 코드 대조, 대조 기준 `origin/main`)  
**담당**: planning 그룹 → frontend 팀장 전달 대기