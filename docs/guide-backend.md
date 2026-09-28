# backend 팀장 전용 가이드

> 이 문서는 **backend 팀장만** 읽는다. frontend/planning 팀장은 이 문서를 읽을 필요가 없다. 공통 규칙은 `AGENTS.md` → `docs/project-agent-guide.md` → `docs/work-plan.md` → `docs/roadmap.md` → `docs/current-status.md` 순서로 먼저 읽는다.

## 1. 기술 기준

| 영역 | 기준 |
|---|---|
| Backend | Java 21, Spring Boot 3.3.4, Spring Data JPA, Spring Security, Flyway, PostgreSQL |
| 인프라 | Docker Compose, PostgreSQL 17 컨테이너, 호스트 포트 5433 |
| 패키지 관리 | Gradle Wrapper |

## 2. 검증 명령

실제 설정 파일(`backend/build.gradle.kts` 등)을 먼저 읽고 다음 명령을 쓴다.

```bash
cd backend
DB_PASSWORD=vibe_local_password ./gradlew test --rerun
DB_PASSWORD=vibe_local_password ./gradlew build
```

로컬 환경·Docker·DB 복구 절차와 실제 환경 변수 이름은 `docs/setup-history.md`를 진실 소스로 사용한다. 시크릿 값은 이 문서, 커밋, work-log 어디에도 쓰지 않는다.

## 3. 백엔드 구현 규칙

- 들여쓰기는 4칸, 탭 금지다.
- Google Java Style Checkstyle을 엄격하게 적용하며, 실제 플러그인 구성 뒤 `checkstyleMain`, `checkstyleTest`를 검증 게이트에 포함한다.
- Controller는 요청 검증·응답 매핑·Service 호출만 담당한다. 업무 규칙과 트랜잭션은 Service, 상태와 불변식은 Domain/Entity, 외부 계약은 DTO에 둔다.
- 모든 조회·변경 API는 계정 소유권 또는 글로벌 기준정보 여부를 명확히 구분한다. 개인정보·인증정보·계좌번호 평문을 로그에 남기지 않는다.
- PostgreSQL 문법만 쓴다. `AUTO_INCREMENT`, `ENGINE=InnoDB`, `ON UPDATE CURRENT_TIMESTAMP`를 쓰지 않는다. PK는 `GENERATED ALWAYS AS IDENTITY`, 조건부 유니크는 별도 `CREATE UNIQUE INDEX ... WHERE ...` 문으로 작성한다.
- Flyway에서 테이블을 만들 때는 테이블 목적, 각 컬럼의 의미, 인덱스·제약조건의 이유를 한글 SQL 주석으로 남긴다.
- Controller 클래스에는 리소스와 제공 API를 설명하는 한글 주석을, 각 매핑 메서드에는 그 엔드포인트의 동작·주요 오류 조건을 설명하는 한글 한 줄 주석을 둔다.

## 4. 재사용 가능한 공용 인프라 (Epic 2에서 확보, 계속 재사용)

- `SecurityUtils.getCurrentAccountId()` (`com.vibeaiteam.accountauthentication.util.security`) — 계정 소유권 검증 헬퍼. 계정 소유 데이터가 있는 Epic만 사용, 글로벌 기준정보 Epic은 불필요.
- 컴포넌트/엔티티/JPA 스캔 범위가 `com.vibeaiteam` 전체 — 새 Epic 패키지는 별도 스캔 설정 없이 바로 인식됨.
- CSRF 전역 비활성화 + `AuthenticationEntryPoint`(401) 설정 완료 — 새 쓰기 API마다 CSRF 예외 추가할 필요 없음.
- `SuccessResponse`/`ErrorResponse` 공용 DTO(`com.vibeaiteam.accountauthentication.controller.dto`) 재사용 가능.
- (name, status) 복합 유니크 인덱스 패턴 — "활성 레코드끼리만 유니크" 요구사항이 있는 기준정보성 Epic에 재사용 가능.
- AES-256-GCM 암호화 유틸(`securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor`) — JDK `javax.crypto`만 사용, 민감정보 암호화 필요 시 재사용.
- 2단계 소유권 검증 패턴 — 대상 리소스가 `Investor`를 거쳐야 소유 계정을 알 수 있는 하위 리소스(예: SecuritiesAccount)에 재사용.
- partial unique index + `DataIntegrityViolationException` 방어 코드 — 명시적 락 없이 "그룹 내 유일 플래그" 처리(개인용 도구라 동시 요청 경쟁이 실질적으로 없음).
- 누적 수량 검증(Σ ACTIVE quantity ≤ 상한)을 Service 계층 `@Transactional` 경계 안에서 수행하는 패턴 — 동시성 제어(락) 없이도 충분.
