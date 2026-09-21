# 일자별 작업 요약 (매일 추가, append-only)

## 2026-09-20

1. 로그인 계정 인증 SF-5~6 완료, PR #41
2. 워크스페이스 운영방식 3개 고정으로 전환
3. main 병합정책을 SF 단위로 전환
4. 로드맵 16개 Epic 전체 조사 완료
5. current-status/daily-log 문서 신규생성

### 기능 구현 현황

| Epic/Task | 분야 | 브랜치 | PR(SF 범위) | 상태 | 주요 내용 |
|---|---|---|---|---|---|
| 로그인 계정과 인증·인가(Epic 1) | BE | `winter0123/account-authentication` | PR #41(SF-5~6) | 완료(사람 승인 대기) | 데이터 격리 방어, 비밀번호 변경 API |
| 로드맵 16개 Epic 전체 조사(Epic 2~16 Phase 0) | BE/FE | 각 Epic별 조사 브랜치 | PR #42~#63 중 일부 | 완료 | 조사·기획·설계 완료 및 병합 |

### 이슈 및 트러블슈팅

| 카테고리 | 발생 이슈 | 해결 및 조치 사항 |
|---|---|---|
| 코드 버그 | Spring Security 세션관리 버그 3건(SessionRegistry 인스턴스 불일치, SessionAuthenticationStrategy 미호출, 만료세션 응답코드) | Master가 직접 진단 후 수정 지시하여 해결 |
| 워크스페이스 운영 | Epic별 워크스페이스 생성/삭제 방식이 비효율적, 동일 브랜치 다중 체크아웃 git 제약 발견 | 역할별 고정 3개(backend/frontend/planning) 절대 삭제 안 하는 방식으로 전환 |
| 병합 정책 | "Epic 전체 완료 후 한 번에 병합"하던 방식 | "기능(SF)/문서 단위로 계속 병합"하는 방식으로 전환 |
| 모델 안정성 | 무료 모델(Nemotron, Laguna 등) 반복적 stall(도구 호출 없이 완료 선언만) | `git diff`/`terminal read`로 실제 진행 확인 후에만 개입(interrupt→재시도) |
| 조사 문서 오류 | Epic 14/15 초안이 "실시간 결제/거래 처리" 전제로 잘못 작성 | 사후기록 서비스라는 실제 범위에 맞게 Master가 직접 수정 |

## 2026-09-21

1. 로드맵 16개 Epic 조사·설계 전체완료
2. 계정인증~증권계좌 4개 Epic 백엔드 구현완료
3. 웹 계정설정 화면 프론트엔드 병렬 착수 결정
4. 공모주 등록 API, 로그인 화면 진행
5. OpenRouter 무료모델 워커 경로 발견
6. 네이티브 모델 전환방법 정정
7. 워커 터미널 정리 재발사고 수정
8. daily-log 표 형식으로 전환
9. 모바일 반응형 최적화 필수화
10. 패키지매니저 npm에서 pnpm 전환

### 기능 구현 현황

| Epic/Task | 분야 | 브랜치 | PR(SF 범위) | 상태 | 주요 내용 |
|---|---|---|---|---|---|
| 로드맵 16개 Epic 전체 조사·기획·설계(Epic 2~16 Phase 0) | BE/FE | `planning` 워크스페이스, Epic별 조사 브랜치 | PR #49~#63 | 완료 | spec+architecture 100% 완료 |
| 로그인 계정과 인증·인가(Epic 1) | BE | `winter0123/account-authentication` | PR #41 | 완료(사람 승인) | `backend` 워크스페이스로 이관 |
| 투자자 관리(Epic 2) | BE | `winter0123/investor-management` | PR #65~#70(SF-1~6) | 완료 | `SecurityUtils` 공용 인프라 신설(이후 모든 Epic 재사용), CSRF 전역 비활성화(D-I-006) |
| 증권사 기준정보 관리(Epic 3) | BE | `winter0123/securities-firm-catalog` | PR #72~#76(SF-1~5) | 완료 | 글로벌 기준정보라 SecurityUtils 불필요 |
| 증권계좌 관리(Epic 4) | BE | `winter0123/securities-account-management` | PR #78~#84(SF-1~7) | 완료 | AES-256-GCM 암호화, 2단계 소유권검증, 네이티브 모델(Sonnet 5) 사용 |
| 공모주 기준정보 관리(Epic 5): 엔티티/Repo/마이그레이션 | BE | `winter0123/ipo-catalog` | PR #87(SF-1) | 완료 | Ipo 엔티티/Repository/V5 마이그레이션, D-IPO-001~004 확정 |
| 공모주 기준정보 관리(Epic 5): IPO 등록 API | BE | `winter0123/ipo-catalog` | PR #91(SF-2) | 완료 | 중복검사 헬퍼(D-IPO-001), V6 마이그레이션으로 partial unique index 버그 수정 |
| 웹 계정·투자자·계좌 설정(Epic 13): 프로젝트 스캐폴딩 | FE | `winter0123/web-account-setup` | PR #88(SF-1) | 완료 | `ui/` Vite+React+TS — 로드맵 최초 프론트엔드 구현, D-W13-007~015 확정 |
| 웹 계정·투자자·계좌 설정(Epic 13): 로그인 화면 | FE | `winter0123/web-account-setup` | PR #89(SF-2) | 완료 | react-hook-form+TanStack Query |
| 프론트엔드 패키지 매니저 전환 | FE | `winter0123/web-account-setup` | PR #90 | 완료 | npm → pnpm, package-lock.json → pnpm-lock.yaml |

