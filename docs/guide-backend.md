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

실제 설정 파일(`backend/build.gradle.kts` 등)을 먼저 읽고 다음 명령을 쓴다. backend 테스트는 **슬롯 DB**에서 돌린다.

```bash
# 1) 먼저 슬롯을 빌린다(1~4). 빌릴 때마다 reset 해서 이전 사용자의 마이그레이션·테스트 잔재를 지운다.
scripts/db-slot.sh reset 1

# 2) 그 슬롯을 명시해서 검증한다.
cd backend
DB_NAME=vibe_slot_1 DB_PASSWORD=vibe_local_password ./gradlew test --rerun
DB_NAME=vibe_slot_1 DB_PASSWORD=vibe_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q
DB_NAME=vibe_slot_1 DB_PASSWORD=vibe_local_password ./gradlew build
```

`backend/src/main/resources/application.yml`과 `backend/src/test/resources/application-test.yml`의 datasource url은 `jdbc:postgresql://localhost:5433/${DB_NAME:vibe_ai_team}` 이다. 즉 **`DB_NAME`을 주지 않으면 기존과 완전히 동일하게 `vibe_ai_team`을 쓴다.** 슬롯은 `scripts/db-slot.sh`가 만드는 `vibe_slot_1`~`vibe_slot_4` 네 개뿐이고, 슬롯 용도 밖으로 늘리지 않는다. `scripts/db-slot.sh list`로 어떤 슬롯 DB가 이미 만들어져 있는지 볼 수 있다. 슬롯을 쓰지 않고 `DB_NAME` 없이 테스트를 돌리면 공유 DB에 Flyway 흔적을 남기므로 하지 않는다(5절 슬롯 규칙).

**`--rerun`은 바로 앞 task 하나에만 적용된다.** `checkstyleMain checkstyleTest --rerun`처럼 task 여러 개에 쓰면 마지막 task만 다시 돌고 나머지는 캐시된 결과가 나온다(2026-09-30에 이 때문에 checkstyle 위반 수를 잘못 판단한 적이 있다). `test` 하나에는 `--rerun`으로 충분하고, task를 여러 개 검증할 때는 `--rerun-tasks`를 쓴다. 검증 결과를 PASS로 기록하기 전에 캐시된 결과가 아닌지 출력에서 `UP-TO-DATE`/`FROM-CACHE` 표시를 확인한다.

로컬 환경·Docker·DB 복구 절차와 실제 환경 변수 이름은 `docs/setup-history.md`를 진실 소스로 사용한다. 시크릿 값은 이 문서, 커밋, work-log 어디에도 쓰지 않는다.

## 3. 백엔드 구현 규칙

- 들여쓰기는 2칸, 탭 금지다(Google Java Style, `backend/config/checkstyle/checkstyle.xml`의 `basicOffset=2`). 한 줄은 100자까지다.
- Google Java Style Checkstyle(`build.gradle.kts`에 설정돼 있다, 위반은 WARN 수준이지만 **위반 0건**이 검증 통과 기준이다)을 적용하며 `checkstyleMain`, `checkstyleTest`를 검증 게이트에 포함한다.
- public 클래스·메서드·record에는 **한국어 Javadoc**을 달고 첫 문장 끝에 마침표를 붙인다. star import 금지.
- Controller는 요청 검증·응답 매핑·Service 호출만 담당한다. 업무 규칙과 트랜잭션은 Service, 상태와 불변식은 Domain/Entity, 외부 계약은 DTO에 둔다.
- 세션 쿠키·remember-me 환경 변수: `SESSION_COOKIE_SECURE`(기본 `false`; HTTPS로 서비스하는 운영에서는 `true`)가 세션 쿠키 `Secure` 값을 정한다. `HttpOnly`·`SameSite=Lax`는 `application.yml`의 `server.servlet.session.cookie.*`가 단일 출처이고 `SessionCookieWriter`가 같은 값을 쓴다(실제 컨테이너는 자체 Set-Cookie를 넣으므로 수동 쿠키는 중복 없이 MockMvc에서만 보충된다). `APP_REMEMBER_ME_KEY`(16자 이상 임의 문자열 권장)는 remember-me 서명 키이며, 비어 있으면 기동마다 임의 키를 만들고 WARN을 남긴다(재시작 시 기존 remember-me 쿠키 무효, 키 값은 로그에 남기지 않는다).
- 공용 카탈로그(IPO·증권사) 쓰기(POST/PATCH/DELETE·import)는 `accounts.role = 'ADMIN'`만 가능하다(그 외 403 `FORBIDDEN`, `SecurityConfig`). 읽기는 로그인 사용자 전체. ADMIN은 가입 API로 만들 수 없고 DB에서 직접 지정한다: `UPDATE accounts SET role = 'ADMIN' WHERE email = '...';` (역할은 로그인 시점에 세션에 담기므로 재로그인 필요).
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
- **테스트는 공유 DB(`localhost:5433/vibe_ai_team`)를 쓰지 않고 슬롯 DB를 쓴다.** 슬롯은 `vibe_slot_1`~`vibe_slot_4` 네 개이며 `scripts/db-slot.sh reset <n>`으로 빌리고, 같은 슬롯을 다시 쓰기 전에는 반드시 reset 한다. Master가 워커를 배정할 때 빈 슬롯(1~3 중 하나)을 함께 지정하고, **Master 자신의 검증은 슬롯 4를 쓴다.** 슬롯을 배정받지 않은 채 기본 DB(`vibe_ai_team`)로 테스트하지 않는다 — 기본 DB는 병합된 마이그레이션만 쌓인 dev 용이다. 슬롯마다 DB가 분리되므로 **마이그레이션이 든 SF도 슬롯이 다르면 동시에 진행할 수 있다.** 워커가 끝나면 슬롯은 회수한다(회수한 사람이 다음에 reset 한다). 이미 적용된 마이그레이션 파일의 이름·내용은 바꾸지 않는다(체크섬 불일치).
- **Javadoc 첫 문장은 마침표로 끝낸다.** `SummaryJavadoc` 위반의 대부분이 이것이다. record 컴포넌트·compact constructor에도 필요하다.
- **테스트가 남긴 행은 정리한다.** 슬롯을 reset 하면 남은 행은 지워지지만, 같은 슬롯을 reset 없이 재사용하면 다른 테스트가 시드 데이터를 지워서 테스트가 깨진다. `@Transactional` 롤백을 우선하고, 불가능하면 `@AfterEach`에서 직접 지운다. 시드에 의존하는 테스트는 스스로 필요한 데이터를 만든다.
- **시크릿은 어디에도 남기지 않는다.** 키·토큰·앱시크릿은 로그, 예외 메시지, 테스트 출력, 보고서에 쓰지 않고 테스트에는 더미 값만 쓴다. `@ToString`은 시크릿 필드를 제외한다.
- **모델 ID를 잘못 쓰면 조용히 다른 모델로 폴백된다.** 워커 터미널을 만들 때는 화면의 `Build · <모델명>` 줄이 지정한 모델과 같은지 확인한 뒤 배정한다.

