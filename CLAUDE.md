# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

공모주(IPO) 투자 수익 관리 서비스. 이미 완료된 청약·배정·매도 거래를 사후에 기록하고 수익/수익률을 계산·조회하는 개인용 도구로 시작했다. 로드맵은 `docs/roadmap.md`에 16개 Epic(1~16, 완료)으로 정의돼 있고, 각 Epic의 조사(`docs/research/`)·기획(`docs/spec/`)·설계(`docs/architecture/`) 산출물은 slug 이름으로 대응 폴더에 쌓인다.

**2026-09-29 사용자 결정으로 스코프 확장**: 기존에 범위 밖으로 선언했던 "자동 증권사 연동"을 **조회 전용(read-only)에 한해** 범위 안으로 들인다. 증권사 오픈API(예: 한국투자증권 KIS Developers REST API)를 통해 사용자 본인 계좌의 잔고·매매내역·수수료를 조회해 매도/비용 기록을 자동으로 채우는 기능을 Epic 17 이후로 추가한다. **원칙**: 모든 증권사 API 연동은 예외 없이 조회 전용 스코프로만 연결한다 — 주문·정정·취소 등 매매 실행 권한은 어떤 브로커·어떤 상황에서도 요청하거나 사용하지 않는다. 실시간 시세 연동(분·초 단위 시세 스트리밍)과 모바일 앱은 여전히 범위 밖이다. 공모주 청약 일정 자동 동기화(공공 API 기반)도 이번 결정으로 범위에 포함됐다.

## IMPORTANT: read AGENTS.md first

이 저장소는 `AGENTS.md`(저장소 루트)에 정의된 멀티 에이전트 오케스트레이션 워크플로우로 운영된다 — Master Lead(코디네이터)가 Orca 위에서 무료 LLM 워커들에게 조사/설계/구현을 위임하는 구조다. 다음을 하기 전에 반드시 `AGENTS.md`를 읽는다:
- 워크스페이스 구조를 만지거나 새 워커를 배분하는 작업
- Epic/세부기능(SF) 단위 구현 순서를 판단하는 작업
- git 브랜치 전략, PR 병합 정책을 결정하는 작업

`AGENTS.md`는 계속 갱신되는 살아있는 문서이며, 에이전트 구조(6장 — 6.0절이 기본인 "코디네이터 1명 + 병렬 워커", 6.0.2절이 선택적 팀장 계층), 로드맵·Epic 재분해(5장), 병렬화와 역할별 고정 작업공간(7장), Orca 오케스트레이션 실행 기준(8장), 무료 모델·비용 운영(9장)을 포함한다. **백엔드/프론트엔드 코드 스타일과 검증 명령은 AGENTS.md가 아니라 `docs/guide-backend.md`·`docs/guide-frontend.md`에 있다.**

실제 세션 운영 문서는 이렇게 나뉜다: 코디네이터 운영 지침은 `docs/prompt-master.md`, 워커에게 줄 task spec 형식은 `docs/worker-spec-template.md`, 프로젝트 고유 운영 규칙은 `docs/project-agent-guide.md`, 워커 정지·업스트림 에러 감지는 `scripts/orca-worker-watchdog.py`다. 이 CLAUDE.md는 그 워크플로우를 반복하지 않고 실제 코드베이스 작업에 필요한 것만 담는다.

## Repository layout caveat

이 메인 체크아웃(`/Users/joy/IdeaProjects/ipo-calc`)은 항상 `main` 브랜치를 유지하며 직접 코드를 작업하는 공간이 아니다(`backend/`, `ui/`, `docs/`, `tools/`, `deploy/`가 모두 있다). 실제 작업 위치는 `orca worktree list --json`으로 확인한다. 완료되면 PR로 `main`에 병합하고 브랜치를 지운다(원격에는 `main`만 남긴다). 예전 역할별 작업공간(`backend`·`frontend`·`planning`) 설명(AGENTS.md 7.2절)은 역사 기록이다.

## Commands

### Backend (Spring Boot, `backend/`)

