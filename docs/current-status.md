# 프로젝트 현재 상황 (Master Lead 소유, 계속 최신화)

> 마지막 갱신: 2026-09-28 — PR #155까지 반영. 세션 종료 시점 스냅샷이며 다음 세션은 이 문서 전체를 최신 상태로 신뢰할 수 있다. 시작 전에는 `orca worktree list --json`, `orca terminal list --json`, `orca orchestration worker-list --json`으로 실제 live 상태를 다시 확인한다.

## 워크스페이스 구조 (2026-09-28 기준 실제 경로)

| 워크스페이스 | 실제 경로 | 역할 | 현재 브랜치 |
|---|---|---|---|
| backend | `/Users/joy/orca/workspaces/vibe-ai-team/backend-2` | 백엔드 구현(B그룹) | `winter0123/profit-calculation-sf2`(병합 완료, 다음 세션에서 새 브랜치로 교체 필요) |
| backend-3 | `/Users/joy/orca/workspaces/vibe-ai-team/backend-3-3` | 백엔드 구현(B그룹) | `winter0123/investment-query-api-sf1`(병합 완료, 다음 세션에서 새 브랜치로 교체 필요) |
| frontend | `/Users/joy/orca/workspaces/vibe-ai-team/frontend-3` | 프론트 구현(B그룹) | `winter0123/web-sale-profit-workflow-sf1`(병합 완료, 다음 세션에서 새 브랜치로 교체 필요) |
| planning | `/Users/joy/orca/workspaces/vibe-ai-team/planning-3` | 조사·기획·설계(A그룹) | `winter0123/dashboard-aggregation-api-breakdown-drift-check`(병합 완료, 유휴) |

**주의**: `docs/project-agent-guide.md` 5.2절의 경로 표는 접미사 없는 이름(backend/frontend/planning)으로 돼 있으나 실제로는 위 표의 `-2`/`-3` 접미사 경로다. 2026-09-22, 2026-09-23, 2026-09-28 세 번 연속 워크스페이스가 원인 불명으로 소실·재생성됐다(git은 매번 무손상 확인됨). 다음 세션도 `orca worktree list --json`으로 실제 경로를 재확인하고 이 문서를 신뢰하지 않는다.

## 이번 세션(2026-09-28) 요약

