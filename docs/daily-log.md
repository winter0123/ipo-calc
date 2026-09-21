# 일자별 작업 요약 (매일 추가, append-only)

## 2026-09-20

- Epic 1(account-authentication) SF-5(데이터 격리 방어), SF-6(비밀번호 변경 API) 완료. Epic 1 전체 34개 테스트 통과, PR #41 생성(사람 승인 대기).
  - SF-6 진행 중 Spring Security 세션 관리 버그 3건을 Master가 직접 진단·수정 지시하여 해결(SessionRegistry 인스턴스 불일치, AuthService의 SessionAuthenticationStrategy 미호출, 만료 세션 기본 응답 코드).
- 워크스페이스 운영 방식을 Epic별 생성/삭제에서 **역할별 고정 3개(backend/frontend/planning)로 절대 삭제하지 않는 방식**으로 전환(사용자 지시). 관련 git 제약(같은 저장소 여러 워크트리는 동일 브랜치 동시 체크아웃 불가) 발견 및 절차 수정.
- main 병합 정책을 "Epic 전체 완료 후 한 번에"에서 "기능(SF)/문서 단위로 계속 진행"으로 전환(사용자 지시). 코드는 테스트 통과 확인 후에만, 조사 문서는 완성 즉시 병합.
- Epic 2~16(투자자 관리, 증권사 기준정보, 증권계좌, 공모주 기준정보, 청약, 배정, 매도, 비용, 수익 계산, 조회 API, 대시보드 집계 API, 웹 계정 설정, 웹 청약·배정 입력, 웹 매도·수익 확인, 웹 조회·대시보드) 전체 Phase 0 조사 완료 및 병합. **로드맵 16개 Epic 전체 조사 완료.**
  - 조사 과정에서 무료 모델(Nemotron 3 Ultra/Super, Laguna S 2.1 등)의 반복적 stall(도구 호출 없이 "완료하겠다"고만 말하고 멈춤) 다수 발생 — Master가 매번 `git diff`/`terminal read`로 실제 진행 상황을 확인한 뒤에만 개입(interrupt → 모델 교체 재시도)함.
  - Epic 14(web-subscription-workflow), Epic 15(web-sale-profit-workflow) 조사 초안이 "실시간 결제/거래 처리"를 전제로 작성되는 오류 발견 — 이 서비스는 실제로는 이미 완료된 청약·매도 결과를 사후 기록하는 서비스라는 로드맵 범위에 맞게 Master가 직접 수정.
- `docs/current-status.md`, `docs/daily-log.md` 신규 생성(9.2 요구사항 이행).

## 2026-09-21

### ✅ Epic 2~16 Phase 0(조사·기획·설계) 전체 완료 — 로드맵 100%
- PR #49~#63. Epic 6/7/8은 "실시간 처리" 가정 오류를 반복 발견해 D-S/D-A/D-SL 결정으로 정정, Epic 12는 계정 격리 보안 결함을 발견·수정, Epic 13은 첫 프론트엔드 Epic.
- 상세 결정 내역은 각 Epic `docs/work-log/*.md` 참고.

### ⚠️ OpenRouter 무료 모델 계정 일일 한도 도달 → 우회 경로 발견
- Nemotron/Laguna/Gemma 4개 프로바이더 동일 오류로 계정 단위 한도 확인. `opencode/` 자체 무료 모델(OpenCode Zen)이 별개 quota임을 발견해 우회, AGENTS.md 22장에 기록.

### 🔧 DB 문법 버그 재발 방지
- Flyway SQL에 MySQL 문법이 섞여 있던 걸 발견·수정. "PK는 GENERATED ALWAYS AS IDENTITY, 조건부 유니크는 별도 CREATE UNIQUE INDEX" 규칙 추가.

### ✅ Epic 1 사람 승인·병합 (23:10 UTC)
- `account-authentication` 워크스페이스를 `backend`로 이관.

### ⚠️ 워크스페이스 사고 2건 → 재발 방지 규칙화
- `--no-parent` 플래그 오용, 터미널 생성 시 `--worktree` 미지정 사고. 둘 다 AGENTS.md 12.4에 기록.

### ✅ Epic 2(investor-management) 구현 완료 — B그룹 두 번째 Epic
- SF-1~6, PR #65~#70, 66개 테스트 GREEN. `SecurityUtils` 등 공용 인프라 신설(이후 모든 Epic이 재사용).
- CSRF 활성 상태로는 스펙이 요구하는 401이 불가능한 구조적 문제 발견 → CSRF 전역 비활성화 결정(D-I-006, 사용자 승인).

