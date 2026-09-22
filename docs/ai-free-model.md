# 무료 AI 모델 역할 적합도·선정 기록

## 0. 이 문서의 목적과 사용법

이 문서는 무료 AI 모델을 단순히 나열하거나 사용 이력을 보관하는 문서가 아니다. **어떤 역할에 어떤 무료 모델을 먼저 배정할지 결정하고, 실제 결과로 그 결정을 갱신하는 운영 기준**이다.

모델을 배정할 때는 다음 순서를 따른다.

1. 아래 역할 적합도 매트릭스에서 해당 역할의 `추천` 모델을 확인한다.
2. 실행 직전에 무료 여부·모델 ID·도구 호출 가능 여부를 다시 확인한다.
3. 처음 쓰는 모델은 작은 파일 쓰기 태스크로 검증한 뒤 실제 업무에 배정한다.
4. 완료 보고가 아니라 실제 diff·테스트·빌드 결과로 품질을 평가한다.
5. 결과를 역할별 관찰 기록과 실제 사용 기록에 남기고, 반복 실패 모델은 해당 역할의 추천 목록에서 내린다.

### 판정 등급

| 등급 | 의미 | 배정 기준 |
|---|---|---|
| 추천 | 해당 역할에서 실제 산출물과 검증 결과가 안정적 | 1순위로 배정 |
| 조건부 | 일부 성공했지만 장애·재작업·표본 부족이 있음 | 대체 모델 또는 작은 SF에 배정 |
| 미검증 | 카탈로그에 있으나 해당 역할의 실제 작업 증거가 없음 | 작은 검증 태스크만 배정 |
| 금지 | 도구 호출 불가·무응답·실제 변경 없음 등으로 실패 | 해당 역할에 배정하지 않음 |

모델 자체의 품질과 일시적인 업스트림 장애를 구분한다. 타임아웃·무료 한도 초과는 모델의 `금지` 판정 근거로 단정하지 않고 비고에 별도로 기록한다.

## 1. OpenRouter 무료 모델 카탈로그

`docs/project-agent-guide.md` 6절의 파일-쓰기 검증 절차로 확인한 모델 목록은 다음과 같다.

### 작동 확인(15개) — 1순위로 바로 시도 가능:

| 모델 ID | 상태(작동확인) | 검증 방식 | 비고 |
|---------|----------------|-----------|------|
| dots-studio/dots-3-note-preview:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| nvidia/nemotron-3.5-lightning:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| nvidia/nemotron-3-super-120b-a12b:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| nvidia/nemotron-3-ultra-550b-a55b:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| inclusionai/ling-3.0-flash-vl:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| inclusionai/ling-3.0-flash-sante:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| inclusionai/ling-3.0-flash-fin:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| nex-agi/nex-n2.5-mini:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| nex-agi/nex-n2.5-pro:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| thinkingmachines/inkling-small:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| thinkingmachines/inkling:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| poolside/laguna-s-2.1:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| poolside/laguna-xs-2.1:free | 작동확인 | 파일-쓰기 방식 검증 |  |
| qwen/qwen3.8-27b:free | 작동확인 | 파일-쓰기 방식 검증 |  |

실패 확인된 모델 목록은 다음과 같다.

### 실패 확인(6개) — 이 경로로 쓰지 말고 바로 opencode로 전환:

| 모델 ID | 상태(실패) | 원인 | 비고 |
|---------|------------|------|------|
| z-ai/glm-5.2:free | 실패 | API Error: 400 No endpoints found that support tool use | 이 모델은 도구 호출 자체를 지원하지 않아 Claude Code 에이전트로 쓸 수 없음 |
| google/gemma-4-26b-a4b-it:free | 실패 | 응답 없이 멈춤(무한 대기) |  |
| google/gemma-4-31b-it:free | 실패 | 응답 없이 멈춤(무한 대기) | gemma-4 계열 전체가 이 경로에서 막히는 것으로 보임 |
| liquid/lfm-2.5-2.6b:free | 실패 | "완료" 표시(`Crunched for Ns · done`)는 뜨지만 실제로는 도구를 호출하지 않아 파일이 생성되지 않음 |  |
| cohere/north-mini-code:free | 실패 | 응답 없이 멈춤 |  |
| nvidia/nemotron-3.5-content-safety:free | 실패 | 응답 없이 멈춤(콘텐츠 검열 전용 모델이라 애초에 범용 에이전트 용도가 아니었을 가능성 높음) |  |

## 2. OpenCode Zen 무료 모델 카탈로그

`docs/project-agent-guide.md` 6.3절에 언급된 OpenCode Zen 무료 모델 목록은 다음과 같다.

