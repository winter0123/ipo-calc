# Epic 내부 재조사: 계정 인증 및 인가 (account-authentication)

## 확인 배경

`docs/architecture/account-authentication.md`는 "build.gradle.kts에 spring-boot-starter-security가 이미 있을 가능성이 높다"고 가정하고 패키지 구조, API, DB 설계를 정의했다. 그러나 2026-09-20에 기존 `backend/` 스캐폴딩 전체가 삭제되어 **현재 이 프로젝트에는 Java/Spring Boot 프로젝트 자체가 존재하지 않는다.** 이 사실이 세부 기능 분해(breakdown)에 큰 영향을 주므로, Epic worktree 내에서 미니 Phase 0(5.4)을 수행해 실제 현황을 재확인하고 분해 계획을 보정한다.

---

## 확인한 현재 상태 (1~3번 검증 결과)

### 1. 프로젝트 구조 확인 (`ls`, `find` 검증)
```
$ ls -la
backend/ 디렉토리 없음
build.gradle.kts 없음
pom.xml 없음
settings.gradle.kts 없임
```
- 워크트리 최상위에 Java/Spring Boot 프로젝트 관련 파일/디렉토리 **전무**
- 기존 `backend/` 스캐폴딩은 2026-09-20에 삭제됨 (setup-history.md 기록 확인)

### 2. Java/Gradle 환경 확인
```bash
$ java -version
openjdk version "21.0.12.1" 2026-08-18 LTS
OpenJDK Runtime Environment Microsoft-14941480 (build 21.0.12.1+1-LTS)
OpenJDK 64-Bit Server VM Microsoft-14941480 (build 21.0.12.1+1-LTS, mixed mode, sharing)

$ gradle -v
zsh: command not found: gradle
```
- **Java 21 LTS 설치됨** (Microsoft Build of OpenJDK) — architecture.md 전제와 일치
- **Gradle 미설치** — wrapper(`./gradlew`)도 없으므로 프로젝트 생성 시 wrapper 생성 필요
- Spring Boot 3.x + Java 21 조합은 공식 지원됨

### 3. Docker Compose PostgreSQL 설정 확인 (`docker-compose.yml`)
```yaml
services:
  postgres:
    image: postgres:17
    container_name: vibe-ai-team-postgres
    environment:
      POSTGRES_DB: vibe_ai_team
      POSTGRES_USER: vibe
      POSTGRES_PASSWORD: vibe_local_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
```
- **DB명**: `vibe_ai_team`
- **사용자**: `vibe`
- **비밀번호**: `vibe_local_password` (로컬 개발용, `.env`로 관리 예정)
- **포트**: `5432` (호스트 포트 매핑)
- Spring Boot `application.yml`의 `spring.datasource` 설정에 **그대로 사용 가능**
- Architecture.md의 DB 설계(Account 테이블 등)는 이 PostgreSQL 인스턴스 대상으로 생성하면 됨

---

## Architecture.md와의 차이 / 재확인 필요했던 가정 (4번)

| Architecture.md 가정 | 실제 상황 | 영향도 | 대응 방안 |
|---|---|---|---|
| `build.gradle.kts`에 `spring-boot-starter-security` 이미 존재 | **프로젝트 자체가 없음** (gradle 파일 전무) | **치명적** | **세부기능 0: 프로젝트 초기 셋업**을 맨 앞에 추가 필수 |
| 패키지 구조(`controller`, `service`, `domain`, `repository`, `infrastructure`) 제안 | 제안 자체는 유효하나 **적용 대상이 없음** | 중간 | 프로젝트 생성 후 패키지 구조 적용 |
| Security 설정(`config.security`, `service.auth` 등) | Spring Security 의존성부터 추가해야 함 | 높음 | 초기 셋업에 `spring-boot-starter-security` 포함 |
| DB 설정(`spring.datasource.*`) | docker-compose.yml 설정과 **완전 일치** | 낮음 | 그대로 사용, `application.yml`에 반영 |
| `Account` 테이블 DDL | Flyway/Liquibase 마이그레이션으로 생성 필요 | 중간 | 초기 셋업에 Flyway 의존성 포함, `V1__init_account.sql` 작성 |

**핵심 차이**: Architecture.md는 "기존 프로젝트에 기능 추가" 전제였으나, 실제로는 **"빈 디렉토리에서 Spring Boot 프로젝트 신규 생성"부터 시작**해야 한다.

---

## 프로젝트 초기 셋업 방법 (5번, 출처 포함)

### 표준 방법: Spring Initializr (공식 권장)
- **웹 UI**: https://start.spring.io — 가장 널리 쓰이는 표준 방식
- **IDE 통합**: IntelliJ IDEA (New Project → Spring Initializr), VS Code (Spring Boot Extension Pack)
- **CLI**: Spring Boot CLI `spring init` 명령 (CLI 설치 필요, `sdk install springboot` 또는 홈브루/수동 설치)

