# 조사 문서: 계정 인증 및 인가 (Epic 1 Phase 0)

## 조사 목적

로그인 계정과 인증·인가(`account-authentication`) Epic의 안전한 구현을 위해 Spring Boot + Spring Security 기반에서 비밀번호 저장, 인증 방식(세션 vs JWT), 표준 설정 구조, 실패/잠금/토큰 정책, 타 사용자 데이터 접근 차단 패턴을 조사하고 spec/architecture 단계에서 결정해야 할 사항을 정리한다.

## 조사 대상

- Spring Security 7.1.1 (Spring Boot 3.x 호환) 공식 문서
- Spring Boot 3.x / Java 21 환경 기준

## 출처 (URL, 확인 날짜)

| 주제 | URL | 확인 날짜 |
|---|---|---|
| Spring Security Password Storage (PasswordEncoder, DelegatingPasswordEncoder) | https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/storage.html | 2025-09-20 |
| Spring Security PasswordEncoder 상세 | https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/password-encoder.html | 2025-09-20 |
| Spring Security DaoAuthenticationProvider | https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html | 2025-09-20 |
| Spring Security Session Management | https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html | 2025-09-20 |
| Spring Security Form Login | https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html | 2025-09-20 |
| Spring Security Remember Me | https://docs.spring.io/spring-security/reference/servlet/authentication/rememberme.html | 2025-09-20 |
| Spring Security Java Configuration (SecurityFilterChain) | https://docs.spring.io/spring-security/reference/servlet/configuration/java.html | 2025-09-20 |
| Spring Security Authorization (authorizeHttpRequests, Method Security) | https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html | 2025-09-20 |
| Spring Security Method Security (@PreAuthorize 등) | https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html | 2025-09-20 |
| Spring Security OAuth2 Resource Server JWT | https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | 2025-09-20 |

---

## 확인한 내용

### 질문 1. Spring Boot 프로젝트에서 비밀번호를 안전하게 저장하는 표준 방법

**공식 권장 방식: `PasswordEncoder` 인터페이스 + `DelegatingPasswordEncoder`**

- Spring Security는 비밀번호 저장을 위해 `PasswordEncoder` 인터페이스를 제공한다(공식 문서: "Password Storage" 섹션).
- **프로덕션에서 반드시 사용해야 할 구현체**: `BCryptPasswordEncoder` (기본 권장), `Pbkdf2PasswordEncoder`, `Argon2PasswordEncoder`, `SCryptPasswordEncoder`.
- **절대 사용 금지**: `NoOpPasswordEncoder`(평문), `StandardPasswordEncoder`(SHA-256 단일 해시 — 현재는 안전하지 않음), `MessageDigestPasswordEncoder`(단순 해시).

