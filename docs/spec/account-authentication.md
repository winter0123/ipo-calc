# 계정 인증 및 인가 명세 (Epic 1: account-authentication)

## 1. 사용자 목표

- 사용자가 이메일과 비밀번호로 안전하게 회원가입하고 로그인할 수 있다.
- 로그인한 사용자(계정)가 자신의 투자자·증권계좌·투자 기록만 조회·관리할 수 있도록 **계정 단위 데이터 격리**가 보장된다.
- 세션 기반 인증으로 브라우저 환경에서 CSRF 보호, 세션 고정 공격 방어, 동시 세션 제어 등 기본 보안 기능이 자동 적용된다.
- 로그아웃, 세션 만료, 인증 실패/계정 잠금 등 인증 상태 전이가 명확하게 처리된다.

---

## 2. 시나리오 및 Acceptance Criteria

> 형식: **Given / When / Then** (work-plan.md 33장 예시 준용)

### 시나리오 1: 회원가입 (성공)

| ID | AC-001 |
|---|---|
| **Given** | 사용자가 서비스에 처음 방문했고, 아직 계정이 없다. |
| **When** | 유효한 이메일(`user@example.com`), 비밀번호(정책 충족), 계정 이름 및 최초 투자자 표시 이름을 입력하고 회원가입을 요청한다. |
| **Then** | 계정과 계정 소유의 ACTIVE 본인 투자자가 함께 생성되고, 비밀번호는 `DelegatingPasswordEncoder`(BCrypt)로 해시되어 저장된다. 승인된 계정은 자동 로그인(세션 생성)되어 대시보드로 이동한다. 응답에 비밀번호 해시나 평문이 포함되지 않는다. |

### 시나리오 2: 회원가입 (중복 이메일)

| ID | AC-002 |
|---|---|
| **Given** | `user@example.com` 이메일로 이미 가입된 계정이 존재한다. |
| **When** | 동일한 이메일로 회원가입을 시도한다. |
| **Then** | "이미 사용 중인 이메일입니다" 에러 메시지와 함께 회원가입이 거부된다. HTTP 409 Conflict 또는 400 Bad Request 반환. 기존 계정 정보는 변경되지 않는다. |

### 시나리오 3: 회원가입 (입력값 검증 실패)

| ID | AC-003 |
|---|---|
| **Given** | 회원가입 폼이 표시된 상태다. |
| **When** | 이메일 형식이 아닌 값(예: "invalid-email"), 또는 비밀번호가 최소 길이 미만(예: "123")인 값을 입력하고 제출한다. |
| **Then** | 필드별 검증 에러 메시지가 표시되고(이메일: "올바른 이메일 형식이 아닙니다", 비밀번호: "비밀번호는 최소 8자 이상이어야 합니다"), 계정이 생성되지 않는다. HTTP 400 Bad Request 반환. |

### 시나리오 4: 로그인 (성공)

| ID | AC-004 |
|---|---|
| **Given** | `user@example.com` / 유효한 비밀번호로 가입된 계정이 존재하고, 계정 잠금 상태가 아니다. |
| **When** | 올바른 이메일과 비밀번호를 입력하고 로그인한다. |
| **Then** | 세션이 생성되고(`JSESSIONID` 쿠키 발급, `HttpOnly`, `Secure`, `SameSite=Lax`), 인증된 상태로 대시보드(/dashboard)로 리다이렉트된다. `SecurityContext`에 `CustomUserDetails`(accountId 포함)가 저장된다. 로그인 성공 이벤트가 감사 로그에 기록된다(IP, 시각, 계정 ID). |

### 시나리오 5: 로그인 (잘못된 비밀번호)

| ID | AC-005 |
|---|---|
| **Given** | `user@example.com` 계정이 존재하고, 계정 잠금 상태가 아니다. |
| **When** | 올바른 이메일과 틀린 비밀번호를 입력하고 로그인한다. |
| **Then** | "이메일 또는 비밀번호가 올바르지 않습니다" 메시지와 함께 로그인 실패. 실패 횟수 카운터가 1 증가한다. HTTP 401 Unauthorized 반환. 로그인 실패 이벤트가 감사 로그에 기록된다. |

### 시나리오 6: 로그인 (존재하지 않는 계정)

| ID | AC-006 |
|---|---|
| **Given** | `unknown@example.com` 이메일로 가입된 계정이 없다. |
| **When** | 해당 이메일과 임의의 비밀번호로 로그인을 시도한다. |
| **Then** | "이메일 또는 비밀번호가 올바르지 않습니다" 메시지와 함께 로그인 실패(계정 존재 여부 노출 방지). HTTP 401 Unauthorized 반환. |

