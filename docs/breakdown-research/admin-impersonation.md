# 관리자 임퍼소네이션 breakdown-research — Spring Boot 4.1.1 / Spring Security 7.1.1 (resolved `spring-security-web`, `./gradlew dependencyInsight` 확인) / Spring Session JDBC / PostgreSQL / JPA Account(Long ID) — 경로 분리: 외부 `/api/admin/impersonation/*` vs proxy-merged `/admin/impersonation/*`

> **Epic 상태**: 저장소 `docs/roadmap.md`에 관리자 임퍼소네이션이 별도 Epic 번호로 등록되어 있지 않음. 실제 코드(`AdminImpersonationController`, `ImpersonationService`, `ImpersonationSession`)는 존재하나 `docs/roadmap.md` 5절 Epic 표(1~22)에는 포함되어 있지 않음.
> **근거 문서**: 이 문서는 실제 파일 내용과 줄 번호를 기준으로 작성함(`docs/breakdown-research/` 규칙). 추측으로 기능 요구를 추가하지 않음.
> **기술 기준**: `backend/build.gradle.kts`: Spring Boot 4.1.1 (`line 2`); `spring-boot-starter-security` 포함 — resolved `spring-security-web:7.1.1` (`./gradlew dependencyInsight --dependency spring-security-web --configuration testRuntimeClasspath` 실행 기록); `spring-boot-starter-session-jdbc` 포함(`line 31`); `postgresql`(`line 31`); `jakarta.persistence` API; Java 21(`line 14`). `docs/guide-backend.md` 1절 기준. **경로 분리**: 프론트엔드 `ui/vite.config.ts:14` proxy rewrite(`/api` 제거)로 인해 외부 API 호출(`/api/admin/impersonation/*`)과 backend 수신 경로(`/admin/impersonation/*`)를 구분 기록함.
> **v7 승인 및 실행 범위**: 현재 대화에서 사용자가 v7 기준 구현을 명시적으로 지시했으므로, SF-1~SF-5(엔티티·서비스·컨트롤러·인터셉터·컨텍스트)의 실제 코드 확인·수정 범위는 승인된 실행 범위로 간주한다. 별도 상태는 SF-6(`NOT_IMPLEMENTED` — 감사 기록 통합 구현 누락; v7 설계 `docs/architecture/admin-impersonation.md:17`에 영속 저장 명시되어 있으나 `ImpersonationService.java:29` 주입 부재 및 직접 호출 부재)와 SF-7(`NOT_RUN` — 통합 검증 대기)로 분리 관리. 확인된 정책: `SecurityConfig.java:68`(`/admin/**` ADMIN 전용 — proxy-merged 경로와 일치); `AdminImpersonationController.java:24`(`@PreAuthorize`); `ReadOnlyImpersonationInterceptor.java`(읽기 전용 강제). **미해결**: 테스트 환경에서의 `ImpersonationSession` 만료 처리(`expireSessions` 스케줄러) 실제 DB 반영 여부(`docs/current-status.md` 2026-10-23 기준 전체 테스트 1,099건 통과, checkstyle 0; 별도 임퍼소네이션 전용 테스트는 저장소 내 없음 — `NOT_RUN`으로 기록, 전체 PASS를 SF 완료로 과장하지 않음).

---

## 1. 실제 코드 기반 확인 (파일·줄 근거)

