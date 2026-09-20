# 계정 인증 및 인가 세부 기능 분해 (account-authentication)

## 세부 기능 목록

| 순서 | 세부기능명 | 의존성 |
|------|------------|--------|
| 1 | 프로젝트 초기 셋업 | 없음 |
| 2 | Account 도메인 및 보안 기초 설정 | 1 |
| 3 | 회원가입 API | 2 |
| 4 | 로그인/로그아웃 API | 3 |
| 5 | 데이터 격리(인가) 방어 | 4 |
| 6 | 비밀번호 변경 API | 4 |

## 세부 기능별 상세

### 1. 프로젝트 초기 셋업
**범위**
- 포함: Spring Boot 프로젝트 초기 생성 (Gradle Kotlin DSL, Java 21), Gradle Wrapper 생성, application.yml에 docker-compose.yml의 PostgreSQL 설정 반영, Flyway 마이그레이션 기초 설정 (의존성 추가 및 초기 마이그레이션 디렉토리 구조)
- 불포함: 구체적인 Account 엔티티 정의, Security 설정 구현, API 엔드포인트 구현

**Acceptance Criteria**
- Given: 빈 프로젝트 디렉토리
- When: 프로젝트 초기 셋업을 수행한다
- Then: 
  - build.gradle.kts가 존재하고 Spring Boot 3.x, Java 21, Gradle Kotlin DSL을 사용한다 (AC-참조: 연구 문서에서 Gradle Wrapper 필수 및 Java 21 고정)
  - ./gradlew wrapper 실행 시 gradlew 스크립트가 생성된다 (연구 문서: Gradle Wrapper 필수)
  - application.yml에 spring.datasource 설정이 docker-compose.yml과 일치하여 주입된다 (architecture.md: DB 설정)
  - src/main/resources/db/migration 디렉토리가 존재하며 Flyway가 설정된다 (연구 문서: Flyway 마이그레이션 기초 설정)
  - 의존성: spring-boot-starter-web, spring-boot-starter-data-jpa, postgresql-driver, flyway-core가 build.gradle.kts에 포함된다 (연구 문서: 필수 의존성)

**구현 순서 근거(의존성)**: 첫 번째 세부 기능으로, 이후 모든 기능의 기반이 되는 프로젝트 환경을 구축해야 하므로 의존성이 없음.

**대응 Sub-ticket ID**: TBD

### 2. Account 도메인 및 보안 기초 설정
**범위**
- 포함: Account 엔티티 (id, email, password_hash, name, failed_login_attempts, locked_until, created_at, updated_at, last_login_at) 정의, AccountRepository 인터페이스 생성, V1__init_account.sql을 통한 Flyway 초기 마이그레이션, SecurityFilterChain 빈을 통한 기본 보안 설정 (인증 필수 경로, 폼 로그인, 로그아웃, 세션 관리, CSRF, Remember Me), PasswordEncoder 빈 (DelegatingPasswordEncoder), CustomUserDetailsService 구현 (UserDetailsService 구현 및 Account 엔티티와 매핑)
- 불포함: 구체적인 API 엔드포인트 구현 (회원가입, 로그인 등), 서비스 레이어 비즈니스 로직

**Acceptance Criteria**
- Given: 프로젝트 초기 셋업이 완료된 상태
- When: Account 도메인 및 보안 기초 설정을 수행한다
- Then:
  - Account 엔티티가 정의되어 architecture.md의 테이블 구조와 일치한다 (architecture.md: Account 테이블)
  - AccountRepository가 JpaRepository를 상속하여 생성된다
  - V1__init_account.sql이 계정 테이블을 생성하고 인덱스를 적용한다 (architecture.md: 인덱스)
  - SecurityFilterChain이 설정되어 인증이 필요한 경로를 보호하고 폼 로그인/로그아웃이 작동한다 (research 문서: SecurityFilterChain 표준 설정)
  - PasswordEncoder 빈이 DelegatingPasswordEncoder를 반환한다 (research 문서: 비밀번호 저장 표준 방법)
  - CustomUserDetailsService가 Account 엔티티를 기반으로 UserDetails를 반환한다 (research 문서: DaoAuthenticationProvider 구조)
  - 의존성: spring-boot-starter-security가 build.gradle.kts에 추가된다 (architecture.md: 의존성)

**구현 순서 근거(의존성)**: 프로젝트 초기 셋업(1)에 의존함. Account 엔티티와 Repository가 있어야 후에 API를 구현할 수 있으며, 보안 기초가 있어야 인증/인가 로직을 구현할 수 있음.

**대응 Sub-ticket ID**: TBD