### 이슈 및 트러블슈팅

| 카테고리 | 발생 이슈 | 해결 및 조치 사항 |
|---|---|---|
| 모델 가용성 | OpenRouter 무료 모델 계정 일일 한도(`free-models-per-day-high-balance`) 도달, 4개 프로바이더 동일 오류 | `opencode/` 자체 무료 모델(별개 quota)로 우회, AGENTS.md 22장 기록 |
| DB 문법 | Flyway SQL에 MySQL 문법(AUTO_INCREMENT 등) 혼입 | PostgreSQL 전용 문법 규칙 AGENTS.md에 명시, 기존 문서도 수정 |
| 워크스페이스 운영 | `--no-parent` 플래그 오용, 터미널 생성 시 `--worktree` 미지정 사고 2건 | 절차 수정, AGENTS.md 12.4에 기록 |
| 아키텍처 설계 오류(3회 반복) | 청약/배정/매도 기록(Epic 6/7/8) 조사문서가 "사후기록" 전제를 놓치고 실시간 처리(상태머신/락) 가정 | Master가 D-S/D-A/D-SL 결정으로 스코프 정정 |
| 보안 결함 | 대시보드 집계 API(Epic 12) architecture가 "단일 사용자 가정"으로 계정 소유권 검증 누락 | SecurityUtils 기반 필터링 spec/architecture에 추가 |
| 모델 운영 | Claude Code + OpenRouter 무료모델을 워커로 쓰는 새 경로 발견 | AGENTS.md 22.4 신설, 워커 우선순위 변경(1순위 claude --model) |
| 모델 운영 | 네이티브↔게이트웨이 전환 방법이 처음에 틀렸음(1개 env만 unset해선 부족) | `ANTHROPIC_BASE_URL`/`ANTHROPIC_AUTH_TOKEN`까지 3개 unset 필요로 AGENTS.md 22.5 정정 |
| 코드 버그 | 증권계좌 관리(Epic 4) SF-3: 증권사 비활성화 시 연결계좌 이름이 사라짐(`findByIdAndDeletedAtIsNull` 오용) | `findById`로 수정 + 회귀 테스트 추가 |
| 프로세스 | 웹 계정설정(Epic 13)이 계정인증~증권계좌(Epic 1~4)만으로 착수 가능했는데 순차 진행 가정으로 늦게 시작 | 사용자 지적("프론트 개발은 왜 안해?"), 매 Epic 완료 시 의존관계 재확인 방침 수립 |
| 세션 관리(재발) | AGENTS.md에 이미 기록된 실패("다 쓴 세션 방치")가 Epic 2~4 전체에서 재발, 터미널 18개 방치 | 즉시 정리, "merge 직후 같은 턴에서 터미널 close"를 12.4-1/23.6/25장 세 곳에 명시 |
| 문서 관리 | 워커가 Master 전용 문서(`current-status.md`)를 임의 수정 | 되돌리고 태스크 스펙에 수정 금지 명시 |
| 모델 운영 | 사용자가 특정 기간 네이티브 모델 사용을 지시 | 예외 규칙(22.6) 신설, 이후 OpenRouter 우선으로 복귀 |
| 알림 설계 | Hermes 알림 트리거를 "merge" 기준으로 설계했으나 본인이 직접 merge하므로 무의미 | "PR 생성"/"Orca 태스크 완료" 기준으로 재설계(구현 전, `--state open`은 즉시 merge돼 놓치므로 `--state all`로 신규 PR 번호 감지) |
| 테스트 품질 | 웹 계정설정(Epic 13) SF-2 로그인 화면: 워커가 `QueryClientProvider` 없이 테스트 작성해 4개 전부 실패했는데 "완료"로 보고 | Master가 독립 검증 중 발견, 직접 수정하여 병합 |
| 문서 형식 | daily-log.md가 문단형이라 가독성이 떨어진다는 사용자 피드백 | 표 기반 템플릿(기능 구현 현황/이슈 및 트러블슈팅)으로 전환, AGENTS.md 9.2③에 새 템플릿 반영 |
