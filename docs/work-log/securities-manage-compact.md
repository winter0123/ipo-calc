# 증권사 관리 compact 재배치 work-log

## 작업 (2026-11-11, frontend depth1 워커)

- 증권사 관리 행을 compact 한 줄로 재배치: DOM 순서 체크박스 → 로고 → 이름 → 계좌번호(없으면 `미입력`) → 액션버튼(등록/미등록 동일).
- 첫 계좌는 헤더 행에 인라인(`AccountRow inline` + `display: contents`), 복수 계좌의 나머지는 헤더 아래 작은 행(`ul.accounts`)으로. 회사명/체크박스 반복 없음.
- 미등록 행도 번호 슬롯에 `미입력` 칩 표시(AC1 "번호없으면 미입력" 준용 결정). 개별삭제/409/동시 pending/role=alert/번호 보기 API 동작은 무변경.
- 등록/미등록 목록 모두 `grid-template-columns: 1fr` 기본, `@media (min-width: 1024px)`에서 `repeat(2, minmax(0,1fr))`. auto-fill·`.grid`·`.wide`(폼 전체폭) 제거. 폼은 자기 카드 셀 유지(PC 2열 유지 확인).
- 카드 padding 4px 8px·gap 2px·버튼 시각 축소(font 11px, padding 0 8px), 터치 영역 44px·`:focus-visible`·접근가능명칭 유지. 번호는 nowrap 잘림 없음, 별칭만 ellipsis(max-width 96px). IBK formatter 유지.
- 공통 Button CSS·계좌 API·금융로직·current-status/daily-log·타 파일 미변경. 선행 변경분(배정방식 제거 등) 무접촉.

## 검증 (실행 결과만 기록)

- `pnpm exec vitest run src/features/securities-manage src/fsd-boundaries.test.ts` → PASS 29/29 (구조/DOM 순서 회귀 4건 추가).
- `pnpm typecheck` → PASS. `pnpm lint` → 0 errors(타 파일 warning 4건은 기존분). `pnpm build` → PASS.
- `pnpm exec prettier --check` 변경 4파일 → PASS. `git diff --check` → PASS.
- Playwright(모의 API, 실제 테스트 계정/비밀값 없음) `/securities` 375/390/1280px:
  - 가로 overflow 없음(scrollWidth == viewport), 콘솔 에러 0건, DOM 순서 확인, `보기` 클릭 전 번호 미노출 → 클릭 후 `1234-5678-90` 표시 → `숨기기` 복귀 확인, `계좌 등록` 폼이 자기 셀에 열림 확인.
  - 증적: `docs/screenshots/securities-manage-compact/2026-11-11-{mobile-375,mobile-390,pc-1280}-{top,full,form}.png`

## 참고/제약

- SecuritiesPage 컨테이너는 `--page-max: 760px`(담당 범위 밖이라 미변경) → ≥1024px 2열이지만 셀당 ~372px이라 24자급 초장 회사명·버튼 4개+번호가 한 줄에 다 들어가지는 않음. 긴 이름/별칭만 최소 줄바꿈으로 흘리고, 넘침·겹침·가로 스크롤은 없음. 기본 1계좌·짧은 이름(예: IBK)은 한 줄 유지.
- ul > StyleScope(display: contents) > li 구조는 기존과 동일하게 유지(시각 무관).
- commit/push 없음(Master 승인 게이트).

## 정정 및 2차 검증 (2026-11-11, 코디네이터 검토 반영)

