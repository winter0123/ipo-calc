# 아키텍처 설계: 계정 인증 및 인가 (Epic 1 Phase 0)

## 아키텍처 개요 (패키지 구조)

Spring Boot 백엔드는 관심사 분리 원칙에 따라 다음과 같은 레이어 구조를 따 structure로 구성한다:

```
controller
application/service
domain
repository
infrastructure
```

- **controller**: HTTP 요청 처리, 입력 검증, 응답 매핑. 복잡한 업무 규칙은 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계.
- **domain**: 엔티티, 밸류오브젝트, 도메인 이벤트, 불변식.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의.
- **infrastructure**: 외부 시스템 연접, 설정, 공통 인프라 컴포넌트.

특히 인증 및 인가 관련 컴포넌트는 다음과 같이 구성한다:
- `config.security`: SecurityFilterChain, PasswordEncoder 설정
- `service.auth`: 인증/인가 비즈니스 로직 (로그인, 로그아웃, 비밀번호 변경 등)
- `domain.model.account`: Account, CustomUserDetails 엔티티
- `repository.account`: AccountRepository, CustomUserDetailsRepository
- `util.security`: SecurityUtils, 인증 관련 헬퍼 클래스

## 데이터 흐름 (회원가입/로그인/로그아웃 각각을 시퀀스 형태로 간단히)

### 회원가입 흐름
1. 사용자가 회원가입 폼에 이메일, 비밀번호, 이름 입력 후 제출
2. Controller가 입력값 검증 (이메일 형식, 비밀번호 정책)
3. Service에서 이메일 중복 확인 (AccountRepository.existsByEmail)
4. 중복이 아니면 비밀번호를 DelegatingPasswordEncoder(BCrypt)로 해시
5. Account 엔티티 생성 및 저장 (AccountRepository.save)
6. 저장된 Account로 CustomUserDetails 생성 후 세션에 저장 (자동 로그인)
7. 대시보드로 리다이렉트 (/dashboard)

### 로그인 흐름
1. 사용자가 로그인 폼에 이메일, 비밀번호 입력 후 제출
2. Controller가 입력값 검증
3. Service에서 AccountRepository.findByEmail로 계정 조회
4. 계정이 존재하면 PasswordEncoder.matches로 비밀번호 검증
5. 검증 성공 시 세션 생성 및 CustomUserDetails 저장
6. 실패 시 실패 횟수 증가 (Account 인증실패횟수 필드 업데이트)
7. 5회 실패 시 계정 잠금 (Account.isAccountLocked = true)
8. 성공 시 대시보드로, 실패 시 로그인 페이지로 리다이렉트

### 로그아웃 흐름
1. 사용자가 로그아웃 요청 (/logout 엔드포인트 호출)
2. SecurityContextLogoutHandler가 세션 무효화
3. JSESSIONID 쿠키 삭제 (Max-Age=0)
4. SecurityContext.clear()로 인증 정보 제거
5. 로그인 페이지로 리다이렉트 (/login?logout)

## API 설계 (엔드포인트/요청/응답/에러)

### 엔드포인트 목록
1. POST /accounts - 회원가입
2. POST /auth/login - 로그인
3. POST /auth/logout - 로그아웃
4. POST /auth/password-change - 비밀번호 변경 (선택 사항)
5. GET /auth/session-status - 세션 상태 확인 (선택 사항)

### 요청/응답 DTO

#### 회원가입 요청 (POST /accounts)
```json
{
  "email": "user@example.com",
  "password": "securePassword123!",
  "name": "김철수"
}
```

#### 회원가입 응답 (성공)
```json
{
  "accountId": 1,
  "email": "user@example.com",
  "name": "김철수"
}
```

#### 로그인 요청 (POST /auth/login)
```json
{
  "email": "user@example.com",
  "password": "securePassword123!"
}
```

#### 로그인 응답 (성공)
- HTTP 302 Found (리다이렉트) + Set-Cookie: JSESSIONID=...
- Location: /dashboard

#### 로그아웃 응답 (성공)
- HTTP 302 Found (리다이렉트) + Set-Cookie: JSESSIONID=deleted; Max-Age=0
- Location: /login?logout

#### 비밀번호 변경 요청 (POST /auth/password-change)
```json
{
  "currentPassword": "oldPassword123!",
  "newPassword": "newSecurePassword456!",
  "newPasswordConfirm": "newSecurePassword456!"
}
```

