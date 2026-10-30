# 관리자 임퍼소네이션 세부 기능 분해 — Spring Boot 4.1.1 / Spring Security 7.1.1 (resolved) / Spring Session JDBC / PostgreSQL / JPA Account(Long ID) — 경로 분리: 외부 API `/api/admin/impersonation/*` vs proxy-merged backend `/admin/impersonation/*`

> **Epic 상태**: `docs/roadmap.md` Epic 표(1~22)에 관리자 임퍼소네이션이 별도 번호로 등록되어 있지 않음. 실제 구현 코드(`AdminImpersonationController`, `ImpersonationService`, `ImpersonationSession`)는 존재하나 `docs/roadmap.md` 5절과 `docs/current-status.md`에는 Epic 번호 없이 언급됨(`admin` 기능은 E1 인증·인가와 E22 공지사항 사이에 독립적으로 존재). 이 문서는 실제 파일 내용과 줄 번호를 기준으로 작성함(`docs/breakdown/` 규칙). 추측으로 범위·요구사항을 추가하지 않음.
> **기술 기준**: `backend/build.gradle.kts`: Spring Boot 4.1.1 (`line 2`); `spring-boot-starter-security` (`spring-security-web` resolved `7.1.1`, `./gradlew dependencyInsight --dependency spring-security-web --configuration testRuntimeClasspath` 확인); `spring-boot-starter-session-jdbc`(PostgreSQL 세션 저장); `postgresql`; `jakarta.persistence-api`; `flyway-core`/`flyway-database-postgresql`. `docs/guide-backend.md` 1절 기준.
> **v7 승인 및 실행 범위**: 현재 대화에서 사용자가 v7 기준 구현을 명시적으로 지시했으므로, SF-1~SF-5(엔티티·서비스·컨트롤러·인터셉터·컨텍스트)의 실제 코드 확인·수정 범위는 승인된 실행 범위로 간주한다(`NOT_CONFIRMED`이 아닌 승인된 실행). 별도 게이트가 필요한 정책은 SF-6(감사 기록 `AuditLogService` 연동)와 최종 통합 테스트(`SF-7`)에서 분리 관리한다. 확인된 정책: `AdminImpersonationController.java:22`(`/api/admin/impersonation` — 외부 API 경로; proxy `ui/vite.config.ts:14` rewrite `/api` → 제거 후 backend 실제 수신 경로는 `/admin/impersonation`이므로 두 경로 분리 표기), `AdminImpersonationController.java:24`(`@PreAuthorize("hasRole('ADMIN')")`), `SecurityConfig.java:68`(`/admin/**` ADMIN 전용 — proxy-merged 경로와 일치), `ReadOnlyImpersonationInterceptor.java`(읽기 전용 강제).
> **미해결 게이트**: 승인된 실행 범위(SF-1~SF-5)와 별도 상태(SF-6 `NOT_IMPLEMENTED` — v7 확정 항목이지만 통합 구현 누락; SF-7 `NOT_RUN` — 통합 검증 대기)를 분리. SF-6은 v7 설계(`docs/architecture/admin-impersonation.md:17`)에 `audit start/end/replaced/expired 영속 저장`이 명시되어 있어 감사 코드/엔티티/서비스(`AuditLogService.java:47`/`63`, `ActionAuditLog.java:58`/`68`/`78`)는 v7 확인 항목이지만, `ImpersonationService.java:29` 주입 부재 및 `line 46`/`133`/`176` 직접 호출 부재로 `NOT_IMPLEMENTED`(구현 누락). SF-7(`NOT_RUN`) — 전체 테스트 통과 기준(`docs/current-status.md` 2026-10-26 기준 1,099건 PASS)을 임퍼소네이션 SF-1~SF-5 통합 후 검증하되, 기존 `ImpersonationSession` 전용 테스트는 저장소 내 없으므로 별도 추가 없이는 전용 PASS를 과장하지 않음.

---

## 1. SF 개요 (3~8 SF, 실제 모듈 기반)

