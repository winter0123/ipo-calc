# 공지사항(Announcements) Epic — 조사 문서

> Epic 번호: 22 (roadmap 마지막 E21 이후), slug: `announcements`
> 작성일: 2026-10-23
> 목적: 기존 코드·DB·인증·메뉴·라우터·모달·로컬스토리지 패턴을 조사해 공지사항 기능의 실현 가능성을 근거로 문서화한다.

---

## 1. 사용자 요청 요약 (2026-10-23 확정)

관리자가 공지사항을 CRUD하고, 모든 로그인 사용자가 읽을 수 있다. 공지사항은 **공모주(IPO)와 독립된 기능/데이터**이며, **활성 노출 기간(시작일~종료일)**을 가진다. 로그인 사용자가 홈(대시보드) 첫 진입 시 팝업으로 노출된다. 상세 화면으로 이동 가능하고, **닫기(닫기 버튼)**는 이번 방문에서만 닫고 다음 재접속/새 방문에 재표시, **다시 보지 않기**는 해당 공지에 대해 영구 숨김 처리한다. 관리자만 CRUD 가능. 관리자 URL은 기존 admin route 패턴(`/admin/announcements`)을 따른다.

---

## 2. 기존 백엔드 패턴 조사

### 2.1 인증·인가 (SecurityConfig.java:30-109)

- **세션 기반 인증**: `SecurityConfig`에서 세션 관리, `SessionRegistry`로 동시 세션 제어(최대 1개)
- **미인증 요청**: 401 Unauthorized 반환 (`HttpStatusEntryPoint`)
- **권한 부족**: 403 Forbidden 반환 (JSON `{"error":"FORBIDDEN","message":"관리자 권한이 필요합니다"}`)
- **CSRF**: 전역 비활성화 (React SPA가 JSON API만 사용)
- **공개 GET 허용**: `/ipos`, `/ipos/**`, `/market-holidays` → `permitAll()`
- **관리자 전용**:
  - `/admin/**` → `hasRole("ADMIN")`
  - `POST/PATCH/DELETE /ipos/**`, `/securities-firms/**` → `hasRole("ADMIN")`
  - `GET /ipos/sync/**` → `hasRole("ADMIN")`
- **그 외**: `anyRequest().authenticated()` → 로그인 필요

### 2.2 도메인 엔티티 패턴 (Ipo.java:20-125)

```java
@Entity @Table(name = "ipos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Ipo {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;

  // 비즈니스 필드들...

  @Column(name = "status", nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private RecordStatus status = RecordStatus.ACTIVE;  // ACTIVE / CANCELLED

  @Column(name = "deleted_at") LocalDateTime deletedAt;  // 소프트 삭제

  @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
  @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
}
```

- **RecordStatus enum** (`accountauthentication/domain/model/RecordStatus.java`): `ACTIVE`, `CANCELLED`
- **소프트 삭제**: `deleted_at` 타임스탬프로 논리 삭제, 물리 삭제 안 함
- **감사 필드**: `created_at`, `updated_at` (JPA `@Column(updatable=false)`로 생성 시만 설정)
- **동기화 출처**: `sync_source` (OPENDART, OPENDART+USTOCKPLUS, null=관리자 직접 등록)

### 2.3 컨트롤러 패턴 (IpoController.java:30-125)

```java
@RestController @RequestMapping("/ipos") @RequiredArgsConstructor
public class IpoController {
  // 공개 조회
  @GetMapping public ResponseEntity<List<IpoResponse>> list()
  @GetMapping("/{id}") public ResponseEntity<IpoResponse> getById(@PathVariable Long id)
  @GetMapping("/{id}/insight") public ResponseEntity<IpoInsightResponse> getInsight(...)

  // 관리자 전용 (POST/PATCH/DELETE는 SecurityConfig에서 ADMIN 강제)
  @PostMapping public ResponseEntity<IpoResponse> register(@RequestBody IpoRegisterRequest request)
  @PatchMapping("/{id}") public ResponseEntity<IpoResponse> update(...)
  @DeleteMapping("/{id}") public ResponseEntity<Void> deactivate(@PathVariable Long id)

  // 관리자 동기화
  @PostMapping("/sync") public ResponseEntity<?> syncNow(...)
  @GetMapping("/sync/status") public ResponseEntity<IpoSyncStatusResponse> getSyncStatus()
  @PostMapping("/insight/sync") public ResponseEntity<Map<String, Integer>> syncInsight()
}
```

