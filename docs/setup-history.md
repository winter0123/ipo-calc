# 설치 이력 및 환경 복구 절차

> 이 문서의 목적: DB가 날아가거나 로컬/서버 환경이 망가졌을 때, 또는 다른 서버로 이전할 때 **이 문서만 보고 처음부터 다시 세팅**할 수 있게 하기 위함이다. 새 프로그램/인프라를 설치하거나 DB 마이그레이션을 실행하면 그 즉시(몰아서 적지 않고) 이 문서의 "1. 설치 이력"에 추가한다. 시크릿(비밀번호, API 키 등)은 절대 값 자체를 적지 않고, 필요한 환경변수 이름만 `.env.example` 참조로 안내한다.

---

## 1. 설치 이력 (append-only, 시간순)

- 2026-09-16 — PostgreSQL 17 설치 (Docker Compose) — 이유: 메인 DB — 명령: `docker compose up -d postgres` (커밋 `8886c41`)
- 2026-09-20 — `docs/setup-history.md` 신설 — 이유: 설치/환경 이력을 한 곳에 모아 서버 이전·복구를 빠르게 하기 위함(사용자 지시)

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

### 백엔드 (아직 스캐폴딩 없음 — 2026-09-20 기준)
5. 2026-09-20에 기존 Java/Spring Boot 스캐폴딩(`backend/`)을 삭제하고 처음부터 다시 시작하기로 결정함(과거 세션 산출물, 미승인 상태였음). Epic 1(account-authentication) 구현이 시작되면 이 절차에 실제 빌드/실행 명령을 추가한다.

### 프론트엔드
6. 아직 프론트엔드 코드 없음. 생성되면 `npm install && npm run dev` 형태로 이 절차에 추가한다.

---

## 3. 참고

- 인프라 정의 파일: `docker-compose.yml` (저장소 루트)
- Docker/PostgreSQL 외 추가 인프라(Redis 등)가 필요해지면 그 즉시 위 1~2절에 추가한다.
- Git Manager는 새 설치/마이그레이션이 실제 설치된 것과 이 문서가 일치하는지 주기적으로 감사한다(AGENTS.md 9.2 참고).
