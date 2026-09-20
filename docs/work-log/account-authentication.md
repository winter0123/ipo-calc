# Work Log: account-authentication (Epic 1)

## 2026-09-20 — Phase 0 (조사/기획/설계)

### 완료 작업
- [x] `docs/research/account-authentication.md` — Research 워커(Nemotron 3 Ultra) 작성, Master 승인
- [x] `docs/spec/account-authentication.md` — Planning 워커(Nemotron 3 Ultra) 작성, Master 승인
- [x] `docs/architecture/account-authentication.md` — Architecture 워커(Nemotron 3 Super) 작성, Master 승인
- [x] GitHub 이슈 #35 생성 및 종료 (Git Manager 워커, Nemotron 3 Ultra)

### 확인 사항
- 세션 기반 인증(HttpSession) 채택, JWT는 MVP 이후 검토
- API 5개 엔드포인트: POST /accounts, POST /auth/login, POST /auth/logout, POST /auth/password-change, GET /auth/session-status
- Account 테이블: email(unique), password_hash, failed_login_attempts, locked_until, created_at, updated_at, last_login_at

### Master 결정 사항 (architecture.md의 "결정 필요 사항" D-001~D-008에 대한 판단)

기술적 구현 세부사항이며 되돌리기 쉬운 초기 설계 수준이라 Master가 MVP 기준으로 결정. 사용자가 다른 의견이 있으면 구현 전에 언제든 변경 가능.

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-001 | 비밀번호 정책 강도 | 최소 8자 + 영문/숫자/특수문자 중 2종류 이상 | Spring Security 일반 권장 수준, 과하지 않음 |
| D-003 | 다중 기기 동시 로그인 | 제한 없음(여러 세션 허용) | MVP 단순화, 나중에 `maximumSessions`로 제한 추가 가능 |
| D-004 | Remember Me 포함 여부 | **MVP 제외** | spec.md 제외 범위와 정합성(2단계 인증 등과 같은 이유로 후순위), 토큰 관리 복잡도 회피 |
| D-005 | 계정 잠금 해제 방식 | 일정 시간(30분) 경과 후 자동 해제 | 관리자 화면이 없는 MVP에 적합, `locked_until` 필드로 이미 지원 가능한 설계 |
| D-006 | 세션 타임아웃 | 30분 | 금융 데이터 서비스로서 과도하게 길지 않은 일반적인 값 |
| D-007 | 비밀번호 변경 후 세션 처리 | 변경한 계정의 다른 모든 세션 무효화 | 보안 모범 사례(비밀번호 유출 대응) |
| D-008 | 로그인 실패 카운터 저장소 | DB(`Account.failed_login_attempts`) | architecture.md의 DB 설계에 이미 반영되어 있어 별도 결정 불필요 |

---

## 2026-09-20 — Epic worktree 및 breakdown

### 완료 작업
- [x] Epic worktree 생성 (`winter0123/account-authentication` 브랜치 — `feature/`가 아니라 Orca 기본 네이밍, AGENTS.md 12장 정정 반영)
- [x] `docs/breakdown-research/account-authentication.md` — backend/ 전체가 삭제되어 완전히 빈 상태임을 확인
- [x] `docs/breakdown/account-authentication.md` — 세부기능 6개로 확정(최초 제안 10개에서 통합)
- [x] Orca Epic 티켓(task_8126226e91ac) + Sub-ticket 6개(SF-1~6) 생성, 의존성 체인 연결
- [x] GitHub 이슈 #36 생성 (SF-1 추적용)

### 남은 작업
- [ ] SF-2~6 순차 구현
- [ ] Epic 완료 후 PR 생성 및 merge

---

## 2026-09-20 — SF-1: 프로젝트 초기 셋업 (task_02c8ed8e4386)

### 완료된 작업

**1. backend/ 디렉토리 구조 생성**
- Spring Boot 3.3.4, Java 21, Gradle Kotlin DSL 기반 프로젝트 골격 생성

**2. 빌드 설정 파일**
- `build.gradle.kts`: spring-boot-starter-web/data-jpa/security/validation, postgresql, flyway-core, spring-boot-starter-test, spring-security-test, lombok
- `settings.gradle.kts`: rootProject.name = "account-authentication"

**3. Gradle Wrapper**
- 최초 구현 워커가 gradlew/gradlew.bat/gradle-wrapper.properties는 생성했으나 시스템에 Gradle이 없어 gradle-wrapper.jar 생성 불가로 보고(정직하게 막힘을 보고 — 임의로 인터넷에서 바이너리를 내려받지 않음)
- Master가 `brew install gradle`로 Gradle 9.7.1 설치(docs/setup-history.md 기록) 후 `gradle wrapper` 실행하여 jar 생성 완료

**4. 버그 발견 및 수정**: 최초 구현 워커가 build.gradle.kts에 Kotlin JVM 플러그인(`kotlin("jvm")`, `kotlin("plugin.spring")`)과 `kotlin("kapt")("org.projectlombok:lombok")`을 잘못 추가함("Gradle Kotlin DSL" = 빌드 스크립트 문법을 "앱을 Kotlin으로 작성"으로 오해). Gradle 9.7.1에서 파싱 에러(`TaskContainer.invoke Unresolved reference`)로 빌드 실패. 별도 버그수정 티켓(task_7bc9f5524aa4)으로 Implementation 워커가 제거, 순수 Java 프로젝트로 정정.

**5. 애플리케이션 설정**
- `application.yml`: docker-compose.yml PostgreSQL 설정 반영(DB vibe_ai_team, 사용자 vibe, 포트 5432, 비밀번호는 `${DB_PASSWORD}` 환경변수 참조, 하드코딩 없음), Flyway 활성화, JPA ddl-auto: validate

**6. 나머지**
- `src/main/resources/db/migration/.gitkeep` (Flyway 마이그레이션 위치, SF-2에서 V1 작성 예정)
- `AccountAuthenticationApplication.java` (@SpringBootApplication 진입점)
- architecture.md 레이어 구조에 맞춘 10개 패키지(.gitkeep으로 자리 표시)

### 실행한 검증
- `./gradlew build --dry-run` → BUILD SUCCESSFUL (Master 직접 실행, 2차 확인)
- `./gradlew compileJava` → BUILD SUCCESSFUL (실제 컴파일 확인, Master 직접 실행)

### 결과
PASS — SF-1 완료. Gradle 10 호환성 관련 deprecation 경고가 있어 추후 재확인 필요(현재는 빌드에 영향 없음).

### 변경된 파일
- backend/build.gradle.kts, settings.gradle.kts, gradlew, gradlew.bat
- backend/gradle/wrapper/gradle-wrapper.{jar,properties}
- backend/src/main/resources/application.yml, db/migration/.gitkeep
- backend/src/main/java/com/vibeaiteam/accountauthentication/AccountAuthenticationApplication.java
- backend/src/main/java/com/vibeaiteam/accountauthentication/{controller,application/service,domain,domain/model/account,repository,repository/account,infrastructure,config/security,service/auth,util/security}/.gitkeep

### 다음 단계
SF-2: Account 도메인 및 보안 기초 설정 (task_e3810229f545) 착수.

### 참고: 워커의 work-log 작성 방식 문제
구현 워커가 이 파일을 이어쓰지 않고 전체를 자기 내용으로 덮어써서 Phase 0 기록이 유실될 뻔함(Master가 이 커밋 전에 병합·복구). AGENTS.md에 "work-log는 항상 append, 기존 내용 유지 후 새 섹션 추가"를 명시적으로 추가함.
