# 공지사항(Epic 22) 브레이크다운 리서치

> **Epic**: 22, slug: `announcements`
> **작성일**: 2026-10-23
> **기반 문서**: `docs/research/announcements.md`, `docs/spec/announcements.md`, `docs/architecture/announcements.md`, `docs/breakdown/announcements.md`
> **확정됨 (사용자 요청 2026-10-23)**: GT-01(기간 경계: KST 날짜, 양쪽 포함), GT-02(이메일 계정 기준 서버 DB 저장), GT-03(로그인 사용자만), GT-04(전부 순차 표시, priority 불필요), GT-05(로그인 후 대시보드 첫 진입), GT-06(닫기 재접속 재표시), GT-07(관리자 경로 `/admin/announcements`), IPO 독립성
> **미결정 게이트 없음**: 모든 게이트 사용자 확정 완료

## 1. 기능 범위 및 세부 기능(SF) 후보

| 카테고리 | 항목 | 비고 |
|----------|------|------|
| **백엔드 CRUD** | `Announcement` 엔티티 생성 | `title`, `content`, `start_at`(DATE), `end_at`(DATE), `status`, `deleted_at` 필드 |
| | 리포지토리 인터페이스 | Spring Data JPA `JpaRepository<Announcement, Long>` |
| | 서비스 계층 | CRUD 거래 로직, 중복 검사(`title` + `status=ACTIVE`), 기간 필터(KST 날짜, 양쪽 포함) |
| | 컨트롤러 (관리자) | 엔드포인트: `POST /api/admin/announcements` 등 (`/admin/announcements` 경로) |
| | 컨트롤러 (사용자) | 엔드포인트: `GET /api/announcements`, `GET /api/announcements/popup`, `POST /api/announcements/{id}/dismiss` |
| | SecurityConfig 매핑 | `/api/admin/announcements/**` → `hasRole("ADMIN")`, `/api/announcements/**` → `authenticated()` |
| | Flyway 마이그레이션 | `announcements` 테이블: V33, `announcement_dismissals` 테이블: V34 (**구현 직전 migration tree 재확인**) |
| **프론트엔드 UI** | 관리자 목록 페이지 | `/admin/announcements` 경로 (기존 admin route 패턴) |
| | 관리자 등록/수정 폼 | 제목, 내용(에디터), 시작일, 종료일(KST 날짜), 상태, 유효성 검증 |
| | 사용자 목록 페이지 | `/announcements` 활성 공지 카드/리스트, 페이지네이션, 정렬(시작일 순) |
| | 사용자 상세 페이지 | `/announcements/{id}` 전체 내용, 메타 정보 |
| | 홈 첫 진입 팝업 | `ConfirmDialog` 모달, 제목/내용 요약, "자세히 보기", "닫기", "다시 보지 않기" (전부 순차 표시) |
| | "닫기" 액션 | 모달 닫기, 서버 호출 없음, 이번 방문에서만 닫고 다음 재접속/새 방문에 재표시 (구현 저장소 비특정, 관측 동작만 요구) |
| | "다시 보지 않기" 액션 | `POST /api/announcements/{id}/dismiss` 호출, 모달 닫기, 계정 ID 기준 DB 저장으로 영구 숨김·기기 동기화 |
| | 상세 이동 | "자세히 보기" 클릭 시 `/announcements/{id}` 네비게이션, 모달 닫기 |
| | 에러 상태 | API 실패 시 인라인 에러 메시지, 네트워크 에러 시 토스트 |
| | 빈 상태 | 활성 공지 없을 때 "등록된 공지사항이 없습니다" 표시 |
| **IPO 독립성** | 별도 도메인 패키지 | `announcements` 패키지 별도 생성, IPO 엔티티·API와 무관 |

## 2. 의존 관계 및 순서 후보 (게이트 의존 없음 — 모두 확정)

> **모든 게이트(GT-01~GT-07) 사용자 확정 완료**. 코드 레벨에서 임의 값 가정 불필요.

```
[GT-01 확정] → 기간 경계 필터 적용 (조회 쿼리 WHERE 절에 start_at/end_at DATE 조건, 양쪽 포함)
[GT-02 확정] → dismissal 저장소 확정, API `POST /{id}/dismiss` 구현 (계정 ID 기준 DB 저장)
[GT-04 확정] → 정렬 로직 시작일 순 적용, priority 컬럼/입력 필드 없음
[GT-07 확정] → 관리자 메뉴 경로 `/admin/announcements`, ADMIN_ITEMS 구성 완료
[GT-03·GT-05·GT-06·IPO 독립성 확정] → 비로그인 팝업 미표시, 로그인 후 대시보드 첫 진입 팝업 노출, "닫기" 시 다음 재접속 재표시, IPO와 별도 도메인

순서 후보:
1. 기본 CRUD SF 구현 (기간 필터·정렬·dismissal 모두 확정값 적용)
2. 사용자 목록·상세·팝업 순차 구현 (병렬 가능)
3. 관리자 메뉴 통합 및 경로 확정
4. IPO 독립성 별도 패키지 구성 (병렬 가능)
```

## 3. 기존 패턴 재사용 후보

- **Ipo 엔티티 패턴**: `status`, `deleted_at`, `created_at`, `updated_at` 필드 구조 재사용
- **SecurityConfig**: `hasRole("ADMIN")` 패턴 재사용 (새 엔드포인트 추가のみ)
- **MainLayout**: ADMIN_ITEMS에 메뉴 추가 패턴 재사용 (`/admin/announcements`)
- **ConfirmDialog**: `role="alertdialog"`, `aria-modal="true"`, 포커스 트랩 재사용
- **localStorage 패턴**: `useCurrentInvestor.ts` 키 구조 및 폴백 로직 재사용 (투자자 선택용, 팝업 닫기와 무관)
- **apiFetch**: 세션 쿠키 `credentials: 'include'` 재사용
- **publicPaths**: 비로그인 공개 경로 유지(`/about`, `/ipo-calendar` 등)
- **Flyway 마이그레이션**: V32 이하 패턴(V1~V32 기존 문법 준수)

## 4. 리스크 및 의존성 차단 사항

| 항목 | 위험도 | 완화 방안 |
|------|--------|-----------|
| GT-01~07 모두 확정 | 낮음 | 게이트 대기 없이 즉시 구현 가능, 더미 데이터 불필요 |
| IPO 독립성 | 낮음 | 별도 패키지·엔티티·리포지토리 생성, 기존 IPO 코드 변경 없음 |
| 기기 간 동기화 | 중간 | `announcement_dismissals` 테이블로 계정 ID 기준 저장, 기존 인증 패턴 활용 |

## 5. 착수 전 체크리스트

- [x] GT-01, GT-02, GT-04, GT-07 최종 결정 확인 (사용자 승인 완료)
- [x] GT-03, GT-05, GT-06, IPO 독립성 확정 사항 반영 확인 (이미 사용자 요청으로 확정)
- [x] `docs/research/announcements.md` 내용 확인 및 유지
- [x] `docs/spec/announcements.md` AC 및 게이트 일치성 검토
- [x] `docs/architecture/announcements.md` SQL 패턴과 SecurityConfig matcher 확인
- [x] Flyway 마이그레이션 번호·스키마 확정 (V33, V34 — **구현 직전 migration tree 재확인**)
- [x] `docs/breakdown/announcements.md` 초안 검토 및 정책 후보 확인
- [x] roadmap Epic 22 항목 상태 'Phase 0 검토 완료 / 구현 대기' 갱신
- [x] 모든 게이트 확정으로 BLOCKED SF 없음 - 확인된 AC대로 구현 진행