### 3. 회원가입 API
**범위**
- 포함: POST /accounts 엔드포인트 구현, 이메일 중복 검증 (AccountRepository.existsByEmail), 비밀번호 해시 저장 (PasswordEncoder.encode), 성공 시 자동 로그인 (세션 생성) 및 대시보드 리다이렉트, 입력값 검증 (이메일 형식, 비밀번호 정책)
- 불포함: 로그인/로그아웃 엔드포인트, 데이터 격리 로직, 비밀번호 변경 API

**Acceptance Criteria**
- Given: Account 도메인 및 보안 기초 설정이 완료된 상태
- When: POST /accounts 요청을 처리한다
- Then:
  - 유효한 이메일, 비밀번호(정책 충족), 선택적 이름을 입력하면 계정이 생성되고 비밀번호는 DelegatingPasswordEncoder(BCrypt)로 해시되어 저장된다 (spec.md: AC-001)
  - 중복된 이메일로 요청 시 "이미 사용 중인 이메일입니다" 에러와 함께 HTTP 409 Conflict를 반환한다 (spec.md: AC-002)
  - 이메일 형식 오류 또는 비밀번호 정책 위반 시 필드별 검증 에러 메시지를 반환하고 HTTP 400 Bad Request를 반환한다 (spec.md: AC-003)
  - 계정 생성 성공 시 자동 로그인되어 세션이 생성되고 대시보드(/dashboard)로 리다이렉트된다 (spec.md: AC-001)
  - 응답에 비밀번호 해시나 평문이 포함되지 않는다 (spec.md: AC-001)

**구현 순서 근거(의존성)**: Account 도메인 및 보안 기초 설정(2)에 의존함. Account 엔티티와 Repository, PasswordEncoder가 있어야 이메일 중복 검증과 비밀번호 저장을 할 수 있음.

**대응 Sub-ticket ID**: TBD

### 4. 로그인/로그아웃 API
**범위**
- 포함: POST /auth/login 엔드포인트 (이메일/비밀번호 인증, 실패 횟수 증가, 5회 실패 시 계정 잠금), POST /auth/logout 엔드포인트 (세션 무효화, 쿠키 삭제), 계정 잠금 로직 (Account.isAccountLocked 검사), 실패 횟수 카운터 업데이트 (Account.failed_login_attempts)
- 불포함: 회원가입 엔드포인트, 데이터 격리 로직, 비밀번호 변경 API

**Acceptance Criteria**
- Given: 회원가입 API가 구현된 상태
- When: POST /auth/login 요청을 처리한다
- Then:
  - 올바른 이메일과 비밀번호로 요청 시 세션이 생성되고 JSESSIONID 쿠키가 발급되며 대시보드로 리다이렉트된다 (spec.md: AC-004)
  - 틀린 비밀번호로 요청 시 실패 횟수가 1 증가하고 "이메일 또는 비밀번호가 올바르지 않습니다" 메시지와 함께 HTTP 401 Unauthorized를 반환한다 (spec.md: AC-005)
  - 존재하지 않는 계정으로 요청 시 계정 존재 여부 노출 없이 동일한 오류 메시지와 함께 HTTP 401 Unauthorized를 반환한다 (spec.md: AC-006)
  - 5회 실패 시 계정이 잠금 상태가 되고 이후 로그인 시도 시 "계정이 잠겼습니다. 관리자에게 문의하세요" 메시지와 함께 HTTP 403 Forbidden을 반환한다 (spec.md: AC-007)
  - 로그인 성공 시 로그인 성공 이벤트가 감사 로그에 기록된다 (IP, 시각, 계정 ID) (spec.md: AC-004)
- When: POST /auth/logout 요청을 처리한다
- Then:
  - 세션이 무효화되고 JSESSIONID 쿠키가 삭제된다 (Max-Age=0) (spec.md: AC-008)
  - SecurityContext가 클리어된다 (spec.md: AC-008)
  - 로그인 페이지(/login?logout)로 리다이렉트된다 (spec.md: AC-008)
  - 로그아웃 이벤트가 감사 로그에 기록된다 (spec.md: AC-008)

**구현 순서 근거(의존성)**: 회원가입 API(3)에 의존함. Account 엔티티와 보안 기초가 있어야 인증 로직과 실패 횟수 관리, 계정 잠금 기능을 구현할 수 있음.

**대응 Sub-ticket ID**: TBD

### 5. 데이터 격리(인가) 방어
**범위**
- 포함: 
  - HTTP 레벨: 인증이 필요한 경로 보호 (SecurityFilterChain의 authorizeHttpRequests)
  - 서비스 레벨: @PreAuthorize 또는 SecurityUtils를 통한 소유권 검사 (예: 투자자 데이터 접근 시 현재 계정 소유 확인)
  - 리포지토리 레벨: 계정 ID 필수 조건절 강제 (findByIdAndAccountId 등)
  - 인증 안 된 접근 차단: 인증이 필요한 API에 대한 무인증 요청 차단