## 6. 증권사 연동 비밀키 운영 (Epic 18)

- **서버 키**: 증권사 앱키·앱시크릿·접근토큰은 `APP_ENCRYPTION_BROKER_KEY`(32바이트 키의 Base64, AES-256-GCM)로 암호화해 `securities_account_api_credentials`에 저장한다. 계좌번호 키(`APP_ENCRYPTION_KEY`)와 다른 키여야 한다. 생성: `openssl rand -base64 32`. 값은 저장소·로그·보고서에 쓰지 않는다.
- **미설정이면**: 기동은 되지만 연결 등록과 동기화 조회가 503(`BROKER_CONNECTION_UNAVAILABLE`)이고, 이미 저장된 인증정보 상태는 `ERROR`로 보인다. 평문으로 저장하는 경로는 없다.
- **백업**: 이 키를 잃으면 저장된 증권사 키를 전부 복호화할 수 없다. DB 백업과 **별도 장소**(비밀번호 관리자 등)에 키를 보관한다. DB 백업과 같은 곳에 두지 않는다.
- **서버 암호화 키 교체(rotation)**: 키는 버전 관리된다(D-B-028). 암호문은 `v<번호>:...` 형태이고 접두사 없는 예전 암호문은 `v1`로 읽는다. 기존 단일 키(`APP_ENCRYPTION_BROKER_KEY`·`APP_ENCRYPTION_KEY`)는 `v1`이다. 절차(두 키 모두 같은 방식):
  1. 새 키를 만든다: `openssl rand -base64 32`.
  2. 추가 키 목록에 넣는다: `APP_ENCRYPTION_BROKER_KEYS=v2:<새 키>`(계좌번호는 `APP_ENCRYPTION_KEYS`). 예전 키 변수는 그대로 둔다. 현재 버전은 가장 큰 번호(`v2`)이고 `APP_ENCRYPTION_BROKER_KEY_CURRENT`·`APP_ENCRYPTION_KEY_CURRENT`로 고정할 수도 있다. 이 상태에서 새로 저장하는 값은 `v2`, 예전 값은 `v1`로 계속 읽힌다.
  3. 한 번 `APP_ENCRYPTION_ROTATE_ON_STARTUP=true`로 기동해 저장된 값을 모두 `v2`로 다시 암호화한다. 로그의 `암호화 키 버전 재암호화 완료 ... failures=0`을 확인한다(실패한 행은 ID만 로그에 남고 건너뛴다).
  4. 플래그를 끄고, DB 백업이 끝났는지 확인한 뒤 예전 키(`v1`)를 지운다. 새 키 `v2`를 DB 백업과 다른 곳에 보관한다.
  - 새 키를 목록에 넣지 않은 채 예전 키만 지우면 `v1` 암호문을 못 읽는다. 3단계 전에 예전 키를 지우지 않는다.
- **연결 해제**: 인증정보 행을 물리 삭제한다(D-B-015, 이 테이블만의 예외). 암호문이 DB에 남지 않는다.
- **키 교체·만료**: 서비스 사용기간 만료일(발급일+1년)이 지나면 상태가 `EXPIRED`다. 증권사에서 갱신하면 키가 바뀌므로 `PUT /securities-accounts/{id}/api-credential`로 새 키를 넣는다(D-B-018·019). 증권사가 인증을 거절하면 424로 키 갱신을 안내한다(D-B-020).
- **증권사 쪽 수명**: 접근토큰 24시간은 서버가 자동 갱신한다. 서비스 사용기간은 신청일부터 1년이고 갱신하면 앱키·시크릿이 새로 발급되니 다시 연결해야 한다. 3개월 이상 미거래면 서비스가 해지될 수 있다(D-B-012).