| 항목 | 파일·줄 | 내용 |
|---|---|---|
| Controller | `backend/src/main/java/com/ipocalc/accountauthentication/controller/AdminImpersonationController.java:22` | `@RequestMapping("/api/admin/impersonation")` |
| Controller — 시작 | `AdminImpersonationController.java:37` | `@PostMapping("/start/{accountId}")`; `@PathVariable Long accountId` |
| Controller — 상태 | `AdminImpersonationController.java:60` | `@GetMapping("/status")`; `ImpersonationStatusResponse` 반환 |
| Controller — 종료 | `AdminImpersonationController.java:72` | `@PostMapping("/end")`; `ResponseEntity<Void>`(`noContent`) |
| Controller — 권한 | `AdminImpersonationController.java:24` | `@PreAuthorize("hasRole('ADMIN')")` |
| Service — 시작 | `service/impersonation/ImpersonationService.java:46` | `startImpersonation(Long adminId, Long targetAccountId, HttpSession)`; `ValidationFailedException`(자기 자신 방지, `line 48`); 대상 존재·승인 확인(`findByIdAndApprovedTrue`, `line 53`); 기존 세션 종료(`endCurrentImpersonation`, `line 57`) |
| Service — 세션 ID | `ImpersonationService.java:60` | `String sessionId = UUID.randomUUID().toString()` |
| Service — 만료 | `ImpersonationService.java:35` | `IMPERSONATION_TIMEOUT_MINUTES = 30` |
| Service — 상태 조회 | `ImpersonationService.java:104` | `getStatus(HttpServletRequest)`; 활성 세션 조회(`findActiveSessionById`, `line 155`); 만료 시 자동 종료(`endImpersonation`, `line 121`) |
| Service — 종료 | `ImpersonationService.java:133` | `endImpersonation(String sessionId, String reason)` |
| Service — 스케줄러 | `ImpersonationService.java:176` | `@Scheduled(fixedRate = 60_000)`; `expireSessions()`; `findExpiredActiveSessions`(`line 44` repository) |
| Domain — 엔티티 | `domain/model/impersonation/ImpersonationSession.java:17` | `@Table(name = "impersonation_sessions")` |
| Domain — PK | `ImpersonationSession.java:25` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;` |
| Domain — actor | `ImpersonationSession.java:29` | `actorAdminId` (`Long`, `nullable = false`) |
| Domain — 대상 | `ImpersonationSession.java:34` | `subjectAccountId` (`Long`, `nullable = false`) |
| Domain — 세션 ID | `ImpersonationSession.java:37` | `sessionId` (`String`, `nullable = false`, `unique = true`, `length = 36`) |
| Domain — 만료 | `ImpersonationSession.java:45` | `expiresAt` (`LocalDateTime`, `nullable = false`) |
| Domain — 종료 | `ImpersonationSession.java:49` | `endedAt` (`LocalDateTime`) + `endedReason` (`String`, `length = 20`) |
| Domain — 읽기 전용 | `ImpersonationSession.java:57` | `readOnly` (`Boolean`, `nullable = false`, `@Builder.Default` = `true`) |
| Repository | `repository/impersonation/ImpersonationSessionRepository.java:17` | `JpaRepository<ImpersonationSession, Long>`; `findBySessionIdAndEndedAtIsNullAndExpiresAtAfter`(`line 22`); `findActiveByActorAdminId`(`line 36`); `findExpiredActiveSessions`(`line 43`) |
| Security Config | `config/security/SecurityConfig.java:68` | `.requestMatchers("/admin/**").hasRole("ADMIN")` |
| Security — 세션 | `SecurityConfig.java:109` | `.maximumSessions(1)`, `.sessionRegistry(sessionRegistry)`(DB 기반) |
| Security — CSRF | `SecurityConfig.java:82` | `CookieCsrfTokenRepository.withHttpOnlyFalse()`; `ignoringRequestMatchers`에 `/accounts`, `/auth/login`, `/auth/logout`, `/actuator/**` 포함 |
| Security — 필터 | `SecurityConfig.java:168` | `ImpersonationContextFilter` 빈 생성; `addFilterAfter(impersonationContextFilter, SecurityContextHolderFilter.class)` |
| Interceptor | `config/security/ReadOnlyImpersonationInterceptor.java:18` | `HandlerInterceptor`; `ALLOWED_READ_ONLY_PATTERNS`(`line 23`); `IMPERSONATION_ALLOWED_POST`(`line 37`); `isImpersonating()`(`ImpersonationContextHolder`, `line 47`) |
| Context Holder | `util/security/ImpersonationContextHolder.java:13` | `ThreadLocal<String> IMPERSONATION_SESSION_ID`; `set`, `get`, `remove` |
| Security Utils | `util/security/SecurityUtils.java:37` | `getCurrentAccountId()`; `isImpersonating()` 시 `getSubjectAccountId()` 반환 |
| Audit — 엔티티 | `audit/ActionAuditLog.java` (파일 존재 확인) | 감사 로그 테이블; `impersonation` 관련 액션 기록 가능 |
| Audit — 서비스 | `audit/AuditLogService.java` (파일 존재 확인) | 감사 기록 서비스; `ActionAuditLogRepository` 사용 |
| Build — 버전 | `build.gradle.kts:2` | Spring Boot `version "4.1.1"` |
| Build — DB | `build.gradle.kts:31` | `postgresql` + `spring-boot-starter-session-jdbc` |
| Build — 테스트 | `build.gradle.kts:63` | `systemProperty("app.ipo-sync.enabled", "false")` 등; `useJUnitPlatform()`; `finalizedBy(jacocoTestReport)` |
| Guide — 스타일 | `docs/guide-backend.md:36` | 2칸 들여쓰기, 탭 금지, 100자 제한 |
| Guide — Checkstyle | `docs/guide-backend.md:39` | `checkstyleMain`, `checkstyleTest` 포함, 위반 0건 통과 기준 |
| Guide — 슬롯 DB | `docs/guide-backend.md:66` | `DB_NAME=ipo_slot_1`~`4`; `scripts/db-slot.sh reset <n>`; Master 검증은 슬롯 4 |
| Guide — 지뢰 | `docs/guide-backend.md:64` | `spotlessApply` 금지; 테스트는 슬롯 DB 사용; 시크릿 로그 금지 |
| Template — SF 형식 | `docs/worker-spec-template.md` | [범위], [비범위], [AC], [검증], [완료 조건], [보고 형식] 포함 |

---

## 2. 실제 구현 상태와 범위 확인

- **코드 존재**: `AdminImpersonationController`, `ImpersonationService`, `ImpersonationSession` 엔티티, `ImpersonationSessionRepository`, `SecurityConfig`(`admin/**` ADMIN 전용), `ReadOnlyImpersonationInterceptor`, `ImpersonationContextFilter`, `ImpersonationContextHolder` — 모두 실제 파일 존재.
- **DB 테이블**: `impersonation_sessions` 테이블이 실제 DB(`postgresql`)에 존재해야 함(`Flyway` 마이그레이션 기반). 현재 저장소 `backend/src/main/resources/db/migration/` 내 파일 목록 확인 필요(이 파일은 직접 확인하지 않았으나 `build.gradle.kts` `spring-boot-starter-flyway` 존재로 마이그레이션 기반 운영이 확정). **마이그레이션 파일 내용 변경 금지**(`guide-backend.md` 5절 지뢰 복사).
- **API 경로 실제 확인**: 외부(front-facing) 경로는 `/api/admin/impersonation/start/{accountId}`(POST), `/api/admin/impersonation/status`(GET), `/api/admin/impersonation/end`(POST) — `AdminImpersonationController.java:22`(`/api/admin/impersonation`). 프론트엔드 proxy(`ui/vite.config.ts:14`) rewrite(`path.replace(/^\/api/, '')`)로 인해 backend 실제 수신 경로는 `/admin/impersonation/start/{accountId}` 등(`SecurityConfig.java:68` `/admin/**` ADMIN 전용과 일치) — 두 경로를 분리 기록. `AdminUserController`는 `/admin/accounts` 등 별도 관리 기능 담당(`docs/roadmap.md` 6.2절 E1 카드와 일치).
- **안전하지 않은 수락 기준 제거**: 기존 문서(`docs/breakdown/admin-impersonation.md` 원본)에 존재하던 `python -m pytest`, `sqlite3.connect`, `DB_SLOT=1` 이후 SQLite 파일 직접 접근, UUID PK(`PK UUID`) 설계, `tests/db-schema` 외 SQLite 스키마 수정 금지 — **전부 제거**. 실제 기준은 PostgreSQL 문법(`GENERATED ALWAYS AS IDENTITY` 권장, `guide-backend.md:46`), `Long` PK(`Account.java:63`), `UUID`는 세션 식별자 문자열(`sessionId`, `ImpersonationSession.java:37`)로만 사용.

---

## 3. 미해결 게이트와 정책

| 게이트/정책 | 상태 | 근거 |
|---|---|---|
| `v7` 실행 범위 | **승인된 실행** (현재 대화 명시 지시) | 사용자가 v7 기준 구현을 명시적으로 지시함; SF-1~SF-5는 승인된 실행 범위로 간주; 별도 상태는 SF-6(`NOT_IMPLEMENTED`)과 SF-7(`NOT_RUN`)으로 분리 관리 |
| `v7` 별도 상태 | `NOT_IMPLEMENTED`(`SF-6`) / `NOT_RUN`(`SF-7`) | 감사 기록(`AuditLogService`) 통합 구현 누락(`ImpersonationService.java:29` 주입 부재, `line 46`/`133`/`176` 직접 호출 없음 — `NOT_IMPLEMENTED`, `BLOCKED`이 아님); 통합 검증(`SF-7`)은 `NOT_RUN` — 전체 PASS를 과장하지 않음 |
| 관리자 역할 확인 | **확인됨** (`CONFIRMED`) | `AccountRole.java`(`USER`/`ADMIN`), `Account.java:89`; `SecurityConfig.java:68`(`/admin/**` ADMIN 전용) |
| 읽기 전용(`readOnly=true`) | **확인됨** | `ImpersonationSession.java:57`; `ReadOnlyImpersonationInterceptor.java:23` |
| 30분 만료 | **확인됨** | `ImpersonationService.java:35` (`IMPERSONATION_TIMEOUT_MINUTES = 30`) |
| 자기 자신 임퍼소네이션 차단 | **확인됨** | `ImpersonationService.java:48` (`adminId.equals(targetAccountId)` 예외) |
| 대상 계정 승인 여부 확인 | **확인됨** | `ImpersonationService.java:53` (`findByIdAndApprovedTrue`) |
| CSRF 보호 상태 | **확인됨** | `SecurityConfig.java:80`; `CookieCsrfTokenRepository`; `CsrfTokenFilter` 존재 |
| 테스트 환경(DB 슬롯) | **확인됨** | `docs/guide-backend.md:66` (`DB_NAME=ipo_slot_1`~`4`) |
| 테스트 통과 기준 | **확인됨** | `checkstyle` 위반 0건(`maxWarnings = 0`); `spotlessCheck` 통과; `build` 통과(`docs/current-status.md` 2026-10-26 기준 PASS) |
| 마이그레이션 금지 | **확인됨** | `guide-backend.md` 5절: 기존 마이그레이션 파일 이름·내용 변경 금지; `scripts/db-slot.sh reset` 사용 |

---

## 4. SF 분해 (3~8 SF, 실제 모듈·API 기반)

> 의존성은 `docs/roadmap.md` 3절 의존성 맵 형식(`E1 → E2` 등)을 참고해 `--deps`로 표현. 각 SF는 독립 PR 단위(`AGENTS.md` 11.2절).

```
SF-1 (entity/repository/migration)  →  SF-2 (service)  →  SF-3 (controller/API)
      ↓ (병렬 가능: repository는 service 없이 독립 테스트 가능)
SF-5 (security/interceptor)  ←  SF-3  (인터셉터는 controller 경로 필요)
      ↓
SF-4 (context/utility)  ←  SF-3 (컨텍스트는 세션 존재 필요)
      ↓
SF-6 (audit)  ←  SF-2 (감사 로그는 서비스 액션 필요)
      ↓
SF-7 (검증/QA)  ←  전체 SF (test/build/checkstyle/diff)
```

### SF-1: `ImpersonationSession` 엔티티 + `ImpersonationSessionRepository` + Flyway 테이블
- **범위**: `ImpersonationSession.java` 엔티티(`Long id`, `actorAdminId(Long)`, `subjectAccountId(Long)`, `sessionId(String UUID)`, `readOnly(Boolean)`, `startedAt`, `expiresAt`, `endedAt`, `endedReason`, `createdAt`, `updatedAt`) 확인; `ImpersonationSessionRepository.java` 쿼리(`findBySessionIdAndEndedAtIsNullAndExpiresAtAfter`, `findActiveByActorAdminId`, `findExpiredActiveSessions`, `findBySessionId`) 확인; `postgresql` 기반 `impersonation_sessions` 테이블 존재 확인(`Flyway` 마이그레이션 기반 — 파일 내용 직접 확인 금지, 존재 여부만 확인).
- **비범위**: `UUID`를 엔티티 PK로 사용하지 않음(`Account.java`와 동일한 `Long IDENTITY`); SQLite 설계 문서 작성 금지; `sqlite_master` 조회 금지; 마이그레이션 내용 수정 금지.
- **AC**: AC-101: 엔티티 파일 존재 및 컴파일(`compileJava`) 통과; AC-102: `sessionId`가 `String`(`UUID.randomUUID().toString()`)이며 `unique=true`, `length=36` 확인; AC-103: `actorAdminId`와 `subjectAccountId`가 `Long`(`nullable=false`)임 확인; AC-104: `readOnly` 기본값이 `true`임 확인.
- **선행 SF**: 없음 (`task-create --deps '[]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew compileJava --rerun-tasks -q`; `git diff --check`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain --rerun-tasks -q`
- **테스트 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun` (마이그레이션 실행 후 엔티티 저장·조회 테스트 — 기존 마이그레이션 존재 확인만 수행, 새 테스트 파일 작성은 별도 SF로 처리)

### SF-2: `ImpersonationService` — 시작/상태/종료/만료 스케줄러
- **범위**: `service/impersonation/ImpersonationService.java`의 `startImpersonation`(자기 자신 방지, 대상 `approved=true` 확인, 기존 세션 `REPLACED` 종료, 새 세션 생성, `HTTP_SESSION` 마커 설정), `getStatus`(활성 세션 조회 + 만료 자동 처리), `endImpersonation`(`MANUAL`/`EXPIRED`/`REPLACED`), `expireSessions`(`@Scheduled` 1분); `AuditLogService`와 연동 여부 확인(현재 코드에서 직접 호출하지 않음 — `ImpersonationService.java` 내 `log.info`만 존재); 감사 로그 기록(`ActionAuditLog`)은 별도 SF-6에서 처리.
- **비범위**: `python -m pytest` 사용 금지; `sqlite3.connect` 사용 금지; `DB_SLOT=1` 이후 SQLite 파일 직접 접근 금지; `tests/db-schema` 외 스키마 수정 금지; `tests/` 외 테스트 디렉토리 수정 금지.
- **AC**: AC-201: `startImpersonation`이 `adminId.equals(targetAccountId)` 시 `ValidationFailedException` 발생 확인; AC-202: 대상이 `findByIdAndApprovedTrue`로 확인되지 않으면 `AccountNotFoundException` 발생; AC-203: 새 세션 `sessionId`가 `UUID` 문자열, `readOnly=true`, `expiresAt`이 현재 시각 +30분(`IMPERSONATION_TIMEOUT_MINUTES`)임 확인; AC-204: 기존 활성 세션이 있으면 `REPLACED`로 종료 후 새 세션 생성(`endCurrentImpersonation`); AC-205: 만료된 세션 조회(`expireSessions`) 시 `endImpersonation` 호출됨 확인.
- **선행 SF**: SF-1 (`task-create --deps '[]'`는 SF-1 자체; SF-2는 `--deps '["<sf1_task_id>"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun` (서비스 테스트 — 기존 테스트 파일이 없으므로 현재 통과 기준은 `compileJava` + `checkstyle` 통과로 한정; 실제 서비스 단위 테스트 작성은 SF-2 범위 내에서 최소 구현만)
- **테스트 명령 예**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests '*Impersonation*' --rerun`

### SF-3: `AdminImpersonationController` — `/api/admin/impersonation/*` API
- **범위**: `AdminImpersonationController.java` 확인(`line 22`: `/api/admin/impersonation`); `POST /start/{accountId}`(`line 37`), `GET /status`(`line 60`), `POST /end`(`line 72`); 응답 DTO(`ImpersonationStartResponse.java`: `sessionId`, `expiresAt`, `readOnly`; `ImpersonationStatusResponse.java`: `impersonating`, `targetAccountId`, `expiresAt`) 확인; `PreAuthorize("hasRole('ADMIN')")` 확인.
- **비범위**: 새로운 인증 메커니즘 설계 금지(`SecurityConfig` 기존 `CookieCsrfTokenRepository` 유지); JWT 기반 CSRF 구현 금지(`CsrfTokenFilter` 존재 확인); `GET /auth/csrf` 익명 bootstrap은 `CsrfTokenFilter.java`에서 이미 처리(`line 1` 확인 필요 — 별도 파일 존재) — 이 SF에서는 `AdminImpersonationController`의 CSRF 예외 추가가 필요하지 않음(`SecurityConfig.java:83`: `ignoringRequestMatchers`에 `/auth/login` 등 포함, `/api/admin/**`은 예외 없음 — 따라서 `AdminImpersonationController`는 CSRF 토큰이 필요한 상태로 유지).
- **AC**: AC-301: `POST /api/admin/impersonation/start/{accountId}`가 `200`과 `sessionId`를 반환; AC-302: `GET /api/admin/impersonation/status`가 현재 세션 상태를 정확히 반환(`impersonating=true/false`, `targetAccountId`, `expiresAt`); AC-303: `POST /api/admin/impersonation/end`가 `204` 반환; AC-304: 비-ADMIN 요청이 `403` 반환(`SecurityConfig` `accessDeniedHandler` 확인).
- **선행 SF**: SF-2 (`--deps '["<sf2_task_id>"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`; `git diff --check`
- **API 경로 실제 확인**: `AdminImpersonationController.java` 내 `@PathVariable Long accountId`는 `Account.java`의 `id`(`Long`)와 일치; `SecurityUtils.getCurrentAccountId()`는 현재 로그인된 관리자 `Long` ID를 반환.

### SF-4: `ReadOnlyImpersonationInterceptor` + `ImpersonationContextFilter` — 읽기 전용 강제
- **범위**: `ReadOnlyImpersonationInterceptor.java` 확인(`line 23`: `ALLOWED_READ_ONLY_PATTERNS` — `/dashboard/**`, `/ipos/**`, `/market-holidays/**`, `/announcements/**`, `/subscriptions/**`, `/allocations/**`, `/sales/**`, `/profits/**`, `/notifications/**`, `/securities/**`, `/investors/**`, `/securities-firms/**`, `/auth/me`, `/api/admin/users/**`, `/api/admin/impersonation/**`, `/auth/**`, `/actuator/**`); `IMPERSONATION_ALLOWED_POST`(`line 37`: `/api/admin/impersonation/end`만 허용); `preHandle`(`line 45`)에서 `GET`/`HEAD`/`OPTIONS`만 허용하고 그 외 `unsafe method`는 `403` 반환; `ImpersonationContextFilter.java`(`SecurityConfig.java:168`)는 `ImpersonationContextHolder`를 HTTP 요청 속성에서 읽어 `SecurityContext`에 반영하는 필터.
- **비범위**: `ReadOnlyImpersonationInterceptor`에서 `/admin/**` 전체를 안전 메서드 허용 목록에 추가하지 않음(현재 `ALLOWED_READ_ONLY_PATTERNS`에는 `/api/admin/users/**`와 `/api/admin/impersonation/**`만 포함 — `/admin/accounts` 등 다른 관리 API는 임퍼소네이션 중 접근 불가로 설계됨 — 이는 기존 코드의 의도적 설계이므로 변경 금지).
- **AC**: AC-401: 임퍼소네이션 중 `GET /dashboard/**` 접근 성공; AC-402: 임퍼소네이션 중 `POST /sales/**` 차단(`403`); AC-403: `POST /api/admin/impersonation/end`는 임퍼소네이션 중에도 허용; AC-404: `POST /api/admin/impersonation/start`는 이미 진행 중이면 `403` 반환(`line 60` `forbid` 확인).
- **선행 SF**: SF-3 (`--deps '["<sf3_task_id>"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build`
- **테스트 명령 예**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests '*Interceptor*' --rerun`

### SF-5: `ImpersonationContextHolder` + `SecurityUtils` — 컨텍스트 전파
- **범위**: `ImpersonationContextHolder.java`(`line 13`: `ThreadLocal<String>`); `SecurityUtils.java`(`line 37`: `getCurrentAccountId()` — `ImpersonationContextHolder.isImpersonating()` 확인 후 `getSubjectAccountId()` 반환); `Account.java`(`line 63`: `id`는 `Long` — 컨텍스트에서 사용되는 `actorAdminId`, `subjectAccountId`는 모두 `Long`).
- **비범위**: `SecurityUtils`에서 `getCurrentAccountId()`가 `null` 반환 시 예외 처리는 기존 코드 유지(`docs/guide-backend.md` 4절: 계정 소유권 검증 패턴 재사용); `ImpersonationContextHolder`를 `ThreadLocal`이 아닌 `InheritableThreadLocal`로 변경하지 않음(기존 설계 유지).
- **AC**: AC-501: `ImpersonationContextHolder.set(sessionId)` 호출 후 `getImpersonationSessionId()`가 동일 문자열 반환; AC-502: `SecurityUtils.getCurrentAccountId()`가 임퍼소네이션 중 `subjectAccountId` 반환; AC-503: `SecurityUtils.getSubjectAccountId()`가 세션 없으면 `null` 반환(기존 동작 유지 확인).
- **선행 SF**: SF-4 (`--deps '["<sf4_task_id>"]'`) — 컨텍스트는 인터셉터 이후에만 유효하나, 실제 코드 구조상 `SecurityUtils`는 `ImpersonationContextHolder`에만 의존하므로 SF-1과 병렬 가능하지만 안전상 SF-3 이후로 지정.
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`

### SF-6: 감사 로그 — `AuditLogService` + `ActionAuditLog` 연동
- **범위**: `audit/ActionAuditLog.java`(`line 1`: 엔티티 존재 확인 필요 — 실제 파일 확인 결과 존재); `audit/ActionAuditLogRepository.java` 존재 확인; `audit/AuditLogService.java` 존재 확인(`line 1`). 현재 `ImpersonationService.java`는 `AuditLogService`를 직접 주입받지 않음(`line 29`: `ImpersonationSessionRepository`, `AccountRepository`, `SecurityUtils`만 주입). **미해결**: `startImpersonation`, `endImpersonation`, `expireSessions`에서 `AuditLogService`를 통한 기록이 코드에 존재하지 않음 — 기존 `log.info`만 사용. 이 SF는 감사 기록 추가 여부를 결정하는 게이트로 정의.
- **비범위**: `AuditLogService` 자체 수정 금지(`docs/current-status.md`에서 감사 기능은 별도 범위로 관리됨); `ActionAuditLog` 엔티티 스키마 변경 금지(마이그레이션 금지).
- **AC**: AC-601: 현재 코드 기준 `ImpersonationService`에서 `AuditLogService` 직접 사용 여부 확인(`NOT_USED` — `line 29` 주입 목록 검토); AC-602: `audit/` 패키지 존재 및 `ActionAuditLog` 엔티티 컴파일 통과(`compileJava`); AC-603: 감사 기록 추가는 v7 설계(`docs/architecture/admin-impersonation.md:17`)에 명시되어 있으므로 확정 항목이지만, `ImpersonationService` 내 통합은 현재 `NOT_IMPLEMENTED`(`line 29` 주입 부재, `line 46`/`133`/`176` 직접 호출 부재) — `BLOCKED`이 아닌 구현 누락으로 기록.
- **게이트(`NOT_IMPLEMENTED` / `NOT_RUN`)**: `v7` 실행 범위(SF-1~SF-5)와 별도 상태(`SF-6` 감사 기록 통합 — `NOT_IMPLEMENTED`; `SF-7` 통합 검증 — `NOT_RUN`)를 분리. 감사 기록은 v7 설계(`docs/architecture/admin-impersonation.md:17`, `line 13`: `scheduler는 DB 종료 + 감사 일체 tx`)에 명시되어 있으므로 `AuditLogService`/`ActionAuditLog` 존재는 v7 확정 항목이지만, `ImpersonationService.java` 내 통합 구현(`line 29` 주입, `line 46`/`133`/`176` 호출)이 현재 누락되어 있으므로 `NOT_IMPLEMENTED`. 이는 `BLOCKED`(사용자 승인 대기)과 혼동하지 않음 — 구현 누락. 감사 기록 추가 여부는 별도 결정이 필요하다고 추측하지 않고, 현재 누락된 구현 상태를 정확히 기록함.
- **선행 SF**: SF-2 (`--deps '["<sf2_task_id>"]'`)
- **검증 명령**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew compileJava --rerun-tasks -q`; `git diff --check`

### SF-7: 통합 검증 — `guide-backend.md` 검증 명령 + `git diff --check` + 테스트 통과
- **범위**: 전체 SF 통합 후 `docs/guide-backend.md` 2절 검증 명령 실행(`DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`); `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`; `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build`; `git diff --check`; `git status --short --branch`; `git log --oneline -10`; `git diff --stat` 확인. 마이그레이션 수정 없음 확인(`backend/src/main/resources/db/migration/` 파일 목록 확인 — 내용 변경 금지).
- **비범위**: `spotlessApply` 실행 금지(`guide-backend.md` 5절); 테스트 슬롯 외 기본 DB(`ipo_calc`) 사용 금지; 마이그레이션 파일 내용 변경 금지; `tests/` 외 디렉토리 테스트 수정 금지.
- **AC**: AC-701: `build` 통과; AC-702: `checkstyleMain`/`checkstyleTest` 위반 0건(`maxWarnings = 0`); AC-703: `test` 전체 통과 기준은 기존 1,099건 PASS이지만 임퍼소네이션 전용 테스트는 저장소 내 없으므로 별도 존재 없이는 전용 PASS를 과장하지 않음(`NOT_RUN`); AC-704: `git diff --check` PASS; AC-705: 변경 파일이 `[범위]` 파일만 포함.
- **선행 SF**: 전체 SF 완료 (`--deps '["<sf1>","<sf2>","<sf3>","<sf4>","<sf5>","<sf6>"]'`)
- **검증 명령** (정확히 `docs/guide-backend.md` 2절 복사):
  ```bash
  scripts/db-slot.sh reset 1
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q
  DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build
  git diff --check
  git diff --stat
  ```

---

## 5. 의존성 DAG (`task-create --deps` 표현)

```
SF-1 (entity/repo)  —  task: admin-impersonation-sf1
SF-2 (service)     —  task: admin-impersonation-sf2  --deps '["admin-impersonation-sf1"]'
SF-3 (controller)  —  task: admin-impersonation-sf3  --deps '["admin-impersonation-sf2"]'
SF-4 (security/interceptor) — task: admin-impersonation-sf4 --deps '["admin-impersonation-sf3"]'
SF-5 (context/utility)    — task: admin-impersonation-sf5 --deps '["admin-impersonation-sf4"]'  (안전상)
SF-6 (audit — v7 확정 항목 / 통합 `NOT_IMPLEMENTED`)  —  task: admin-impersonation-sf6 --deps '["admin-impersonation-sf2"]'  (`NOT_IMPLEMENTED`: 통합 구현 누락, `BLOCKED`이 아님)
SF-7 (verification)      — task: admin-impersonation-sf7 --deps '["admin-impersonation-sf1","admin-impersonation-sf2","admin-impersonation-sf3","admin-impersonation-sf4","admin-impersonation-sf5","admin-impersonation-sf6"]'
```

> **주의**: 같은 브랜치에서 앞 SF 코드 위에 뒤 SF를 쌓지 않음(`AGENTS.md` 7.2절). SF-6(`NOT_IMPLEMENTED` — 구현 누락, `BLOCKED`이 아님)이 통합 구현 완료되기 전까지 SF-7은 시작하지 않음.

---

## 6. 검증 결과 기록 형식 (`PASS` / `FAIL` / `NOT_RUN` / `BLOCKED`)

- `PASS`: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build` + `checkstyleMain` + `test` 통과; `git diff --check` PASS; 변경 파일이 `[범위]` 파일만 포함.
- `FAIL`: 위 명령 중 하나라도 실패; `checkstyle` 위반 0건이 아니면 `FAIL`; `git diff --stat`이 `[범위]` 외 파일 포함 시 `FAIL`.
- `NOT_RUN`: 테스트를 실행하지 않았거나(`docs/current-status.md` 2026-10-26 기준 전체 검증 `NOT_RUN` 항목 존재), 마이그레이션 변경이 필요한 SF는 `NOT_RUN`으로 기록. `BLOCKED`와 혼동하지 않음(`NOT_IMPLEMENTED`는 구현 누락, `NOT_RUN`은 검증 미실행, `BLOCKED`는 별도 승인 게이트).
- `BLOCKED`: 별도 승인 게이트(`docs/project-agent-guide.md` 8.5절 `gate-create`)가 필요한 정책 결정 상태. 현재 SF-6은 감사 기록이 v7 설계에 명시되어 있어 `NOT_IMPLEMENTED`(구현 누락)이므로 `BLOCKED`로 오해하지 않음.

---

## 7. 실제 파일·줄 인용 (주요 근거만 요약)

- `AdminImpersonationController.java:22`(`/api/admin/impersonation`), `:24`(`@PreAuthorize`), `:37`(`/start/{accountId}`), `:60`(`/status`), `:72`(`/end`)
- `ImpersonationService.java:35`(30분), `:46`(시작), `:60`(UUID 세션 ID), `:68`(HTTP 세션 마커 `IMPERSONATION_SESSION_ID`), `:104`(상태 조회), `:133`(종료), `:176`(스케줄러 1분)
- `ImpersonationSession.java:17`(`impersonation_sessions` 테이블), `:25`(`Long id`), `:29`(`actorAdminId`), `:34`(`subjectAccountId`), `:57`(`readOnly` 기본 `true`)
- `ImpersonationSessionRepository.java:22`(활성 세션 조회 쿼리), `:36`(관리자별 활성 조회), `:43`(만료 세션 조회)
- `SecurityConfig.java:68`(`/admin/**` ADMIN 전용), `:82`(CSRF 설정), `:109`(세션 레지스트리 DB 기반)
- `ReadOnlyImpersonationInterceptor.java:23`(허용 패턴 목록), `:37`(허용 POST 목록 — `/api/admin/impersonation/end`만 포함), `:45`(읽기 전용 강제 로직: `GET`/`HEAD`/`OPTIONS`만 허용, 그 외 `403`)
- `ImpersonationContextHolder.java:13`(`ThreadLocal` 세션 ID), `:22`(`set`), `:29`(`get`), `:36`(`isImpersonating`)
- `SecurityUtils.java:37`(`isImpersonating` → `getSubjectAccountId()`)
- `Account.java:63`(`id`는 `Long` `IDENTITY`), `:89`(`AccountRole` enum: `USER`, `ADMIN`)
- `guide-backend.md:11`(Spring Boot 4.1.1), `:39`(checkstyle 0건 기준), `:66`(DB 슬롯 규칙), `:83`(마이그레이션 이름/내용 변경 금지)
- `docs/roadmap.md`: 관리자 임퍼소네이션 Epic 번호 없음(기존 Epic 표에 포함 안 됨)
- `docs/current-status.md`: `v7` 관련 승인 기록 없음; 현재 대화에서 사용자가 명시적으로 지시했으므로 SF-1~SF-5는 승인된 실행 범위로 간주; SF-6(`NOT_IMPLEMENTED`)과 SF-7(`NOT_RUN`)은 별도 상태로 유지; 전체 테스트 기준(1,099건) 유지
- `docs/worker-spec-template.md`: SF 형식(`[범위]`, `[비범위]`, `[AC]`, `[검증]`) 준수

---

## 8. 금지 계약 모순 검색 (`git diff --check` 통과 조건)

- 기존 원본(`docs/breakdown/admin-impersonation.md` 이전 내용)과 비교 시 제거된 부정확 항목:
  - `Python`/`sqlite3` 사용 명령 → `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew ...`로 교체
  - `sqlite_master` 조회 → PostgreSQL 기반 `Flyway` 테이블 존재 확인으로 교체(`SELECT` 쿼리 예시 제거)
  - `UUID PK`(`PK UUID`) 설계 → `Long` `IDENTITY` (`Account.java`와 동일)로 교체
  - `DB_SLOT=1` 이후 `sqlite3.connect('db/ipo_slot_1.db')` 파일 직접 접근 → `DB_NAME=ipo_slot_1` 환경 변수 기반 `gradlew` 명령으로 교체
  - `tests/` 외 테스트 디렉토리 언급 → `docs/guide-backend.md` 5절 지뢰(테스트 슬롯 규칙) 복사로 교체
  - `python -m pytest` 명령 → `DB_NAME=ipo_slot_1 ... ./gradlew test --rerun`으로 교체
  - `npm test` / `npm run lint` / `npm run e2e` (프론트엔드 테스트 명령) → `guide-backend.md` 기준 `gradlew` 명령으로 교체(관리자 임퍼소네이션은 백엔드 Epic이므로 프론트엔드 E2E는 별도 범위로 명시; 현재 문서에서는 프론트엔드 SF는 별도 Epic으로 분리하지 않고 SF-3(API)까지만 정의)
  - `fictitious routes`(`GET /admin/users`, `/admin/users/{id}`가 아닌 실제 `/api/admin/users/**`) → 실제 `AdminUserController.java` 경로 확인 필요(파일 존재 확인 결과 `controller/admin/AdminUserController.java` 존재)
- **모순 없음 확인**: 새 문서에서 언급된 API 경로(`/api/admin/impersonation/*`)는 실제 `AdminImpersonationController.java`의 `@RequestMapping`과 일치; `SecurityConfig.java`의 `/admin/**` ADMIN 전용 규칙과 `ReadOnlyImpersonationInterceptor.java`의 허용 패턴이 서로 충돌하지 않음(인터셉터는 임퍼소네이션 중 `GET`/`HEAD`/`OPTIONS`와 `/api/admin/impersonation/end` POST만 허용 — 시작(`start`)은 POST이므로 인터셉터에서 차단됨 — 이는 기존 설계의 의도적 제한으로 문서에 명시).

---

*작성일: 2026-10-30* (기준 문서 날짜와 동일)
*기준 기술: Spring Boot 4.1.1 (`build.gradle.kts:2`), Spring Security 7.1.1 (resolved `spring-security-web`), Spring Session JDBC (`session-jdbc` starter), PostgreSQL (`postgresql` + `flyway` starter), JPA (`jakarta.persistence:jakarta.persistence-api` + Lombok `@Entity`), Java 21 (`java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }`). 감사: v7 설계(`docs/architecture/admin-impersonation.md:17`)에 `audit start/end/replaced/expired 영속 저장` 명시; `AuditLogService.java`(`line 47`/`63`), `ActionAuditLog.java`(`line 58`/`68`/`78`) 존재 — v7 확정 항목; `ImpersonationService.java` 내 통합은 현재 `NOT_IMPLEMENTED`.*
