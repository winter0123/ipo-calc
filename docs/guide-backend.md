# backend 작업 가이드

> **누가 읽는가**: ① Master가 backend 워커의 task spec을 쓸 때(특히 2절 검증 명령을 **여기서 그대로 복사해** spec에 넣는다 — 기억으로 쓰면 없는 Gradle task를 적어 워커가 막힌다), ② backend 워커가 작업 전에.
>
> 공통 규칙은 `AGENTS.md`(6.0절 구조) → `docs/project-agent-guide.md` → `docs/roadmap.md` → `docs/current-status.md` 순서로 먼저 읽는다. spec 형식은 `docs/worker-spec-template.md`를 따른다.

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
DB_PASSWORD=vibe_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q
DB_PASSWORD=vibe_local_password ./gradlew build
```

**`--rerun`은 바로 앞 task 하나에만 적용된다.** `checkstyleMain checkstyleTest --rerun`처럼 task 여러 개에 쓰면 마지막 task만 다시 돌고 나머지는 캐시된 결과가 나온다(2026-09-30에 이 때문에 checkstyle 위반 수를 잘못 판단한 적이 있다). `test` 하나에는 `--rerun`으로 충분하고, task를 여러 개 검증할 때는 `--rerun-tasks`를 쓴다. 검증 결과를 PASS로 기록하기 전에 캐시된 결과가 아닌지 출력에서 `UP-TO-DATE`/`FROM-CACHE` 표시를 확인한다.

로컬 환경·Docker·DB 복구 절차와 실제 환경 변수 이름은 `docs/setup-history.md`를 진실 소스로 사용한다. 시크릿 값은 이 문서, 커밋, work-log 어디에도 쓰지 않는다.

## 3. 백엔드 구현 규칙

- 들여쓰기는 4칸, 탭 금지다.
- Google Java Style Checkstyle(`build.gradle.kts`에 설정돼 있다, 위반은 WARN 수준이지만 **위반 0건**이 검증 통과 기준이다)을 적용하며 `checkstyleMain`, `checkstyleTest`를 검증 게이트에 포함한다.
- public 클래스·메서드·record에는 **한국어 Javadoc**을 달고 첫 문장 끝에 마침표를 붙인다. star import 금지.
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

## 5. 워커가 자주 밟는 지뢰

같은 실수가 워커가 바뀔 때마다 반복돼서 적어둔다. **Master는 backend spec의 [하지 말 것]에 이 절을 그대로 붙여 넣는다.**

- **`./gradlew spotlessApply`를 실행하지 않는다.** 작업공간의 추적 파일 전체를 다시 포맷해 diff가 수백 파일로 부풀어 오른다. 실수로 실행했다면 곧바로 `git checkout -- backend`로 되돌리고 자기 변경만 다시 적용한다. 포맷 확인이 필요하면 `spotlessCheck`만 쓴다.
- **공유 로컬 DB(`localhost:5433`) 하나를 모든 작업공간이 같이 쓴다.** Flyway 마이그레이션이 든 브랜치가 한 번이라도 테스트를 돌리면 그 버전이 공유 DB에 적용되고, 병합되지 않은 상태에서 다른 작업공간이 테스트를 돌리면 `applied migration not resolved locally`로 Flyway 검증이 깨진다. 그래서 **마이그레이션이 든 SF는 한 번에 하나만 진행**하고, 마이그레이션이 없는 SF 워커는 대상 클래스만 `--tests`로 돌린다(전체 테스트는 Master가 병합 전에 한다). 이미 적용된 마이그레이션 파일의 이름·내용은 바꾸지 않는다(체크섬 불일치).
- **Javadoc 첫 문장은 마침표로 끝낸다.** `SummaryJavadoc` 위반의 대부분이 이것이다. record 컴포넌트·compact constructor에도 필요하다.
- **테스트가 남긴 행은 정리한다.** 공유 DB라 `@Transactional` 롤백을 우선하고, 불가능하면 `@AfterEach`에서 직접 지운다. 다른 테스트가 지워서 시드 데이터가 사라지는 경우도 있으니, 시드에 의존하는 테스트는 스스로 필요한 데이터를 만든다.
- **시크릿은 어디에도 남기지 않는다.** 키·토큰·앱시크릿은 로그, 예외 메시지, 테스트 출력, 보고서에 쓰지 않고 테스트에는 더미 값만 쓴다. `@ToString`은 시크릿 필드를 제외한다.
- **모델 ID를 잘못 쓰면 조용히 다른 모델로 폴백된다.** 워커 터미널을 만들 때는 화면의 `Build · <모델명>` 줄이 지정한 모델과 같은지 확인한 뒤 배정한다.