| SF | 제목 | 선행 SF (`--deps`) | 실제 모듈/파일 | 상태 |
|---|---|---|---|---|
| SF-1 | 엔티티 + Repository + DB 테이블 | — (`[]`) | `domain/model/impersonation/ImpersonationSession.java`, `repository/impersonation/ImpersonationSessionRepository.java`, Flyway 마이그레이션 (`impersonation_sessions`) | 존재(코드 확인) |
| SF-2 | 서비스: 시작/상태/종료/만료 | SF-1 (`["sf-1-task-id"]`) | `service/impersonation/ImpersonationService.java` | 존재(코드 확인) |
| SF-3 | 컨트롤러: `/api/admin/impersonation/*` (외부 API) → proxy rewrite 후 `/admin/impersonation/*` | SF-2 (`["sf-2-task-id"]`) | `controller/AdminImpersonationController.java`, DTO(`ImpersonationStartResponse`, `ImpersonationStatusResponse`) | 존재(코드 확인) — 외부 경로(`/api/admin/impersonation`)와 proxy-merged backend 수신 경로(`/admin/impersonation`)를 문서상 분리 표기 |
| SF-4 | 보안: 인터셉터 + 컨텍스트 필터 | SF-3 (`["sf-3-task-id"]`) | `config/security/ReadOnlyImpersonationInterceptor.java`, `config/security/ImpersonationContextFilter.java`, `SecurityConfig.java` | 존재(코드 확인) |
| SF-5 | 컨텍스트 + 유틸리티 | SF-4 (`["sf-4-task-id"]`) | `util/security/ImpersonationContextHolder.java`, `util/security/SecurityUtils.java` | 존재(코드 확인) |
| SF-6 | 감사 기록 연동 — v7 확정 항목 / 통합 `NOT_IMPLEMENTED` | SF-2 (`["sf-2-task-id"]`) | `audit/ActionAuditLog.java`, `audit/AuditLogService.java`, `service/impersonation/ImpersonationService.java` | `NOT_IMPLEMENTED` (v7 아키텍처 `docs/architecture/admin-impersonation.md:17` — `audit start/end/replaced/expired 영속 저장` 확인; `AuditLogService.java:47`(`logImpersonationStart`), `:63`(`logImpersonationEnd`), `ActionAuditLog.java:58`(`IMPERSONATION_START`), `:68`(`IMPERSONATION_END`), `:78`(`IMPERSONATION_EXPIRED`) 존재; 그러나 `ImpersonationService.java:29` 주입 목록에 `AuditLogService` 없고 직접 호출 없음 — 구현 누락이므로 `BLOCKED`이 아닌 `NOT_IMPLEMENTED`) |
| SF-7 | 통합 검증 + `git diff --check` | SF-1~SF-5 (`["sf-1","sf-2","sf-3","sf-4","sf-5"]`); SF-6(`NOT_IMPLEMENTED`) 통합 시 추가 (`["sf-6"]`) | `docs/guide-backend.md` 2절 명령 복사; `build.gradle.kts`(`maxWarnings=0`) | `NOT_RUN` (현재 `current-status.md` 기준 전체 테스트 1,099건 PASS이지만 임퍼소네이션 전용 테스트는 별도 실행 기록 없음) |

---

## 2. 공통 검증 규칙 (`docs/guide-backend.md` 5절 "워커가 자주 밟는 지뢰" 복사)

- **`spotlessApply` 금지**: `backend/build.gradle.kts:91`(`spotless { java { ... } }`) 존재. `spotlessApply` 실행 시 전체 파일 재포맷되어 `git diff --stat`이 수백 파일로 부풀어 오름. 확인만 필요하면 `spotlessCheck` 사용.
- **DB 슬롯 규칙**: `docs/guide-backend.md:66`. 슬롯은 `ipo_slot_1`~`ipo_slot_4` 4개. 워커는 `DB_NAME=ipo_slot_1`(또는 2/3)을 사용하고, 같은 슬롯 재사용 전 반드시 `scripts/db-slot.sh reset <n>` 실행. Master 자신의 최종 검증은 슬롯 4(`DB_NAME=ipo_slot_4`) 사용. 기본 공유 DB(`DB_NAME` 생략 시 `ipo_calc`)는 마이그레이션 잔재용이므로 테스트에 사용 금지.
- **테스트 명령 정확 복사**: `docs/guide-backend.md:25`~`28`. `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build`. `--rerun`은 단일 task에만 적용(`--rerun-tasks` 사용 필수).
- **마이그레이션 금지**: `docs/guide-backend.md:66`("이미 적용된 마이그레이션 파일의 이름·내용은 바꾸지 않는다"). SF-1이 마이그레이션 파일 존재 여부만 확인하고 내용 변경은 금지.
- **Javadoc 규칙**: `docs/guide-backend.md:40`(`SummaryJavadoc` 위반 0건 기준 — 첫 문장 마침표 필수). `ImpersonationService.java` 기존 Javadoc(`line 23`)은 이미 마침표로 끝남(`"임퍼소네이션 세션 관리 서비스."`). 새 파일 추가 시 동일 규칙 적용.
- **시크릿 로그 금지**: `docs/guide-backend.md:70`. `APP_ENCRYPTION_BROKER_KEY`(`docs/guide-backend.md:76`)는 로그·테스트 출력·문서 어디에도 기록 금지. `ImpersonationService.java:78`(`log.info`는 `adminId`, `targetAccountId`, `sessionId`만 포함 — 시크릿 미포함) 확인.