- **DTO 분리**: Request/Response DTO 별도 정의 (`IpoRegisterRequest`, `IpoUpdateRequest`, `IpoResponse`, `IpoInsightResponse`)
- **검증**: `@Valid` 어노테이션 사용
- **응답**: `ResponseEntity`로 HTTP 상태 코드 명시적 제어

### 2.4 서비스·리포지토리 패턴

- **IpoService**: 비즈니스 로직, 소유권 검증(계정 격리), 트랜잭션 경계
- **IpoRepository**: `JpaRepository<Ipo, Long>` 확장, 커스텀 쿼리 메서드
- **소유권 검증**: `OwnershipGuard` 패턴으로 타 계정 데이터 접근 차단 (403 FORBIDDEN)

### 2.5 Flyway 마이그레이션 패턴

- `backend/src/main/resources/db/migration/V{번호}__{설명}.sql`
- 테이블 생성, 컬럼 추가, 인덱스, 코멘트 순차 적용
- 예: `V22__add_api_columns_to_securities_firms.sql`, `V24__create_ipo_details_table.sql`, `V25__add_korean_comments.sql`

---

## 3. 기존 프론트엔드 패턴 조사

### 3.1 라우팅·레이아웃 (App.tsx:47-107, MainLayout.tsx:11-138)

- **React Router v6** + 지연 로딩(`lazy`)
- **MainLayout**: 헤더(브랜드, 메뉴, 투자자 전환, 로그아웃), 본문(`<Outlet />`)
- **메뉴 구성** (`MainLayout.tsx:11-42`):
  ```typescript
  const NAV_ITEMS = [대시보드, 공모주 일정, 청약, 투자 내역, 수익 확인, 서비스 소개]
  const RECORD_ITEMS = [청약 등록, 배정 입력, 매도 등록]
  const SETTINGS_ITEMS = [마이페이지, 투자자 추가, 증권사 계좌 등록, 증권사 연동, 알림 설정]
  const ADMIN_ITEMS = [가입 승인, 공모주 등록]  // currentAccount?.role === 'ADMIN'일 때만 표시
  const GUEST_ITEMS = [서비스 소개, 공모주 일정]  // 비로그인용
  ```

### 3.2 권한 가드 (RouteGuards.tsx:5-19, InvestorGate.tsx:8-91)

- **AdminOnly**: `account?.role !== 'ADMIN'`이면 안내 메시지 표시, 서버에서도 403 검증
- **GuestOnly**: 로그인済 → `/dashboard` 리다이렉트
- **InvestorGate** (`GATED_PREFIXES`): 투자자별 작업 화면 진입 전 투자자 선택 강제
  - `/investments`, `/subscriptions`, `/allocations`, `/sales`, `/profits`, `/broker-sync`, `/securities-accounts`, `/securities`
  - 투자자 1명 → 자동 선택, 2명 이상 → 선택 UI 표시

### 3.3 공개 경로 (publicPaths.ts:1-11)

```typescript
export function isPublicPath(pathname: string): boolean {
  return pathname === '/' || pathname === '/about' || pathname === '/login'
      || pathname === '/signup' || pathname === '/ipo-calendar' || pathname.startsWith('/ipos/')
}
```

### 3.4 루트 리다이렉트 (RootRedirect.tsx:7-11)

```typescript
export default function RootRedirect() {
  const { data: currentAccount, isPending } = useCurrentAccount()
  if (isPending) return <p role="status">불러오는 중...</p>
  return currentAccount ? <Navigate to="/dashboard" replace /> : <LandingPage />
}
```

