# 공지사항(Epic 22) 세부 기능 분해 (breakdown)

> **Epic**: 22, slug: `announcements`
> **작성일**: 2026-10-23
> **상태**: Phase 0 검토 완료 / 모든 게이트(GT-01~GT-07) 사용자 확정 / 구현 대기
> **의존**: `docs/breakdown-research/announcements.md`, `docs/roadmap.md`, `docs/spec/announcements.md`, `docs/architecture/announcements.md`
> **가이드라인**: 모든 게이트 확정으로 더미 데이터 불필요. 확정된 동작에 대해 BLOCKED 표시 없음.

## 이 Epic의 특이사항

1. **GT-01~GT-07 모두 사용자 확정 완료** — 코드에 임의 값 가정 금지, 확정값으로 즉시 구현 가능
2. **GT-03(비로그인 팝업: 로그인 사용자만), GT-05(홈 첫 진입: 로그인 후 대시보드 첫 진입), GT-06(닫기 후 재접속 재표시: 재표시함), IPO 독립성** — 사용자 요청으로 확정, 별도 게이트 불필요
3. **백엔드·프론트엔드 의존 있음** — admin CRUD와 user read/popup 연동이 동시에 진행되어야 함
4. **기존 패턴 완벽 재사용** — Ipo/SecurityConfig/ConfirmDialog 패턴 그대로 적용 가능 (localStorage 패턴은 투자자 선택용, 팝업 닫기용 아님)
5. **모든 게이트 해제로 BLOCKED SF 없음** — 의존관계가 있는 SF도 확정값으로 즉시 구현 가능
6. **CRUD API와 프론트 메뉴 병렬 가능** — 의존관계가 낮은 SF는 동시에 진행 가능
7. **SF 3~8개로 분해** — 관리자 CRUD, 공지 목록/상세, 홈 팝업, 다시 보지 않기를 각각 검증 가능한 작업 단위로 구분

## 세부 기능(SF) 개요 — 의존성별 상태 표시

| SF | 내용 | 주요 의존 AC | 의존 게이트 | 상태 | 비고 |
|---|---|---|---|---|---|
| **SF-1** | 관리자 공지 CRUD 화면 (`/admin/announcements`) | AC-BE-01~05, AC-FE-01~04 | (게이트 없음, GT-07 확정) | **구현 가능** | `title`, `content`, `start_at`(DATE), `end_at`(DATE), `status` 필드; CRUD 패턴은 Ipo 엔티티와 동일; IPO와 별도 패키지 |
| **SF-2** | 사용자 공지 목록 조회 (`/announcements`) | AC-BE-06, AC-FE-05, AC-FE-14 | (게이트 없음, GT-01/04 확정) | **구현 가능** | `status=ACTIVE AND deleted_at IS NULL` 필터, KST 날짜 양쪽 포함, 페이지네이션, 정렬(start_at ASC) |
| **SF-3** | 사용자 공지 상세 조회 (`/announcements/{id}`) | AC-BE-07, AC-FE-06 | (게이트 없음, GT-01 확정) | **구현 가능** | `status=ACTIVE AND deleted_at IS NULL` 필터, KST 날짜 양쪽 포함, 단건 조회 |
| **SF-4** | 홈 첫 진입 팝업 노출 | AC-FE-07, AC-FE-08 | (게이트 없음, GT-01/02/05 확정) | **구현 가능** | `GET /api/announcements/popup` 호출, `ConfirmDialog` 모달 순차 표시; 기간 필터·dismissal 제외 모두 확정값 적용 |
| **SF-5** | "닫기" 액션 처리 | AC-FE-09 | (게이트 없음, GT-06 확정) | **구현 가능** | 모달 닫기, 서버 호출 없음, 이번 방문에서만 닫고 다음 재접속/새 방문에 재표시 (세션 범위 휘발성 상태) |
| **SF-6** | "다시 보지 않기" 액션 처리 | AC-FE-10, AC-BE-09 | (게이트 없음, GT-02 확정) | **구현 가능** | `POST /api/announcements/{id}/dismiss` 호출, 계정 ID 기준 DB 저장, 영구 숨김·기기 동기화 |
| **SF-7** | 관리자 공지 삭제 (논리 삭제 확정) | AC-BE-05, AC-FE-04 | (게이트 없음, GT-07 확정) | **구현 가능** | `status=CANCELLED`, `deleted_at=now()` 논리 변경; Ipo 엔티티 패턴 준용 |
| **SF-8** | IPO 독립성 보장 (별도 패키지·엔티티·리포지토리) | AC-BE-13, AC-SEC-05 | (게이트 없음, 독립) | **구현 가능** | `announcements` 패키지 별도 생성, 기존 IPO 테이블·API 변경 없음 |