### 시나리오 7: 로그인 (계정 잠금 상태)

| ID | AC-007 |
|---|---|
| **Given** | `user@example.com` 계정이 로그인 실패 5회 누적으로 잠금 상태(`isAccountNonLocked=false`)다. |
| **When** | 올바른 이메일과 비밀번호를 입력하고 로그인을 시도한다. |
| **Then** | "계정이 잠겼습니다. 관리자에게 문의하세요" 메시지와 함께 로그인 거부. HTTP 403 Forbidden 반환. 잠금 해제 전까지 로그인 불가. |

### 시나리오 8: 로그아웃

| ID | AC-008 |
|---|---|
| **Given** | 사용자가 로그인된 상태(유효한 세션 보유)다. |
| **When** | 로그아웃 버튼을 클릭하거나 `/logout` 엔드포인트를 호출한다. |
| **Then** | 세션이 무효화되고 `JSESSIONID` 쿠키가 삭제된다(`Max-Age=0`, `Expires=과거`). 로그인 페이지(/login?logout)로 리다이렉트된다. `SecurityContext`가 클리어된다. 로그아웃 이벤트가 감사 로그에 기록된다. |

### 시나리오 9: 세션 만료 후 보호된 페이지 접근

| ID | AC-009 |
|---|---|
| **Given** | 사용자가 로그인했고 세션이 30분(기본값) 동안 비활성 상태였다. |
| **When** | 보호된 페이지(예: `/dashboard`, `/api/investors`)에 접근한다. |
| **Then** | 세션이 만료되어 인증되지 않은 상태로 판단된다. 로그인 페이지(/login?expired)로 리다이렉트되거나(API인 경우 401 반환), "세션이 만료되었습니다. 다시 로그인하세요" 메시지가 표시된다. |

### 시나리오 10: 로그인하지 않은 상태에서 보호된 API 접근

| ID | AC-010 |
|---|---|
| **Given** | 사용자가 로그인하지 않은 상태(세션 없음, 쿠키 없음)다. |
| **When** | 인증이 필요한 API(예: `GET /api/investors`, `POST /api/subscriptions`)를 호출한다. |
| **Then** | HTTP 401 Unauthorized 반환. 응답 본문에 `{ "error": "UNAUTHORIZED", "message": "인증이 필요합니다" }` 형식의 에러 정보 포함. 로그인 페이지로 리다이렉트하지 않음(API 전용). |

### 시나리오 11: 다른 계정의 데이터 접근 차단 (IDOR 방지)

| ID | AC-011 |
|---|---|
| **Given** | 계정 A(`accountId=1`)가 투자자 1(`investorId=10`)을 소유하고, 계정 B(`accountId=2`)가 투자자 2(`investorId=20`)을 소유한다. 계정 A로 로그인된 상태다. |
| **When** | 계정 A가 `GET /api/investors/20`(계정 B의 투자자 ID)로 요청한다. |
| **Then** | HTTP 403 Forbidden 반환. "접근 권한이 없습니다" 메시지. 계정 B의 데이터가 절대 노출되지 않음. 서비스/리포지토리 레벨에서 `accountId` 조건절 강제로 필터링됨. |

### 시나리오 12: 비밀번호 변경 (선택, MVP 포함)

| ID | AC-012 |
|---|---|
| **Given** | 사용자가 로그인된 상태다. |
| **When** | 현재 비밀번호, 새 비밀번호(정책 충족), 새 비밀번호 확인을 입력하고 변경 요청한다. |
| **Then** | 현재 비밀번호가 일치하면 새 비밀번호가 BCrypt로 해시되어 업데이트된다. "비밀번호가 변경되었습니다" 메시지. 변경 후 기존 세션 유지(또는 보안 정책에 따라 재로그인 유도 — 결정 필요). 현재 비밀번호 불일치 시 "현재 비밀번호가 올바르지 않습니다" 에러. |

### 시나리오 13: Remember Me (선택, MVP 포함 여부 결정 필요)

| ID | AC-013 |
|---|---|
| **Given** | 로그인 페이지에서 "자동 로그인(Remember Me)" 체크박스가 제공된다. |
| **When** | 체크 후 로그인하고 브라우저를 완전히 종료했다가 다시 접속한다(세션 쿠키 삭제됨). |
| **Then** | Remember Me 토큰(해시 기반)으로 자동 인증되어 대시보드로 진입한다. 토큰 유효기간 14일. 토큰 탈취 시 해시 비교로 원본 노출 방지. |

---

## 3. 기능 요구사항

