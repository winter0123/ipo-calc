# 작업 로그: 웹 조회·대시보드 (web-inquiry-dashboard, Epic 16)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 최신화, 마지막 Epic)

`docs/research/web-inquiry-dashboard.md`는 Epic 10/11/12 확정 전 초기
조사 결론(총투자금 선택 옵션 등)을 그대로 인용하고 있고, 반응형
브레이크포인트도 기존 프론트 Epic(13/14/15)과 다른 값을 제안한다.
이미 확정된 백엔드/프론트 spec 기준으로 정렬한다.

### D-W16-001: 총투자금 정의는 이미 확정됨 (선택 옵션 없음)
[[profit-calculation]] D-P-005, [[dashboard-aggregation-api]] D-D-001에
따라 배정금액 단일 기준으로 이미 확정되었다. "사용자가 투자금 기준을
선택하는 옵션"은 설계하지 않는다.

### D-W16-002: 반응형 브레이크포인트는 기존 프론트 Epic과 동일하게 640/768/1024 사용
조사 문서가 제안한 `<768px`/`768-1024px`/`>1024px`이 아니라, Epic
13/14/15에서 이미 확립한 `640px`(모바일)/`768px`(태블릿)/`1024px`(데스크톱)
min-width 기준을 그대로 사용해 프로젝트 전체 일관성을 유지한다.

