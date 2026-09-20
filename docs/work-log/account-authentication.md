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

---

## 2026-09-20 — SF-2: Account 도메인 및 보안 기초 설정 (task_e3810229f545)

### 완료된 작업
- Test 워커(Laguna S 2.1): 실패 테스트 4개 작성 — AccountTest, AccountRepositoryTest, SecurityConfigTest, CustomUserDetailsServiceTest (컴파일 실패로 red 단계 확인)
- Implementation 워커(Nemotron 3 Ultra): 테스트를 통과시키는 프로덕션 코드 작성
  - `domain/model/account/Account.java` — JPA 엔티티, Lombok 빌더
  - `repository/account/AccountRepository.java` — findByEmail, existsByEmail
  - `src/main/resources/db/migration/V1__init_account.sql` — accounts 테이블 + 인덱스
  - `config/security/SecurityConfig.java` — SecurityFilterChain, PasswordEncoder(DelegatingPasswordEncoder 수동 구성)
  - `service/auth/CustomUserDetailsService.java` — UserDetailsService 구현

### 중간에 발생한 문제 3건 (모두 Master가 진단·해결)
1. **DB 포트 충돌**: 이 프로젝트와 무관한 다른 postgres 컨테이너가 호스트 포트 5432를 이미 점유 중이라 `vibe-ai-team-postgres`가 한 번도 기동되지 못함. 사용자 확인 후 호스트 포트를 5433으로 변경(`docker-compose.yml`, `docs/setup-history.md` 기록). `application.yml`의 datasource URL도 5433으로 수정.
2. **DB_PASSWORD 미전달**: Epic worktree에는 `.envrc`가 없어(gitignore 대상이라 worktree 생성 시 자동 복사 안 됨) 워커 터미널에 `DB_PASSWORD` 환경변수가 전달되지 않음. main 워크트리의 `.envrc`를 epic worktree에도 복사해 해결(둘 다 커밋 대상 아님).
3. **Flyway가 PostgreSQL 17 미지원**: Spring Boot 3.3.4가 관리하는 flyway-core 10.10.0이 PostgreSQL 17을 인식하지 못함(`Unsupported Database: PostgreSQL 17.11`). `flyway-core:10.20.1`로 버전을 올리고, Flyway 10부터 별도 모듈로 분리된 `flyway-database-postgresql:10.20.1`을 추가 의존성으로 넣어 해결.
4. (부수) `@DataJpaTest`가 기본적으로 임베디드 DB로 치환을 시도하다 실패 → `@AutoConfigureTestDatabase(replace = Replace.NONE)` + `@ActiveProfiles("test")` + `application-test.yml`로 실제 개발용 Postgres를 그대로 사용하도록 구성.

### 알려진 이슈 (다음 리뷰 단계에서 확인 필요)
- `backend/src/test/resources/application-test.yml`에 개발용 DB 비밀번호(`vibe_local_password`)가 평문으로 들어가 있음. `docker-compose.yml`에도 이미 같은 값이 평문으로 있어 신규 노출 위험 증가는 적지만, 보안 리뷰(C그룹) 단계에서 `${DB_PASSWORD}` 환경변수 참조로 통일할지 확인 필요.
- 테스트가 Testcontainers(이미 테스트 의존성에는 포함됨)를 쓰지 않고 로컬에 떠 있는 실제 dev DB에 의존함 — CI 환경이나 여러 명이 동시에 테스트를 돌릴 때 격리가 안 될 수 있음. 후속 개선 후보.

### 실행한 검증
- `./gradlew test --tests "*AccountTest" --tests "*AccountRepositoryTest" --tests "*SecurityConfigTest" --tests "*CustomUserDetailsServiceTest"` → 4개 클래스, 14개 테스트 전부 통과 (Master가 XML 결과 직접 확인)
- `./gradlew test` (전체) → BUILD SUCCESSFUL (Master 직접 재실행 재확인)

### 결과
PASS — SF-2 완료.