- **로그인 사용자**: `/dashboard`로 이동 (홈 = 대시보드)
- **비로그인 사용자**: `LandingPage` 표시 (`/about`과 동일)

### 3.5 API 클라이언트 (client.ts:1-52)

```typescript
export async function apiFetch<T = unknown>(path: string, options: RequestInit = {}): Promise<T> {
  const config: RequestInit = {
    ...options,
    credentials: 'include',  // 쿠키(세션) 자동 포함
    headers: { 'Content-Type': 'application/json', ...options.headers },
  }
  // 401 → ApiError(401) throw, 라우터 직접 참조 안 함
  // 4xx/5xx → ApiError throw (서버 메시지 추출 시도)
}
```

- **credentials: 'include'**: 모든 요청에 세션 쿠키 자동 포함
- **에러 처리**: `ApiError` 클래스(`apiError.ts`)로 상태 코드·메시지 래핑

### 3.6 로컬스토리지 패턴 (useCurrentInvestor.ts:5-68)

```typescript
const STORAGE_KEY = 'currentInvestorId'
let memorySelection: string | null = null  // 프라이빗 모드 등 대비 메모리 폴백

function readStored(): string | null {
  try { return localStorage.getItem(STORAGE_KEY) ?? memorySelection } catch { return memorySelection }
}

function subscribe(onChange: () => void) {
  window.addEventListener('current-investor-change', onChange)
  window.addEventListener('storage', onChange)  // 타 탭 동기화
  return () => { ... }
}

export function useCurrentInvestor(): CurrentInvestor {
  const { data, isLoading } = useInvestors()
  const stored = useSyncExternalStore(subscribe, readStored, () => null)
  // ...
  const select = useCallback((investorId: number) => {
    memorySelection = String(investorId)
    try { localStorage.setItem(STORAGE_KEY, String(investorId)) } catch {}
    window.dispatchEvent(new Event('current-investor-change'))
  }, [])
  // ...
}
```

- **키**: `currentInvestorId` (문자열 저장)
- **메모리 폴백**: `localStorage` 불가 시 메모리 변수로 세션 동안 유지
- **크로스 탭 동기화**: `storage` 이벤트 + 커스텀 `current-investor-change` 이벤트
- **useSyncExternalStore**: React 18 동시성 모드 대응

### 3.7 모달 패턴 (ConfirmDialog.tsx:1-100)

```typescript
interface ConfirmDialogProps {
  isOpen: boolean
  title: string
  message: string
  onConfirm: () => void
  onCancel: () => void
  confirmLabel?: string
  cancelLabel?: string
  pending?: boolean
}
```

- **role="alertdialog"**, `aria-modal="true"`로 접근성 준수
- **포커스 트랩**: Tab/Shift+Tab 순환, Escape로 취소
- **오버레이** + 중앙 다이얼로그 구조
- **pending 상태**: 확인 버튼 비활성화, `aria-busy`

### 3.8 알림/토스트 패턴 (NotificationSettings.tsx:153-202)

- 별도 토스트 라이브러리 없음 → 인라인 상태 메시지(`role="status"`, `role="alert"`) 사용
- 성공/에러 메시지를 컴포넌트 내부 상태(`testMessage`, `save.isSuccess`, `save.isError`)로 표시

---

## 4. 데이터베이스 스키마 조사

### 4.1 계정·역할 (Flyway 마이그레이션에서 확인)

- `accounts` 테이블: `id`, `email`, `password`, `name`, `role` (USER/ADMIN), `approved`, `created_at`, `updated_at`, `deleted_at`
- `RecordStatus` enum 공유: `ACTIVE`, `CANCELLED`

### 4.2 기존 공지/알림 관련 테이블

- `notification_preferences`: 계정별 푸시 알림 설정 (청약 마감, 상장일)
- `push_subscriptions`: Web Push 구독 정보 (엔드포인트, 키, user_agent)
- `notification_logs`: 발송 로그 (`account_id`, `type`, `ref_id`, `sent_on`)
- **공지사항 전용 테이블 없음** — 신규 생성 필요