### D-W16-003: 차트 라이브러리는 도입하지 않는다 (카드/테이블로 지표 표시)
로드맵 Epic 16 제외 범위("모바일 앱, 실시간 주가, 고급 포트폴리오
분석, 자동 증권사 연동, 투자 추천, 평가손익의 실시간 시세 연동")와
프로젝트의 MVP/개인용 도구 성격을 고려해, 지표는 **숫자 카드와
테이블**로 표시한다. 막대/선/파이 차트 등 시각화 라이브러리를 새로
도입하지 않는다 — 다른 어떤 Epic도 차트 라이브러리 설치를 확인한 바
없다.

### D-W16-004: 확정된 API 계약 (추측 금지)
- **투자 내역 목록/상세/필터**: `GET /investment-history`, `GET /investment-history/{subscriptionId}` (Epic 11)
- **대시보드 전체 집계**: `GET /dashboard/summary` (Epic 12)
- **투자자별 집계**: `GET /dashboard/by-investor` (Epic 12)
- **증권사별 집계**: `GET /dashboard/by-securities-firm` (Epic 12)
- **기간별 집계**: `GET /dashboard/by-period?unit=month|year` (Epic 12)
- 위 대시보드 API들은 이미 [[dashboard-aggregation-api]] 아키텍처에서
  Master가 계정 소유권 필터링(SecurityUtils)을 추가해뒀으므로, 프론트는
  별도 investorId 파라미터 없이 호출해도 로그인 계정 소유 데이터만
  반환된다(단, 특정 투자자로 좁히고 싶으면 Epic 11처럼 investorId
  쿼리 파라미터로 필터 가능).

### D-W16-005: 화면 구성 — 투자 내역 화면 + 대시보드 화면 2개
로드맵의 "목록·상세·필터", "투자자별·증권사별·기간별 조회", "전체·
투자자별·증권사별 대시보드", "월별·연도별 지표"는 실질적으로 두 화면에
담긴다:
1. **투자 내역 화면**: Epic 11 API 기반 목록/상세/필터(투자자·증권계좌·공모주)
2. **대시보드 화면**: Epic 12 API 기반, 전체/투자자별/증권사별/월별/연도별 집계를 탭 또는 토글로 전환 (Epic 15의 ProfitViewPage 탭 패턴 재사용)
새로운 화면을 추가로 만들지 않는다.

### D-W16-006: 필터 UI는 상단 필터 바 형태 (사이드바 아님)
Epic 14의 필터 버튼 그룹 패턴(청약 목록 화면의 미배정/배정완료/전체
필터)을 재사용해, 사이드바가 아닌 화면 상단의 필터 바로 구현한다.
프로젝트 전체 UI 일관성을 위해 새로운 레이아웃 패턴을 도입하지 않는다.

### D-W16-007: 빈 결과 처리는 기존 패턴 재사용
"해당 조건에 해당하는 기록이 없습니다" 안내 메시지 + 필터 초기화
버튼을 제공한다(조사 결론 유지). 별도의 특수 컴포넌트 없이 기존
`ErrorMessage`/빈 상태 표시 패턴을 재사용한다.

이 결정들은 `docs/spec/web-inquiry-dashboard.md` /
`docs/architecture/web-inquiry-dashboard.md` 작성 태스크 지시에
반영되었다. **이 Epic이 완료되면 로드맵 16개 Epic 전체의 Phase 0가
완료된다.**

## 2026-09-28 — SF-2 구현 완료 (frontend worker)

### 작업 개요
Epic 16 SF-2: 투자 내역 화면의 목록 표(`InvestmentHistoryTable`)와 상세 확장(`InvestmentHistoryDetail`) 위젯, TanStack Query 훅(`useInvestmentHistory`, `useInvestmentHistoryDetail`) 구현. SF-1(FilterBar, EmptyState, InvestmentHistoryPage 기본 배치)은 main에 이미 병합 완료(PR #168). 백엔드 API(GET /investment-history, GET /investment-history/{subscriptionId})는 이미 구현·병합 완료이므로 **실연동**으로 개발.

### 구현 파일
- **신규**: `ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx` — 목록 행 렌더링, 상세 확장/접기, 선택된 행 하이라이트
- **신규**: `ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryTable.module.css` — 테이블 스타일, 반응형(640/768/1024px)
- **신규**: `ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx` — 단일 행, 키보드 접근성(Tab/Enter/Space, `role="row"`, `tabIndex=0`)
- **신규**: `ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx` — subscription + allocation + sales[] 중첩 표시
- **신규**: `ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryDetail.module.css` — 상세 정보 스타일, 중첩 그룹화
- **신규**: `ui/src/features/investment-history/useInvestmentHistory.ts` — `queryKey: ['investmentHistory', filters]`, `staleTime: 0`
- **신규**: `ui/src/features/investment-history/useInvestmentHistoryDetail.ts` — `queryKey: ['investmentHistoryDetail', subscriptionId]`, `enabled: !!subscriptionId`, 404 에러 처리
- **수정**: `ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx` — SF-1 placeholder를 `InvestmentHistoryTable`로 교체, `useInvestmentHistory` 훅 통합
- **수정**: `ui/src/pages/InvestmentHistoryPage/InvestmentHistoryPage.test.tsx` — SF-2 반영하여 테스트 업데이트

### 실연동 검증
- `GET /investment-history`(목록, PR #153)와 `GET /investment-history/{subscriptionId}`(상세, PR #162)는 **모두 구현·병합 완료**이므로 mock이 아닌 실 API로 개발
- `entities/subscription/types.ts`의 `InvestmentHistoryItem`(allocation, sales[], remainingQuantity 포함), `AllocationSummary`, `SaleSummary` 타입 재사용
- `entities/subscription/api-client.ts`의 `getInvestmentHistory`, `getSubscriptionDetail` 함수 재사용
- `InvestmentHistoryDto.java`의 실제 필드(`id`, `subscribedAt`, `ipoId`, `ipoName`, `ipoFixedPrice`, `subscriptionQuantity`, `subscriptionStatus`, `allocation`, `sales`, `remainingQuantity`)와 프론트 타입 정합 확인 완료
- `investorName`/`securitiesAccount`는 `GET /investment-history` 응답에 해당 필드가 없으므로 표시하지 않음(백엔드 계약 확장 필요 시 별도 조회)

### 접근성/반응형/코드 품질
- `remainingQuantity`에 `aria-readonly="true"` 적용
- 404 에러 메시지에 `aria-live="polite"` 적용
- 테이블 행 키보드 네비게이션(Tab, Enter, Escape) 지원
- 640px/768px/1024px 브레이크포인트 반응형 구현
- ESLint/Prettier/TypeScript strict 모두 통과

### 검증 결과
- `pnpm run test`: 15 passed, 118 passed ✅
- `pnpm run lint`: 0 errors, 1 warning(기존) ✅
- `pnpm run typecheck`: ✅
- `pnpm run format:check`: 내 파일 모두 통과 ✅
- `pnpm run build`: ✅

### 참고
- `FilterBar.tsx`, `EmptyState.tsx`, `useInvestmentHistoryFilters.ts`는 SF-1 산출물로 재사용(수정 금지)
- `ConfirmDialog`는 기존 재사용(중복 생성 금지)
- `shared/components/Button/Input/FormField/ErrorMessage/Spinner`는 존재하지 않으므로 인라인 패턴 사용
