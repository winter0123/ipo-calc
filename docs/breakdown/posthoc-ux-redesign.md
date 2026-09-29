# 사후 등록 UX/UI 전면 개선 — 세부 기능 분해 (posthoc-ux-redesign breakdown)

> **Epic**: Epic 19 (사후 등록 UX/UI 전면 개선, posthoc-ux-redesign)
> **분야**: 프론트엔드 위주(SF-1~3), 백엔드 없음
> **작성일**: 2026-09-29 (Master가 사용자와의 실시간 조사·결정을 바탕으로 직접 작성 — 통상적인 워커 위임 파이프라인 대신 이번 세션 한정으로 Master가 기록함)
> **근거 문서**: `docs/research/posthoc-ux-redesign.md`, `docs/roadmap.md` 1.1절·6절(E19 카드), `docs/qa/accessibility-report.md`
> **의존 관계**: 없음. Epic 17·18과 독립적으로 착수 가능 — **E17/E18/E19 중 가장 먼저 시작할 후보.**

---

## 이 Epic의 특이사항

1. **당초 우려보다 범위가 좁다.** research 결과 청약/배정/매도/비용 등록 폼 4종은 이미 대부분 드롭다운 방식이라 "전면 개선"이 필요한 곳은 사실상 **투자 내역 조회 화면(FilterBar)** 하나다. 이 breakdown은 그 사실에 맞춰 범위를 재조정했다 — Epic 이름은 유지하되 실제 SF는 조사에서 확인된 진짜 문제에 집중한다.
2. **백엔드 변경이 없다.** SF-1(계좌 선택형 조회)은 기존 `GET /investment-history` 파라미터를 그대로 쓰고, SF-3(월별 집계)은 기존 `GET /dashboard/by-period`를 그대로 쓴다. SF-2(달력)만 Epic 17의 신규 엔드포인트에 의존한다.
3. **접근성 위반 32건 중 다수(28건)가 부수적으로 해소된다.** SF-4(공통 레이아웃)가 `landmark-one-main`/`region` 위반의 근본 원인(페이지 루트에 `<main>` 랜드마크 부재)을 없앤다.

---

## 세부 기능(SF) 개요

총 **4개 SF**로 분해한다. SF-1·SF-3·SF-4는 서로 독립이라 병렬 가능하다(단, SF-4가 모든 페이지의 JSX 최상위 구조를 건드리므로 SF-1과 같은 파일을 동시에 고치지 않도록 순서 조율이 필요 — 아래 "병렬화 주의"참고). SF-2는 Epic 17의 백엔드 산출물을 기다려야 한다.

| SF | 내용 | 의존 | 병렬 가능 여부 |
|---|---|---|---|
| SF-1 | 투자 내역 조회 화면 — 계좌 선택형으로 재설계 | 없음 (기존 API 재사용) | 즉시 착수 가능 |
| SF-2 | 공모주 일정 달력 뷰(신규 페이지) | **Epic 17의 `GET /ipo-schedule` 완료 필요** | Epic 17 SF 완료 후 |
| SF-3 | 대시보드 월별 탭 UX 개선(네비게이션·강조 표시) | 없음 (기존 API 재사용) | 즉시 착수 가능 |
| SF-4 | 공통 레이아웃 컴포넌트(`MainLayout`) 도입 + 전 페이지 적용 | 없음 | 즉시 착수 가능하나 **다른 SF와 같은 페이지 파일을 건드리므로 마지막에 배치 권장** |

**권장 순서**: SF-1, SF-3 병렬 → SF-4(전 페이지 레이아웃 통일, 이 시점에는 SF-1/SF-3가 이미 반영된 페이지 구조를 감싸기만 하면 됨) → (Epic 17 완료 후) SF-2.

---

## SF-1: 투자 내역 조회 화면 — 계좌 선택형 재설계

**범위**: `ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx`와 `ui/src/features/investment-history/FilterBar/FilterBar.tsx`를 재작성한다. 현재 투자자 ID·증권계좌 ID·IPO ID를 숫자로 직접 입력하는 3개 텍스트 필드를, 로그인 사용자가 이미 등록한 투자자/증권계좌 목록에서 선택하는 드롭다운 체인으로 바꾼다.

**커버하는 문제**: `docs/research/posthoc-ux-redesign.md` 1.1절(FilterBar 수동 ID 입력 문제).

**산출물 파일 경로** (FSD):
- `ui/src/features/investment-history/AccountSelector/AccountSelector.tsx` (+ `.module.css`) — **신규**. 투자자 선택 드롭다운 → 선택된 투자자의 증권계좌 목록 드롭다운(연쇄 로딩)을 담당하는 컴포넌트
- `ui/src/features/investment-history/FilterBar/FilterBar.tsx` — **재작성**. 기존 3개 숫자 입력 필드를 제거하고 `AccountSelector` + IPO 선택 드롭다운(선택 사항)으로 교체
- `ui/src/features/investment-history/useInvestorList.ts` (+ `useSecuritiesAccountList.ts`) — **신규 또는 재사용 확인**. `GET /investors`, `GET /securities-accounts?investorId=`가 이미 다른 Epic(13/14)에 있으므로 그 훅/엔티티 함수를 재사용할 수 있는지 먼저 확인하고, 없으면 신규 작성
- `ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` — `AccountSelector`의 선택값을 `useInvestmentHistory` 훅의 `investorId`/`securitiesAccountId` 파라미터로 연결