---

## 5. "홈 첫 진입" 정의 조사

### 5.1 현재 홈 진입 플로우

1. **비로그인**: `/` → `RootRedirect` → `LandingPage` (`/about`과 동일)
2. **로그인**: `/` → `RootRedirect` → `Navigate to="/dashboard" replace` → `DashboardPage`
3. **대시보드**: `DashboardPage`에서 `할 일`(미입력 배정) + `다가오는 일정`(7일) 표시

### 5.2 "첫 진입" 판단 기준 (2026-10-23 확정: 로그인 후 대시보드 최초 진입)

**확정됨**: 로그인 후 대시보드 최초 진입 시 1회 팝업 표시. 세션/브라우저 세션/일일 기준 등 다른 대안은 검토 후 배제. 현재 코드에 "첫 진입" 추적 로직 없음 — 신규 구현 필요.

---

## 6. "다시 보지 않기" 저장 범위 (2026-10-23 확정: 이메일 계정 기준 서버 DB 저장)

**확정됨**: 이메일 계정(기존 안정적 `account_id`) 기준으로 서버 DB에 저장 — 같은 계정의 다른 기기에서도 숨김 처리. 브라우저 localStorage/cookie 단독 저장은 요구 불충분이므로 후보에서 제외한다.

---

## 7. 여러 활성 공지 노출 우선순위 (2026-10-23 확정: 전부 순차 표시, priority 불필요)

**확정됨**: 기간이 겹치는 공지는 전부 순차 표시한다. 하나만 선택하거나 priority/우선순위 입력 필드를 추가하지 않는다. 정렬은 시작일 순(가장 이른 것부터) 기본 적용.

---

## 8. 로그인 전/후 팝업 대상 (2026-10-23 확정: 로그인 사용자만)

| 대상 | 설명 |
|------|------|
| **로그인 후만** (확정) | 공지사항은 계정 단위 기능(다시 보지 않기 저장 필요)이므로 비로그인 상태에서는 의미 없음 |
| 로그인 전도 포함 (공개 공지) | `/ipo-calendar`, `/ipos/**` 진입 시도 표시 — 범위 밖 |

**확정됨**: 로그인 후만 — 비로그인 홈(`/about`, `/ipo-calendar`) 진입 시 팝업 표시 안 함

---

## 9. 확정 사항 요약 (모든 게이트 해제)

> **모든 게이트(GT-01~GT-07)는 사용자 요청으로 확정되어 별도 게이트 불필요.**

| 게이트 ID | 내용 | 확정 값 | 비고 |
|-----------|------|---------|------|
| GT-01 | 기간 경계 | KST 날짜 기준, 시작일~종료일 양쪽 포함 (`LocalDate`, 종료일 23:59:59까지) | 게이트 불필요 |
| GT-02 | "다시 보지 않기" 저장소 | 이메일 계정(계정 ID) 기준 서버 DB 저장 | 게이트 불필요 |
| GT-03 | 비로그인 사용자 팝업 | 로그인 사용자만 | 게이트 불필요 |
| GT-04 | 다중 활성 공지 노출 우선순위 | 전부 순차 표시, priority 컬럼/입력 필드 불필요 | 게이트 불필요 |
| GT-05 | "홈 첫 진입" 정의 | 로그인 후 대시보드 최초 진입 | 게이트 불필요 |
| GT-06 | "닫기" 후 재접속 시 재표시 | 이번 방문에서만 닫고 다음 재접속/새 방문에 재표시 | 게이트 불필요 |
| GT-07 | 관리자 공지 관리 메뉴 경로 | 기존 admin route 패턴 `/admin/announcements` | 게이트 불필요 |

---

## 10. 참고 파일 경로 (구현 시 재사용)

