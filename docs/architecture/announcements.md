# 공지사항(Announcements) 아키텍처 설계

> Epic 번호: 22, slug: `announcements`
> 기반 문서: `docs/research/announcements.md`, `docs/spec/announcements.md`
> 작성일: 2026-10-23

## 1. 아키텍처 개요

본 기능은 **관리자 CRUD**와 **일반 사용자 조회/팝업**으로 분리되며, 기존 프로젝트 패턴(SecurityConfig, Ipo 등)을 그대로 재사용한다. 핵심은 **활성 노출 기간**(KST 날짜 기준, 시작일~종료일 양쪽 포함) 기반 필터링과 **로그인 사용자 대상 홈 첫 진입 팝업** 노출이다. 공지사항은 **공모주(IPO)와 독립된 기능/데이터**로 별도 도메인으로 구현한다.

### 1.1 레이어 구조 (Spring Boot)

Spring Boot 백엔드는 기존 프로젝트 관심사 분리를 따른다.

```
controller
application/service
domain
repository
infrastructure
```

- **controller**: HTTP 요청 처리, 입력 검증, 인증·인가 확인, 응답 매핑. 복잡한 업무 규칙 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계. IPO 서비스와 유사한 패턴(중복 검사·상태 전이·CSV 파싱 포함).
- **domain**: 엔티티, 밸류오브젝트, 불변식. Announcement 엔티티 및 상태(ACTIVE/CANCELLED) 상수 정의.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의.
- **infrastructure**: 외부 시스템 연동, 설정, 공통 인프라 컴포넌트.

### 1.2 컴포넌트 구성

- `controller.admin.AnnouncementController`: 관리자 전용 API (CRUD)
- `controller.user.AnnouncementController`: 일반 사용자 조회 API
- `service.admin.AnnouncementService`: 관리자 서비스 로직
- `service.user.AnnouncementService`: 사용자 조회 서비스 로직
- `domain.model.Announcement`: Announcement 엔티티
- `repository.AnnouncementRepository`: JPA 리포지토리
- `config.security.SecurityConfig`: 권한 매핑 추가 (새 엔드포인트 추가)

### 1.3 데이터베이스 설계

기존 테이블(`accounts`, `ipos`) 스키마 변경 **금지** — 신규 테이블만 생성. 공지사항은 IPO 테이블과 무관하게 별도 테이블 생성.

#### `announcements` 테이블

| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 공지 고유 식별자 |
| title | VARCHAR(200) | NOT NULL | 공지 제목 |
| content | TEXT | NOT NULL | 공지 내용 (마크다운/HTML) |
| start_at | DATE | NOT NULL | 노출 시작일 (KST 날짜) |
| end_at | DATE | NOT NULL | 노출 종료일 (KST 날짜) |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'ACTIVE' | 상태 (ACTIVE/CANCELLED) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 수정 일시 |
| deleted_at | TIMESTAMP | NULL 허용 | 논리 삭제 일시 |

#### `announcement_dismissals` 테이블 (이메일 계정 기준 서버 DB 저장 확정)

| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| account_id | BIGINT | PK, FK → accounts.id | 로그인 계정 식별자 |
| announcement_id | BIGINT | PK, FK → announcements.id | 공지 식별자 |
| dismissed_at | TIMESTAMP | NOT NULL | "다시 보지 않기" 클릭 시각 |

- **PK 복합**: (`account_id`, `announcement_id`)
- **CASCADE**: 계정 또는 공지 삭제 시 연관 행 자동 삭제
- **인덱스**: `announcement_id` 단일 인덱스 (공지별Dismissal 조회 최적화)

#### 인덱스

- PRIMARY KEY (id)
- INDEX idx_announce_status_active (`status`, `start_at`, `end_at`) — 활성·기간 필터링 최적화
- INDEX idx_announce_start_at (`start_at ASC`) — 정렬용 (시작일 순)
- UNIQUE INDEX uk_announce_title_active (`title`, `status = 'ACTIVE` 부분 유니크) — 제목 중복 방지 (ACTIVE에 한함)

## 2. API 계약 초안

### 2.1 관리자 API (ADMIN 인증 필수)

| 메서드 | 엔드포인트 | 권한 | 요청 본문 | 응답 | 비고 |
|--------|-----------|------|-----------|------|------|
| POST | `/api/admin/announcements` | ADMIN | title, content, start_at, end_at | 201 Created | 필수 필드 검증 |
| GET | `/api/admin/announcements` | ADMIN | — | 200 OK | 페이지네이션, 상태 필터 |
| GET | `/api/admin/announcements/{id}` | ADMIN | — | 200 OK, 404 | 단건 조회 |
| PATCH | `/api/admin/announcements/{id}` | ADMIN | 부분 수정 필드 | 200 OK, 404, 403 | 자기 자신 제외 중복 검사 |
| DELETE | `/api/admin/announcements/{id}` | ADMIN | — | 204 No Content, 404 | 논리 삭제 (`status=CANCELLED`, `deleted_at=now()`) |