**`DelegatingPasswordEncoder` (기본 권장)**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```
- 저장 형식: `{bcrypt}$2a$10$...` 처럼 **알고리즘 접두사({id})를 포함**해 저장.
- 여러 알고리즘 공존 가능 → 향후 마이그레이션 용이.
- `User.withDefaultPasswordEncoder()`는 **샘플용으로만 제공되며 프로덕션 사용 금지**라고 문서에 명시됨.

**DaoAuthenticationProvider 구조**
- `UserDetailsService`로 사용자 조회 → `PasswordEncoder.matches(rawPassword, encodedPassword)`로 검증.
- 커스텀 `UserDetailsService` 구현 시 `UserDetails`에 해시된 비밀번호 반환.

**권장 설정 패턴 (Spring Boot 3.x + Java 21)**
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .httpBasic(Customizer.withDefaults());
    return http.build();
}

@Bean
public UserDetailsService userDetailsService(PasswordEncoder encoder) {
    // JPA 리포지토리 기반 구현 권장
    return new CustomUserDetailsService(userRepository, encoder);
}

@Bean
public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

---

### 질문 2. 세션 기반 인증 vs JWT(Stateless) 인증 — 이 프로젝트 규모에 적합한 방식 비교

**프로젝트 특성 재확인 (work-plan 7~8장, roadmap Epic 1)**
- MVP: 단일 로그인 계정이 여러 투자자(본인, 가족 등)를 관리.
- 투자자·증권계좌·청약·배정·매도·수익 등 **금융 데이터 소유권이 계정 단위로 격리**되어야 함.
- "다른 로그인 사용자의 데이터에 접근하지 못하도록 막는 것"이 최우선 보안 요구사항(work-plan 28~29장).
- 초기 규모: 단일 서버, 소규모 사용자, 웹 브라우저 중심.

---

#### 세션 기반 인증 (Server-Side Session, Spring Security 기본)

| 장점 | 단점 |
|---|---|
| **Spring Security 기본 동작** — 별도 설정 최소, `SecurityContextRepository`가 `HttpSession` 사용(공식 문서: "Session Management" 기본값) | 수평 확장 시 세션 공유 필요 (Spring Session + Redis 등 추가 인프라) |
| **CSRF 보호 내장** — Form Login 시 `CsrfFilter` 자동 적용, 동기화 토큰 패턴 기본 제공 | 모바일 앱/SPA 연동 시 쿠키/CORS 설정 추가 고려 필요 |
| **세션 고정 공격 방어** — `changeSessionId()` / `newSession()` / `migrateSession()` 기본 제공 (공식 문서: "Session Fixation Protection") | 상태 저장소(세션) 서버 메모리/DB 점유 |
| **동시 세션 제어** — `maximumSessions(1)` 등으로 중복 로그인 차단 간단 구현 (공식 문서: "Concurrent Session Control") | Stateless API 설계와 맞지 않음 |
| **Remember Me** — `TokenBasedRememberMeServices`(해시 기반) 또는 `PersistentTokenBasedRememberMeServices`(DB 저장) 기본 제공 | |
| **세션 타임아웃/무효 세션 감지** — `invalidSessionUrl`, `InvalidSessionStrategy` 기본 제공 | |

**적합도 평가**: ✅ **MVP 웹 중심, 단일 서버, 보안 기본기 충실 → 가장 적합**

---

#### JWT (Stateless, OAuth2 Resource Server 패턴)

| 장점 | 단점 |
|---|---|
| 서버 측 세션 저장 불필요 → 수평 확장 유리 | **토큰 무효화(로그아웃, 권한 변경) 어렵다** — 만료 전까지 유효, 블랙리스트/Redis 별도 구현 필요 |
| 모바일/SPA/마이크로서비스 간 토큰 전달 용이 | **Refresh Token 관리 복잡** — 회전(rotation), 재사용 탐지, 저장소 필요 |
| CSRF 걱정 없음 (쿠키 미사용 시) | **토큰 탈취 시 만료 전까지 완전 접근 가능** — 단기 만료(예: 15분) + Refresh Token 필수 |
| 인증 상태가 클라이언트 측에 있음 | **구현 복잡도 증가** — `JwtDecoder`, `JwtAuthenticationConverter`, 공개키 순환(JWKS) 처리 필요 |
| | **Spring Security 기본 Form Login/Session 기능과 병행 시 설정 꼬임** — `SessionCreationPolicy.STATELESS` 설정 시 Form Login, Remember Me, CSRF 등 기본 기능 비활성화됨 |

**공식 문서 확인 사항 (OAuth2 Resource Server JWT)**
- `spring-security-oauth2-resource-server` + `spring-security-oauth2-jose` 의존성 필요.
- `issuer-uri` 또는 `jwk-set-uri`로 공개키 자동 발견.
- `JwtAuthenticationConverter`로 권한 매핑(`SCOPE_` 접두사 기본).
- **주의**: "Resource Server startup is not coupled to the authorization server's availability" — 첫 요청 시 지연 초기화.

**적합도 평가**: ❌ **MVP 단계에서 불필요한 복잡도 증가, 세션 무효화/갱신 구현 부담, 금융 데이터 보호 우선순위와 상충**

---

#### 권장 방향 (조사 결론)

> **세션 기반 인증(Server-Side Session) + Spring Security 기본 스택**을 1차 선택.
>
> - 이유: 구현 단순성, 보안 기능(CSRF, 세션 고정 방어, 동시 세션 제어, Remember Me) 완비, 금융 데이터 격리(인가 체크)와 직교, 향후 필요 시 Spring Session으로 확장 가능.
> - JWT는 "모바일 앱 출시", "마이크로서비스 분리", "타사 API 제공" 등 Stateless가 필수가 될 때 2단계로 도입 검토.

---

### 질문 3. Spring Security 표준 설정 구조 (SecurityFilterChain 등, 최신 버전 기준)

**Spring Security 6+ / Spring Boot 3.x 기준: `SecurityFilterChain` Bean 기반 Java Config**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. 인가 규칙 (최신 DSL: authorizeHttpRequests)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/signup", "/css/**", "/js/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            // 2. 폼 로그인 (커스텀 로그인 페이지 권장)
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            // 3. 로그아웃
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            // 4. 세션 관리
            .sessionManagement(session -> session
                .sessionFixation().changeSessionId() // Servlet 3.1+ 기본값
                .maximumSessions(1) // 동시 세션 1개 제한
                .maxSessionsPreventsLogin(false) // 두 번째 로그인 시 기존 세션 만료
                .invalidSessionUrl("/login?expired")
            )
            // 5. CSRF (폼 로그인 시 기본 활성화, API 분리 시 별도 설정)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/public/**") // 필요 시 예외
            )
            // 6. 보안 헤더 (기본 활성화: HSTS, X-Content-Type-Options, Cache-Control, X-Frame-Options 등)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
            )
            // 7. Remember Me (선택)
            .rememberMe(remember -> remember
                .key("unique-app-key")
                .tokenValiditySeconds(1209600) // 14일
                .userDetailsService(userDetailsService)
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher(); // 동시 세션 제어용 필수
    }
}
```