> 이 목록은 기록 시점 기준이며 실행 전 `opencode models`로 재확인 필요

| 모델 ID | 프로바이더 | 비고 |
|---------|------------|------|
| opencode/big-pickle | OpenCode Zen |  |
| opencode/mimo-v2.5-free | OpenCode Zen |  |
| opencode/ling-3.0-flash-fin-free | OpenCode Zen |  |
| opencode/nemotron-3-ultra-free | OpenCode Zen |  |

## 3. 역할별 현재 기본 배정

역할별 현재 기본 배정은 다음과 같다.

| 구분 | 역할 | agent | model |
|------|------|-------|-------|
| 코디네이터 | Master Lead | `claude` (Claude Code) | Claude — `--model` 지정 대상 아님 |
| A. 조사·설계 | Research / Planning | `opencode` | Nemotron 3 Ultra (free) |
| A. 조사·설계 | Architecture | `opencode` | Nemotron 3 Super (free) |
| B. 구현 | Implementation | `opencode` | Nemotron 3 Ultra (free) |
| B. 구현 | Test / QA | `opencode` | Laguna S 2.1 (free) |
| B. 구현 | UI Vision | `opencode` | Ling 3.0 Flash VL (free) |
| C. 운영 이벤트 | Review / Security | `opencode` 또는 Claude Code | 호환성 검증된 범용 무료 모델 — `Nemotron 3.5 Content Safety`는 Claude 직접 경로 무응답 사례가 있어 기본값으로 쓰지 않음 |
| C. 운영 이벤트 | Git Manager | `opencode` | Nemotron 3 Ultra (free) |

위 표는 당장 사용할 기본값이고, 다음 매트릭스가 역할 선정의 근거다. 기본값과 실제 관찰 결과가 충돌하면 4절 기록을 반영해 기본값을 갱신한다.

## 4. 역할 적합도 매트릭스

| 모델 | Research/Planning | Architecture | Implementation | Test/QA | UI Vision | Review/Security | Git Manager | 현재 판단 근거 |
|---|---|---|---|---|---|---|---|---|
| `nvidia/nemotron-3-ultra-550b-a55b:free` | 추천 | 조건부 | 추천 | 조건부 | 미검증 | 조건부 | 추천 | Planning·Implementation 실제 성공. 구현은 테스트 누락 재작업 사례가 있어 검증 필요 |
| `nvidia/nemotron-3-super-120b-a12b:free` | 추천 | 추천 | 미검증 | 미검증 | 미검증 | 조건부 | 조건부 | 운영문서 기획·수정 성공, 정확한 지시 재주입 후 품질 개선 |
| `poolside/laguna-s-2.1:free` | 조건부 | 미검증 | 미검증 | 추천 | 미검증 | 조건부 | 조건부 | Nemotron 장애 재시도 대체 모델로 성공했으나 표본 부족 |
| `inclusionai/ling-3.0-flash-vl:free` | 미검증 | 미검증 | 미검증 | 미검증 | 추천 | 미검증 | 미검증 | 멀티모달/UI 역할 후보. 실제 업무 표본 추가 필요 |
| `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` | 미검증 | 미검증 | 금지 | 금지 | 조건부 | 미검증 | 금지 | Implementation 첫 턴 무응답, 도구 호출 역할에 재배정하지 않음 |
| `nvidia/nemotron-3.5-content-safety:free` | 금지 | 금지 | 금지 | 금지 | 금지 | 금지 | 금지 | 범용 에이전트 경로에서 무응답. 콘텐츠 검열 전용 성격 |

`미검증`은 나쁜 모델이라는 뜻이 아니라 아직 역할 선정에 사용할 증거가 없다는 뜻이다. 새 모델이나 새 역할 조합은 반드시 실제 파일 변경과 검증 결과를 남긴 뒤 등급을 올린다.

## 5. 역할 적합도 관찰 기록

| 모델 | 관찰된 역할 | 결과 | 비고 |
|------|-------------|------|------|
| nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free | B그룹 Implementation(Epic5 SF-5, 백엔드 API 구현) | 즉시 실패 — 첫 턴부터 진행 없음(빈 화면, 파일 변경 0) | 멀티모달 인식 보조용 성격으로 추정된다. 작동 확인 목록에서는 제거하지 않지만 **Implementation/Test처럼 도구 호출이 잦은 역할로 재배정하지 않는다.** Research/Planning에서의 적합성은 미검증이다. |

## 6. PR별 실제 사용 기록 (사실만 기록)

PR별 실제 사용 기록 표는 다음과 같다.