### 2.2 사용자 조회 API (로그인 인증 필수)

| 메서드 | 엔드포인트 | 권한 | 요청 본문 | 응답 | 비고 |
|--------|-----------|------|-----------|------|------|
| GET | `/api/announcements` | 로그인 사용자 | — | 200 OK | 활성·기간 내 공지만, 페이지네이션, 정렬(start_at ASC) |
| GET | `/api/announcements/{id}` | 로그인 사용자 | — | 200 OK, 404 | 활성·기간 내 단건 조회 |
| GET | `/api/announcements/popup` | 로그인 사용자 | — | 200 OK (공지 배열), 204 No Content | 홈 첫 진입용 활성 공지 전부 반환 |

### 2.3 "다시 보지 않기" API

| 메서드 | 엔드포인트 | 권한 | 요청 본문 | 응답 | 비고 |
|--------|-----------|------|-----------|------|------|
| POST | `/api/announcements/{id}/dismiss` | 로그인 사용자 | — | 200 OK (멱등) | DB에 dismissal 저장 (account_id 기준) |

### 2.4 에러 응답 형식 (공통)

```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

### 2.5 주요 에러 코드

- VALIDATION_ERROR: 입력값 검증 실패 (필수값 누락, 날짜 순서 오류, 255자 초과 등)
- DUPLICATE_ANNOUNCEMENT: 활성 상태(ACTIVE) 공지 중복 생성 시도 (AC-BE-11)
- NOT_FOUND: 공지를 찾을 수 없음 (미등록 또는 논리 삭제된 레코드)
- PERMISSION_DENIED: 관리자 전용 API에 일반 사용자 요청 → 403, 비로그인 요청 → 401
- EXPIRED_PERIOD: 현재 시각이 start_at/end_at 범위를 벗어난 경우 (AC-BE-08에서 204 반환)

## 3. SecurityConfig 역할 검사

SecurityConfig에 새로운 엔드포인트 매핑을 추가한다. 기존 인증·인가 규칙 유지, 새 엔드포인트만 매핑 추가.

```java
// SecurityConfig.java에 추가 (기존 규칙 유지)

// 관리자 전용 공지 CRUD
.and()
  .requestMatchers("/api/admin/announcements/**").hasRole("ADMIN");

// 사용자 조회 API (인증 필요)
.and()
  .requestMatchers("/api/announcements/**").authenticated();