### ✅ Epic 3(securities-firm-catalog) 구현 완료 — B그룹 세 번째 Epic
- SF-1~5, PR #72~#76, 94개 테스트 GREEN. 글로벌 기준정보라 SecurityUtils 불필요(Epic 2와 차이).
- 스쿼시 머지 후 rebase 시 "both added" 가짜 충돌 패턴 확인, 대응 절차 AGENTS.md에 기록.

### 🔍 Claude Code + OpenRouter 무료 모델 워커 경로 신규 발견
- `claude --model "<openrouter 무료 모델>"`로 완전한 도구 사용 가능한 워커를 띄울 수 있음을 확인. 15개 작동/6개 실패 전수 테스트, AGENTS.md 22.4에 기록. 워커 우선순위를 "1순위 claude --model → 2순위 opencode"로 변경.

### 🔧 네이티브 ↔ 게이트웨이 모델 전환 방법 정리·정정
- `CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY`만 unset하면 충분하다고 처음 문서화했으나 오류로 판명(여전히 OpenRouter 과금). `ANTHROPIC_BASE_URL`/`ANTHROPIC_AUTH_TOKEN`까지 3개 모두 unset해야 진짜 네이티브. AGENTS.md 22.5 정정.

### ✅ Epic 4(securities-account-management) 구현 완료 — B그룹 네 번째 Epic
- SF-1~7, PR #78~#84, 140개 테스트 GREEN. 사용자 지시로 네이티브 Claude 모델(Sonnet 5) 워커 사용.
- AES-256-GCM 계좌번호 암호화, 2단계 소유권 검증, `saveAndFlush` + partial unique index 기반 기본계좌 동시성 제어.
- Master가 직접 발견한 버그: 증권사 비활성화 시 연결계좌 이름이 사라지던 결함 → `findById`로 수정 + 회귀 테스트 추가.

### 🔍 로드맵 의존관계 재확인 → 프론트엔드 병렬 착수
- 사용자 지적("프론트 개발은 왜 안해?")으로 발견: Epic 13은 Epic 1~4만 있으면 착수 가능했는데 순차 진행 가정 때문에 늦게 시작. 이후 매 Epic 완료 시 의존관계 재확인하기로 함.

### ✅ Epic 5(ipo-catalog) SF-1 완료 — backend
- Ipo 엔티티/Repository/V5 마이그레이션, 147개 테스트, PR #87. D-IPO-001~004 확정(이중 중복검사/CSV 5MB/commons-csv/deletedAt 필터).

### ✅ Epic 13(web-account-setup) SF-1 완료 — 로드맵 최초 프론트엔드 구현
- `ui/` Vite+React+TS 스캐폴딩, FSD 뼈대, PR #88. D-W13-007~015 확정(신규 프로젝트라 "확인 후 결정" 항목 전부).
- 워커가 Master 전용 문서(`current-status.md`)를 임의로 건드린 것을 되돌림 → 태스크 스펙에 금지 명시.

### ⚠️ 터미널 정리 재발 사고
- AGENTS.md에 이미 기록된 실패("다 쓴 세션 방치")가 Epic 2~4 전체에서 재발 — 사용자가 직접 지적. 18개 즉시 정리, "merge 직후 같은 턴에서 터미널 close"를 12.4-1/23.6/25장 세 곳에 명시.
- 사용자 요청으로 "질문엔 한국어로 답변" 규칙 확정.

### 🔧 네이티브 모델 예외 규칙 신설 (22.6)
- 기본은 OpenRouter 무료 모델, 사용자가 명시적으로 지시한 범위에 한해 네이티브 모델 예외 허용. 이후 다시 OpenRouter 우선으로 복귀.

### 🔧 헤르메스(Telegram 알림 봇) 리포팅 파이프라인 설계 논의 — 구현 전
- 감지 트리거를 Orca 태스크 완료 / PR 생성(`gh pr list --state all`로 신규 번호 감지, `--state open`은 이 프로젝트에선 즉시 merge돼 놓치기 쉬움) 기준으로 설계.
- 알림 전송은 Hermes LLM(Gemini RPD 500)을 거치지 않는 순수 CLI 호출로, 감지는 Claude 세션과 무관한 독립 폴링 스크립트로 분리해 양쪽 토큰/쿼터 모두 안 씀. 실제 스크립트 작성은 사용자 확인 대기 중.