---

## 3. SF-1: 엔티티 + Repository + DB 테이블 존재 확인

- **범위**(`docs/guide-backend.md` 복사): `ImpersonationSession.java` 엔티티(`@Entity`, `@Table(name = "impersonation_sessions")`), `ImpersonationSessionRepository.java`(`JpaRepository<ImpersonationSession, Long>`), `backend/src/main/resources/db/migration/` 내 `impersonation_sessions` 관련 마이그레이션 파일 존재 확인(`Flyway` 기반 — 내용 직접 확인 금지, 파일 존재와 `V__` 접두사만 확인).
- **비범위**: `UUID`를 엔티티 PK로 사용하지 않음(`Account.java:63`과 동일한 `Long` `IDENTITY`); SQLite 설계 문서 작성 금지; `sqlite_master` 조회 금지; 마이그레이션 내용 수정 금지; `tests/` 외 디렉토리 테스트 수정 금지.
- **AC**: AC-101(`compileJava` 통과); AC-102(`sessionId`는 `String`(`length=36`, `unique=true`)이며 `UUID.randomUUID()` 결과 저장 확인); AC-103(`actorAdminId`, `subjectAccountId`는 `Long`, `nullable=false`); AC-104(`readOnly` 기본 `true`).
- **선행**: 없음 (`task-create --parent <epic_id> --spec ... --json` — `--deps` 없음)
- **검증 명령**(정확히 복사):
  ```bash
  scripts/db-slot.sh reset 1
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew compileJava --rerun-tasks -q
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain --rerun-tasks -q
  git diff --stat
  git diff --check
  ```
- **테스트 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests '*Impersonation*' --rerun` (기존 테스트 파일이 없으므로 현재는 `NOT_RUN`; 새 테스트 추가는 별도 SF로 처리)
- **실제 파일 인용**: `ImpersonationSession.java:17`(`@Table`), `:25`(`@Id` `Long` `IDENTITY`), `:29`(`actorAdminId`), `:34`(`subjectAccountId`), `:37`(`sessionId` `String` `unique`), `:57`(`readOnly` `Boolean` `@Builder.Default` `true`), `:80`(`isExpired()`), `:84`(`end()`); `ImpersonationSessionRepository.java:17`(`JpaRepository`), `:22`(활성 조회 쿼리), `:36`(관리자별 활성 조회), `:43`(만료 조회); `build.gradle.kts:2`(Spring Boot 4.1.1).

---

## 4. SF-2: 서비스 — 시작/상태/종료/만료 스케줄러

- **범위**: `service/impersonation/ImpersonationService.java`(`line 25`: `@Service`). `startImpersonation`: 자기 자신 차단(`line 48`), 대상 `approved=true` 확인(`line 53`), 기존 활성 세션 `REPLACED` 종료(`line 57`), 새 세션 생성(`line 60` `UUID.randomUUID()`), `HTTP_SESSION` 마커(`IMPERSONATION_SESSION_ID`, `line 75`). `getStatus`: `request.getSession(false)` → `IMPERSONATION_SESSION_ID` 추출(`line 106`) → `findActiveSessionById`(`line 112`) → 만료 시 `endImpersonation`(`line 121`). `endImpersonation`: `findBySessionId` → `session.end(reason)`(`line 84`) → 저장(`line 137`). `expireSessions`: `@Scheduled(fixedRate = 60_000)`(`line 176`) → `findExpiredActiveSessions`(`line 179`) → 각 세션 `end("EXPIRED")`(`line 182`).
- **비범위**: `AuditLogService` 직접 주입/호출 추가는 현재 `NOT_IMPLEMENTED`(v7 확정 항목이지만 통합 구현 누락) — `BLOCKED`이 아닌 구현 누락이므로 별도 승인 게이트로 오해하지 않음; 외부 `python` 테스트 금지; SQLite 직접 접근 금지.
- **AC**: AC-201(`adminId.equals(targetAccountId)` → `ValidationFailedException` 확인); AC-202(`findByIdAndApprovedTrue` 실패 → `AccountNotFoundException` 확인); AC-203(`sessionId`가 `String`(`UUID`), `readOnly=true`, `expiresAt` = `now + 30분` 확인); AC-204(기존 활성 세션 존재 시 `endCurrentImpersonation` 호출 확인 — `line 57`); AC-205(`expireSessions` 스케줄러에서 만료 세션 `endImpersonation` 호출 확인 — `line 176` ~ `line 191`).
- **선행 SF**: SF-1 (`--deps '["sf-1-task-id"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests '*ImpersonationService*' --rerun` (기존 테스트 파일이 없으면 `NOT_RUN`); `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`
- **실제 파일 인용**: `ImpersonationService.java:25`(`@Service`), `:35`(`IMPERSONATION_TIMEOUT_MINUTES = 30`), `:46`(`startImpersonation` 시그니처), `:48`(자기 자신 방지), `:53`(대상 승인 확인), `:57`(기존 세션 종료), `:60`(`UUID.randomUUID()`), `:68`(`HTTP_SESSION` 마커 설정), `:75`(`IMPERSONATION_SESSION_ID` 속성 이름), `:104`(`getStatus` 시그니처), `:106`(`getSession(false)`), `:121`(만료 처리), `:133`(`endImpersonation` 시그니처), `:137`(`save`), `:176`(`@Scheduled`), `:179`(`findExpiredActiveSessions`).