// 그 외: 로그인 필요
.anyRequest().authenticated();
```

- `/api/admin/announcements/**` → `hasRole("ADMIN")` 관리자 CRUD 전용
- `/api/announcements/**` → `authenticated()` 로그인 사용자 조회
- 그 외 `anyRequest().authenticated()` 로그인 필요 유지

## 4. UI 경로·상태 흐름

### 4.1 라우팅 패턴 (기존 MainLayout·RouteGuards 재사용)

- **Admin Only**: `currentAccount?.role === 'ADMIN'`일 때만 MainLayout의 ADMIN_ITEMS에 "공지사항 관리" 표시
- **Route**: `/admin/announcements` → 관리자 목록 페이지 (기존 admin route 패턴 적용)
- **User Path**: 로그인 후 `/dashboard` 최초 진입 시 팝업 노출 (확정: 로그인 후 대시보드 최초 진입)

### 4.2 플로우 다이어그램

```
로그인 → /dashboard 진입
     ↓
GET /api/announcements/popup (최초 1회만 호출)
     ↓
결과가 있으면 ConfirmDialog 모달 순차 표시 (제목, 내용 요약, "자세히 보기", "닫기", "다시 보지 않기")
     ↓
사용자 액션
      ├─ "닫기" → 모달 닫기, 다음 재접속/새 방문 시 same 공지 재표시 (확정)
      ├─ "다시 보지 않기" → POST /api/announcements/{id}/dismiss, 영구 숨김 (계정 ID 기준 DB 저장)
      └─ "자세히 보기" → /announcements/{id} 상세 페이지 네비게이션, 모달 닫기
```

### 5.3 "닫기" 후 재표시 규칙 (확정: 이번 방문에서만 닫고 다음 재접속/새 방문에 재표시)

- **동작**: 현재 방문(브라우저 세션/탭) 동안만 닫힌 상태를 유지하며, 재접속(재로그인) 또는 새 방문(새 탭/브라우저) 시에는 다시 표시된다.
- **구현 저장소 비특정**: 구체적 저장 기술(localStorage, sessionStorage, 메모리 변수 등)을 명시하지 않으며, **이번 방문 동안만 지속되고 재방문 시 초기화되는 관측 가능한 동작**만 요구한다.
- **DB 기반 ("다시 보지 않기")**: `account_id` + `announcement_id` 복합 키로 DB에 영구 저장 → 재로그인·재방문 시 **절대 표시 안 함** (기기 간 동기화)

### 4.4 여러 활성 공지 노출 순서 (확정: 전부 순차 표시, 시작일 오름차순)

**확정**: 기간이 겹치는 활성 공지는 전부 순차 표시한다. 순서는 시작일 오름차순(가장 이른 것부터). priority/우선순위 입력 필드나 컬럼은 없다.

## 5. 로컬 저장 키/사용자 계정 간 동작

### 5.1 "닫기" 휘발성 상태 (이번 방문 범위 — 확정 동작)

- **동작**: "닫기"는 현재 방문(브라우저 세션/탭) 동안만 적용되며, 재접속(재로그인) 또는 새 방문(새 탭/브라우저) 시 초기화되어 공지가 다시 표시된다.
- **구현 저장소 비특정**: 구체적 저장 기술(localStorage, sessionStorage, 메모리 변수 등)을 명시하지 않으며, **이번 방문 동안만 지속되고 재방문 시 초기화되는 관측 가능한 동작**만 요구한다.
- **DB 기반 ("다시 보지 않기")**: `account_id` + `announcement_id` 복합 키로 DB에 영구 저장 → 재로그인·재방문 시 **절대 표시 안 함** (기기 간 동기화)

### 5.2 "다시 보지 않기" DB 저장 (확정: 계정 ID 기준 서버 저장)

- **위치**: `announcement_dismissals` 테이블 (`account_id` + `announcement_id` 복합 PK)
- **계정 격리**: 같은 공지라도 다른 계정에서는 다시 팝업 표시 가능
- **권위적 동작**: DB dismissal이 계정 단위 권위적 저장소이며, 로컬 저장소 상태와 무관하게 적용된다.
- **삭제**: 관리자가 공지를 CANCELLED(비활성화)해도 dismissal 행은 보존

### 5.3 로그인 전/후 팝업 대상 (확정: 로그인 후만)

- **로그인 후만**: 공지사항은 계정 단위 기능(다시 보지 않기 저장 필요)이므로 비로그인 상태에서는 의미 없음
- **확정**: 로그인 사용자만 대상. 비로그인 홈(`/about`, `/ipo-calendar`) 진입 시 팝업 **표시 안 함**

## 6. 팝업 노출 선정 규칙

### 6.1 조회 로직 (AC-BE-08)

1. `announcements` 테이블에서 `status = 'ACTIVE'` AND `deleted_at IS NULL` 필터
2. 현재 날짜(KST)가 `start_at` 이상 AND `end_at` 이하인 것들 필터 (기간 내 확인 — 양쪽 날짜 포함)
3. `announcement_dismissals` 테이블에서 현재 로그인 `account_id` + 각 `announcement_id`에 대한 dismissal이 있는지 제외
4. remaining among them에서 `start_at` 오름차순(가장 이른 것부터) 정렬
5. 전부 반환. 없으면 204 No Content

### 6.2 기간 경계 처리 (확정: KST 날짜 기준, 양쪽 포함)

- **시작일**: `start_at` 날짜 00:00:00부터 노출 시작
- **종료일**: `end_at` 날짜 23:59:59까지 노출 유지 (양쪽 날짜 포함)
- `LocalDate` 타입 사용, KST 타임존 기준

## 7. 기존 패턴 참조

- **SecurityConfig**: `backend/src/main/java/com/ipocalc/accountauthentication/config/security/SecurityConfig.java` (기존 `hasRole("ADMIN")` 패턴 재사용)
- **Ipo 엔티티**: `backend/src/main/java/com/ipocalc/ipocatalog/domain/model/ipo/Ipo.java` (id, status, deleted_at, created_at, updated_at 필드 구조 재사용)
- **IpoController**: 관리자 CRUD 패턴과 유사하게 설계 (POST/PATCH/DELETE에 ADMIN 권한 매핑)
- **MainLayout**: ADMIN_ITEMS에 "공지사항 관리" 메뉴 추가 (관리자 전용, `/admin/announcements`)
- **ConfirmDialog**: `ui/src/shared/components/ConfirmDialog/ConfirmDialog.tsx` (팝업 다이얼로그 재사용)
- **localStorage 패턴**: `ui/src/entities/investor/useCurrentInvestor.ts` (키 패턴 및 폴백 로직 재사용)
- **apiFetch**: `ui/src/shared/api/client.ts` (세션 쿠키 `credentials: 'include'` 재사용)
- **publicPaths**: `ui/src/app/publicPaths.ts` (비로그인 공개 경로 유지)

## 8. Flyway 마이그레이션 제안

> 실제 마이그레이션 번호는 기존 최대 번호(V32)를 확인한 뒤 이어서 사용한다. **구현 시점에는 migration tree를 다시 확인한다.**
> 이 설계는 구현 후보이며, SecurityConfig의 실제 endpoint matcher ordering과 연동된 정확한 경로만을 기술한다.

### V33__create_announcements_table.sql (구현 후보 — 공통)

```sql
-- announcements 테이블 생성
CREATE TABLE announcements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    start_at DATE NOT NULL,
    end_at DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- 활성 공지 인덱스
CREATE INDEX idx_announce_status_active ON announcements (status, start_at, end_at);
CREATE INDEX idx_announce_start_at ON announcements (start_at ASC);

-- 제목 ACTIVE 중복 방지 부분 유니크 (PostgreSQL)
-- 이 인덱스는 status='ACTIVE'일 때 title 중복만 제한한다.
-- deleted_at과 동시 필터링은 Service 계층에서 보장한다.
CREATE UNIQUE INDEX uk_announce_title_active ON announcements (title) WHERE status = 'ACTIVE';
```

### V34__create_announcement_dismissals_table.sql

```sql
-- announcement_dismissals 테이블 생성
CREATE TABLE announcement_dismissals (
    account_id BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    dismissed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (account_id, announcement_id),
    FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    FOREIGN KEY (announcement_id) REFERENCES announcements (id) ON DELETE CASCADE
);

-- dismissal 조회 인덱스
CREATE INDEX idx_dismiss_announce ON announcement_dismissals (announcement_id);
```

## 9. 확정 사항 요약 (모든 게이트 해제)

> **모든 게이트(GT-01~GT-07)는 사용자 요청으로 확정되어 별도 게이트 불필요.**

| 게이트 ID | 내용 | 확정 값 | 비고 |
|-----------|------|---------|------|
| GT-01 | 시작/종료 시각 포함 단위 | KST 날짜 기준, 시작일~종료일 양쪽 포함 (`LocalDate`, 종료일 23:59:59까지) | 게이트 불필요 |
| GT-02 | "다시 보지 않기" 저장소 | 이메일 계정(계정 ID) 기준 서버 DB 저장 | 게이트 불필요 |
| GT-03 | 비로그인 사용자에게 팝업 표시 | 로그인 사용자만 (사용자 요청) | 게이트 불필요 |
| GT-04 | 다중 활성 공지 노출 우선순위 | 전부 순차 표시, priority 컬럼/입력 필드 불필요 | 게이트 불필요 |
| GT-05 | "홈 첫 진입" 정의 | 로그인 후 대시보드 최초 진입 (사용자 요청) | 게이트 불필요 |
| GT-06 | "닫기" 후 재접속 시 재표시 | 이번 방문에서만 닫고 다음 재접속/새 방문에 재표시 | 게이트 불필요 |
| GT-07 | 관리자 공지 관리 메뉴 경로 | 기존 admin route 패턴 `/admin/announcements` | 게이트 불필요 |

> **확정 사항 (게이트 불필요)**: 관리자 CRUD, 로그인 사용자 읽기, 기간 경계(KST 날짜, 시작일~종료일 양쪽 포함), 팝업 노출 구조(전부 순차), 상세 이동, "닫기"(이번 방문에서만 닫고 다음 재접속/새 방문에 재표시), "다시 보지 않기"(계정 기준 서버 DB 영구 숨김), 삭제(관리자 전용, 기존 `RecordStatus` + `deleted_at` 패턴 준용: `status=CANCELLED`, `deleted_at=now()` 논리 삭제, 조회 쿼리는 `status=ACTIVE AND deleted_at IS NULL` 필터), IPO 독립성.

> **SQL/코드 예시 주의**: 본 섹션의 SQL과 코드 예시는 **구현 후보**이며, 실제 프로덕션 적용 전 SecurityConfig의 `.requestMatcher()` 순서와 `antMatchers` 패턴 일치를 반드시 확인한다. 기존 Ipo 엔티티 패턴을 차용했으나, `status`와 `deleted_at`이 동시에 존재하는 경우 쿼리 내 `WHERE` 절의 결합 조건이 의도치 않은 결과를 낳을 수 있다. index/unique title 등 스펙에 명시되지 않은 제약은 제거하며, 실제 적용은 Service 계층 검증과 DB 제약을 이중으로 적용하는 방식으로 설계한다. IPO 도메인과 완전히 분리하여 별도 패키지·엔티티·리포지토리로 구현한다.

---
*작성일: 2026-10-23*
*Epic 22, slug: announcements*