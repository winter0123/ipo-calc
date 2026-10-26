# 설치 이력 및 환경 복구 절차

> 이 문서의 목적: DB가 날아가거나 로컬/서버 환경이 망가졌을 때, 또는 다른 서버로 이전할 때 **이 문서만 보고 처음부터 다시 세팅**할 수 있게 하기 위함이다. 새 프로그램/인프라를 설치하거나 DB 마이그레이션을 실행하면 그 즉시(몰아서 적지 않고) 이 문서의 "1. 설치 이력"에 추가한다. 시크릿(비밀번호, API 키 등)은 절대 값 자체를 적지 않고, 필요한 환경변수 이름만 `.env.example` 참조로 안내한다.

---

## 1. 설치 이력 (append-only, 시간순)

- 2026-09-16 — PostgreSQL 17 설치 (Docker Compose) — 이유: 메인 DB — 명령: `docker compose up -d postgres` (커밋 `8886c41`)
- 2026-09-20 — `docs/setup-history.md` 신설 — 이유: 설치/환경 이력을 한 곳에 모아 서버 이전·복구를 빠르게 하기 위함(사용자 지시)
- 2026-09-20 — Gradle 9.7.1 설치 (Homebrew, `brew install gradle`) — 이유: SF-1(프로젝트 초기 셋업)에서 `backend/`의 Gradle Wrapper(gradle-wrapper.jar)를 생성하려면 시스템 Gradle이 필요했음. 부수 설치: gradle-completion, openjdk@25(Gradle 자체 구동용, 프로젝트 빌드는 여전히 Java 21 사용), glib/harfbuzz/xz/little-cms2(의존성) — 명령: `brew install gradle`
- 2026-09-20 — `backend/` Gradle Wrapper 생성 — 명령: `cd backend && gradle wrapper`(설치된 시스템 Gradle 9.7.1 버전 그대로 사용, JAVA_HOME을 Java 21로 지정) — 이후 `gradle` 시스템 설치 없이 `./gradlew`만으로 빌드 가능. `./gradlew build --dry-run`으로 설정 단계 통과 확인(BUILD SUCCESSFUL). Gradle 10 호환성 관련 deprecation 경고가 있어 추후 재확인 필요.
- 2026-09-20 — **PostgreSQL 호스트 포트를 5432 → 5433으로 변경**(사용자 결정) — 이유: SF-2 테스트(`@SpringBootTest`) 실행 중 이 프로젝트와 무관한 다른 Postgres 컨테이너(`postgres-db`, postgis 이미지, 다른 프로젝트용으로 추정)가 이미 호스트 포트 5432를 점유하고 있어 `ipo-calc-postgres` 컨테이너가 한 번도 기동되지 못했음을 발견. 컨테이너 내부 포트는 5432 그대로, 호스트 노출 포트만 5433으로 변경(`docker-compose.yml`). 다른 컨테이너는 손대지 않음. `backend/src/main/resources/application.yml`의 datasource URL도 `localhost:5433`으로 함께 수정 필요(SF-2 구현 워커가 반영).
- 2026-09-22 — **GitHub Issue를 Orca 태스크와 병행 운영하기로 결정**(사용자 지시, AGENTS.md 8.4장) — 이유: Orca 태스크는 실행 단위일 뿐 사람이 보기 편한 진행 현황 창구가 아니므로, 사용자가 GitHub에서 Epic 단위 진행 상황을 볼 수 있게 함. `status:planned/in-progress/review/changes-requested/approved` 라벨 신설. 기존에 방치돼 있던 Epic 2~4 이슈(#64/#71/#77)를 실제 완료 상태에 맞춰 정리하고, Epic 5~16에 대한 이슈(#122~#133)를 각 Epic의 실제 진행 상태(완료/진행중/계획됨)에 맞춰 신규 생성·정리함. 이후로는 Epic/SF 상태가 바뀔 때마다 그 즉시 이슈 상태도 함께 갱신한다.

---

## 2. 환경 복구 절차 (현재 기준)

### 사전 준비
1. 저장소 클론: `git clone https://github.com/winter0123/ipo-calc.git`
2. 로컬 개발용 `.envrc`에 `JAVA_HOME`, `DB_PASSWORD`를 설정한다. 실제 값은 저장소에 기록하지 않는다.
3. 프런트엔드 도구는 `ui/package.json`의 package manager 기준으로 설치한다.

### 로컬 개발 데이터베이스

루트 Compose는 개발용 PostgreSQL만 실행한다. 운영 앱이나 운영 DB를 Compose로 기동하지 않는다.

```bash
docker compose up -d postgres
```

- 컨테이너: `ipo-calc-postgres-dev`
- DB/계정: `ipo_calc` / `ipo`
- 호스트 포트: `5433` (컨테이너 내부 `5432`)
- 데이터 볼륨: `postgres_data`

### 백엔드와 프런트엔드

- 백엔드 빌드·개발 실행: `cd backend && ./gradlew build` 또는 `./gradlew bootRun`
- 프런트엔드: `cd ui && pnpm install && pnpm dev`
- 백엔드 테스트 슬롯 DB는 아래 테스트용 슬롯 DB 절차를 사용한다.

### 테스트용 슬롯 DB (2026-09-30 추가)

같은 Postgres 컨테이너(`ipo-calc-postgres-dev`) 안에 슬롯 DB `ipo_slot_1`~`ipo_slot_4`를 두고 백엔드 테스트를 격리한다. 슬롯 1~3은 워커, 4는 Master 검증용이다.

1. 슬롯 초기화(없으면 생성): `scripts/db-slot.sh reset <1-4>` — 마지막 줄에 `DB_NAME=ipo_slot_N`을 출력한다. `scripts/db-slot.sh list`로 존재 여부를 본다.
2. 테스트 실행: `DB_NAME=ipo_slot_N DB_PASSWORD=<로컬비밀번호> ./gradlew test`
3. `DB_NAME`을 주지 않으면 기본 DB(`ipo_calc`)를 쓴다. 이 DB는 개발 전용으로 유지한다.

### 운영 서버

운영은 Docker를 사용하지 않는다. Ubuntu 호스트의 Java·PostgreSQL·systemd·Caddy 구성과 Ansible 재배포 순서는 개인 로컬 가이드 `docs/deployment/ubuntu-home-server.md`를 따른다. 이 개인 가이드는 접속 정보가 포함될 수 있어 Git에 올리지 않는다.

---

## 3. 참고

- 인프라 정의 파일: `docker-compose.yml` (저장소 루트)
- Docker/PostgreSQL 외 추가 인프라(Redis 등)가 필요해지면 그 즉시 위 1~2절에 추가한다.
- Git Manager는 새 설치/마이그레이션이 실제 설치된 것과 이 문서가 일치하는지 주기적으로 감사한다(AGENTS.md 9.2 참고).
