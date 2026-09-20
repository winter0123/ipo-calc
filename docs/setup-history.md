# 설치 이력 및 환경 복구 절차

> 이 문서의 목적: DB가 날아가거나 로컬/서버 환경이 망가졌을 때, 또는 다른 서버로 이전할 때 **이 문서만 보고 처음부터 다시 세팅**할 수 있게 하기 위함이다. 새 프로그램/인프라를 설치하거나 DB 마이그레이션을 실행하면 그 즉시(몰아서 적지 않고) 이 문서의 "1. 설치 이력"에 추가한다. 시크릿(비밀번호, API 키 등)은 절대 값 자체를 적지 않고, 필요한 환경변수 이름만 `.env.example` 참조로 안내한다.

---

## 1. 설치 이력 (append-only, 시간순)

- 2026-09-16 — PostgreSQL 17 설치 (Docker Compose) — 이유: 메인 DB — 명령: `docker compose up -d postgres` (커밋 `8886c41`)
- 2026-09-20 — `docs/setup-history.md` 신설 — 이유: 설치/환경 이력을 한 곳에 모아 서버 이전·복구를 빠르게 하기 위함(사용자 지시)
- 2026-09-20 — Gradle 9.7.1 설치 (Homebrew, `brew install gradle`) — 이유: SF-1(프로젝트 초기 셋업)에서 `backend/`의 Gradle Wrapper(gradle-wrapper.jar)를 생성하려면 시스템 Gradle이 필요했음. 부수 설치: gradle-completion, openjdk@25(Gradle 자체 구동용, 프로젝트 빌드는 여전히 Java 21 사용), glib/harfbuzz/xz/little-cms2(의존성) — 명령: `brew install gradle`
- 2026-09-20 — `backend/` Gradle Wrapper 생성 — 명령: `cd backend && gradle wrapper`(설치된 시스템 Gradle 9.7.1 버전 그대로 사용, JAVA_HOME을 Java 21로 지정) — 이후 `gradle` 시스템 설치 없이 `./gradlew`만으로 빌드 가능. `./gradlew build --dry-run`으로 설정 단계 통과 확인(BUILD SUCCESSFUL). Gradle 10 호환성 관련 deprecation 경고가 있어 추후 재확인 필요.

---

## 2. 환경 복구 절차 (현재 기준 최신 상태로 계속 갱신, 순서대로 실행하면 전체 환경이 재현되어야 함)

### 사전 준비
1. 저장소 클론: `git clone https://github.com/winter0123/vibe-ai-team.git`
2. 환경변수 파일 준비: `.envrc`(direnv 사용 중)에 아래 값을 채운다. 실제 값은 이 문서에 적지 않는다.
   - `JAVA_HOME` — 로컬 JDK 21 설치 경로 (예: Microsoft Build of OpenJDK 21)
   - `DB_PASSWORD` — PostgreSQL 비밀번호 (docker-compose.yml의 `POSTGRES_PASSWORD`와 일치해야 함)
3. `direnv allow` (direnv 미설치 시 `.envrc`의 `export`를 셸 프로파일에 직접 반영)

### 인프라 기동
4. `docker compose up -d postgres` — PostgreSQL 17 컨테이너 기동 (컨테이너명 `vibe-ai-team-postgres`, DB명 `vibe_ai_team`, 사용자 `vibe`, 포트 `5432`, 볼륨 `postgres_data`)

### 백엔드
5. Gradle 설치: `brew install gradle` (시스템에 Gradle이 없으면 `backend/`의 Gradle Wrapper가 이미 커밋되어 있으므로 보통 불필요 — wrapper가 없거나 재생성해야 할 때만 설치)
6. `cd backend && ./gradlew build` — Java 21, Spring Boot 3.3.4 기반 프로젝트 빌드(현재 `winter0123/account-authentication` 브랜치에서 진행 중, main에는 아직 merge 안 됨)
7. 참고: 2026-09-20에 기존 `backend/` 스캐폴딩(과거 세션 산출물, 미승인 상태)을 한 번 삭제하고 Epic 1(account-authentication) SF-1에서 처음부터 재생성함.

### 프론트엔드
6. 아직 프론트엔드 코드 없음. 생성되면 `npm install && npm run dev` 형태로 이 절차에 추가한다.

---

## 3. 참고

- 인프라 정의 파일: `docker-compose.yml` (저장소 루트)
- Docker/PostgreSQL 외 추가 인프라(Redis 등)가 필요해지면 그 즉시 위 1~2절에 추가한다.
- Git Manager는 새 설치/마이그레이션이 실제 설치된 것과 이 문서가 일치하는지 주기적으로 감사한다(AGENTS.md 9.2 참고).