> **상태 키**:
> - **구현 가능**: 모든 게이트 확정으로 실제 로직 즉시 적용 가능
> - **BLOCKED 표시 없음**: 모든 사용자 요청 확정 동작은 BLOCKED 마커로 지정하지 않음

## 의존 관계 및 순서 후보

```
[GT-01 확정] → 기간 경계 필터 적용 (SF-2, SF-3, SF-4 쿼리 필터, KST 날짜 양쪽 포함)
[GT-02 확정] → dismissal 저장소 확정, SF-6 API 구현 (계정 ID 기준 DB 저장, 영구 숨김·기기 동기화)
[GT-04 확정] → 정렬 로직 시작일 순 적용 (SF-2 UI 정렬, priority 컬럼/입력 필드 없음)
[GT-07 확정] → 메뉴 경로 `/admin/announcements` 확정, 관리자 CRUD 엔드포인트 확정

[GT-03·GT-05·GT-06·IPO 독립성 확정] →
   - GT-03: 비로그인 팝업 미표시 (로그인 사용자만)
   - GT-05: "홈 첫 진입" = 로그인 후 /dashboard 최초 진입
   - GT-06: "닫기" 후 다음 재접속(재로그인) 시 재표시 (이번 방문에서만 닫음)
   - IPO 독립성: 별도 패키지·엔티티·리포지토리, 기존 IPO 코드 변경 없음

> **권장 순서**: SF-1, SF-4, SF-8은 의존 관계가 낮아 병렬 가능.
> SF-2와 SF-3는 SF-1의 엔티티/리포지토리 완료 후 진행 (선행 관계).
> SF-6은 SF-1의 엔티티/리포지토리 + dismissal 테이블 완료 후 진행.
> SF-7은 SF-1과 병렬 가능.
> **모든 게이트 확정으로 실제 의존성만 남음 — 게이트 대기 없음**.
```

## SF 파일 경로 (FSD)

> **아래 경로는 게이트 해제로 즉시 생성 가능.**

| 경로 | 비고 |
|------|------|
| `ui/src/pages/admin/Announcement/AnnouncementPage.tsx` | 관리자 CRUD 페이지 (SF-1) |
| `ui/src/pages/user/Announcement/AnnouncementListPage.tsx` | 사용자 목록 페이지 (SF-2) |
| `ui/src/pages/user/Announcement/AnnouncementDetailPage.tsx` | 사용자 상세 페이지 (SF-3) |
| `ui/src/shared/components/AnnouncementPopup/ConfirmDialog.tsx` | 팝업 다이얼로그 (SF-4~SF-6) |
| `backend/src/main/java/.../announcements/controller/AnnouncementController.java` | admin/user controller (SF-1, SF-2, SF-3, SF-7) |
| `backend/src/main/java/.../announcements/service/AnnouncementService.java` | service logic (SF-1~SF-8) |
| `backend/src/main/java/.../announcements/repository/AnnouncementRepository.java` | JPA repository (SF-1~SF-8) |
| `backend/src/main/java/.../announcements/domain/model/Announcement.java` | entity (SF-1~SF-8) |
| `backend/src/main/java/.../announcements/domain/model/AnnouncementDismissal.java` | dismissal entity (SF-6) |

## 착수 전 체크리스트

- [x] GT-01, GT-02, GT-04, GT-07 최종 결정 확인 (사용자 승인 완료)
- [x] GT-03, GT-05, GT-06, IPO 독립성 확정 사항 반영 확인 (이미 사용자 요청으로 확정)
- [x] `docs/research/announcements.md` 최신 유지
- [x] `docs/spec/announcements.md` AC 및 게이트 일치성 검토
- [x] `docs/architecture/announcements.md` SQL 패턴과 SecurityConfig matcher 일치 확인
- [x] Flyway 마이그레이션 번호·스키마 확정 (V33, V34 — **구현 직전 migration tree 재확인**)
- [x] roadmap Epic 22 항목 상태 'Phase 0 검토 완료 / 구현 대기' 갱신
- [x] 모든 게이트 확정으로 코드에 확정값 즉시 적용 — 게이트 대기 없음
- [x] BLOCKED SF 없음 — 확인된 AC대로 구현 진행