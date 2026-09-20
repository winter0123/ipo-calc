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

---

## 2026-09-20 — SF-4: 로그인/로그아웃 API (task_19a56f7ed4e8)

### 완료된 작업
- Test 워커: `AuthControllerTest.java` 작성 (AC-004~008). 5개 테스트 모두 예상대로 실패 확인.
- Implementation 워커: 테스트를 통과시키는 프로덕션 코드 작성
  - `controller/AuthController.java` — POST /auth/login, POST /auth/logout
  - `application/service/AuthService.java` — 로그인 검증/세션 생성/로그아웃
  - `application/service/FailedLoginService.java` — 실패 횟수 증가/계정 잠금을 별도 트랜잭션으로 처리(별도 서비스 빈으로 분리해 self-invocation 문제 회피)
  - `domain/model/account/Account.java` — `isAccountLocked()` 메서드 추가
  - `exception/{AccountLockedException,InvalidCredentialsException}.java` + GlobalExceptionHandler 핸들러 추가
  - `config/security/SecurityConfig.java` — `/auth/**` permitAll 추가

### 중간에 발생한 문제 3건 (Implementation 워커가 스스로 진단·수정)
1. **302 리다이렉트**: `/auth/login`, `/auth/logout`이 SecurityConfig의 permitAll 목록에 없어 인증 요구 → 무한 루프(로그인하려면 인증이 먼저 필요) → `/auth/**`를 permitAll에 추가.
2. **`@Transactional` self-invocation 버그**: 같은 클래스 안에서 `@Transactional` 메서드를 호출하면 Spring AOP 프록시를 우회해 트랜잭션이 적용되지 않음 → 로그인 실패 시 실패 횟수 증가가 예외 발생과 함께 롤백되어 저장 안 됨. TransactionTemplate 시도 후 최종적으로 별도 `@Service` 빈(`FailedLoginService`)으로 분리해 프록시를 통한 정상 호출이 되도록 해결.
3. **compileTestJava 에러**: `Account.isAccountLocked()` 메서드가 없어 컴파일 실패 → 메서드 추가.
- 이번에도 세 문제 모두 워커가 스스로 정확히 진단하고 수정함(Master 개입 없음, 관찰만 함).

### 실행한 검증
- `./gradlew test --tests "*AuthControllerTest"` → 5개 테스트 전부 통과
- `./gradlew test` (전체) → BUILD SUCCESSFUL, 24개 테스트 전부 통과 (Master 직접 재실행 재확인)

### 결과
PASS — SF-4 완료.

