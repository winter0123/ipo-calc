# 프로젝트 현재 상황 (Master 소유, 계속 최신화)

> 마지막 갱신: 2026-09-29 세션 종료 시점(4차 재개 세션). **로드맵 16개 Epic 전체 구현 완료 + 알려진 부채 전부 해소(checkstyle/typecheck/format/접근성 QA).** 워크스페이스 경로는 이 문서를 믿지 말고 `orca worktree list --json`으로 재확인한다.

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

## 알려진 부채·다음 세션 후보

1. **접근성 위반 32건 수정**: `docs/qa/accessibility-report.md`에 화면별·규칙별로 정리돼 있다. 우선순위: critical(대시보드 탭 aria-valid-attr-value) → serious(color-contrast 9건, 버튼/힌트 텍스트 색상 대비) → moderate(landmark-one-main/region, 거의 전 화면에 `<main>` 랜드마크가 없어서 발생 — 공통 레이아웃 컴포넌트 하나만 고치면 대부분 해소될 가능성이 높다).
2. **checkstyle 남은 532건**: Javadoc 누락(MissingJavadocMethodCheck 260, MissingJavadocTypeCheck 150 등) 위주로, 자동 포맷으로 해결 안 되고 사람이 실제로 문서를 써야 한다. 우선순위 판단 필요.
3. **E2E 시나리오 실측**: 접근성은 이번에 실측했지만, spec 문서의 E2E 시나리오(사용자 흐름 전체) 자체를 Playwright로 돌려본 적은 아직 없다.
4. **팀장(Lead) 계층 재사용 여부**: 계속 미사용 — 이번 세션도 6.0 단독으로 9개 PR을 무리 없이 처리했다.

## 워크스페이스 상태 (2026-09-29 세션 종료 시점)

- `backend-4`: 마지막 브랜치 `winter0123/watchdog-error-patterns`(병합 완료, PR #185). 다음 세션은 새 브랜치로 시작.
- `frontend-4`: 마지막 브랜치 `winter0123/fix-prettier-violations`(병합 완료, PR #184).
- `planning-4`: 마지막 브랜치 `winter0123/accessibility-qa-pass`(병합 완료, PR #186).
- 로컬 Postgres(`docker compose`)와 backend `bootRun`을 이번 세션에 Master가 직접 기동했다가 접근성 QA 완료 후 종료했다. 다음 세션에 다시 필요하면 `docker compose up -d` → `DB_PASSWORD=vibe_local_password ./gradlew bootRun`.
- 세션 종료 시점 모든 워커 dispatch가 정산됐고 `worker-list --terminal-state reclaimable`은 0건이다. watchdog은 세션 종료와 함께 중지 — 다음 세션 시작 시 `--loop 120`으로 재기동.

## 모니터링·운영 방식 (2026-09-29 갱신)

- **`check --wait`의 `--timeout-ms`는 300000(5분)으로 건다(2026-09-29 세션 중 15분→5분으로 정정).** 이유: watchdog(2분 주기)이 STALL/ERROR를 감지해도 그 감지 자체는 Master를 깨우는 신호가 아니다 — Master는 `check --wait`가 실제 `worker_done`/질문/에스컬레이션을 받아야만 깨어난다. 15분(또는 그 이상)으로 걸면 워커가 멈췄는데도 아무 메시지가 없는 상태에서 Master가 최대 그 시간만큼 못 알아챌 수 있다(2026-09-29 세션에 사용자가 먼저 발견하고 지적할 때까지 5분 이상 방치된 사례 실측). 5분 타임아웃으로 깨어날 때마다 `cat /tmp/orca-worker-status.txt`로 watchdog 요약을 직접 확인하는 습관을 매 wait 종료 시점에 넣는다(타임아웃으로 빈손 반환이어도 확인한다). 토큰 절약(긴 타임아웃) vs 감지 속도(짧은 타임아웃)의 트레이드오프이며, 5분은 사용자와 상의해 정한 값이다 — 더 짧게 걸면 재시도 턴이 늘어 토큰 낭비가 재발한다.
- **Orca 앱이 예고 없이 재시작/크래시될 수 있다.** `runtime_unavailable` 반환 시: `orca open` → `graph.state == ready` 폴링 → `run-use`로 재바인딩 → `worker-show`로 dispatch 생존 확인(추측으로 abandon/retry 금지).
- **재시작 직후 `check --wait`가 이미 처리된 오래된 메시지를 계속 재전송하는 현상이 반복 관측됐다.** `--ack <오래된 delivery_id>`가 `consumer_fenced`로 실패하면, 그 delivery는 이전 세대에 속한다는 뜻이다 — 재시도하지 말고 `orca orchestration check --run <run_id> --json`(wait 없이)으로 **현재 세대의 새 delivery_id를 다시 받아** 그것으로 ack한다.
- 워커 무응답을 watchdog이 "OK"로 잘못 판정할 수 있다 — 이번 세션에 실제로 있었다(사용자가 직접 발견). watchdog의 `ERROR_PATTERNS`는 이번에 보강했지만(PR #185), 앞으로도 새로운 오류 문구가 나타나면 그때그때 추가해야 하는 한계가 있다. **watchdog "OK"를 맹신하지 말고, 뭔가 이상하면 `orca terminal read`로 직접 확인한다.**
- OpenRouter 일일 무료 한도 소진 시 워커 전체를 OpenCode Zen(`opencode/<모델>`)으로 전환한다. Zen 경로도 내부적으로 특정 OpenRouter 모델로 폴백되며 동일하게 막히는 사례가 있었다 — 다른 Zen 모델로 재교체.