### 3.1 입력값

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---|---|
| 이메일 | String | Y | RFC 5322 이메일 형식, 최대 254자, 소문자 정규화 저장 |
| 비밀번호 | String | Y | 최소 8자 이상, 영문+숫자+특수문자 중 2종류 이상 포함(정확한 정책은 **결정 필요 사항** 참고), 최대 72자(BCrypt 제한) |
| 이름 | String | N | 최대 50자, XSS 방지를 위한 이스케이프 처리(저장 시) |
| 투자자 표시 이름 (`displayName`) | String | Y (회원가입 화면) | 최대 50자. 계정 생성과 함께 최초 본인 투자자에 저장 |
| 투자자 관계 (`relationship`) | String | N | 회원가입으로 만드는 본인 투자자이므로 `SELF`만 허용하며 생략 시 `SELF` |

### 3.2 검증 규칙

- **이메일**: `@` 포함, 도메인 부분 유효, 중복 불가(UNIQUE 제약조건)
- **비밀번호**: 평문으로 로그/콘솔/에러 메시지 출력 절대 금지
- **이름**: 선택 사항, 미입력 시 이메일 앞부분(`user@` → `user`)을 기본 표시명으로 사용

### 3.3 응답/에러 케이스

| 상황 | HTTP 상태 | 응답 예시 |
|---|---|---|
| 회원가입 성공 | 201 Created | `{ "accountId": 1, "email": "user@example.com", "name": "김철수" }` |
| 중복 이메일 | 409 Conflict | `{ "error": "DUPLICATE_EMAIL", "message": "이미 사용 중인 이메일입니다" }` |
| 검증 실패 | 400 Bad Request | `{ "error": "VALIDATION_FAILED", "fieldErrors": { "email": "올바른 이메일 형식이 아닙니다", "password": "비밀번호는 최소 8자 이상이어야 합니다" } }` |
| 로그인 성공 | 302 Found (리다이렉트) | `Location: /dashboard`, `Set-Cookie: JSESSIONID=...; HttpOnly; Secure; SameSite=Lax` |
| 로그인 실패(자격증명) | 401 Unauthorized | `{ "error": "INVALID_CREDENTIALS", "message": "이메일 또는 비밀번호가 올바르지 않습니다" }` |
| 계정 잠금 | 403 Forbidden | `{ "error": "ACCOUNT_LOCKED", "message": "계정이 잠겼습니다. 관리자에게 문의하세요" }` |
| 세션 만료(API) | 401 Unauthorized | `{ "error": "SESSION_EXPIRED", "message": "세션이 만료되었습니다. 다시 로그인하세요" }` |
| 권한 없음(타 계정 데이터) | 403 Forbidden | `{ "error": "ACCESS_DENIED", "message": "접근 권한이 없습니다" }` |
| 로그아웃 성공 | 302 Found | `Location: /login?logout`, `Set-Cookie: JSESSIONID=deleted; Max-Age=0; Expires=...` |

### 3.4 세션/쿠키 설정 (MVP 기본값)

| 설정 | 값 | 비고 |
|---|---|---|
| `server.servlet.session.timeout` | 30분 (1800초) | 비활성 타임아웃 |
| `server.servlet.session.cookie.http-only` | `true` | JavaScript 접근 차단 |
| `server.servlet.session.cookie.secure` | `true` | HTTPS 전송만 허용(운영) |
| `server.servlet.session.cookie.same-site` | `Lax` | CSRF 기본 방어, 필요 시 `Strict` |
| `spring.session.timeout` | 동일 | Spring Session 사용 시 |
| Remember Me 토큰 유효기간 | 14일 (1209600초) | 포함 결정 시 |

---

## 4. 엣지케이스

