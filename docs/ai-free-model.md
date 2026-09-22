# AI Free Model Documentation

## 1. OpenRouter 무료 모델 카탈로그
AGENTS.md 22.4에서 파일-쓰기 방식으로 검증된 작동 확인 모델 목록은 다음과 같다.

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
AGENTS.md 22.3/22에 언급된 OpenCode Zen 무료 모델 목록은 다음과 같다.

> 이 목록은 AGENTS.md 작성 시점 기준이며 실행 전 `opencode models`로 재확인 필요

| 모델 ID | 프로바이더 | 비고 |
|---------|------------|------|
| opencode/big-pickle | OpenCode Zen |  |
| opencode/mimo-v2.5-free | OpenCode Zen |  |
| opencode/ling-3.0-flash-fin-free | OpenCode Zen |  |
| opencode/nemotron-3-ultra-free | OpenCode Zen |  |

## 3. 역할별 현재 기본 배정 (AGENTS.md 22.1 표 그대로 인용)
역할별 현재 기본 배정은 다음과 같다.

| 구분 | 역할 | agent | model |
|------|------|-------|-------|
| 코디네이터 | Master Lead | `claude` (Claude Code) | Claude — `--model` 지정 대상 아님 |
| A. 조사·설계 | Research / Planning | `opencode` | Nemotron 3 Ultra (free) |
| A. 조사·설계 | Architecture | `opencode` | Nemotron 3 Super (free) |
| B. 구현 | Implementation | `opencode` | Nemotron 3 Ultra (free) |
| B. 구현 | Test / QA | `opencode` | Laguna S 2.1 (free) |
| B. 구현 | UI Vision | `opencode` | Ling 3.0 Flash VL (free) |
| C. 운영 이벤트 | Review / Security | `opencode` | Nemotron 3.5 Content Safety (free) |
| C. 운영 이벤트 | Git Manager | `opencode` | Nemotron 3 Ultra (free) |

## 4. 역할 적합도 관찰 기록 (AGENTS.md 22.4-1 표 그대로 인용)

| 모델 | 관찰된 역할 | 결과 | 비고 |
|------|-------------|------|------|
| nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free | B그룹 Implementation(Epic5 SF-5, 백엔드 API 구현) | 즉시 실패 — 첫 턴부터 진행 없음(빈 화면, 파일 변경 0) | 모델 자체 설명이 "enterprise agent systems의 perception/context sub-agent" — 멀티모달 인식 보조용으로 설계된 모델이라 범용 코딩 에이전트 역할에는 안 맞을 가능성. 22.4의 "작동 확인" 목록에서 제거하지는 않음(다른 역할에서는 될 수도 있음) — **Implementation/Test처럼 도구 호출이 잦은 역할로는 재배정하지 않는다.** 대신 Research/Planning처럼 도구 호출이 적은 역할에서 시도해볼 가치는 있음(미검증) |

## 5. PR별 실제 사용 기록 (지금부터 채워나갈 빈 표 — 절대 과거 PR의 모델을 추측해서 채우지 마라)
PR별 실제 사용 기록 표는 다음과 같다. (지금부터 채워나갈 빈 표)

**이 표는 행 없이 헤더만 만들고 빈 채로 둔다.** 과거 PR이 어떤 모델로 만들어졌는지 기록이 없으므로 절대 지어내지 마라(AGENTS.md 3.1 추측 금지 원칙).

| 날짜 | PR# | Epic/SF | 역할 | 사용 모델 | Master 리뷰 결과(코드 품질/재작업 필요 여부) | 비고 |
|------|-----|---------|------|-----------|---------------------------------------------|------|

이후 Master가 각 PR을 머지·검증할 때마다 이 표에 한 줄씩 추가한다.