### 변경된 파일
- backend/build.gradle.kts (flyway 버전/모듈 추가)
- backend/src/main/resources/application.yml (datasource URL 포트 수정)
- backend/src/main/java/com/vibeaiteam/accountauthentication/domain/model/account/Account.java
- backend/src/main/java/com/vibeaiteam/accountauthentication/repository/account/AccountRepository.java
- backend/src/main/java/com/vibeaiteam/accountauthentication/config/security/SecurityConfig.java
- backend/src/main/java/com/vibeaiteam/accountauthentication/service/auth/CustomUserDetailsService.java
- backend/src/main/resources/db/migration/V1__init_account.sql
- backend/src/test/resources/application-test.yml (신규)
- backend/src/test/java/.../AccountRepositoryTest.java (테스트 DB 설정 어노테이션 추가)
- docker-compose.yml, docs/setup-history.md (포트 변경, main에 커밋)

### 다음 단계
SF-3: 회원가입 API (task_5fb89b0e8bc5) 착수.

---

## 2026-09-20 — SF-3: 회원가입 API (task_5fb89b0e8bc5)

### 완료된 작업
- Test 워커: `AccountControllerTest.java` 작성 (AC-001~003 + 세션 생성). 5개 테스트 모두 예상대로 실패(Controller 없음/404) 확인.
- Implementation 워커: 테스트를 통과시키는 프로덕션 코드 작성
  - `controller/AccountController.java` — POST /accounts
  - `controller/dto/` — SignupRequest, SignupResponse, ErrorResponse
  - `exception/` — DuplicateEmailException, ValidationFailedException, GlobalExceptionHandler(@RestControllerAdvice)
  - `application/service/SignupService.java` — 중복 이메일 검증, 비밀번호 해시, 저장, 세션 생성
  - `config/security/SecurityConfig.java` — `/accounts`에 CSRF 예외 추가

### 중간에 발생한 문제 3건 (Implementation 워커가 스스로 진단·수정)
1. **403 Forbidden**: `/accounts`가 CSRF 보호 대상이라 POST 요청이 차단됨 → SecurityConfig에 CSRF 예외 경로 추가로 해결.
2. **세션 쿠키 미발급**: `HttpSession`을 직접 생성해도 Spring Security의 `SecurityContextRepository`에 컨텍스트를 저장하지 않으면 세션이 제대로 커밋되지 않음 → `securityContextRepository.saveContext(...)` 호출로 해결.
3. **한글 에러 메시지 인코딩 깨짐**: 기본 응답 Content-Type에 UTF-8 charset이 명시되지 않아 MockMvc가 ISO-8859-1로 디코딩 → `GlobalExceptionHandler`에서 `MediaType("application","json",StandardCharsets.UTF_8)`을 명시적으로 지정해 해결.
- 이번 SF에서는 세 문제 모두 워커가 스스로 원인을 정확히 진단하고 수정함(Master 개입 없음). Nvidia 쪽 일시 과부하(503)로 몇 차례 재시도가 있었으나 자동 복구됨.

### 실행한 검증
- `./gradlew test --tests "*AccountControllerTest"` → 5개 테스트 전부 통과
- `./gradlew test` (전체) → BUILD SUCCESSFUL, 19개 테스트 전부 통과 (Master 직접 재실행 재확인)

### 결과
PASS — SF-3 완료.

### 변경된 파일
- backend/src/main/java/.../controller/AccountController.java (신규)
- backend/src/main/java/.../controller/dto/{SignupRequest,SignupResponse,ErrorResponse}.java (신규)
- backend/src/main/java/.../exception/{DuplicateEmailException,ValidationFailedException,GlobalExceptionHandler}.java (신규)
- backend/src/main/java/.../application/service/SignupService.java (신규)
- backend/src/main/java/.../config/security/SecurityConfig.java (CSRF 예외 추가)
- backend/src/test/java/.../controller/AccountControllerTest.java (신규)

### 다음 단계
SF-4: 로그인/로그아웃 API (task_19a56f7ed4e8) 착수.