| ID | 상황 | 예상 동작 |
|---|---|---|
| EC-001 | 중복 가입 시도(동시 요청) | DB UNIQUE 제약조건으로 1건만 성공, 나머지는 409 에러. 트랜잭션 격리로 경쟁 상태 방지. |
| EC-002 | 존재하지 않는 계정으로 로그인 | 계정 존재 여부 노출 방지 위해 "이메일 또는 비밀번호가 올바르지 않습니다" 통일 메시지. |
| EC-003 | 세션 만료 후 폼 제출(CSRF 토큰 만료) | 세션 만료 감지 → 로그인 페이지 리다이렉트(`?expired`), 사용자 재로그인 후 원래 요청 재시도 권장. |
| EC-004 | 로그인 안 한 상태에서 보호된 API 직접 호출 | 401 반환, 로그인 페이지로 리다이렉트하지 않음(SPA/API 분리 고려). |
| EC-005 | 다른 계정의 리소스 ID 직접 조회(예: `/api/investors/999`) | 서비스/리포지토리 레벨 `accountId` 조건절로 빈 결과 → 403 또는 404(정보 은닉 위해 404 권장). |
| EC-006 | 로그인 실패 5회 도달 시 계정 잠금 | `UserDetails.isAccountNonLocked()=false` 반영, 이후 로그인 시도 시 403 반환. 관리자 해제 전까지 지속. |
| EC-007 | 잠금 해제 후 로그인 | 실패 횟수 카운터 리셋, 정상 로그인 가능. |
| EC-008 | 비밀번호 변경 중 동시 세션 존재 | 변경 후 기존 세션 유지(편의성) vs 전체 세션 무효화(보안) — **결정 필요**. |
| EC-009 | Remember Me 토큰 탈취 시 | 해시 저장으로 원본 노출 없음. 로그아웃 시 토큰 삭제(DB에서 제거). 재로그인 시 새 토큰 발급(회전). |
| EC-010 | HTTPS 미적용 환경(로컬 개발)에서 Secure 쿠키 | 로컬에서는 `Secure=false` 허용(프로파일 분리), 운영 배포 시 반드시 `true`. |

---

## 5. MVP 범위 (포함)

1. **이메일+비밀번호 회원가입**: 입력 검증, 중복 체크, BCrypt 해시 저장, 자동 로그인
2. **이메일+비밀번호 로그인**: `DaoAuthenticationProvider` + `UserDetailsService`, 세션 생성, 성공/실패 이벤트 로깅
3. **로그아웃**: 세션 무효화, 쿠키 삭제, SecurityContext 클리어
4. **세션 기반 인증**: `HttpSession` + `SecurityContextRepository` 기본, CSRF 보호(`CsrfFilter` 자동), 세션 고정 공격 방어(`changeSessionId()`)
5. **동시 세션 제어**: `maximumSessions(1)`로 중복 로그인 시 기존 세션 만료(`maxSessionsPreventsLogin=false`)
6. **계정 잠금**: 로그인 실패 5회 → `isAccountNonLocked=false`, 관리자 해제 필요(이메일 인증 링크는 제외 범위)
7. **데이터 격리 3단계 방어**:
   - HTTP 레벨: `authorizeHttpRequests`로 인증 필수 경로 보호
   - 서비스 레벨: `@PreAuthorize("@securityUtils.isOwner(#resourceId, authentication)")`로 소유권 검사
   - 리포지토리 레벨: `findByIdAndAccountId(id, accountId)` 시그니처 강제, `findById` 단독 제공 금지
8. **민감정보 보호**: 비밀번호/토큰/계좌번호 로그 출력 금지, 마스킹 처리
9. **비밀번호 변경 (선택 포함)**: 현재 비밀번호 확인 후 새 비밀번호로 업데이트
10. **Remember Me (선택 포함)**: TokenBased(해시) 방식, 14일 유효, 토큰 회전

---

## 6. 제외 범위 (이유 포함)

| 제외 항목 | 이유 |
|---|---|
| **소셜 로그인 (OAuth2/OIDC: Google, Kakao, Naver 등)** | MVP 범위 초과. 외부 제공자 의존성, 토큰 검증/사용자 매핑/계정 연결 로직 복잡도 증가. 향후 별도 Epic으로 검토. |
| **이메일 인증(회원가입 시 인증 메일 발송)** | 메일 발송 인프라(SMTP, 템플릿, 발송 이력, 반송 처리) 필요. MVP에서 인프라 비용·운영 부담 배제. "이메일=로그인 ID"로 중복만 방지. |
| **비밀번호 재설정(이메일 링크 방식)** | 이메일 발송 인프라 필요 + 토큰 생성/검증/만료/일회용 관리 복잡도. 관리자 수동 해제로 대체 가능. |
| **2단계 인증(2FA/MFA: TOTP, SMS, 이메일 코드)** | MVP 보안 요구사충족(세션+잠금+데이터 격리)로 충분. TOTP 공유 비밀키 저장/백업 코드/복구 흐름 별도 구현 필요. 2차 Epic 검토. |
| **관리자 권한 체계(RBAC: ROLE_ADMIN, ROLE_USER 등)** | MVP는 단일 계정 타입(개인 사용자)만 존재. 관리자 기능(계정 잠금 해제, 사용자 조회)은 별도 관리자 콘솔 Epic에서. |
| **계정 삭제/탈퇴** | 금융 기록 보존 정책(work-plan 23, 52장)과 충돌. 논리 삭제/익명화/보존 범위 결정 후 별도 Epic. |
| **프로필 이미지, 닉네임, 추가 개인정보** | MVP 핵심 가치(투자 기록/수익 계산)와 무관. 최소 수집 원칙 준수. |
| **다국어/로케일 지원** | 한국어 단일 언어 대상. |
| **API 키/토큰 기반 인증(외부 연동용)** | MVP는 브라우저 웹 앱만 대상. 외부 API 제공 시 별도 Epic. |
| **단일 로그아웃(SLO), 세션 공유(SSO), Spring Session + Redis** | 단일 서버 MVP에서 불필요. 수평 확장 시 별도 인프라 Epic. |