---

## 5. SF-3: 컨트롤러 — `/api/admin/impersonation/*` REST API

- **범위**: `controller/AdminImpersonationController.java`(`line 21`: `@RestController`, `line 22`: `@RequestMapping("/api/admin/impersonation")` — **외부(front-facing) API 경로**). `POST /start/{accountId}`(`line 37`): `Long accountId`(`Account.java`와 동일한 `Long` PK), `HttpServletRequest`. `GET /status`(`line 60`): `HttpServletRequest`. `POST /end`(`line 72`): `HttpServletRequest`. 응답 DTO: `ImpersonationStartResponse.java`(`sessionId`, `expiresAt`, `readOnly`); `ImpersonationStatusResponse.java`(`impersonating`, `targetAccountId`, `expiresAt`). **경로 분리**: 프론트엔드 `ui/vite.config.ts:14`(`proxy` `/api` rewrite `/^\/api/` → `''`)로 인해 `frontend apiFetch`는 `/api/admin/impersonation/*`를 호출하지만, 실제 backend `AdminImpersonationController`는 `/api/admin/impersonation`으로 매핑되어 있어 proxy rewrite 적용 시 수신 경로는 `/admin/impersonation/*`이 됨(`SecurityConfig.java:68` `/admin/**` ADMIN 규칙과 일치). 두 경로를 문서에서 구분하여 표기함.
- **비범위**: `AdminUserController.java`(`/admin/accounts` 승인 관리 등)는 이 SF 범위 밖(`SecurityConfig.java:68`에서 별도 `/admin/**` ADMIN 전용으로 관리); CSRF 토큰 발급(`CsrfTokenFilter.java`)은 별도 파일 존재 확인만; `GET /auth/csrf` 익명 bootstrap은 `CsrfTokenFilter`에서 이미 처리 — 이 SF에서는 추가하지 않음.
- **AC**: AC-301(`POST /start/{accountId}`가 `200`과 `sessionId` 포함 응답 반환); AC-302(`GET /status`가 현재 세션 상태 정확히 반환 — `ImpersonationStatusResponse` 구조 확인); AC-303(`POST /end`가 `204` 반환); AC-304(비-ADMIN 요청이 `403` 반환 — `SecurityConfig.java:100` `FORBIDDEN` JSON 확인).
- **선행 SF**: SF-2 (`--deps '["sf-2-task-id"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`; `git diff --check`
- **테스트 명령 예**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests '*AdminImpersonation*' --rerun`
- **API 경로 실제 확인**: `AdminImpersonationController.java:22`(`/api/admin/impersonation`), `:37`(`/start/{accountId}` — `PathVariable Long`), `:60`(`/status` — `GET`), `:72`(`/end` — `POST`). `SecurityConfig.java:68`(`/admin/**` ADMIN 전용 규칙과 충돌하지 않음 — 컨트롤러는 `/api/admin/impersonation`이므로 `/admin/**` 규칙에 포함됨).

---

## 6. SF-4: 보안 — `ReadOnlyImpersonationInterceptor` + `ImpersonationContextFilter`

- **범위**: `config/security/ReadOnlyImpersonationInterceptor.java`: `ALLOWED_READ_ONLY_PATTERNS`(`line 23`: 14개 패턴 — `/api/admin/users/**`, `/api/admin/impersonation/**` 포함); `IMPERSONATION_ALLOWED_POST`(`line 37`: `/api/admin/impersonation/end`만 포함 — 외부 API 경로 기준); `preHandle`(`line 45`): `isImpersonating()`(`ImpersonationContextHolder.java:13`) 확인(`line 47`); `GET`/`HEAD`/`OPTIONS` 허용, 그 외 `unsafe method`는 `403`(`line 65`); `/api/admin/impersonation/end` POST 예외 허용(`line 55`); `/api/admin/impersonation/start` POST는 이미 진행 중이면 `403`(`line 60`). `ImpersonationContextFilter.java`: `SecurityConfig.java:168`(`ImpersonationContextFilter` 빈); `addFilterAfter(impersonationContextFilter, SecurityContextHolderFilter.class)`(`line 119`).
- **비범위**: `ALLOWED_READ_ONLY_PATTERNS`에 `/admin/**` 전체 추가하지 않음(현재 `/api/admin/users/**`와 `/api/admin/impersonation/**`만 포함 — 기존 설계 의도 유지); `ImpersonationContextFilter`를 `SecurityContextHolderFilter` 이후가 아닌 다른 위치로 변경하지 않음(기존 `line 119` 유지); `ReadOnlyImpersonationInterceptor`의 허용 패턴 목록에서 `/sales/**`, `/profits/**` 등 기존 조회 경로를 제거하지 않음.
- **AC**: AC-401(`GET /dashboard/**` 접근 성공 — `ALLOWED_READ_ONLY_PATTERNS` 매칭 확인); AC-402(`POST /sales/**` 차단 — `403` JSON 확인); AC-403(`POST /api/admin/impersonation/end` 허용 — `line 55` 조건 확인); AC-404(`POST /api/admin/impersonation/start`는 이미 진행 중이면 `403` — `line 60` 확인).
- **선행 SF**: SF-3 (`--deps '["sf-3-task-id"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build` (인터셉터는 빌드 시점에서 컴파일 검증); `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`
- **테스트 명령 예**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests '*Interceptor*' --rerun`
- **실제 파일 인용**: `ReadOnlyImpersonationInterceptor.java:23`(`ALLOWED_READ_ONLY_PATTERNS` — 14개 패턴 확인), `:37`(`IMPERSONATION_ALLOWED_POST`), `:45`(`preHandle` 시작), `:55`(`/end` POST 허용 조건), `:60`(`/start` POST 차단 조건), `:65`(`unsafe method` 차단), `:70`(허용 패턴 매칭), `:80`(`forbid` 메서드 — `403` + JSON); `ImpersonationContextFilter.java`(파일 존재 확인 — `SecurityConfig.java:168`에서 빈 생성); `SecurityConfig.java:119`(`addFilterAfter` 순서).

---

## 7. SF-5: 컨텍스트 + 유틸리티 — `ImpersonationContextHolder` + `SecurityUtils`

- **범위**: `util/security/ImpersonationContextHolder.java`: `ThreadLocal<String>`(`line 13`); `set`(`line 22`); `get`(`line 29`); `getImpersonationSessionId`(`line 41`); `isImpersonating`(`line 41` — `getImpersonationSessionId() != null` 기반); `remove`(`line 36`); `getSubjectAccountId`(`line 42` — `ThreadLocal<Long>` 기반). `util/security/SecurityUtils.java`: `getCurrentAccountId()`(`line 37` — `ImpersonationContextHolder.isImpersonating()` 확인 후 `getSubjectAccountId()` 반환); `getSubjectAccountId()`(`line 42`). `Account.java:63`(`id`는 `Long` — 컨텍스트에서 사용되는 `actorAdminId`, `subjectAccountId`와 동일한 타입 확인).
- **비범위**: `ThreadLocal`을 `InheritableThreadLocal`로 변경하지 않음; `SecurityUtils`에서 `getCurrentAccountId()`가 `null` 반환 시 예외 처리 방식 변경 금지(기존 `docs/guide-backend.md` 4절 `SecurityUtils` 패턴 유지); `ImpersonationContextHolder`를 정적 싱글턴으로 변경하지 않음.
- **AC**: AC-501(`set`/`get`/`remove` 사이클이 동일 문자열 반환); AC-502(`SecurityUtils.getCurrentAccountId()`가 임퍼소네이션 중 `Long`(`subjectAccountId`) 반환 — `Account.java` `id` 타입과 일치); AC-503(`isImpersonating()`이 세션 ID 존재 여부로 정확히 판정).
- **선행 SF**: SF-4 (`--deps '["sf-4-task-id"]'`) — 안전상 인터셉터 이후로 지정했으나 코드 구조상 SF-3 이후 가능(`SecurityUtils`는 `ImpersonationContextHolder`에만 의존하므로 SF-1과 병렬 가능하지만 `ReadOnlyImpersonationInterceptor` 확인 후로 안전하게 지정)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests '*Context*' --rerun`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain --rerun-tasks -q`
- **실제 파일 인용**: `ImpersonationContextHolder.java:13`(`ThreadLocal` 선언), `:22`(`set`), `:29`(`get`), `:36`(`remove`), `:41`(`getImpersonationSessionId`), `:42`(`getSubjectAccountId`); `SecurityUtils.java:37`(`isImpersonating()` → `getSubjectAccountId()` 경로); `Account.java:63`(`id` `Long` `IDENTITY`).

---

## 8. SF-6: 감사 기록 — v7 확정 항목 (`audit start/end/replaced/expired` 영속 저장) / 통합 `NOT_IMPLEMENTED`

- **v7 승인 상태**: `docs/architecture/admin-impersonation.md:13`(`scheduler는 DB 종료 + 감사 일체 tx를 수행한다`), `:17`(`audit start/end/replaced/expired 영속 저장`) — v7 아키텍처 설계에 감사 기록 영속 저장이 명시되어 있으므로 감사 코드/엔티티/서비스 존재는 v7 확인 항목으로 간주함(`NOT_CONFIRMED`이 아님).
- **현재 구현 상태 (`NOT_IMPLEMENTED`)**: `audit/ActionAuditLog.java` 엔티티 존재(`line 58`: `IMPERSONATION_START`, `:68`: `IMPERSONATION_END`, `:78`: `IMPERSONATION_EXPIRED`); `audit/AuditLogService.java` 존재(`line 47`: `logImpersonationStart`, `:63`: `logImpersonationEnd`); `ActionAuditLogRepository.java` 존재. 그러나 `service/impersonation/ImpersonationService.java:29` 주입 목록(`ImpersonationSessionRepository`, `AccountRepository`, `SecurityUtils`)에 `AuditLogService`가 **없음** — `startImpersonation`(`line 46`), `endImpersonation`(`line 133`), `expireSessions`(`line 176`)에서 `AuditLogService` 직접 호출이 코드상 존재하지 않음(`NOT_IMPLEMENTED` — 구현 누락). `ImpersonationService.java:78`(`log.info`)는 `slf4j` 로그만 사용. `docs/current-status.md` 2026-10-26 기준 전체 테스트 1,099건 PASS이지만 임퍼소네이션 전용 감사 테스트는 저장소 내 없음(`NOT_RUN`).
- **비범위**: `AuditLogService` 자체 구현 변경 금지(기존 코드 유지); `ActionAuditLog` 엔티티 스키마 변경 금지(마이그레이션 변경 금지); 감사 보존기간(retention) 등 v7 설계문서(`docs/architecture/admin-impersonation.md`)와 코드에서 확인되지 않는 세부 정책은 추측 금지; `python` 기반 테스트 추가 금지.
- **AC**: AC-601(`AuditLogService.java` 존재 및 `logImpersonationStart`/`logImpersonationEnd`/`IMPERSONATION_START`/`IMPERSONATION_END`/`IMPERSONATION_EXPIRED` 코드 확인 — v7 확정 항목); AC-602(`audit/` 패키지 존재 및 `compileJava` 통과); AC-603(`ImpersonationService` 내 `AuditLogService` 직접 호출이 현재 `NOT_IMPLEMENTED` — `line 29` 주입 목록과 `line 46`/`133`/`176` 호출 부재 확인; `BLOCKED`이 아닌 `NOT_IMPLEMENTED`로 기록 — 구현 누락이므로 별도 승인 게이트로 오해하지 않음).
- **상태**: `NOT_IMPLEMENTED` (v7 확정 항목이지만 `ImpersonationService` 통합 구현이 누락됨 — 별도 구현 SF 필요, `BLOCKED`이 아님).
- **선행 SF**: SF-2 (`--deps '["sf-2-task-id"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew compileJava --rerun-tasks -q`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain --rerun-tasks -q`; `git diff --check`
- **실제 파일 인용**: `ImpersonationService.java:29`(주입 목록 — `AuditLogService` 부재), `:46`/`:133`/`:176`(직접 호출 없음 — `NOT_IMPLEMENTED`); `AuditLogService.java:47`(`logImpersonationStart`), `:63`(`logImpersonationEnd`); `ActionAuditLog.java:58`(`IMPERSONATION_START`), `:68`(`IMPERSONATION_END`), `:78`(`IMPERSONATION_EXPIRED`); `docs/architecture/admin-impersonation.md:13`, `:17`(v7 설계 — 감사 영속 저장 명시); `docs/current-status.md`(`v7` 관련 승인 기록 없음 — 그러나 현재 대화에서 사용자가 명시적으로 지시했으므로 v7 기준 실행 범위로 간주).

---

## 9. SF-7: 통합 검증 — `guide-backend.md` 명령 + `git diff --check`

- **범위**: 전체 SF 통합 후 `docs/guide-backend.md` 2절 검증 명령을 **정확히 복사**해 실행(`DB_NAME=ipo_slot_1` ~ `DB_NAME=ipo_slot_4` 중 빈 슬롯 사용). `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build`; `git diff --check`; `git diff --stat`; `git status --short --branch`. 변경 파일이 SF-1~SF-5 범위(`backend/src/main/java/com/ipocalc/accountauthentication/...` 및 `backend/src/main/resources/db/migration/` 내 기존 파일)만 포함되어야 함. SF-6(`NOT_IMPLEMENTED`) 통합 후 추가 가능.
- **비범위**: `spotlessApply` 금지(`docs/guide-backend.md:66` 복사); 기본 DB(`DB_NAME` 생략) 테스트 금지; 마이그레이션 내용 변경 금지; `tests/` 외 디렉토리 테스트 파일 추가 금지(`docs/guide-backend.md` 5절 지뢰 복사); `python -m pytest` 사용 금지; `sqlite3.connect` 사용 금지.
- **AC**: AC-701(`build` 통과 — `gradlew build`); AC-702(`checkstyle` 위반 0건 — `maxWarnings = 0`, `build.gradle.kts:83`); AC-703(`test` 전체 통과 기준은 기존 `docs/current-status.md` 기준 1,099건 PASS이지만 임퍼소네이션 전용 테스트는 별도 존재하지 않으므로 `NOT_RUN` — 전체 PASS를 SF-7 완료로 과장하지 않음); AC-704(`git diff --check` PASS); AC-705(`git diff --stat`이 `[범위]` 파일만 포함 — 다른 Epic 패키지 변경 없음 확인).
- **선행 SF**: SF-1, SF-2, SF-3, SF-4, SF-5 (`--deps '["sf-1-task-id","sf-2-task-id","sf-3-task-id","sf-4-task-id","sf-5-task-id"]'`); SF-6(`NOT_IMPLEMENTED`) 통합 구현 완료 후 추가(`--deps '["sf-1-task-id","sf-2-task-id","sf-3-task-id","sf-4-task-id","sf-5-task-id","sf-6-task-id"]'`).
- **검증 명령**(정확히 `docs/guide-backend.md` 2절 복사, `docs/worker-spec-template.md` 42~45줄 형식 준수):
  ```bash
  scripts/db-slot.sh reset 1
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build
  git diff --check
  git diff --stat
  ```
- **테스트 명령 예**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`
- **실제 파일 인용**: `docs/guide-backend.md:25`(`DB_NAME=ipo_slot_1 ... ./gradlew test --rerun`), `:27`(`checkstyleMain checkstyleTest --rerun-tasks -q`), `:28`(`build`), `:66`(`DB_SLOT` 규칙), `:83`(`maxWarnings = 0`); `build.gradle.kts:2`(Spring Boot 4.1.1), `:83`(`maxWarnings = 0`); `docs/current-status.md`(전체 테스트 1,099건 PASS 기준); `docs/worker-spec-template.md`(SF 형식).

---

## 10. 의존성 (`--deps`) 요약

```bash
# SF-1 (엔티티/리포지토리/마이그레이션 확인)
orca orchestration task-create --task-title "세부기능 1: ImpersonationSession 엔티티+리포지토리+DB 확인" --spec "범위: domain/model/impersonation/ImpersonationSession.java, repository/impersonation/ImpersonationSessionRepository.java, migration 파일 존재 확인. 비범위: SQLite 설계/마이그레이션 내용 변경 금지. AC: AC-101~104. 검증: DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew compileJava --rerun-tasks -q, checkstyleMain --rerun-tasks -q, git diff --check." --json

# SF-2 (서비스)
orca orchestration task-create --task-title "세부기능 2: ImpersonationService 시작/상태/종료/만료" --spec "범위: service/impersonation/ImpersonationService.java. 선행: SF-1 완료(--deps '[\"sf-1-id\"]'). AC: AC-201~205. 검증: DB_NAME=ipo_slot_1 ... ./gradlew test --tests '*ImpersonationService*' --rerun, checkstyleMain --rerun-tasks -q." --parent <epic_task_id> --deps '["sf-1-id"]' --json

# SF-3 (컨트롤러)
orca orchestration task-create --task-title "세부기능 3: AdminImpersonationController API" --spec "범위: controller/AdminImpersonationController.java, DTO. 선행: SF-2(--deps '[\"sf-2-id\"]'). AC: AC-301~304. 검증: DB_NAME=ipo_slot_1 ... ./gradlew test --tests '*AdminImpersonation*' --rerun." --parent <epic_task_id> --deps '["sf-2-id"]' --json

# SF-4 (보안 인터셉터/컨텍스트 필터)
orca orchestration task-create --task-title "세부기능 4: ReadOnlyImpersonationInterceptor + ContextFilter" --spec "범위: config/security/ReadOnlyImpersonationInterceptor.java, config/security/ImpersonationContextFilter.java, SecurityConfig.java 관련. 선행: SF-3(--deps '[\"sf-3-id\"]'). AC: AC-401~404." --parent <epic_task_id> --deps '["sf-3-id"]' --json

# SF-5 (컨텍스트 유틸리티)
orca orchestration task-create --task-title "세부기능 5: ImpersonationContextHolder + SecurityUtils" --spec "범위: util/security/ImpersonationContextHolder.java, util/security/SecurityUtils.java. 선행: SF-4(--deps '[\"sf-4-id\"]'). AC: AC-501~503." --parent <epic_task_id> --deps '["sf-4-id"]' --json

# SF-6 (감사 기록 — v7 확정 항목 / 통합 `NOT_IMPLEMENTED`)
orca orchestration task-create --task-title "세부기능 6: 감사 기록 통합 구현" --spec "범위: v7 설계(`docs/architecture/admin-impersonation.md:17`) 기준 `audit start/end/replaced/expired` 영속 저장; `AuditLogService.java` 존재(`line 47`/`63`) 및 `ActionAuditLog.java`(`line 58`/`68`/`78`) 확인; `ImpersonationService.java:29` 주입 목록에 `AuditLogService` 추가 및 `line 46`/`133`/`176`에서 직접 호출 추가가 현재 `NOT_IMPLEMENTED`(누락) — `BLOCKED`이 아닌 구현 누락. 비범위: 감사 보존기간(retention) 등 v7/코드에서 확인되지 않는 세부 정책 추측 금지. AC: AC-601~603. 상태: NOT_IMPLEMENTED." --parent <epic_task_id> --deps '["sf-2-id"]' --json

# SF-7 (통합 검증)
orca orchestration task-create --task-title "세부기능 7: 통합 검증(build/checkstyle/test/diff)" --spec "범위: 전체 SF 통합 후 guide-backend.md 2절 명령 복사 실행. 선행: SF-1~SF-5(--deps '[\"sf-1-id\",\"sf-2-id\",\"sf-3-id\",\"sf-4-id\",\"sf-5-id\"]'); SF-6(`NOT_IMPLEMENTED`) 통합 시 추가(--deps '[\"sf-1-id\",...,\"sf-6-id\"]'). AC: AC-701~705." --parent <epic_task_id> --deps '["sf-1-id","sf-2-id","sf-3-id","sf-4-id","sf-5-id"]' --json
```

---

*기준: `docs/guide-backend.md`(Spring Boot 4.1.1, Java 21, PostgreSQL, Spring Session JDBC, Checkstyle `maxWarnings=0`, DB 슬롯 `ipo_slot_1`~`4`, `spotlessApply` 금지); `docs/roadmap.md`(Epic 의존성 맵 형식, `breakdown` 승인 전 구현 금지); `docs/worker-spec-template.md`(SF 형식); `AGENTS.md`(3~8 SF, `--deps` DAG, `work-log` 사실만); `docs/current-status.md`(테스트 기준 1,099건 PASS). `v7` 승인: 현재 대화에서 사용자가 명시적으로 지시했으므로 SF-1~SF-5는 승인된 실행 범위로 간주; SF-6은 v7 확정 항목(`docs/architecture/admin-impersonation.md:17`)이지만 통합 `NOT_IMPLEMENTED`; SF-7 통합 검증은 `NOT_RUN`; 별도 정책 게이트(`BLOCKED`)로 오해하지 않음.*
*검증 상태: `git diff --check` 통과 확인 완료(두 파일 모두 새로 작성, 기존 내용 전면 교체 — 이전 `python`/`sqlite` 기반 내용 제거 완료).*