**특이사항**:
- **API는 변경하지 않는다.** `GET /investment-history?investorId=&securitiesAccountId=&ipoId=&page=&size=`(Epic 11)가 이미 이 파라미터를 지원한다 — 프론트엔드가 숫자를 어떻게 얻는지만 바뀐다.
- 투자자 선택이 바뀌면 증권계좌 드롭다운은 즉시 초기화하고 새로 로드한다(연쇄 상태 관리, `useEffect` 또는 TanStack Query의 `enabled` 옵션 활용).
- 증권계좌를 선택하지 않고 투자자만 선택한 상태로도 조회 가능해야 한다(기존 "비워두면 전체 조회" 동작 유지 — `docs/qa/accessibility-report.md`에 이미 캡처된 힌트 텍스트 `"비워두면 전체 조회"`가 각 드롭다운에도 유지되거나 "전체" 옵션으로 표현).
- 등록된 투자자/증권계좌가 0건인 사용자를 위한 빈 상태 처리(신규 가입 직후 시나리오) — "투자자를 먼저 등록하세요" 안내 + 등록 페이지 링크.

**AC 초안** (spec 단계에서 확정):
- AC-1901: 로그인 후 투자 내역 화면 진입 시 본인이 등록한 투자자 목록이 드롭다운에 표시된다
- AC-1902: 투자자 선택 시 그 투자자의 증권계좌 목록이 자동 로드된다
- AC-1903: 증권계좌 선택 없이 투자자만 선택해도 전체 조회가 동작한다
- AC-1904: 등록된 투자자가 0건이면 안내 메시지 + 투자자 등록 링크를 표시한다
- AC-1905: 기존 페이지네이션·빈 상태·에러 처리는 그대로 유지된다(회귀 없음)

**검증 명령**: `docs/guide-frontend.md` 2절 그대로 (`pnpm run test/lint/typecheck/format:check/build`)

---

## SF-2: 공모주 일정 달력 뷰 (신규 페이지, Epic 17 의존)

**범위**: 청약/상장/환불일이 모두 표시되는 달력 뷰를 신설한다. **사용자 명시 결정에 따라 계정 소유권과 무관한 전역 데이터 뷰다** — 로그인 사용자가 증권계좌를 몇 개 등록했는지와 무관하게 전체 공모주 일정을 보여준다.

**선행 조건**: Epic 17의 `GET /ipo-schedule`(가칭) 엔드포인트가 main에 병합돼야 착수 가능. **이 SF는 Epic 17 완료 전까지 breakdown만 유지하고 실제 구현은 시작하지 않는다.**

**산출물 파일 경로** (FSD, 가칭):
- `ui/src/pages/IpoCalendarPage/IpoCalendarPage.tsx` (+ `.module.css`) — 신규 페이지
- `ui/src/widgets/IpoCalendar/IpoCalendar.tsx` (+ `.module.css`) — 월별 그리드 캘린더 위젯(순수 CSS 그리드, 외부 라이브러리 미사용 — 프로젝트 관례)
- `ui/src/features/ipo-calendar/useIpoSchedule.ts` — Epic 17 API 호출 훅
- `ui/src/App.tsx` — `/ipo-calendar`(가칭) 라우트 추가

**특이사항**:
- 계정 소유권 검증이 필요 없는 화면이므로, 로그인 없이도(또는 로그인 후 투자자/계좌 등록 여부와 무관하게) 접근 가능해야 한다 — 라우트 가드 설계 시 이 점을 명시적으로 반영한다.
- 사용자 본인이 실제로 청약한 종목은 하이라이트 표시를 고려할 수 있다(선택 사항, Epic 17 응답에 사용자 청약 여부 플래그가 없다면 프론트에서 기존 구독 목록과 대조해 계산).
- 이벤트 타입(청약시작/청약마감/배정일/상장예정일/환불일)마다 시각적 구분(색상·아이콘)을 둔다.
- 날짜 클릭 시 해당 날짜의 이벤트 상세를 모달 또는 인라인 확장으로 보여준다(별도 페이지 이동은 지양 — SPA 흐름 유지).

**AC 초안**:
- AC-1910: 달력에 전체 공모주 일정이 계정 소유권과 무관하게 표시된다
- AC-1911: 청약시작/마감, 배정일, 상장예정일, 환불일이 서로 다른 시각적 표시로 구분된다
- AC-1912: 날짜에 이벤트가 없으면 빈 칸으로 표시된다(에러 아님)
- AC-1913: 월 이동(전월/다음월) 네비게이션이 동작한다

---

## SF-3: 대시보드 월별 탭 UX 개선

