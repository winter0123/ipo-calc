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

**오늘의 핵심 성과**: 로드맵 16개 Epic 전체 Phase 0 완료 + Epic 1~4 구현 완료(총 340개 테스트) + 로드맵 의존관계 재확인으로 Epic 13 프론트엔드 병렬 착수(로드맵 최초 프론트엔드 구현, SF-1~2).

### 기능 구현 현황

| Epic/Task | 분야 | PR/Test 수 | 상태 | 주요 내용 |
|---|---|---|---|---|
| Epic 2~16 Phase 0(조사·기획·설계) | BE/FE | PR #49~#63 | 완료 | 로드맵 16개 Epic 전체 spec+architecture 100% 완료 |
| Epic 1(account-authentication) | BE | PR #41 | 완료(사람 승인) | `backend` 워크스페이스로 이관 |
| Epic 2(investor-management) SF-1~6 | BE | PR #65~#70 / 66 tests | 완료 | `SecurityUtils` 공용 인프라 신설(이후 모든 Epic 재사용), CSRF 전역 비활성화(D-I-006) |
| Epic 3(securities-firm-catalog) SF-1~5 | BE | PR #72~#76 / 94 tests | 완료 | 글로벌 기준정보라 SecurityUtils 불필요 |
| Epic 4(securities-account-management) SF-1~7 | BE | PR #78~#84 / 140 tests | 완료 | AES-256-GCM 암호화, 2단계 소유권검증, 네이티브 모델(Sonnet 5) 사용 |
| Epic 5(ipo-catalog) SF-1 | BE | PR #87 / 147 tests | 완료 | Ipo 엔티티/Repository/V5 마이그레이션, D-IPO-001~004 확정 |
| Epic 5(ipo-catalog) SF-2 | BE | - | 진행중 | IPO 등록 API, 중복검사 헬퍼(D-IPO-001) |
| Epic 13(web-account-setup) SF-1 | FE | PR #88 | 완료 | `ui/` Vite+React+TS 스캐폴딩 — 로드맵 최초 프론트엔드 구현, D-W13-007~015 확정 |
| Epic 13(web-account-setup) SF-2 | FE | - | 진행중(검증 중 결함 발견, 수정 중) | 로그인 화면, react-hook-form+TanStack Query |

### 이슈 및 트러블슈팅

| 카테고리 | 발생 이슈 | 해결 및 조치 사항 |
|---|---|---|
| 모델 가용성 | OpenRouter 무료 모델 계정 일일 한도(`free-models-per-day-high-balance`) 도달, 4개 프로바이더 동일 오류 | `opencode/` 자체 무료 모델(별개 quota)로 우회, AGENTS.md 22장 기록 |
| DB 문법 | Flyway SQL에 MySQL 문법(AUTO_INCREMENT 등) 혼입 | PostgreSQL 전용 문법 규칙 AGENTS.md에 명시, 기존 문서도 수정 |
| 워크스페이스 운영 | `--no-parent` 플래그 오용, 터미널 생성 시 `--worktree` 미지정 사고 2건 | 절차 수정, AGENTS.md 12.4에 기록 |
| 아키텍처 설계 오류(3회 반복) | Epic 6/7/8 조사문서가 "사후기록" 전제를 놓치고 실시간 처리(상태머신/락) 가정 | Master가 D-S/D-A/D-SL 결정으로 스코프 정정 |
| 보안 결함 | Epic 12 architecture가 "단일 사용자 가정"으로 계정 소유권 검증 누락 | SecurityUtils 기반 필터링 spec/architecture에 추가 |
| 모델 운영 | Claude Code + OpenRouter 무료모델을 워커로 쓰는 새 경로 발견 | AGENTS.md 22.4 신설, 워커 우선순위 변경(1순위 claude --model) |
| 모델 운영 | 네이티브↔게이트웨이 전환 방법이 처음에 틀렸음(1개 env만 unset해선 부족) | `ANTHROPIC_BASE_URL`/`ANTHROPIC_AUTH_TOKEN`까지 3개 unset 필요로 AGENTS.md 22.5 정정 |
| 코드 버그 | Epic 4 SF-3: 증권사 비활성화 시 연결계좌 이름이 사라짐(`findByIdAndDeletedAtIsNull` 오용) | `findById`로 수정 + 회귀 테스트 추가 |
| 프로세스 | Epic 13이 Epic 1~4만으로 착수 가능했는데 순차 진행 가정으로 늦게 시작 | 사용자 지적("프론트 개발은 왜 안해?"), 매 Epic 완료 시 의존관계 재확인 방침 수립 |
| 세션 관리(재발) | AGENTS.md에 이미 기록된 실패("다 쓴 세션 방치")가 Epic 2~4 전체에서 재발, 터미널 18개 방치 | 즉시 정리, "merge 직후 같은 턴에서 터미널 close"를 12.4-1/23.6/25장 세 곳에 명시 |
| 문서 관리 | 워커가 Master 전용 문서(`current-status.md`)를 임의 수정 | 되돌리고 태스크 스펙에 수정 금지 명시 |
| 모델 운영 | 사용자가 특정 기간 네이티브 모델 사용을 지시 | 예외 규칙(22.6) 신설, 이후 OpenRouter 우선으로 복귀 |
| 알림 설계 | Hermes 알림 트리거를 "merge" 기준으로 설계했으나 본인이 직접 merge하므로 무의미 | "PR 생성"/"Orca 태스크 완료" 기준으로 재설계(구현 전, `--state open`은 즉시 merge돼 놓치므로 `--state all`로 신규 PR 번호 감지) |
| 테스트 품질 | Epic 13 SF-2: 워커가 `QueryClientProvider` 없이 테스트 작성해 4개 전부 실패했는데 "완료"로 보고 | Master가 독립 검증 중 발견, 직접 수정 진행 중 |
| 문서 형식 | daily-log.md가 문단형이라 가독성이 떨어진다는 사용자 피드백 | 표 기반 템플릿(기능 구현 현황/이슈 및 트러블슈팅)으로 전환, AGENTS.md 9.2③에 새 템플릿 반영 |