**핵심 변경점 (Spring Security 5 → 6/7)**
- `WebSecurityConfigurerAdapter` **제거됨** → `SecurityFilterChain` Bean 방식만 사용.
- `authorizeRequests()` → `authorizeHttpRequests()` (새 DSL, `AuthorizationManager` 기반).
- `SessionManagementFilter` **기본 비활성화** → 인증 메커니즘(Form Login 등)이 직접 `SessionAuthenticationStrategy` 호출.
- `SecurityContextPersistenceFilter` → `SecurityContextHolderFilter` (기본값 `requireExplicitSave=true`: 세션 저장 명시적 호출 필요).
- 다중 `SecurityFilterChain` 지원: `@Order`로 우선순위 제어, `securityMatcher("/api/**")`로 경로 분리.

**Method Security (Service 레이어 인가)**
```java
@Configuration
@EnableMethodSecurity // @PreAuthorize, @PostAuthorize, @PreFilter, @PostFilter 활성화
public class MethodSecurityConfig {
    // 필요 시 커스텀 AuthorizationManagerFactory 빈 등록
}
```
- `@PreAuthorize("hasAuthority('permission:read')")` 등 SpEL 표현식으로 도메인 객체 레벨 권한 검사.
- **Insecure Direct Object Reference(IDOR) 방지**: `@PostAuthorize("returnObject.accountId == authentication.name")` 패턴 권장.

---

### 질문 4. 로그인 실패/잠금 정책, 토큰 만료·재발급(Refresh Token) 전략

#### 로그인 실패/계정 잠금 (Spring Security 기본 기능 + 커스텀 확장)

