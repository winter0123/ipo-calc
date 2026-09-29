# 프로젝트 현재 상황 (Master 소유, 계속 최신화)

> 마지막 갱신: 2026-09-29 세션 종료 시점(5차 재개 세션). **로드맵 16개 Epic 전체 구현 완료 + 알려진 부채 전부 해소 + 스코프 확장(Epic 17~19)의 spec 2건과 첫 구현 3건(E19 SF-1, E17 SF-1, ipos.corp_code) 병합 완료.** 워크스페이스 경로는 이 문서를 믿지 말고 `orca worktree list --json`으로 재확인한다.

## 세션 요약 (2026-09-29, 5차 재개 — Epic 17~19 spec + 첫 구현 3건)

세션 범위를 "Epic 19 SF-1 + Epic 17 SF-1, spec 먼저 → 승인 후 구현"으로 정하고 PR 5건(#190~#194)을 병합했다. 상세는 `docs/daily-log/2026_09_29.md`(마지막 섹션)를 진실 소스로 한다.

- **spec 2건 병합**: Epic 19(#190), Epic 17(#191). Epic 17 spec은 실측 API 사실·보안 요건(키가 URL 쿼리이므로 URI 로깅 금지, RestClient 타임아웃, 조회 전용)을 반영했고, 기존 DB 규칙(V5/V6)에 맞춰 M-13을 정정했다.
- **Epic 19 SF-1 병합(#192)**: 투자 내역 필터를 숫자 ID 입력에서 투자자→증권계좌 연쇄 드롭다운으로 교체. 워커 1차 결과의 계좌 초기화 버그를 Master가 잡아 재작업.
- **Epic 17 SF-1 병합(#193)**: OpenDART DS001·DS006 클라이언트. 워커 1차 결과에서 오류 응답을 삼키는 처리 순서 버그·가짜 테스트 픽스처·타임아웃 누락을 Master가 코드 리뷰로 잡아 재작업.
- **ipos.corp_code 병합(#194)**: V11 마이그레이션(nullable + ACTIVE 한정 부분 유니크), `Ipo.corpCode`, 조회 메서드. 자동 동기화 레코드의 식별 키로 사용자가 승인한 결정이며, spec M-13에 반영(`0d38ddb`).
- **dev DB 정리**: 공유 dev Postgres에 누수돼 있던 `securities_firms` 1행이 clean main에서도 테스트 2건을 깨뜨리고 있었다. 참조 없음을 확인하고 사용자 승인 후 그 1행만 삭제.
- **운영 변경**: OpenCode 워커는 `OPENCODE_CONFIG=/Users/joy/.config/opencode/orca-worker.json`(검증·읽기 허용, git 변경·비밀 파일·`rm -rf` 거부)으로 띄운다. 워커 완료 판단은 `worker_done` 대기가 아니라 터미널 유휴 여부(`esc interrupt` 유무)와 파일·diff 변경을 직접 확인해서 한다(워커가 `worker_done` 없이 끝내거나 뒤늦게 보내는 일이 잦음). `worker-start`에는 `--worktree path:<절대경로>`가 필수다.

**야간 후속**: E19 SF-3(#195), SF-4(#196), 접근성 color-contrast 교정(#197)을 2026-09-30 사용자 승인 후 병합했다. E17 SF-2는 ustockplus가 헤더를 붙여도 로그인 필요(`LOGIN_REQUIRED`)로 응답해 착수 게이트 (c)가 충족되지 않아 건너뛰었다. axe 재스캔 결과 12개 화면 위반 0건(부채 2번 참고).

**다음 착수 후보**: Epic 17 SF-3(동기화 서비스)는 아래 미결정 항목을 사용자와 정한 뒤 시작한다.

## 세션 요약 (2026-09-29, 4차 재개 후속 — 스코프 확장: Epic 17~19)

사용자가 신규 기능 4건(공모주 일정 자동 동기화·증권계좌 선택형 조회·월별 달력/손익·증권사 조회전용 자동 채움)을 요청했고, "조사 먼저, 계획은 그다음"이라는 명시적 지시에 따라 Phase 0(research→roadmap 문서화→breakdown)까지 진행했다. **코드는 아직 한 줄도 작성하지 않았다.** 상세는 `docs/daily-log/2026_09_29.md`(후속 섹션)를 진실 소스로 한다.

- **Epic 17(공모주 청약 일정 자동 동기화)**: 데이터소스를 사용자와 실시간으로 검증해 최종 확정 — **OpenDART DS006(지분증권) 기본 + `api.ustockplus.com`(네이버 u+stock 비공식 백엔드) 보완**. `docs/research/ipo-schedule-sync.md` 7절에 조사 과정·확정 근거 전체 기록.
- **Epic 18(증권사 조회 전용 연동, 1개사 파일럿)**: 한국투자증권(KIS Developers) 대상. **원칙: 어떤 브로커·어떤 상황에서도 주문 API는 요청·사용하지 않는다.** research가 "높음" 영향도 미확인 4건(체결 필드명, 조회전용 권한 옵션, 토큰 갱신, 계좌번호 파라미터)을 남겼고, 이 4건이 breakdown의 착수 전 게이트로 지정됨 — `docs/research/broker-readonly-api-pilot.md`, `docs/breakdown/broker-readonly-api-pilot.md`.
- **Epic 19(사후 등록 UX/UI 개선)**: 조사 결과 예상보다 범위가 좁음(폼은 이미 대부분 드롭다운) — 실제 문제는 투자내역 필터바와 `<main>` landmark 부재로 좁혀짐. SF-2(공모주 달력)는 Epic 17 완료 전까지 구현 보류.
- **문서화**: `docs/roadmap.md` 1.1절(스코프 확장 결정) 신설, 5절 Epic 마스터 표에 E17~E19 추가(+ E1~E16 상태 정정), 6절에 3개 Epic 실행 카드 신설, 4절 현재 기준선 전면 재작성. `docs/breakdown/{ipo-schedule-sync,broker-readonly-api-pilot,posthoc-ux-redesign}.md` 3건 신규 작성(총 15개 SF) — 통상 breakdown은 planning 워커 산출물이나, 이번엔 실시간 조사 맥락이 Master에게만 있어 예외적으로 직접 작성.
- **`CLAUDE.md`(gitignored, 로컬 전용) 갱신**: "자동 증권사 연동 범위 밖" 조항을 사용자 승인으로 해제하고 프로젝트 개요 문단을 스코프 확장 반영으로 수정. git으로 추적되지 않으므로 실제 스코프 결정의 정본은 `docs/roadmap.md` 1.1절이다.
- **세션 중 사고**: Master가 일본어를 한 차례 출력해 사용자가 강하게 질책 — 이후 세션 전체 한국어 전용으로 확정.

**다음 세션 착수 우선순위(제안)**: (1) Epic 19 SF-1(투자내역 계좌선택 UI) — 독립적이고 리스크 낮음, (2) Epic 17 SF-1(OpenDART 클라이언트) — 데이터소스 확정됨, (3) Epic 18은 KIS Developers 공식 GitHub(`koreainvestment/open-trading-api`) 원문 열람으로 게이트 4건 해소부터.

## 세션 요약 (2026-09-29, 4차 재개 — Epic 16 후속 부채 전량 처리)

2026-09-28 세션 종료 시 남긴 "알려진 부채"를 사용자 지시("계속 진행해")에 따라 전부 처리했다. 총 9개 PR 병합. 상세는 `docs/daily-log/2026_09_29.md`를 진실 소스로 한다.

**1차(사용자 결정 4건)**: Issue #134 잔여 범위 확인(불필요 결론) / 투자 내역 API에 investorName·securitiesFirmName 추가(PR #180) / shared/components 공용 컴포넌트 5종(PR #179) / CostRegisterForm 이동 수정 / 화면 실제 표시(PR #181).

**2차(typecheck no-op 발견·수정)**: `pnpm run typecheck`가 solution-style tsconfig 때문에 0개 파일 검사하던 것을 발견해 `tsc -b`로 수정(PR #182), probe 파일로 독립 재현 검증.

**3차(남은 부채 3건 전부)**:
- **checkstyle 6,940→532건(95.2% 감소)**: Spotless+google-java-format 자동 포맷터 적용(PR #183). 남은 532건은 Javadoc/naming 등 자동화 불가 항목.
- **format:check 38개 파일**: prettier --write로 정리(PR #184), 전체 통과.
- **접근성 axe-core 실측 QA**: 그동안 "WCAG AA 준수"를 코드 레벨로만 주장하고 실제 검증이 0건이었던 것을 확인 후, backend를 로컬 기동(`docker compose up -d` + `bootRun`)해 실제 로그인 데이터로 12개 화면을 axe-core로 스캔. **32건 위반 발견**(critical 1, serious 9, moderate 28) — `docs/qa/accessibility-report.md` 참고(PR #186). 이번엔 발견만 하고 수정은 안 함.
- **덤: watchdog 오류 탐지 사각지대**: 사용자가 "planning 워커 죽었잖아"를 직접 지적해 watchdog의 `ERROR_PATTERNS`가 "Endpoint is unavailable"/"Upstream request failed"를 놓치고 있었음을 발견·수정(PR #185).

세션 중 Orca 앱이 3회 예고 없이 재시작/크래시됐고, `check --wait` 응답이 오래된 세대의 메시지를 반복 재전송하는 현상이 여러 번 있었다(아래 "모니터링" 절 참고) — 매번 재바인딩·재확인으로 복구, 데이터 손실 없음.

## 로드맵 진행 현황

**완료: Epic 1~16 전체.** GitHub Issue #128·#129·#130·#131·#132·#133 모두 close.

**Epic 17~19 구현 진행 중.** research(#187·#188·#189)→roadmap→breakdown→spec(E19 #190, E17 #191)까지 끝났고, 첫 구현 3건이 병합됐다: Epic 19 SF-1(#192), Epic 17 SF-1(#193), `ipos.corp_code`(#194). 남은 것: Epic 17 SF-2 이후(ustockplus 보완·동기화 서비스·스케줄러), Epic 19 SF-2(달력, Epic 17 완료 전까지 보류; SF-1·3·4 병합 완료), Epic 18 전체(KIS 게이트 4건 해소 선행). Epic 18 spec은 아직 없다.

## 알려진 부채·다음 세션 후보

1. **Epic 17 SF-3 착수 전 사용자 결정 5건**: (a) 단일 `slprc`를 밴드/확정가 중 어디에 매핑하고 희망가·확정가 중 무엇인지, (b) `sbd` 하나로 NOT NULL인 `subscriptionEndDate`를 어떻게 채울지, (c) IPO와 유상증자 구분 기준(DS001 `stock_code` 유무는 가설), (d) OpenDART 일일 호출 한도, (e) 상태 코드 011/012 의미. 이후 E17 SF-2(ustockplus 보완)·SF-3 진행. Epic 19 SF-2~4는 E17 완료와 연동, Epic 18은 KIS 공식 GitHub 원문 열람(게이트 4건) 후 spec부터.
2. **접근성 재스캔 완료(2026-09-30)**: #195~#197 병합 후 12개 화면을 axe로 재스캔해 위반 0건(이전 32건). 리포트는 `docs/qa/accessibility-report.md`에 갱신됐다. 스캔은 `tools/accessibility-scan/run-scan.sh`(실행마다 빈 포트를 새로 골라 다른 프로젝트와 충돌하지 않음)로 돌린다. 참고: 스캔 스크립트는 로그인 실패 시 가입 계정을 만들므로 dev DB에 `testscan*@example.com` 잔여 계정이 쌓인다.
3. **checkstyle 남은 532건**: Javadoc 누락(MissingJavadocMethodCheck 260, MissingJavadocTypeCheck 150 등) 위주로, 자동 포맷으로 해결 안 되고 사람이 실제로 문서를 써야 한다. 우선순위 판단 필요.
4. **E2E 시나리오 실측**: 접근성은 이번에 실측했지만, spec 문서의 E2E 시나리오(사용자 흐름 전체) 자체를 Playwright로 돌려본 적은 아직 없다.
5. **팀장(Lead) 계층 재사용 여부**: 계속 미사용 — 이번 세션도 6.0 단독으로 9개 PR + 대형 스코프 확장 문서화를 무리 없이 처리했다.

## 워크스페이스 상태 (2026-09-29 세션 종료 시점)

- `backend-4`: 마지막 브랜치 `winter0123/e17-ipo-corp-code`(병합 완료, PR #194). 다음 세션은 `origin/main`에서 새 브랜치로 시작.
- `frontend-4`: 마지막 브랜치 `winter0123/e19-sf4-main-layout`(병합 완료, PR #196). 다음 세션은 `origin/main`에서 새 브랜치로 시작.
- `planning-5-2`: E17 spec 작업 워크스페이스(PR #191 병합 완료). 경로는 `orca worktree list --json`으로 재확인.
- 이번 세션의 워커 터미널은 모두 닫았고 진행 중인 워커 dispatch는 없다. 로컬 dev Postgres(`vibe-ai-team-postgres`, localhost:5433)는 기동 중이며 공유 DB라 테스트를 병렬 실행하면 안 된다. 테스트가 남긴 잔여 행이 다른 테스트를 깨뜨릴 수 있으니 이상하면 먼저 DB 잔여 행을 확인한다.

## 모니터링·운영 방식 (2026-09-29 갱신)

- **`check --wait`의 `--timeout-ms`는 300000(5분)으로 건다(2026-09-29 세션 중 15분→5분으로 정정).** 이유: watchdog(2분 주기)이 STALL/ERROR를 감지해도 그 감지 자체는 Master를 깨우는 신호가 아니다 — Master는 `check --wait`가 실제 `worker_done`/질문/에스컬레이션을 받아야만 깨어난다. 15분(또는 그 이상)으로 걸면 워커가 멈췄는데도 아무 메시지가 없는 상태에서 Master가 최대 그 시간만큼 못 알아챌 수 있다(2026-09-29 세션에 사용자가 먼저 발견하고 지적할 때까지 5분 이상 방치된 사례 실측). 5분 타임아웃으로 깨어날 때마다 `cat /tmp/orca-worker-status.txt`로 watchdog 요약을 직접 확인하는 습관을 매 wait 종료 시점에 넣는다(타임아웃으로 빈손 반환이어도 확인한다). 토큰 절약(긴 타임아웃) vs 감지 속도(짧은 타임아웃)의 트레이드오프이며, 5분은 사용자와 상의해 정한 값이다 — 더 짧게 걸면 재시도 턴이 늘어 토큰 낭비가 재발한다.
- **Orca 앱이 예고 없이 재시작/크래시될 수 있다.** `runtime_unavailable` 반환 시: `orca open` → `graph.state == ready` 폴링 → `run-use`로 재바인딩 → `worker-show`로 dispatch 생존 확인(추측으로 abandon/retry 금지).
- **재시작 직후 `check --wait`가 이미 처리된 오래된 메시지를 계속 재전송하는 현상이 반복 관측됐다.** `--ack <오래된 delivery_id>`가 `consumer_fenced`로 실패하면, 그 delivery는 이전 세대에 속한다는 뜻이다 — 재시도하지 말고 `orca orchestration check --run <run_id> --json`(wait 없이)으로 **현재 세대의 새 delivery_id를 다시 받아** 그것으로 ack한다.
- 워커 무응답을 watchdog이 "OK"로 잘못 판정할 수 있다 — 이번 세션에 실제로 있었다(사용자가 직접 발견). watchdog의 `ERROR_PATTERNS`는 이번에 보강했지만(PR #185), 앞으로도 새로운 오류 문구가 나타나면 그때그때 추가해야 하는 한계가 있다. **watchdog "OK"를 맹신하지 말고, 뭔가 이상하면 `orca terminal read`로 직접 확인한다.**
- OpenRouter 일일 무료 한도 소진 시 워커 전체를 OpenCode Zen(`opencode/<모델>`)으로 전환한다. Zen 경로도 내부적으로 특정 OpenRouter 모델로 폴백되며 동일하게 막히는 사례가 있었다 — 다른 Zen 모델로 재교체.