Spring Boot 4.1(Spring 7, Hibernate 7, Security 7, **Jackson 3 — 패키지 `tools.jackson`, 애노테이션만 `com.fasterxml.jackson.annotation`**). 2026-10-14 업그레이드(#426).

```bash
cd backend

# 로컬 PostgreSQL 기동 (최초 1회, 또는 컨테이너 재생성 시)
cd .. && docker compose up -d && cd backend

# 전체 테스트 (DB_PASSWORD 필요 — .envrc에 direnv로 설정돼 있음)
DB_PASSWORD=vibe_local_password ./gradlew test

# 단일 테스트 클래스/메서드
DB_PASSWORD=vibe_local_password ./gradlew test --tests "com.vibeaiteam.investormanagement.controller.investor.InvestorControllerTest"
DB_PASSWORD=vibe_local_password ./gradlew test --tests "com.vibeaiteam.investormanagement.controller.investor.InvestorControllerTest.registerInvestorWithValidRequestReturns201AndResponseFields"

# 캐시된 결과 재사용 없이 강제 재실행 (검증 시 항상 이걸 쓴다)
# --rerun은 여러 task와 쓰면 마지막 task에만 적용되므로 --rerun-tasks를 쓴다
DB_PASSWORD=vibe_local_password ./gradlew test --rerun-tasks

# checkstyle (위반·경고 0건 — build.gradle.kts의 maxWarnings=0이라 경고도 빌드 실패)
DB_PASSWORD=vibe_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks

# 실행
DB_PASSWORD=vibe_local_password APP_ENCRYPTION_KEY=<32바이트 키의 Base64> ./gradlew bootRun
```

테스트는 Testcontainers 대신 `localhost:5433`의 로컬 dev Postgres(`docker-compose.yml`)를 공유해서 쓴다 — 격리가 안 돼 있으니 병렬 테스트 실행 시 주의. checkstyle 플러그인은 `build.gradle.kts`에 설정돼 있고 경고까지 0건을 빌드가 강제한다(규칙은 끄지 않는다, 줄 길이 100자는 한글 포함 실제 문자 수). 커버리지는 `./gradlew test jacocoTestReport`(`build/reports/jacoco/`). 워커별 DB 분리는 `scripts/db-slot.sh`와 `DB_NAME`을 쓴다(`docs/guide-backend.md`).

### Frontend

저장소 루트 `ui/`(React + TypeScript, FSD 구조). 검증: `pnpm run test`·`lint`·`typecheck`·`format:check`·`build`, 커버리지 `pnpm run test:coverage`. 상세는 `docs/guide-frontend.md`.

### E2E·접근성·가이드 캡처·배포

- E2E 22개: `tools/e2e/run.sh`(빈 포트 스택 + DB 슬롯 4, 실패 시 종료 1). 접근성: `DB_PASSWORD=vibe_local_password tools/accessibility-scan/run-scan.sh`(63화면, 하나라도 빠지거나 위반이면 실패). 가이드 스크린샷: `tools/screenshot-guide/run.sh`. 셋 다 슬롯 4를 리셋하므로 동시에 돌리지 않는다.
- 배포: 로컬 Jenkins(`:18081`, 잡 `vibe-deploy`, `Jenkinsfile`)가 테스트·checkstyle·UI 검사·E2E(일회용 DB)·운영 compose 배포·스모크를 한다. **로컬 운영 스택(:18080)은 Jenkins로만 재배포** — `deploy/.env`로 직접 `compose up` 하면 Jenkins가 쓰는 DB 비밀번호와 달라 backend가 깨진다.
- 운영 필수 키: `deploy/.env.example` 참고(`OPENDART_API_KEY`·`DATA_GO_KR_SERVICE_KEY`·`APP_ENCRYPTION_BROKER_KEY` 포함). DB 백업 `scripts/backup-db.sh`·복원 `scripts/restore-db.sh`.
- 키 값이 출력되는 명령(`docker compose config` 등)은 키를 가린 환경으로 돌린다.

## Architecture

### Backend: Epic별 독립 패키지, 공유 인프라는 최초 Epic에 위치

`backend/src/main/java/com/vibeaiteam/`에 Epic마다 별도 최상위 패키지가 있다(`accountauthentication`, `investormanagement`, `securitiesfirmcatalog`, `securitiesaccountmanagement`, ...). 각 패키지 내부는 동일한 레이어 구조를 따른다: `controller`(HTTP) → `service`(비즈니스 로직/트랜잭션) → `domain.model`(JPA 엔티티) → `repository`(Spring Data JPA) → `exception`(+ `@RestControllerAdvice` 핸들러). `infrastructure`는 암호화 등 외부 연동이 필요할 때만 존재한다(예: `securitiesaccountmanagement.infrastructure.encryption`).

**공유 컴포넌트는 `accountauthentication` 패키지(Epic 1, 가장 먼저 만들어짐)에 있고 이후 모든 Epic이 재사용한다:**
- `accountauthentication.util.security.SecurityUtils.getCurrentAccountId()` — 현재 로그인 계정 ID. 계정 소유 데이터가 있는 모든 Epic이 소유권 검증에 쓴다.
- `accountauthentication.controller.dto.{ErrorResponse,SuccessResponse}` — 공통 응답 DTO.
- `AccountAuthenticationApplication`(`@SpringBootApplication`)에 `@ComponentScan`/`@EntityScan`/`@EnableJpaRepositories(basePackages = "com.vibeaiteam")`가 걸려 있어, 새 Epic 패키지를 어디에 추가해도 자동으로 스캔된다.

소유권 검증 패턴은 Epic마다 다르다: `Investor`는 `SecurityUtils`와 1단계 비교로 끝나지만, `SecuritiesAccount`처럼 `Investor`를 거쳐야 하는 하위 리소스는 2단계 확인(대상의 `investorId` → 그 `Investor.accountId` → 현재 계정과 비교)이 필요하다.

### DB: PostgreSQL 전용, Flyway 순차 마이그레이션

`backend/src/main/resources/db/migration/V{n}__*.sql`이 Epic 순서대로 쌓인다. **PostgreSQL 문법만 쓴다** — `GENERATED BY DEFAULT AS IDENTITY`(MySQL의 `AUTO_INCREMENT` 아님), 조건부 유니크는 `CREATE TABLE`에 넣지 않고 별도의 `CREATE UNIQUE INDEX ... WHERE ...` 문으로 분리한다(예: `V4`의 `investor_id당 기본 계좌 1개` 제약). 소프트 삭제 컬럼(`status`, `deleted_at`)이 모든 레코드 테이블의 표준 패턴이다 — 물리 삭제는 어떤 Epic에서도 하지 않는다.

### 계정 인증

세션 기반 인증(Spring Security, `JSESSIONID` 쿠키), CSRF는 전역 비활성화돼 있다 — 이 프로젝트 전체에 브라우저 폼 제출 플로우가 없고 React SPA가 JSON으로 `/auth/*`를 호출하는 방식뿐이기 때문이다(`SecurityConfig`, work-log `docs/work-log/investor-management.md` D-I-006 참고). 미인증 요청은 `AuthenticationEntryPoint`를 통해 401을 반환한다(기본 `formLogin` 리다이렉트 아님).