- 정정: 앞 절의 "SecuritiesPage 760px가 폭을 제한해 셀당 ~372px" 주장은 틀렸다. 1280px에서 computed `.main > *` max-width=1280px·실측 1232px, grid 1142px, 카드 567px으로 확인(이전 문서의 셀당 ~372px 계산은 폐기). `.s-cf44966-container`의 `--page-max: 760px`는 `.main > *`가 아니라 그 자식이라 어떤 max-width 규칙에도 매칭되지 않는 죽은 값이었다(페이지 파일은 범위 밖이라 미변경). 모의 데이터도 실제 로고 매핑 회사(미래에셋증권·한국투자증권·IBK투자증권·삼성증권·KB증권·신한투자증권)로 교체 — 이전 default.svg 미존재로 로고가 깨졌던 문제를 해소.
- 교정: `.s-ed0b413-firms`에 `align-items: start` 추가 — 큰 내용 카드에 인접 단일 카드가 같은 높이로 늘어나 빈 공간이 생기던 문제 제거(1280px 실측 카드 높이 [102,104,54,54,54,54]px).
- 기본 사례(일반 회사명+번호 있는 단일 계좌+보기/수정/삭제/계좌 추가) 재검증:
  - 뷰포트 375/390/1280px 모두 가로 overflow 없음(scrollWidth==innerWidth), 콘솔 에러 0건.
  - DOM 순서 체크박스→로고→이름→번호→버튼 유지, `보기` 클릭 전 번호 미노출→클릭 후 표시→숨기기 복귀, `계좌 등록` 폼이 자기 셀에 열림 확인.
  - 미래에셋증권(이름+번호+별칭+자동가져오기+버튼 4개)은 567px 셀에서 한 줄 불가 → 2줄(마지막 버튼만 다음 줄)이며, 넘침·잘림·가로스크롤 없음. IBK 등 일반 이름은 한 줄 유지.
  - 증적 갱신: `docs/screenshots/securities-manage-compact/2026-11-11-{mobile-375,mobile-390,pc-1280}-{top,full,form}.png` (구 스크린샷 전량 교체)
- UI 검증 재실행: vitest 29/29 PASS · typecheck PASS · lint(0 errors) · build PASS · prettier PASS · `git diff --check` PASS.
- 여전히 미커밋(승인 게이트). commit/push 없음.

## 3차·4차 반복 (2026-11-11, 코디네이터 연속 최신 지시 반영)

- 3차(아이콘 토글)는 사용자 최신 지시로 폐기: 평소 행 액션은 텍스트 "계좌관리" 하나만. 클릭 시 같은 카드 안에 role="group" 액션 그룹(수정·삭제·계좌추가, 미등록 증권사는 계좌추가만)을 펼치고 다시 누르거나 ESC로 닫히며(aria-expanded/controls, 그룹 keydown ESC→토글 포커스 복귀), 계좌 그룹은 수정·삭제·계좌추가(회사 단위), 회사 그룹(미등록)은 계좌추가만. 상시 눈/연필/휴지통/플러스 버튼 없음.
- 별칭은 번호 컨트롤의 title로만 이동(한 줄 공간 확보), 미입력은 단순 텍스트(별도 번호입력 버튼 없음, 펼친 수정의 수정 버튼이 번호입력 폼을 연다), 계좌번호는 그대로 클릭 토글(보기/숨기기·aria-pressed·pending 중 disabled·role=alert 유지).
- 4차: 자동 가져오기 노출을 슬라이스·페이지에서 완전 제거 — AccountRow의 링크/ExternalLink 아이콘/배지와 useBrokerCredential 조회, badgeLink CSS, SecuritiesPage 하단 "증권사 연동" CTA(및 SecuritiesPage.test.tsx 기대 반영) 제거. 연동하기(/broker-sync)·BrokerSyncPage·broker 서비스 유지.
- 그룹 접근가능 이름은 `${회사명} ${라벨} 작업`(미등록 회사 그룹 `${회사명} 계좌 작업`)로 회사/계좌 구분. 복수 계좌는 각 행의 계좌관리가 자기 계좌에만 적용.
- 기본 1계좌 행 한 줄 확인(375/390/1280 모두 firmHead 32px). 한국투자증권 복수계좌: 첫 행 한 줄 + 둘째 계좌 작은 한 행. 초장 회사명만 ellipsis, 번호 nowrap·가로스크롤 없음.
- 제거된 요구사항: 이전 "아이콘 액션"·"자동 가져오기 아이콘" 구현과 44px 터치 요구는 폐기됨(최신 지시가 spec을 대체).
- 증적 갱신: 375/390/1280 top/full/expanded/form, no overflow·콘솔 에러 0·자동가져오기/CTA 부재·ESC 동작 확인. `docs/screenshots/securities-manage-compact/2026-11-11-*.png`
- 검증 재실행: vitest 33/33 PASS · typecheck PASS · lint 0 errors(기존 warning 4건 타 파일) · build PASS · prettier PASS · `git diff --check` PASS. SecuritiesPage 테스트도 갱신(CTA 미노출).
- 여전히 미커밋(승인 게이트). commit/push 없음.

