# 증권사 관리 행 compact 재배치 Breakdown (단일 SF)

## 배경/현재 동작 조사

- `SecuritiesManage.tsx`가 증권사별 카드를 렌더한다. 카드(`.firm`)는 `grid-template-columns: minmax(220px,.8fr) minmax(0,1.2fr)` 2열 그리드로 헤더행(로고·이름·체크박스·등록버튼)과 계좌목록(`ul.accounts`, 별도 큰 행)을 분리해 보여 준다.
- DOM 순서가 로고→이름→체크박스→버튼이라 AC의 체크박스 선두와 다르다. 등록 목록은 flex column, 미등록은 `auto-fill, minmax(240px,1fr)` grid라 ≥1024px에서 3열 이상 나올 수 있다. 폼이 열리면 `.wide`로 `grid-column: 1/-1`을 써 2열을 깬다.
- 버튼은 공통 `Button`(min-height 44px) + slice CSS로 padding 10px/font 12px/min-height 40px을 덮어쓴다. 번호 보기 API는 `useRevealAccountNumber`(사용자 버튼 때만)이다.
- 스타일은 `SecuritiesManage.styles.ts`의 `StyleScope` prefixed 클래스, 토큰(`--space-*`, `--border`, `--ink-*` 등) 재사용. 공통 Button CSS·계좌 API·금융로직은 수정 금지.

## SF — compact 한 줄 행 + 2열/1열 그리드

- 범위: `SecuritiesManage.tsx`, `AccountRow.tsx`, `SecuritiesManage.styles.ts`, `SecuritiesManage.test.tsx`(구조/회귀), 이 문서와 work-log.
- 비범위: 계좌 API·금융로직·DB·의존성·공통 Button CSS·다른 제품 파일·current-status/daily-log. 배정방식 제거·매도등록 선행변경(기존 미커밋 변경)은 보존만.
- 계약:
  1. DOM 순서(등록/미등록 동일): 체크박스 → 로고 → 이름 → 계좌번호들(없으면 `미입력`) → 계좌관리 토글(증권사당 정확히 1개). 단일 계좌는 헤더 한 줄 유지. 복수 계좌의 번호들은 헤더 안 `.numbers`에서 필요한 만큼 작은 줄로 접힌다(회사명/체크박스 반복 금지, 계좌별 관리 토글 없음).
  1-정정(2026-11-11, 5차 지시): 기존 "첫 계좌는 헤더 행에 인라인, 나머지는 헤더 아래 `ul.accounts` 작은 행 + 계좌별 계좌관리" 계약은 폐기. "증권사당 계좌관리 정확히 1개"가 상위 계약이다. 펼침 패널은 같은 카드 안 `role="group"`에서 계좌별 행(대상을 별칭→마스킹 번호→`미입력` 순으로 표시, 겹치면 순번)에 `수정`/`삭제` 버튼을 두고, 회사 단위 `계좌추가`는 정확히 1개. 미등록 증권사(계좌 없음)는 계좌추가만. aria-label은 `{라벨} 수정|삭제`, `{회사명} 계좌추가`.
  2. min-width 1024px에서 등록/미등록 모두 정확히 2열, ≤760px 1열, 761~1023px 1열(가독성) 허용. auto-fill 금지. 폼 열림은 자기 카드 셀 안에 유지(2열 유지).
  3. 카드 padding/gap/버튼 padding 축소, 핵심 번호는 nowrap·잘림 금지, 긴 별칭/복수 계좌만 최소 줄바꿈. 375/390/1280px에서 넘침/겹침 없음.
  4. 버튼은 시각만 작게, 터치영역 44px·:focus-visible·접근가능명칭 유지. 체크박스를 DOM 첫 순서로. IBK formatter(`formatSecuritiesFirmName`) 계속 사용.
  5. 체크등록/동시 pending 독립정산/해제 확인·409/복수계좌 개별삭제 안내/번호입력/마스킹 보기숨기기/수정/계좌추가/자동가져오기 링크/role=alert — 동작 동일. 번호 보기는 버튼 조작 때만 API 호출.
- 검증: `pnpm exec vitest run src/features/securities-manage src/fsd-boundaries.test.ts`, `typecheck`, `lint`, `build`, prettier --check, `git diff --check`. 375/390/1280px Playwright 모의API 스크린샷 시도(불가 시 NOT_RUN 사유).
- 완료 보고: PASS/FAIL/NOT_RUN/BLOCKED로만 기록, worker_done에 수정파일·증적·남은 사항.