---

## 7. 결정 필요 사항 (사람 확정 필요)

| 번호 | 항목 | 옵션/고려사항 | 비고 |
|---|---|---|---|
| D-001 | **비밀번호 정책 강도** | A) 최소 8자, 영문+숫자+특수문자 2종류 이상<br>B) 최소 10자, 3종류 이상 + 공통 비밀번호 사전 체크<br>C) NIST 800-63B 권장(최소 8자, 복잡도 강제 없음, 길이 우선) | 보안 vs 사용성 트레이드오프. 금융 서비스이므로 A 또는 B 권장. |
| D-002 | **비밀번호 변경 주기/이력 관리** | A) 변경 주기 없음(권장: NIST 가이드)<br>B) 90일마다 변경 강제<br>C) 최근 5개 비밀번호 재사용 금지 | NIST 최신 가이드는 주기적 변경 비권장. 이력 관리는 구현 복잡도 낮음. |
| D-003 | **다중 기기 동시 로그인 허용 여부** | A) `maximumSessions(1)` 강제 — 두 번째 로그인 시 기존 세션 만료<br>B) `maximumSessions(3)` 허용 — 동시 3개까지<br>C) 사용자 설정으로 선택 | 가족 공유 계정 고려 시 B/C. 보안 우선 시 A. research 문서에서 A 권장. |
| D-004 | **Remember Me 포함 여부** | A) MVP 포함(편의성)<br>B) MVP 제외(단순화), 2차 추가 | 토큰 저장/회전/탈취 대응 구현 필요. research에서 선택적 포함 제시. |
| D-005 | **계정 잠금 해제 방식** | A) 관리자 수동 해제(최소 구현)<br>B) 일정 시간(예: 30분) 후 자동 해제<br>C) 이메일 인증 링크로 본인 해제(이메일 인프라 필요) | research에서 A 권장(MVP), C는 이메일 인증 Epic 이후. |
| D-006 | **세션 타임아웃 값** | A) 30분 고정(기본)<br>B) 1시간<br>C) 사용자 설정 가능(연장 옵션) | 금융 서비스 보안 기준 고려. 연구 문서 30분 제시. |
| D-007 | **비밀번호 변경 후 기존 세션 처리** | A) 유지(편의)<br>B) 현재 세션만 유지, 타 기기 세션 만료<br>C) 전체 세션 무효화(재로그인 강제) | 보안 권장: C. 사용성: A. 결정 필요. |
| D-008 | **로그인 실패 카운터 저장소** | A) 메모리/세션(서버 재시작 시 리셋)<br>B) DB(영구, 재시작 후 유지)<br>C) Redis(분산 환경 대비) | MVP 단일 서버: A 또는 B. research에서 DB 영구 저장 권장. |

---

## 8. 참고: research/architecture 단계 연계 사항

- **Architecture 단계에서 확정할 것** (이 문서에서 다루지 않음):
  - 패키지 구조(`security`, `auth`, `account` 분리)
  - `SecurityFilterChain` 단일 vs 다중(`securityMatcher("/api/**")` 분리)
  - `CustomUserDetails`, `SecurityUtils`, `AccountAwareRepository` 위치
  - 예외 처리: `AuthenticationEntryPoint`, `AccessDeniedHandler` 커스텀, 에러 응답 포맷 통일
  - 감사 이벤트: `ApplicationEventPublisher` 기반 로그인 성공/실패, 잠금, 권한 변경 발행 설계
  - 테스트 패턴: `@WithMockUser`, `@WithAnonymousUser`, `MockMvc` 통합 테스트

- **Spec 단계에서 확정된 값은 Architecture/구현 단계에서 그대로 사용**하며, 변경 시 Spec 문서 갱신 후 승인 필요.

---

*작성일: 2025-09-20*  
*작성자: Planning Worker (A그룹)*  
*관련 Epic: account-authentication (Epic 1 Phase 0)*  
*기반 조사: docs/research/account-authentication.md*  
*참조 계획: docs/work-plan.md (7~8장, 28~29장, 33장, 37장), docs/roadmap.md (Epic 1 상세)*