| 기능 | 구현 방식 | 공식 문서 참고 |
|---|---|---|
| **실패 횟수 카운트** | `AuthenticationFailureHandler` 구현체에서 세션/DB에 카운터 증가 | Form Login 문서: `AuthenticationFailureHandler` 후킹 |
| **계정 잠금(Lockout)** | `UserDetails.isAccountNonLocked()` 반환 값으로 제어. `DaoAuthenticationProvider`가 자동 확인 | `UserDetails` 인터페이스: `isAccountNonLocked()` |
| **잠금 해제** | 관리자 수동 해제 또는 일정 시간 후 자동 해제(스케줄러) | 별도 구현 필요 |
| **브루트포스 방지** | IP 단위 실패 횟수 제한(필터/인터셉터), Captcha 연동 | Spring Security 기본 제공 아님, 별도 구현 |

**권장 MVP 정책**
- 로그인 실패 5회 → 계정 잠금(`isAccountNonLocked=false`)
- 잠금 해제: 관리자 승인 또는 사용자 이메일 인증 링크(향후)
- 실패 로그: IP, 시각, 사용자명 기록 (감사 로그용)

#### 세션/쿠키 만료 설정

| 설정 | 권장값 | 비고 |
|---|---|---|
| `server.servlet.session.timeout` | 30분 (1800초) | 비활성 타임아웃 |
| `spring.session.timeout` (Spring Session 사용 시) | 동일 | |
| Remember Me 토큰 유효기간 | 14일 (1209600초) | `rememberMe.tokenValiditySeconds` |
| 세션 쿠키 설정 | `Secure=true`, `HttpOnly=true`, `SameSite=Lax/Strict` | Spring Boot `server.servlet.session.cookie.*` 프로퍼티 |

#### Refresh Token 전략 (JWT 채택 시에만 해당 — 현재 세션 방식이므로 참고만)

> **현재 권장(세션 방식)에서는 Refresh Token 불필요** — 세션 갱신은 서버 측에서 자동 처리.

JWT 도입 시 참고할 표준 패턴 (OAuth2 명세 + Spring Security 구현):
- Access Token: 단기(15~30분), `JwtDecoder`로 검증.
- Refresh Token: 장기(7~14일), **DB 저장(해시) + 회전(Rotation) + 재사용 탐지(Reuse Detection)**.
- `PersistentTokenBasedRememberMeServices` 패턴을 Refresh Token 저장소로 재활용 가능.
- 로그아웃 시 Refresh Token 폐기(DB에서 삭제/무효화).

---

### 질문 5. 다른 로그인 사용자의 데이터에 접근하지 못하도록 막는 일반적 패턴

**핵심 원칙: "인가(Authorization)는 인증(Authentication) 후에, 모든 진입점에서"**