1. 세션 시작 시 워크스페이스 3차 소실 발견 → git 상태 대조로 무손상 확인 후 재생성
2. 사용자 지시로 워커별 모델을 의도적으로 다양화(같은 모델 반복 금지)
3. Epic 9(비용 기록) SF-2~5 구현·검증·병합 → **Epic 9 전체 완료**(이슈 #128 close)
4. Epic 16 breakdown 작성·병합, Epic 10·12 breakdown 드리프트 점검·병합
5. Epic 15 SF-0(Issue #134 apiFetch 수정) 병합 → **Issue #134 close**
6. Epic 10(수익 계산) SF-1~2 구현·검증·병합
7. Epic 11(투자 내역 조회) SF-2 구현·검증·병합 — Master가 프로덕션 코드 버그(remainingQuantity 계산 오류) 직접 발견·수정
8. Epic 15 SF-1(매도 등록 화면) 구현·검증·병합 — 일부 AC 테스트 커버리지 갭 있음(아래 참고)
9. "조용히 멈춘 워커" 감지 절차를 project-agent-guide.md에 신설(5분 주기 직접 확인 + lastOutputAt만으로 판단 금지)
10. 사용자 지시로 세션을 여기서 멈춤(추가 작업 배정 안 함)

## PR 병합 이력 (이번 세션, 2026-09-28, #146~#155)

| PR | 내용 | 검증 |
|---|---|---|
| #146 | Epic 9 SF-2 비용 등록 API | PASS |
| #147 | Epic 15 SF-0 apiFetch 수정 (Issue #134 close) | PASS |
| #148 | Epic 9 SF-3 비용 목록/상세 조회 | PASS |
| #149 | Epic 12 breakdown 드리프트 점검 | PASS (Master가 워커의 문서 삭제 사고 수정 후 병합) |
| #150 | Epic 9 SF-4 비용 수정 API | PASS |
| #151 | Epic 9 SF-5 비용 취소 API (Epic 9 완료, 이슈 #128 close) | PASS |
| #152 | Epic 10 SF-1 기반 계층(ProfitCalculator) | PASS |
| #153 | Epic 11 SF-2 투자 내역 전체 조회 API | PASS (Master가 프로덕션 버그 수정) |
| #154 | Epic 15 SF-1 매도 등록 화면 | PASS (테스트 갭 있음, 아래 참고) |
| #155 | Epic 10 SF-2 단일 매도건 수익 계산 API | PASS |

(이전 세션 #144 Epic 16 breakdown, #145 Epic 10 드리프트 점검도 이번 세션 초반에 병합됨)

## 알려진 갭 / 다음 세션이 바로 처리할 것

1. **Epic 15 SF-1 테스트 커버리지 갭(PR #154)**: AC-001(등록성공)/AC-004(404)/AC-005(409)/AC-006(400)/AC-021(네트워크 오류) 시나리오가 mutation mock과 vitest 상호작용 문제로 테스트화되지 못했다. 프로덕션 코드는 해당 로직을 구현하고 있으나(SaleRegisterForm.tsx의 getFormErrorMessage 참고) 테스트 보강이 필요하다.
2. Epic 10 SF-3(투자자별 전체 수익 목록 API, GET /investors/{investorId}/profits) 미착수 — SF-2 병합 완료로 착수 가능.
3. Epic 11 SF-3(투자 내역 단건 상세 조회 API) 미착수 — SF-2 병합 완료로 착수 가능.
4. Epic 15 SF-2(비용 등록 화면), SF-3(수익 확인 화면) 미착수 — SF-1 병합 완료로 착수 가능하나 SF-2는 Epic 9 완료로 이미 실연동 가능, SF-3은 Epic 10 SF-3/Epic 11 SF-3 완료 후 실연동 가능.
5. Epic 12(대시보드 집계 API) SF-1 — Epic 10이 아직 SF-1~2만 완료(전체 완료 아님)라 착수 게이트 미충족. Epic 10 전체 완료 후 재확인.
6. Epic 16(웹 조회·대시보드) 구현 — Epic 11·12 API 완료 후 착수.

## 로드맵 진행 현황 요약 (2026-09-28 기준)

- **완료**: Epic 1~9, 13, 14 (Epic 9는 이번 세션에 완료)
- **진행 중**: Epic 10(SF-1~2 완료, SF-3 남음), Epic 11(SF-1~2 완료, SF-3 남음), Epic 15(SF-0~1 완료, SF-2~3 남음)
- **breakdown 완료·구현 대기**: Epic 12(Epic 10 전체 완료 대기), Epic 16(Epic 11·12 대기)

## 모니터링 방식 (2026-09-28 확립)

- `check --wait`의 `--timeout-ms`를 900000(15분) 대신 **300000(5분)**으로 운영한다.
- 5분마다 활성 워커 전체의 `terminal read`로 **실제 텍스트 내용**을 확인한다 — `terminal show`의 `lastOutputAt`이 "방금"이어도 그 출력 자체가 업스트림 에러 메시지(rate-limited, idle timeout)일 수 있으므로 타임스탬프만으로 정상 판단하지 않는다(2026-09-28 사용자가 직접 발견해 지적한 사례 있음).
- OpenCode가 지정한 모델과 다른 모델(주로 Laguna S 2.1)로 자동 폴백하는 사례가 반복 관찰됨 — 터미널 상태줄의 실제 모델명을 지정한 모델과 대조 확인한다.
- 상세 절차는 `docs/project-agent-guide.md` 7.1.1절 참고.

## 모델 다양화 운영 (2026-09-28, 사용자 지시)

같은 시간대 여러 워커에 동일 모델을 쓰지 않고 역할·워크스페이스별로 다르게 배정한다. 이번 세션에서 새로 검증된 모델: `nvidia/nemotron-3.5-lightning:free`(정상), `qwen/qwen3.8-27b:free`(당시 429), `dots-studio/dots-3-note-preview:free`(정상, 단 한 번 4분+ 무응답 사례 있었음), `nex-agi/nex-n2.5-mini:free`(정상), `inclusionai/ling-3.0-flash-fin:free`(정상). 상세 관찰 기록은 `docs/ai-free-model.md` 참고.