| 영역 | 파일 경로 |
|------|-----------|
| SecurityConfig | `backend/src/main/java/com/ipocalc/accountauthentication/config/security/SecurityConfig.java` |
| RecordStatus | `backend/src/main/java/com/ipocalc/accountauthentication/domain/model/RecordStatus.java` |
| Ipo 엔티티 | `backend/src/main/java/com/ipocalc/ipocatalog/domain/model/ipo/Ipo.java` |
| IpoController | `backend/src/main/java/com/ipocalc/ipocatalog/controller/ipo/IpoController.java` |
| IpoService | `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/IpoService.java` |
| MainLayout | `ui/src/app/layouts/MainLayout/MainLayout.tsx` |
| RouteGuards | `ui/src/app/layouts/MainLayout/RouteGuards.tsx` |
| InvestorGate | `ui/src/app/layouts/MainLayout/InvestorGate.tsx` |
| RootRedirect | `ui/src/app/layouts/MainLayout/RootRedirect.tsx` |
| apiFetch | `ui/src/shared/api/client.ts` |
| useCurrentInvestor | `ui/src/entities/investor/useCurrentInvestor.ts` |
| ConfirmDialog | `ui/src/shared/components/ConfirmDialog/ConfirmDialog.tsx` |
| AdminAccountsPage | `ui/src/pages/AdminAccountsPage/AdminAccountsPage.tsx` |
| IpoRegisterPage | `ui/src/pages/IpoRegisterPage/IpoRegisterPage.tsx` |
| Flyway 마이그레이션 | `backend/src/main/resources/db/migration/` |
| publicPaths | `ui/src/app/publicPaths.ts` |
| App.tsx 라우팅 | `ui/src/app/App.tsx` |

---

## 11. 제약사항·리스크

- **기존 테이블 변경 금지**: `accounts`, `ipos` 등 기존 테이블 스키마 변경 없음 — 신규 테이블만 생성
- **인증 플로우 변경 금지**: SecurityConfig의 인증·인가 규칙 유지, 새 엔드포인트만 권한 매핑 추가
- **메뉴 구조 변경 최소화**: `ADMIN_ITEMS`에 공지사항 관리 메뉴만 추가
- **무료 모델 제약**: 구현 워커는 OpenCode 무료 모델 사용, spec을 자기완결적으로 제공해야 함
- **Phase 0 승인 선행**: research → spec → architecture → Master 승인 → breakdown → 구현 순서 준수

---

## 12. 결론

기존 패턴을 모두 재사용하여 공지사항 기능을 구현할 수 있다:
- 백엔드: `Ipo` 패턴 그대로 따라 `Announcement` 엔티티·리포지토리·서비스·컨트롤러 생성, ADMIN 권한 매핑만 SecurityConfig에 추가
- 프론트엔드: `AdminAccountsPage`/`IpoRegisterPage` 패턴으로 관리자 CRUD 페이지, `ConfirmDialog` 패턴으로 팝업, "다시 보지 않기"는 이메일 계정 기준 서버 DB 저장으로 확정
- DB: Flyway 마이그레이션으로 `announcements`, `announcement_dismissals` 테이블 생성
- 모든 게이트(GT-01~GT-07)는 사용자 요청으로 확정되어 별도 게이트 불필요

## GT-01~GT-07 참조 현황
| 게이트 ID | 내용 | 비고 |
|---------|------|------|
| GT-01 | 기간 경계 API 로직 | **확정: KST 날짜 기준, 시작일~종료일 양쪽 포함** |
| GT-02 | "다시 보지 않기" 저장소 | **확정: 이메일 계정(계정 ID) 기준 서버 DB 저장** |
| GT-03 | 비로그인 사용자 팝업 | **확정: 로그인 사용자만** |
| GT-04 | 다중 활성 공지 우선순위 | **확정: 전부 순차 표시, priority 불필요** |
| GT-05 | "홈 첫 진입" 정의 | **확정: 로그인 후 대시보드 최초 진입** |
| GT-06 | "닫기" 재접속 재표시 | **확정: 재접속 시 재표시** |
| GT-07 | 관리자 메뉴 경로 | **확정: 기존 admin route 패턴 `/admin/announcements`** |