### 변경된 파일
- backend/src/main/java/.../controller/AuthController.java (신규)
- backend/src/main/java/.../controller/dto/LoginRequest.java (신규)
- backend/src/main/java/.../application/service/{AuthService,FailedLoginService}.java (신규)
- backend/src/main/java/.../exception/{AccountLockedException,InvalidCredentialsException}.java (신규), GlobalExceptionHandler.java (핸들러 추가)
- backend/src/main/java/.../domain/model/account/Account.java (isAccountLocked() 추가)
- backend/src/main/java/.../config/security/SecurityConfig.java (/auth/** permitAll)
- backend/src/test/java/.../controller/AuthControllerTest.java (신규)

### 다음 단계
SF-5: 데이터 격리(인가) 방어 (task_bd0beb33cfb4) 착수.

---

## 2026-09-20 — SF-5: 데이터 격리(인가) 방어

### 완료된 작업
- [x] `DataIsolationTest.java` 작성 (Test 워커, Nemotron 3 Ultra) — 커밋 `2228595`
- [x] GitHub 이슈 #39 생성 (Git Manager 워커, Nemotron 3 Ultra)

### 문제 3건
1. 테스트 워커가 1차 작성 시 존재하지 않는 쿠키 접근 로직(`new MockHttpServletRequest().getCookies()[0]`)을 넣어 NPE 위험이 있었음 → 워커가 스스로 재실행 결과를 보고 `MockHttpSession`으로 교체해 수정. Master 개입 없음.
2. Git Manager가 `--label "status:in-progress"`를 시도했으나 저장소에 해당 라벨이 없어 실패 → 라벨 없이 재시도해 이슈 생성 성공. 라벨 자체는 부수적이라 그대로 진행.
3. Master가 병렬로 cost-recording 조사 워커를 띄우는 과정에서 모델 ID를 축약형(`nemotron-3-super`)으로 잘못 입력 → opencode가 금지 모델(Nex-N2.5-Pro)로 기본 실행됨을 발견, 즉시 터미널 종료 후 태스크를 ready로 되돌리고 AGENTS.md의 정확한 전체 모델 ID(`openrouter/nvidia/nemotron-3-ultra-550b-a55b:free`)로 재실행. SF-5 작업과는 별개 워크스페이스(main)에서 발생한 사고였음.

### 실행한 검증
- `./gradlew test --tests "*DataIsolationTest"` → 3개 테스트 전부 통과
- `./gradlew test` (전체) → BUILD SUCCESSFUL, 27개 테스트 전부 통과 (Master 직접 재실행 재확인)

### 결과
PASS — SF-5 완료. **별도 Implementation 태스크 불필요** (Master 판단, D-009).

### Master 결정 사항 D-009
SF-2/SF-4에서 이미 구현된 `SecurityConfig`(`anyRequest().authenticated()`, `formLogin`)가 AC-010(무인증 접근 시 401/302 차단)을 이미 만족시킴이 테스트로 확인됨. 새로 작성/수정할 프로덕션 코드가 없으므로 SF-5는 테스트 작성만으로 완료 처리한다. AC-011(계정 간 데이터 격리)은 실제 보호 리소스(투자자 API 등)가 존재하지 않아 이번 Epic 범위에서 테스트 불가 — 후속 Epic(investor-management 등)에서 재검증 필요. 이 결정은 되돌리기 쉬우며(향후 AuthenticationEntryPoint 커스텀 등 필요 시 새 태스크로 추가 가능), 사용자가 다른 의견이 있으면 언제든 변경 가능.

### 변경된 파일
- backend/src/test/java/.../config/security/DataIsolationTest.java (신규)

### 다음 단계
SF-6: 비밀번호 변경 API (task_3c83aa6f4e97) 착수. SF-5 완료 후 이슈 #39 종료 처리 예정.

---

## 2026-09-20 — SF-6: 비밀번호 변경 API (Epic 1 마지막 세부 기능)

### 완료된 작업
- [x] `PasswordChangeControllerTest.java` RED 테스트 작성 (7개 케이스) — 커밋 `e4930ea`
- [x] GitHub 이슈 #40 생성
- [x] `PasswordChangeController`, `PasswordChangeService`, `PasswordChangeRequest`, `SuccessResponse`, `InvalidCurrentPasswordException` 구현
- [x] `SecurityConfig`, `AuthService`, `GlobalExceptionHandler` 수정 — 전체 34개 테스트 GREEN

### 문제 3건 이상 (이번 SF는 특히 난이도가 높아 여러 라운드에 걸쳐 해결됨)
1. **1차 구현 워커가 비용 한도(cost limit)로 중단**: 7개 중 3개만 통과한 상태에서 opencode 세션이 "Exit due to cost limit"로 종료됨. Master가 git diff로 실제 진행 상황(파일별 변경 내역)을 확인한 뒤, 정확히 어디까지 됐는지 파악해서 이어하기 태스크로 재개시킴.
2. **SessionRegistry 인스턴스 불일치**: `SecurityConfig`의 `sessionManagement().maximumSessions(1)` DSL이 `.sessionRegistry(...)`를 명시하지 않으면 자기만의 새 SessionRegistry를 내부적으로 생성한다. 별도로 선언된 `@Bean SessionRegistry` 빈과 다른 인스턴스라서 `PasswordChangeService`가 세션을 expire해도 실제 인증 필터에는 반영되지 않았음(테스트 결과: 401/302 대신 403/404). Master가 직접 SecurityConfig.java를 읽고 원인을 진단해 `.sessionRegistry(sessionRegistry())`를 추가하도록 정확한 수정 지시를 내림 — 워커가 두 번 연속(다른 모델로도) 이 특정 버그를 스스로 찾지 못하고 반복적으로 멈춰서(stall) Master가 개입.
3. **AuthService가 SessionAuthenticationStrategy를 건너뜀**: 커스텀 로그인(`AuthService.createSession()`)이 SecurityContext를 수동으로 만들고 `securityContextRepository.saveContext()`만 호출해서, Spring Security 표준 인증 필터를 거치지 않아 로그인해도 세션이 SessionRegistry에 전혀 등록되지 않았음(테스트 결과: 404 — 인증 필터를 그냥 통과해버림). Master가 근본 원인을 직접 진단(AuthService.java와 SecurityConfig.java를 대조 분석)해서, `SessionAuthenticationStrategy` 빈을 주입받아 `createSession()` 안에서 `onAuthentication()`을 명시적으로 호출하도록 정확한 수정 지시를 내림.
4. **만료된 세션의 기본 응답이 200**: 3번 수정 후 세션 등록/만료 자체는 정상 동작했지만(404→200으로 변화), Spring Security의 `ConcurrentSessionFilter`는 `expiredUrl`/`expiredSessionStrategy`가 없으면 기본적으로 상태 코드 200으로 메시지만 응답한다. Master가 `.expiredUrl("/login?expired")`를 추가하도록 지시해서 302로 리다이렉트되게 만들어 최종 해결.
5. **무료 모델의 반복적 stall**: 이번 SF 전체 과정에서 워커가 "이제 ~하겠다"고 말만 하고 실제 도구 호출(tool call) 없이 멈추는 현상이 여러 번 발생(Implementation 태스크에서 특히 잦음). AGENTS.md 23.7의 컨텍스트 초과 대응과는 다른 패턴(컨텍스트는 낮은데도 멈춤) — Master가 매번 `terminal read`로 실제 화면과 `git diff`로 실제 파일 변경을 대조 확인해서, 진짜 멈춘 건지 그냥 느린 건지 구분한 뒤에만 개입(interrupt → 모델 교체 재시도)했다.

### 실행한 검증
- `./gradlew test --tests "*PasswordChangeControllerTest*"` → 7개 전부 통과
- `./gradlew test` (전체) → BUILD SUCCESSFUL, 34개 테스트 전부 통과 (Master 직접 재실행 재확인)

### 결과
PASS — SF-6 완료. **Epic 1(account-authentication) 전체 완료.**

### 변경된 파일
- backend/src/main/java/.../controller/PasswordChangeController.java (신규)
- backend/src/main/java/.../controller/dto/{PasswordChangeRequest,SuccessResponse}.java (신규)
- backend/src/main/java/.../application/service/PasswordChangeService.java (신규)
- backend/src/main/java/.../exception/InvalidCurrentPasswordException.java (신규), GlobalExceptionHandler.java (핸들러 추가)
- backend/src/main/java/.../application/service/AuthService.java (SessionAuthenticationStrategy 주입 및 onAuthentication 호출 추가)
- backend/src/main/java/.../config/security/SecurityConfig.java (sessionRegistry 명시적 연결, expiredUrl 추가)
- backend/src/test/java/.../controller/PasswordChangeControllerTest.java (신규)

### 다음 단계
Epic 1 PR 생성 및 병합(사람 승인 필요 - 대규모 Epic 단위 PR이므로 12.4-1의 "일상적 기능 완료 PR" 예외에 해당하지 않음). PR 머지 확인 후 `account-authentication` 워크스페이스를 `backend` 워크스페이스로 이관(12.4 전환기 메모 참고). 이후 Epic 2(investor-management) 착수는 `backend` 워크스페이스에서 새 브랜치로 시작.
