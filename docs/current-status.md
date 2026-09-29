# 프로젝트 현재 상황 (Master 소유, 계속 최신화)

> 마지막 갱신: 2026-09-29 세션 종료 시점(4차 재개 세션, Epic 16 후속 부채 처리). **로드맵 16개 Epic 전체 구현 완료 + 사용자 결정 4건 반영 완료.** 워크스페이스 경로는 이 문서를 믿지 말고 `orca worktree list --json`으로 재확인한다.

## 세션 요약 (2026-09-29, 4차 재개 — Epic 16 후속 부채 4건 처리)

2026-09-28 세션 종료 시 남긴 "알려진 부채" 중 사용자가 우선순위로 고른 4건을 처리했다: ① Issue #134 잔여 범위 재확인(추가 작업 불필요로 결론) ② 투자 내역 API에 investorName/securitiesFirmName 추가(백엔드) ③ shared/components 공용 컴포넌트 5종 신설 ④ CostRegisterForm 성공 후 `/investments` 이동. 상세 PR 목록과 이슈·트러블슈팅은 `docs/daily-log/2026_09_28.md`의 네 번째 세션 섹션("4차 재개 세션 — Epic 16 후속 부채 4건 처리")을 진실 소스로 한다.

이번 세션에 새로 발견한 것: `ui/tsconfig.json`이 solution-style(`files: []`)이라 `pnpm run typecheck`(`tsc --noEmit`)가 실제로는 0개 파일을 검사하는 무의미한 명령이다(`tsc --noEmit --listFiles`로 직접 재현 확인). 실질적 타입 체크는 `pnpm run build`(`tsc -b`)가 대신 수행하고 있어 지금까지 실패가 새지는 않았지만, `docs/guide-frontend.md`의 검증 명령 설명을 정정해야 한다(아래 "알려진 부채" 1번).

Orca 앱이 이번 세션 중 여러 차례 예고 없이 재시작/크래시됐다(`runtime_unavailable` → `orca open` → `graph_not_ready` → 수 초~수십 초 후 `ready`). 매번 `orca orchestration run-use --id <run_id>`로 재바인딩하고 `worker-show`로 진행 중이던 워커의 생존을 재확인한 뒤 이어갔다 — 데이터 손실 없음.

## 로드맵 진행 현황

**완료: Epic 1~16 전체(2026-09-28 세션에 완료).** GitHub Issue #128·#129·#130·#131·#132·#133 모두 close.

## 알려진 부채·다음 세션 후보 (착수 전 사용자 우선순위 확인 필요)

1. **`pnpm run typecheck`가 사실상 no-op**: `ui/tsconfig.json`이 `files: []` + project references만 있는 solution-style 구성이라 `tsc --noEmit` 단독 실행은 0개 파일을 검사한다. `pnpm run build`(`tsc -b`)가 실질적 타입 체크 역할을 대신하고 있어 지금까지 실패를 놓친 적은 없지만, `docs/guide-frontend.md`의 "typecheck만으로 끝내지 말고 build도 필수" 문구를 "typecheck는 사실상 no-op이니 build 결과만 신뢰" 수준으로 정정하거나, tsconfig 자체를 고쳐 `pnpm run typecheck`가 실제로 동작하게 만드는 두 가지 선택지가 있다.
2. **checkstyle 기존 위반 6,940건 정리 여부**: 여전히 미결. `checkstyleMain`/`checkstyleTest`는 `check`/`build` 라이프사이클에 안 걸려 있어 당장 빌드를 막지는 않는다.
3. **Epic 16 접근성(axe-core)·E2E 시나리오 실측 검증**: 워커들이 코드 레벨 aria-* 속성 구현을 보고했지만, 실제 브라우저에서 axe-core 스캔이나 E2E 시나리오를 돌려 확인한 적은 없다.
4. **팀장(Lead) 계층 재사용 여부**: 계속 미사용 — 이번 세션도 6.0 단독으로 4개 PR을 무리 없이 처리했다.

## 워크스페이스 상태 (2026-09-29 세션 종료 시점)

- `backend-4`: 마지막 브랜치 `winter0123/investment-history-dto-extend`(병합 완료, PR #180). 다음 세션은 `git checkout -B <새 브랜치> origin/main`으로 새로 시작.
- `frontend-4`: 마지막 브랜치 `winter0123/investment-history-investor-display`(병합 완료, PR #181).
- `planning-4`: 이번 세션에 사용 안 함(마지막 상태는 2026-09-28 세션 그대로, `winter0123/user-guides-catchup` 병합 완료).
- 세션 종료 시점 모든 워커 dispatch가 정산됐고 `worker-list --terminal-state reclaimable`은 0건이다. watchdog은 세션 종료와 함께 중지 — 다음 세션 시작 시 `--loop 120`으로 재기동.

## 모니터링·운영 방식 (2026-09-29 갱신)

- `check --wait`의 `--timeout-ms`는 900000(15분) 이상.
- **Orca 앱 자체가 예고 없이 재시작/크래시될 수 있다**(이번 세션 3회 관측). `orchestration` 명령이 `runtime_unavailable`을 반환하면: ① `orca open --json` ② `graph.state`가 `ready`가 될 때까지 몇 초~수십 초 대기(폴링) ③ `orca orchestration run-use --id <run_id>`로 재바인딩 ④ 진행 중이던 dispatch가 있으면 `worker-show`로 생존 여부부터 확인(추측으로 abandon/retry 하지 않는다) ⑤ 대기 중이던 `check --wait`가 `outcome_unknown`/`connectionLost`로 끊겼으면 `request-show`로 실제 결과를 먼저 확인한 뒤 필요하면 새 `check --wait`를 연다(중복 waiter 에러가 나면 이미 살아있는 이전 waiter가 있다는 뜻이므로 그냥 둔다).
- OpenRouter 일일 무료 한도 소진 시 워커 전체를 OpenCode Zen(`opencode/<모델>`)으로 전환한다. Zen 경로도 내부적으로 OpenRouter 특정 모델(Laguna S 2.1 등)로 폴백되며 동일하게 막히는 사례가 있었다 — 이 경우 다른 Zen 모델로 한 번 더 교체한다. "Endpoint unavailable" 같은 순수 인프라 오류도 watchdog의 STALL(장시간 무출력)로 잡히니, 무출력 20분 이상이면 실제 터미널 텍스트를 확인하고 abandon→재배정한다.