#### 비밀번호 변경 응답 (성공)
```json
{
  "message": "비밀번호가 변경되었습니다"
}
```

### 에러 응답 형식 (공통)
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

#### 주요 에러 코드
- VALIDATION_FAILED: 입력값 검증 실패
- DUPLICATE_EMAIL: 중복된 이메일
- INVALID_CREDENTIALS: 잘못된 이메일 또는 비밀번호
- ACCOUNT_LOCKED: 계정 잠금 상태
- ACCESS_DENIED: 권한 없음 (타 계정 데이터 접근 시도)
- SESSION_EXPIRED: 세션 만료 (API 호출 시)

## DB 설계 (테이블/컬럼/제약)

### Account 테이블
| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, Auto Increment | 계정 고유 ID |
| email | VARCHAR(254) | NOT NULL, UNIQUE | 로그인용 이메일 (소문자 정규화) |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt로 해시된 비밀번호 |
| name | VARCHAR(50) | NULLABLE | 사용자 이름 (선택 사항) |
| failed_login_attempts | INTEGER | NOT NULL, DEFAULT 0 | 연속 로그인 실패 횟수 |
| locked_until | TIMESTAMP | NULLABLE | 계정 잠금 해제 일시 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 계정 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 마지막 수정 일시 |
| last_login_at | TIMESTAMP | NULLABLE | 마지막 성공 로그인 일시 |

### 인덱스
- PRIMARY KEY (id)
- UNIQUE INDEX uk_account_email (email)
- INDEX idx_account_locked_until (locked_until) - 잠금 계정 조회 최적화

### Audit 필드
- created_at, updated_at: 생성 및 수정 시간 추적
- last_login_at: 마지막 로그인 시간 추적 (보안 모니터링용)

## 모듈 소유권

- **Account 엔티티**: domain.model.account 패키지 소유
- **AccountRepository**: repository.account 패키지 소유
- **SecurityFilterChain 설정**: config.security 패키지 소유
- **인증 서비스 (로그인/로그아웃/비밀번호 변경)**: service.auth 패키지 소유
- **사용자 세부정보 서비스**: service.auth 패키지 내 CustomUserDetailsService 구현
- **보안 유틸리티 (SecurityUtils)**: util.security 패키지 소유

## 의존성

새로 추가해야 할 라이브러리:
- `org.springframework.boot:spring-boot-starter-security` (Spring Security 핵심)
- 이미 build.gradle.kts에 존재할 가능성이 높으나, 없는 경우 추가 필요

## 리스크

1. **세션 저장소 확장성 문제**: 현재는 기본 HttpSession 사용 (서버 메모리). 향후 수평 확장 시 Spring Session + Redis 도입 필요.
2. **비밀번호 정책 변경 관리**: 비밀번호 정책이 변경될 경우 기존 해시된 비밀번호와의 호환성 문제.
3. **계정 잠금 서비스 거부 공격(DoS) 가능성**: 악의적인 사용자가 특정 계정을 지속적으로 실패시켜 잠금시킬 수 있음. IP 기반 실패 차단 필요 고려.
4. **Remember Me 토큰 관리 복잡도**: 토큰 도난 시 보안 위험이 있으며, 토큰 저장소 관리 및 회전 로직 추가 복잡도.
5. **세션 고정 공격 대비**: Spring Security의 기본 보호 기능이 있으나, 커스텀 로그인 로직에서 세션 관리를 잘못할 경우 위험.

## 결정 필요 사항

1. **비밀번호 정책 강도** (D-001): 최소 8자, 영문+숫자+특수문자 2종류 이상 적용할지 여부
2. **다중 기기 동시 로그인 허용 여부** (D-003): maximumSessions(1) 강제할지, 또는 여러 세션 허용할지
3. **Remember Me 포함 여부** (D-004): MVP에 포함할지, 포함한다면 토큰 유효기간 설정
4. **계정 잠금 해제 방식** (D-005): 관리자 수동 해제만 구현할지, 또는 자동 해제 옵션 검토
5. **세션 타임아웃 값** (D-006): 30분 고정할지, 또는 다른 값 적용할지
6. **비밀번호 변경 후 기존 세션 처리** (D-007): 변경 후 기존 세션 유지, 현재 세션만 유지, 또는 전체 세션 무효화 중 선택
7. **로그인 실패 카운터 저장소** (D-008): 메모리/세션, DB, 또는 Redis 중 선택