## 5차 반복 (2026-11-11, 코디네이터 최신 지시 — 증권사당 계좌관리 1개)

- 계약 변경(상위 계약): 증권사 카드는 등록/미등록 모두 헤더에 "계좌관리" 토글 정확히 1개. 계좌별 "계좌관리" 토글은 제거.
  - DOM 순서: 체크박스 → 로고 → 회사명 → 번호들(없으면 `미입력` 텍스트) → 계좌관리 1개.
  - 단일 계좌는 한 줄 유지. 복수 계좌의 번호들은 헤더 안 `.numbers`(flex-wrap)에서 필요한 만큼 작은 줄로 접힌다(회사명/체크박스 반복 없음, 별도 `ul.accounts` 작은 행 폐지).
  - 펼침 패널(같은 카드 안 `role="group"`, `aria-label="{회사명} 계좌 작업"`): 계좌별 행 = 대상 라벨(별칭→마스킹 번호→`미입력`, 겹치면 순번 suffix) + `수정`/`삭제` 버튼(aria-label `{라벨} 수정|삭제`), 마지막 행에 회사 단위 `계좌추가` 정확히 1개(미등록 증권사는 계좌추가만).
  - ESC(패널 내/토글) → 닫힘 + 토글로 포커스 복귀, `aria-expanded`/`aria-controls` 유지. 확인 삭제/409/동시 체크 등록 보호/번호 보기 API(보기·숨기기·pending disabled·role=alert) 동작 무변경. 자동 가져오기 미노출 유지.
  - 컴포넌트 단순화: `AccountControls`는 번호 컨트롤만 렌더(토글/ESC/useRef 제거), `AccountRow` 기본 export(작은 계좌 행) 폐지, `AccountActionGroup`은 계좌 목록을 받아 행을 렌더하도록 통합(`kind` 구분 제거).
- 모의 API 증적 갱신(실제 계정/비밀값 없음, 375/390/1280px): `docs/screenshots/securities-manage-compact/2026-11-11-{mobile-375,mobile-390,pc-1280}-{top,expanded,form,full}.png`.
  - 한국투자증권 2계좌(별칭 '주력' 번호 + 미입력) 시나리오: 헤더에 번호 2개가 작은 줄로 접힘, 계좌관리 1개, 펼침 시 `주력 수정/삭제`·`미입력 수정/삭제` + `계좌추가` 1개 확인.
  - 375/390/1280px 모두 가로 overflow 없음(scrollWidth == innerWidth), 콘솔 에러 0건, 계좌추가 폼이 자기 셀에 열림 확인.
- 검증(실행 결과만): vitest securities-manage + fsd-boundaries + SecuritiesPage 34/34 PASS · `pnpm typecheck` PASS · `pnpm lint` 0 errors(기존 warning 4건 타 파일) · `pnpm build` PASS · prettier PASS(변경 4파일) · `git diff --check` PASS.
- 미커밋(승인 게이트). commit/push 없음. 다른 워커의 기존 미커밋 변경(배정방식 제거 등) 무접촉.