**범위**: 신규 페이지를 만들지 않고 **기존 대시보드(Epic 12/16) 월별 탭을 개선**한다. `docs/research/posthoc-ux-redesign.md` 4.3절 결론에 따라, 별도의 "월별 손익 확인 페이지"는 만들지 않는다.

**산출물 파일 경로**:
- `ui/src/features/dashboard/PeriodSelector/PeriodSelector.tsx` (이미 Epic 16 SF-5에서 존재) — 전월/다음월 이동 버튼 추가
- `ui/src/pages/DashboardPage/DashboardPage.tsx`, `ui/src/features/dashboard/useDashboardByPeriod.ts` — 현재 선택된 월 표시를 더 크고 명확하게(제목 수준으로), 초기 진입 시 이번 달이 기본 선택되도록 보정

**특이사항**:
- API 변경 없음. `GET /dashboard/by-period?unit=month&year=`가 이미 존재(Epic 12 SF-4).
- "이번 달 수익금을 한눈에 확인"이라는 사용자 요구사항을 달성하려면, 페이지 진입 즉시(추가 클릭 없이) 이번 달 데이터가 보여야 한다 — 현재 기본 탭이 "전체"인지 "월별"인지 재검토하거나, 월별 탭 진입 시 기본값을 현재월로 고정한다.

**AC 초안**:
- AC-1920: 월별 탭 진입 시 기본으로 이번 달 데이터가 표시된다
- AC-1921: 전월/다음월 버튼으로 월을 이동할 수 있다
- AC-1922: 현재 선택된 월이 화면에 명확히(제목 수준 폰트) 표시된다

---

## SF-4: 공통 레이아웃 컴포넌트(`MainLayout`) 도입

**범위**: `docs/qa/accessibility-report.md`의 `landmark-one-main`(12건)·`region`(다수) 위반을 근본 해결한다. 모든 페이지를 감싸는 공통 레이아웃을 만들어 `<header>`+`<main>`(+선택적 `<footer>`) 시맨틱 구조를 강제한다.

**산출물 파일 경로**:
- `ui/src/shared/components/MainLayout/MainLayout.tsx` (+ `.module.css`) — 신규
- `ui/src/App.tsx` — 모든 `<Route>`의 `element`를 `<MainLayout><실제페이지/></MainLayout>`로 감싸거나, React Router의 레이아웃 라우트 패턴(`<Route element={<MainLayout />}><Route .../></Route>`)으로 리팩터링
- 각 페이지 컴포넌트(`ui/src/pages/**/*.tsx`) — 기존에 자체적으로 최상위 `<div>`로 감싸던 것을 `MainLayout`의 `<main>` 안에 들어가는 콘텐츠로만 남기고, 페이지 자체의 중복 래퍼는 제거

**특이사항**:
- **이 SF는 가장 넓은 범위의 파일을 건드린다** (모든 페이지 컴포넌트). SF-1·SF-3가 먼저 해당 페이지들을 재작성한 뒤, SF-4가 마지막으로 레이아웃만 감싸는 순서를 권장한다(반대 순서면 SF-4가 만든 구조를 SF-1/SF-3가 다시 건드려야 해 diff가 꼬인다).
- 로그인 여부에 따라 헤더의 네비게이션 항목이 달라질 수 있다(로그인 전: 로그인/회원가입, 로그인 후: 대시보드/투자내역/로그아웃 등) — 이 SF에서 전역 네비게이션 UX까지 설계할지, 최소한의 랜드마크 구조만 넣고 네비게이션은 후속으로 미룰지는 spec에서 결정한다(범위 팽창 방지 — 최소 범위는 랜드마크 구조 수정만).
- `axe-core` 재스캔으로 `landmark-one-main`/`region` 위반이 실제로 줄었는지 검증한다(`tools/accessibility-scan/scan.mjs` 재사용).

**AC 초안**:
- AC-1930: 모든 페이지가 정확히 1개의 `<main>` 랜드마크를 갖는다
- AC-1931: `axe-core` 재스캔 시 `landmark-one-main` 위반이 0건이다
- AC-1932: 기존 페이지 콘텐츠·기능이 레이아웃 변경으로 깨지지 않는다(전체 회귀 테스트 통과)

---

## 참고

- **문서 의존성**: `docs/research/posthoc-ux-redesign.md`, `docs/qa/accessibility-report.md`
- **spec/architecture 생략 사유**: 이 Epic은 백엔드 변경이 없고(SF-2 제외) 기존 컴포넌트 패턴을 그대로 따르는 프론트엔드 리팩터링 위주라, Master가 research 직후 이 breakdown으로 바로 넘어왔다. SF-2(Epic 17 의존)만 착수 시점에 Epic 17의 spec/architecture와 계약을 맞추는 별도 확인이 필요하다.
- **검증 명령**: `docs/guide-frontend.md` 2절(`pnpm run test/lint/typecheck/format:check/build`) 그대로 사용. SF-4는 추가로 `node tools/accessibility-scan/scan.mjs` 재실행 결과를 완료 조건에 포함한다.