### 필수 선택 항목 (account-authentication Epic 기준)
| 항목 | 값 | 비고 |
|---|---|---|
| **Project** | Gradle - Kotlin DSL | `build.gradle.kts` 생성 |
| **Language** | Java | Java 21 |
| **Spring Boot** | 3.x (최신 안정 버전, 예: 3.3.x) | Spring Security 7.x 포함 |
| **Packaging** | Jar | 실행 가능 JAR |
| **Java Version** | 21 | 설치된 버전과 일치 |
| **Dependencies** | Spring Web, Spring Security, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation, Lombok (선택) | architecture.md 요구사항 충족 |

### 생성 후 필수 후처리
1. `./gradlew wrapper` 실행 → `gradlew`/`gradlew.bat` 생성 (Gradle 미설치 환경 대응)
2. `src/main/resources/application.yml`에 docker-compose.yml DB 설정 반영
3. `src/main/resources/db/migration/V1__init_account.sql` 작성 (architecture.md Account 테이블 DDL)
4. `settings.gradle.kts`에서 `rootProject.name = "account-authentication"` 설정

### 출처
- Spring Boot 공식 문서: "Getting Started" → "Creating a Spring Boot Project" (https://docs.spring.io/spring-boot/docs/current/reference/html/getting-started.html#getting-started.creating-project)
- Spring Initializr 웹사이트: https://start.spring.io (공식 프로젝트 생성 도구)
- Spring Boot CLI Reference: `spring init` 명령 (https://docs.spring.io/spring-boot/docs/current/reference/html/cli.html#cli.using-the-cli.initialize-new-project)

---

## 추가 제약

1. **Gradle Wrapper 필수**: 시스템 Gradle 미설치 상태이므로 `./gradlew`로만 빌드/실행 가능
2. **로컬 DB 비밀번호 관리**: `vibe_local_password`는 `.env` 또는 `application-local.yml`로 분리, Git 커밋 금지
3. **Java 21 고정**: `java -version` 확인된 버전과 `build.gradle.kts`의 `java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))` 일치 필수
4. **단일 모듈 시작**: 멀티 모듈 분리 불필요 (Epic 1 범위 내 단일 JAR)
5. **테스트 의존성**: `spring-boot-starter-test`, `spring-security-test`, `testcontainers` (PostgreSQL 통합 테스트용) 초기부터 포함 권장

---

## 세부 기능 분해에 대한 제안

Architecture.md 전제("기존 프로젝트 있음")와 실제("빈 프로젝트")의 차이로 인해 **세부 기능 분해 순서를 전면 재조정**해야 한다.

### 제안하는 세부 기능 순서 (breakdown.md에 반영할 것)

| 순서 | 세부기능 ID | 세부기능명 | 비고 |
|---:|---|---|---|
| **0** | **SF-00** | **프로젝트 초기 셋업** (Spring Initializr 생성, Wrapper, application.yml, Flyway 마이그레이션 기초) | **신규 추가 — 맨 앞 필수** |
| 1 | SF-01 | Account 엔티티 및 Repository 구현 | Architecture.md `domain.model.account`, `repository.account` |
| 2 | SF-02 | Security 설정 (SecurityFilterChain, PasswordEncoder, UserDetailsService) | Architecture.md `config.security`, `service.auth` |
| 3 | SF-03 | 회원가입 API (POST /accounts) | Spec AC-001~003 |
| 4 | SF-04 | 로그인/로그아웃 API (POST /auth/login, POST /auth/logout) | Spec AC-004~008, 010 |
| 5 | SF-05 | 계정 잠금/실패 횟수 처리 | Spec AC-006~007, EC-006 |
| 6 | SF-06 | 데이터 격리 3단계 방어 구현 (HTTP/Service/Repository 레벨) | Spec AC-011, Research 3단계 방어 |
| 7 | SF-07 | 비밀번호 변경 API (선택, MVP 포함) | Spec AC-012 |
| 8 | SF-08 | Remember Me (선택, MVP 포함 여부 확정 후) | Spec AC-013 |
| 9 | SF-09 | 통합 테스트 및 검증 (QA 게이트) | 전체 AC 회귀 테스트 |

### 분해 시 고려사항
- **SF-00(초기 셋업)**은 약 1~2시간 소요 예상, 별도 Sub-ticket으로 분리 권장
- **SF-01~02**는 도메인/보안 기초이므로 순서 고정 (의존성 있음)
- **SF-03~05**는 인증 플로우 핵심, 순차 구현
- **SF-06**은 전 도메인에 적용되는 횡단 기능이므로 인증 기초 완성 후 구현
- **SF-07~08**은 결정 필요 사항(D-004, D-007) 확정 후 포함 여부 결정
- 각 세부기능은 Epic 티켓(`account-authentication`)의 자식 Sub-ticket으로 생성, `--deps`로 순서 체인 구성

---

*작성일: 2026-09-20*  
*작성자: Research Worker (A그룹, Epic 내부 미니 Phase 0)*  
*관련 Epic: account-authentication*  
*기반 문서: docs/architecture/account-authentication.md, docs/spec/account-authentication.md, docs/research/account-authentication.md, docs/setup-history.md, docker-compose.yml*