- 불포함: 구체적인 비즈니스 로직 구현 (서비스 메서드 구현은 별도이지만, 여기서 데이터 격리를 위한 인프라/패턴을 설정함)

**Acceptance Criteria**
- Given: 로그인/로그아웃 API가 구현된 상태
- When: 데이터 접근 시도를 수행한다
- Then:
  - 인증 안 된 사용자가 보호된 API(예: /api/investors)에 접근 시 HTTP 401 Unauthorized를 반환한다 (spec.md: AC-010)
  - 계정 A가 계정 B의 데이터에 접근하려고 할 때 (예: /api/investors/20에서 계정 A 로그인 상태) HTTP 403 Forbidden을 반환하고 "접근 권한이 없습니다" 메시지를 반환한다 (spec.md: AC-011)
  - 서비스 레벨에서 @PreAuthorize("@securityUtils.isOwner(#resourceId, authentication)")가 적용되어 소유권 검사가 수행된다 (research 문서: 질문 5.2단계)
  - 리포지토리 레벨에서 findByIdAndAccountId 메서드가 강제되어 계정 ID 조건이 포함된다 (research 문서: 질문 5.3단계)
  - 컨트롤러에서 현재 계정 ID를 인증 객체에서 추출하여 서비스 레이어로 전달한다 (research 문서: 질문 5.4단계)

**구현 순서 근거(의존성)**: 로그인/로그아웃 API(4)에 의존함. 인증 기능이 있어야 현재 계정을 식별할 수 있고, 그에 따라 데이터 격리를 적용할 수 있음.

**대응 Sub-ticket ID**: TBD

### 6. 비밀번호 변경 API
**범위**
- 포함: POST /auth/password-change 엔드포인트 구현, 현재 비밀번호 검증, 새 비밀번호 유효성 검사 (정책 충족), 비밀번호 변경 시 다른 세션 전체 무효화 (현재 세션 제외 옵션은 결정 필요 사항에 따름), 비밀번호 해시 저장
- 불포함: 회원가입, 로그인/로그아웃, 데이터 격리 로직 (이미 구현됨)

**Acceptance Criteria**
- Given: 데이터 격리(인가) 방어가 구현된 상태
- When: POST /auth/password-change 요청을 처리한다
- Then:
  - 현재 비밀번호가 일치하면 새 비밀번호가 BCrypt로 해시되어 업데이트되고 "비밀번호가 변경되었습니다" 메시지를 반환한다 (spec.md: AC-012)
  - 현재 비밀번호가 일치하지 않으면 "현재 비밀번호가 올바르지 않습니다" 에러를 반환한다 (spec.md: AC-012)
  - 새 비밀번호가 정책을 위반하면 필드별 검증 에러 메시지를 반환한다 (spec.md: AC-012)
  - 비밀번호 변경 시 다른 세션 전체가 무효화된다 (현재 세션 유지 여부는 결정 필요 사항 D-007에 따름) (spec.md: EC-008)
  - 변경 후 기존 세션 유지 여부는 결정 필요 사항에 따름 (spec.md: AC-012, EC-008)
  - 비밀번호 변경 이벤트가 감사 로그에 기록된다 (사용자 계정 ID, 시각) (암시적 요구사항)

**구현 순서 근거(의존성)**: 데이터 격리(인가) 방어(4)에 의존함. 현재 계정을 식별할 수 있는 인증 기능과 데이터 격리 로직이 있어야 비밀번호 변경을 안전하게 처리할 수 있음.

**대응 Sub-ticket ID**: TBD

## 구현 순서와 전체 근거
1. **프로젝트 초기 셋업** → 모든 기능의 기반이 되는 프로젝트 환경을 구축해야 하므로 først.
2. **Account 도메인 및 보안 기초 설정** → 프로젝트 생성이 완워진 후에 도메인 모델과 보안 인프라를 설정해야 함.
3. **회원가입 API** → 계정 생성 및 저장이 가능해야 인증 플로우를 시작할 수 있음.
4. **로그인/로그아웃 API** → 회원가입이 가능해야 인증 자격 증명을 생성할 수 있음.
5. **데이터 격리(인가) 방어** → 인증 기능이 완워진 후에 인증된 사용자의 데이터만 접근 가능하도록 보장해야 함.
6. **비밀번호 변경 API** → 인증과 데이터 격리 기반이 완워진 후에 보안 기능을 강화할 수 있음.

각 단계는 이전 단계의 산출물(엔티티, 리포지토리, 보안 설정 등)에 의존하므로 순차적으로 구현해야 하며, 이는 의존성 체인을 명확히 하여 병목 현상을 최소화하고 통합 테스트 용이성을 높인다.