#### 1단계: HTTP 요청 레벨 (Web Layer) — `authorizeHttpRequests`

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/investors/**").authenticated() // 인증 필수
    .anyRequest().authenticated()
)
```
- 인증되지 않은 요청 원천 차단.
- 하지만 **"본인 계정의 투자자만 조회"** 같은 **데이터 소유권 검사는 여기서 불가** — 경로만으로는 계정 ID 모름.

#### 2단계: 메서드/서비스 레벨 (Domain Layer) — `@PreAuthorize` + SpEL

```java
@Service
@RequiredArgsConstructor
public class InvestorService {

    @PreAuthorize("@securityUtils.isOwner(#investorId, authentication)")
    public Investor getInvestor(Long investorId) {
        return investorRepository.findById(investorId)
            .orElseThrow(() -> new EntityNotFoundException("Investor not found"));
    }

    @PreAuthorize("@securityUtils.isOwner(#investorId, authentication)")
    public void deleteInvestor(Long investorId) { ... }
}
```

```java
@Component
public class SecurityUtils {
    public boolean isOwner(Long resourceOwnerId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        Long currentAccountId = ((CustomUserDetails) auth.getPrincipal()).getAccountId();
        // resourceOwnerId가 속한 계정 ID 조회 후 비교
        return accountRepository.findAccountIdByInvestorId(resourceOwnerId)
            .map(id -> id.equals(currentAccountId))
            .orElse(false);
    }
}
```

#### 3단계: 리포지토리/쿼리 레벨 — **계정 ID 필수 조건절 강제**

```java
public interface InvestorRepository extends JpaRepository<Investor, Long> {
    // 계정 ID 없이는 조회 불가하게 메서드 시그니처 강제
    Optional<Investor> findByIdAndAccountId(Long id, Long accountId);
    List<Investor> findByAccountId(Long accountId);
    // findById(Long) 단독 메서드는 제공하지 않거나 private 처리
}
```

```java
@Service
@RequiredArgsConstructor
public class InvestorService {
    private final InvestorRepository investorRepository;

    public Investor getMyInvestor(Long investorId, Long currentAccountId) {
        return investorRepository.findByIdAndAccountId(investorId, currentAccountId)
            .orElseThrow(() -> new AccessDeniedException("Not your investor"));
    }
}
```

#### 4단계: 컨트롤러 레벨 — 현재 계정 ID 주입

```java
@RestController
@RequiredArgsConstructor
public class InvestorController {
    private final InvestorService investorService;

    @GetMapping("/api/investors/{id}")
    public InvestorDto getInvestor(@PathVariable Long id, Authentication auth) {
        Long accountId = ((CustomUserDetails) auth.getPrincipal()).getAccountId();
        return investorService.getMyInvestor(id, accountId);
    }
}
```

#### 5단계: 공통 기반 클래스/어노테이션으로 강제 (아키텍처 단계에서 결정)

- `AccountAwareRepository<T, ID>` 인터페이스: `findByIdAndAccountId` 강제.
- `@CurrentAccountId` 어노테이션으로 컨트롤러 파라미터 자동 바인딩.
- Spring Data JPA `@Query`에 `accountId = :#{principal.accountId}` SpEL 바인딩 활용 가능.

---

## 제약사항

1. **Java 21, Spring Boot 3.x, Spring Security 7.1.x** 고정 (프로젝트 기술 스택 확정).
2. **무료/오픈소스만 사용** — 상용 보안 솔루션 도입 불가 (AGENTS.md 4장).
3. **MVP 범위**: 웹 브라우저(React) 단일 클라이언트, 단일 백엔드 서버.
4. **개인정보 최소 수집**: 이메일(로그인 ID), 비밀번호 해시, 이름(선택)만 저장. 계좌번호 마스킹 저장 필수 (work-plan 28장).
5. **로그 민감정보 금지**: 비밀번호, 토큰, 계좌번호 전체 출력 금지 (work-plan 29장, 46장).
6. **세션 쿠키 보안 설정 필수**: `Secure`, `HttpOnly`, `SameSite` (HTTPS 환경 전제).

---

## 불확실한 점

| 항목 | 불확실 내용 | 결정 필요 시점 |
|---|---|---|
| **계정 잠금 해제 방식** | 관리자 수동 vs 자동(시간 경과) vs 이메일 인증 링크 | Spec 단계 |
| **비밀번호 정책** | 최소 길이, 특수문자 강제, 만료 주기, 이력 관리(재사용 금지) | Spec 단계 |
| **다중 기기 동시 로그인 허용 여부** | `maximumSessions(1)` 강제 vs 허용(몇 개까지) | Spec 단계 |
| **Remember Me 사용 여부** | MVP에 포함할지, 포함한다면 Token 기반 vs Persistent Token | Spec 단계 |
| **세션 타임아웃 값** | 30분 고정 vs 사용자 설정 가능 vs 연장 옵션 | Spec 단계 |
| **HTTPS 강제 리다이렉트** | 로컬 개발 환경에서도 강제할지 (HSTS preload 등) | Architecture 단계 |
| **계정 삭제 시 데이터 처리** | 계정 삭제 → 투자자/투자기록 함께 삭제 vs 논리 삭제/익명화 vs 보존 | Spec/Architecture 단계 |
| **2FA/MFA 도입 여부** | MVP 제외 후 2차 검토 vs 초기부터 선택적 적용 | Roadmap 재검토 시 |

---

## 개발에 미치는 영향 (spec/architecture 단계에서 결정해야 할 것)

### Spec 단계에서 확정해야 할 사항 (Acceptance Criteria 직결)
1. **인증 플로우 상세**: 로그인/로그아웃/세션 만료/Remember Me 플로우, 에러 화면/메시지.
2. **비밀번호 정책**: 복잡도 규칙, 변경 주기, 찾기/재설정 플로우(이메일 인증).
3. **계정 잠금 정책**: 실패 임계값, 잠금 지속 시간, 해제 프로세스.
4. **데이터 격리 규칙**: "계정 ID 필수 조건절"을 모든 리포지토리 메서드에 강제할지, 예외 허용할지.
5. **세션/쿠키 설정값**: 타임아웃, SameSite, Secure, 도메인 범위.

### Architecture 단계에서 확정해야 할 사항
1. **패키지/모듈 구조**: `security`, `auth`, `account` 패키지 분리, `CustomUserDetails`, `SecurityUtils`, `AccountAwareRepository` 위치.
2. **SecurityFilterChain 구성**: 단일 체인 vs API/웹 분리(`securityMatcher`), CSRF 예외 경로.
3. **UserDetailsService 구현체**: JPA 기반 vs JDBC 기반, 캐시 적용 여부(`UserDetailsCache`).
4. **예외 처리 구조**: `AuthenticationEntryPoint`, `AccessDeniedHandler` 커스텀 구현, 에러 응답 포맷 통일.
5. **감사 로그/보안 이벤트**: 로그인 성공/실패, 잠금, 권한 변경 등 `ApplicationEventPublisher` 기반 이벤트 발행 설계.
6. **테스트 전략**: `@WithMockUser`, `@WithAnonymousUser`, `MockMvc` 기반 인증/인가 통합 테스트 패턴 정의.

---

## 결론

1. **비밀번호 저장**: `DelegatingPasswordEncoder` + `BCrypt`(기본) 필수. 평문/단일 해시 절대 금지.
2. **인증 방식**: **세션 기반(Server-Side Session)**을 1차 채택. JWT는 향후 모바일/마이크로서비스 전환 시 2단계 검토.
3. **표준 설정**: `SecurityFilterChain` Bean + `authorizeHttpRequests` + `formLogin` + `sessionManagement` + `rememberMe` 조합. Method Security(`@EnableMethodSecurity`)로 서비스 레이어 인가 병행.
4. **실패/잠금**: `AuthenticationFailureHandler` + `UserDetails.isAccountNonLocked()` 기반 구현. MVP는 5회 실패 시 잠금, 관리자 해제.
5. **데이터 격리(가장 중요)**: **3단계 방어** — (1) HTTP 레벨 인증 필수, (2) `@PreAuthorize` + `SecurityUtils`로 소유권 검사, (3) 리포지토리 메서드 시그니처에 `accountId` 필수화. 컨트롤러에서 현재 계정 ID 주입하여 서비스/리포지토리로 전달.

> **권장 요약**: 세션 기반 인증 + Spring Security 기본 보안 스택(CSRF, 세션 고정 방어, 동시 세션 제어, Remember Me) 완전 활용 + 서비스/리포지토리 레벨 계정 ID 강제 조건절로 데이터 격리 철저. JWT는 현 단계에서 도입하지 않음.

---

*작성일: 2025-09-20*  
*작성자: Research Worker (A그룹)*  
*관련 Epic: account-authentication (Epic 1 Phase 0)*