과거 PR의 모델 기록이 없으면 빈칸으로 두며, 절대 추측해 채우지 않는다(`AGENTS.md` 4.1절).

| 날짜 | PR# | Epic/SF | 역할 | 사용 모델 | Master 리뷰 결과(코드 품질/재작업 필요 여부) | 비고 |
|------|-----|---------|------|-----------|---------------------------------------------|------|
| 2026-09-22 | (직접 커밋, PR 없음) | 운영문서(ai-free-model.md 신설) | Planning | nvidia/nemotron-3-super-120b-a12b:free | 재작업 1회 필요 — 표 데이터/내용 자체는 정확했으나 작업 지시문 문장 5곳을 본문에 그대로 복사해 남김. 같은 모델에게 정확한 줄 번호를 지정해 재지시하니 한 번에 깔끔히 수정함 | |
| 2026-09-22 | #135 | 분할 매도 기록(Epic 8): SF-3 매도 목록/상세 조회 API | Implementation | nvidia/nemotron-3-ultra-550b-a55b:free | 재작업 없이 통과 — 기존 SF-1 Repository 메서드 재사용, 3단계 JOIN 필터·CANCELLED 제외 로직 스펙 그대로 구현, 테스트 8개 신규 작성, 전체 회귀 통과 | Master가 "Closes #126" 키워드를 잘못 써서 Epic 이슈가 오토클로즈된 것은 워커가 아닌 Master의 실수(바로 reopen) |
| 2026-09-22 | #136 | 수익·수익률 계산(Epic 10): SF 분해(breakdown) | Planning | nvidia/nemotron-3-ultra-550b-a55b:free | 재작업 없이 통과 — 3개 SF로 적절히 분해, AC/D-P 결정 코드 인용이 spec 문서와 전부 정확히 일치함(대조 확인함) | 이 모델은 OpenRouter 업스트림 타임아웃(`Upstream idle timeout exceeded`)으로 정상 turn 종료 없이 멈췄었음 — 재시도(`--retry-of`)로 `poolside/laguna-s-2.1:free`에 재배정해 성공. 첫 시도(nemotron-3-ultra) 자체가 나쁜 게 아니라 그 순간 업스트림 인프라 문제였을 가능성이 높음. 또한 `worker_done`이 `dispatch_capability_invalid`로 거부되어 Master가 파일 검증 후 수동으로 completed 처리함(자동 보고 인프라 이슈, 모델 품질과 무관) |
| 2026-09-22 | (진행 중) | 웹 IPO·청약·배정 입력(Epic 14): SF-3 배정 등록 화면 | Implementation | nvidia/nemotron-3-ultra-550b-a55b:free | 1차 재작업 필요 — 프로덕션 코드는 스펙대로 잘 구현했으나(POST /allocations 실연동, mock 처리 방침 준수) 신규 컴포넌트에 테스트 파일이 0개였음. 이 프로젝트의 다른 모든 Register 폼은 테스트가 짝을 이루는 관례가 있는데 이번만 빠뜨림 — 같은 모델에게 템플릿 파일을 지정해 재지시함(결과 대기 중) | AC-014(서버 400 메시지 표시) 미충족을 발견했으나 원인이 여러 Epic에 걸친 기존 공용 코드(apiFetch)의 pre-existing 버그로 확인되어 이 SF 재작업 범위에서 제외, 이슈 #134로 별도 추적 |

이후 Master가 각 PR을 병합·검증할 때마다 한 줄씩 추가한다. 기록에는 모델명뿐 아니라 역할, 실제 결과, 재작업 여부, 실패 원인 분류(모델 문제/인프라 문제/지시 문제)를 포함한다. 과거 PR의 모델은 추측하지 않는다.

## 7. 모델 선정 갱신 규칙

- 성공한 작업 1건만으로 `추천`으로 올리지 않는다. 같은 역할에서 최소 2건의 실제 작업 또는 Master가 명시적으로 승인한 대표 작업이 필요하다.
- 동일 역할에서 도구 호출 실패·무응답·실제 변경 0건이 2회 발생하면 `금지`로 내리고, 원인과 재시도 모델을 기록한다.
- 재작업이 있었더라도 요구사항·테스트·문서 중 무엇이 문제였는지 분리한다. 모델 문제가 아닌 지시 불명확·기존 코드 버그·인프라 장애를 모델 등급에 반영하지 않는다.
- 무료 한도, API 장애, 업스트림 타임아웃은 모델 품질 판정과 별도로 기록한다.
- 모델 카탈로그·가격·도구 호출 지원은 실행 시점에 다시 확인한다. 이 문서의 목록은 현재 결정을 돕는 기록이지 영구적인 최신 목록이 아니다.
