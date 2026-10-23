# 작업 로그: 공지사항 (announcements, Epic 22)

## 2026-10-23 — planning 워커 착수

### 착수 전 준비 작업
- `docs/research/announcements.md` 기존 문서 검토 및 패턴 조사 완료
- `docs/spec/announcements.md` 기존 spec 검토 및 한국어 AC 완성 확인
- `docs/architecture/announcements.md` 신규 작성 (API 계약, 스키마, SecurityConfig, UI 흐름, 로컬스토리지, 팝업 선정 규칙)
- `docs/work-log/announcements.md` 신규 생성 (append-only)

### 1. 조사 패턴 재확인 (research 기반)
- **SecurityConfig**: 관리자 전용 API에 `hasRole("ADMIN")` 매핑 추가 패턴 재사용
- **Ipo 엔티티**: `status`, `deleted_at`, `created_at`, `updated_at` 필드 구조 재사용
- **IpoController**: CRUD 패턴(POST/PATCH/DELETE 권한 매핑) 재사용
- **MainLayout**: ADMIN_ITEMS에 메뉴 추가 패턴 재사용
- **ConfirmDialog**: 팝업 다이얼로그 재사용 (`role="alertdialog"`, `aria-modal="true"`, 포커스 트랩)
- **localStorage 패턴**: `useCurrentInvestor.ts` 키 구조 및 폴백 로직 재사용
- **apiFetch**: 세션 쿠키 `credentials: 'include'` 재사용
- **publicPaths**: 비로그인 공개 경로 유지(`/about`, `/ipo-calendar` 등)

### 2. 아키텍처 설계 완료 (architecture 기반)
- `announcements` 테이블 스키마 설계 (title, content, start_at, end_at, priority, status)
- `announcement_dismissals` 테이블 설계 (account_id + announcement_id 복합 PK)
- API 계약 초안 (관리자 CRUD, 사용자 조회, dismissal API)
- SecurityConfig 역할 매핑 추가 계획
- UI 경로·상태 흐름 설계 (플로우 다이어그램 포함)
- 로컬 저장 키/사용자 계정 동작 설계 (세션 "닫기" vs DB "다시 보지 않기")
- 팝업 노출 선정 규칙 조회 로직 (기간 필터 + dismissal 제외 + priority 정렬)
- Flyway 마이그레이션 제안 작성 (번호·스키마는 GT 확정 후 최종 결정)
- 미결정 게이트 7개 (GT-01~GT-07) 명시 및 선택지 제시

### 3. 확인 실행 명령
- `git diff --check` (공백 오류 없음 확인)
- `git diff --stat` (수정 파일 목록 확인)
- `git status --short` (워킹트리 상태 확인)

### 4. 다음 단계 예정
- Master 검토 피드백 반영 후 최종 검증
- roadmap에 Epic 22(`announcements`) 편입 확인
- `docs/breakdown-research/announcements.md` 작성
- `docs/breakdown/announcements.md` 초안 작성
- backend·frontend 구현 워커 배정 전 Phase 0 승인 대기

### 5. 2026-10-23 Master 검토 1차 수정 로그
- spec: GT-01~GT-07 게이트 선택지 정리, AC와 미결정 사항 구분, "soft delete" 용어 금지
- architecture: SQL 구현 후보 명시, SecurityConfig matcher 확인 필요, 불필요한 제약 제거, status+deleted_at 동시 존재 가정 금지
- research: 기존 결과 유지, 인용 패턴 수정
- 전각/반각 문구 통일, 영문 혼입(chius 등) 제거

### 6. 2026-10-23 Master 검토 2차 수정 로그 (후속 정정)
- breakdown-research: 구체 API endpoint 목록, dismissal 영구 숨김, Flyway V26, priority=0 운영 등을 확정 사항에서 제거 → 제안 후보로 표시, 해당 결정 게이트(GT-01/02/04/07)와 연결
- breakdown: SF-7 soft/논리 삭제를 확정 요구에서 제거 → 처리 방식 미정(대상 검토)으로 변경, GT-07과 연결
- breakdown: "모든 게이트 해결 후 전체 구현 적용" 문구 수정 → GT-01/02/04/07에 실제로 종속된 항목만 기다림, GT-03/05/06 확정 동작은 BLOCKED 아님 명시
- breakdown: trailing whitespace 제거
- work-log: 과거 "GT-01~07 전부 미해결" 문구를 현재 상태처럼 남기지 않음 → 수정 이력(1차/2차)과 현재 상태(판단 결과) 분리

---

## 2026-10-23 — planning 워커: 전체 게이트 확정에 따른 문서 전면 정정 (Master 검증 실패 후 재작업)

### 1. 배경
이전 완료 보고에서 GT-01, GT-02, GT-04, GT-07을 '미해결/대기'로 남겼으나, 사용자가 2026-10-23에 모든 게이트를 확정함. Master 검증에서 "미해결 gate가 잘못 남아 있다"는 지적 받아 전면 정정함.

### 2. 사용자 최종 확정 요구사항 (2026-10-23) — 게이트 없음

**확정 사항 (모든 게이트 해제):**
1. **GT-01 기간 경계**: KST 날짜 기준, 시작일과 종료일 양쪽 날짜 포함 (`LocalDate`, 종료일 23:59:59까지)
2. **GT-02 다시 보지 않기 저장소**: 이메일 계정(기존 안정적 `account_id`) 기준 서버 DB 저장 — 같은 계정의 다른 기기에서도 숨김. 브라우저 localStorage/cookie 단독 저장은 요구 불충분
3. **GT-03 비로그인 팝업**: 로그인 사용자만 (확정)
4. **GT-04 복수 공지 노출**: 기간 겹치는 공지 전부 순차 표시. 하나만 선택하거나 priority/우선순위 입력 필드 추가하지 않음. 정렬은 시작일 순(가장 이른 것부터) 기본
5. **GT-05 홈 첫 진입**: 로그인 후 대시보드 최초 진입 (확정)
6. **GT-06 닫기 재표시**: 일반 닫기는 이번 방문에서만 닫히고 다음 재접속 때 다시 뜸 (확정)
7. **GT-07 관리자 URL**: 저장소의 기존 admin route 패턴 따름 (`/admin/announcements`). 경로 원칙 확정으로 gate 대기 상태 해제
8. **IPO 독립성**: 공지사항 기능과 데이터는 IPO와 독립

### 3. 여섯 문서 전면 정정 내용

| 문서 | 주요 정정 내용 |
|------|----------------|
| `docs/research/announcements.md` | §6·§7·§9·§12·GT표: GT-01/02/04/07을 '확정'로 변경, 미결정 표현 제거, GT-02는 계정 ID 기준 DB 저장 명시, GT-04는 전부 순차 표시·priority 불필요 명시, GT-07은 기존 패턴 `/admin/announcements` 확정 |
| `docs/spec/announcements.md` | 사용자 목표·In Scope·AC·비범위·엣지케이스·§7: priority 컬럼/입력 제거, start_at/end_at을 DATE(KST)로 변경, dismissal DB 저장 확정, popup API 전부 반환으로 변경, 정렬 start_at ASC, 게이트 표 전체 '확정/게이트 불필요'로 변경 |
| `docs/architecture/announcements.md` | DB 스키마: start_at/end_at을 DATE로, priority 컬럼 제거, dismissal 테이블 확정 생성, API: popup 전부 반환, dismiss API 확정, SecurityConfig 경로 확정, 플로우·정렬·기간 경계 모두 확정값 적용, Flyway V26/V27 확정, 게이트 표 전체 '확정/게이트 불필요' |
| `docs/breakdown-research/announcements.md` | 헤더: 미결정 정책 후보 제거, 확정됨에 GT-01~07 모두 추가. SF 후보: priority 제거, DATE 타입 명시, 게이트 의존 제거. 의존관계: 게이트 대기 문구 제거. 리스크: 게이트 미해결 행 제거. 체크리스트: 게이트 확인 완료로 변경 |
| `docs/breakdown/announcements.md` | 헤더: 상태 'Phase 0 검토 완료 / 구현 대기'. 특이사항: 게이트 대기 문구 제거, BLOCKED SF 없음 명시. SF-1~8: 의존 게이트 열 '(게이트 없음)'로 변경, 상태 '구현 가능'로 변경. 의존관계: 게이트 대기 문구 제거, 확정값 적용 명시. 체크리스트: 게이트 확인 완료·BLOCKED 없음 명시 |
| `docs/work-log/announcements.md` | 본 항목 추가 (append-only) |

### 4. 검증 실행 명령 및 결과

```bash
git diff --check -- docs/research/announcements.md docs/spec/announcements.md docs/architecture/announcements.md docs/breakdown-research/announcements.md docs/breakdown/announcements.md docs/work-log/announcements.md
git diff --stat HEAD -- docs/research/announcements.md docs/spec/announcements.md docs/architecture/announcements.md docs/breakdown-research/announcements.md docs/breakdown/announcements.md docs/work-log/announcements.md
git status --short
```

### 5. 정정 후 상태 요약

| 게이트 ID | 내용 | 상태 | 비고 |
|-----------|------|------|------|
| GT-01 | 기간 경계 | **확정** | KST 날짜, 양쪽 포함 |
| GT-02 | 다시 보지 않기 저장소 | **확정** | 계정 ID 기준 서버 DB 저장 |
| GT-03 | 비로그인 팝업 | **확정** | 로그인 사용자만 |
| GT-04 | 복수 공지 노출 | **확정** | 전부 순차 표시, priority 불필요 |
| GT-05 | 홈 첫 진입 | **확정** | 로그인 후 대시보드 최초 진입 |
| GT-06 | 닫기 재표시 | **확정** | 재접속 시 재표시 |
| GT-07 | 관리자 메뉴 경로 | **확정** | 기존 admin route 패턴 `/admin/announcements` |

**미해결 제품 gate 수: 0개**
**요구사항 때문에 구현이 막히는 SF 수: 0개 (SF-1~8 모두 '구현 가능')**

---

## 2026-10-23 — Phase 0 문서 잔여 모순 및 오래된 마이그레이션 번호 정정 (Master diff review 후속)

### 1. 정정 대상 및 내용

| 문서 | 정정 내용 |
|------|-----------|
| `docs/research/announcements.md` | §6: localStorage/cookie 단독 저장 후보 제거, 계정 ID 기준 서버 DB 저장으로 확정. §7: 여러 우선순위 후보 테이블 제거, 전부 순차 표시·시작일 순 정렬로 확정. |
| `docs/spec/announcements.md` | SC-U-01: '또는 세션 시작' 문구 제거. 엣지케이스 §6: localStorage fallback 문구 제거, 메모리 변수 세션 유지로 단순화. 확정 요약: '구현 금지' 모순 문단 삭제, 기존 `RecordStatus` + `deleted_at` 패턴 준용으로 통합. |
| `docs/architecture/announcements.md` | §5.3: sessionStorage 명시 제거, '이번 방문만 닫히고 재방문 시 재표시' 관측 가능 동작만 기술. §5.2: localStorage 무시 문구를 DB dismissal 계정 단위 권위적 동작으로 단순화. Flyway 마이그레이션: V26/V27 → V33/V34로 수정, 구현 직전 tree 재확인 문구 추가. |
| `docs/breakdown-research/announcements.md` | §3: localStorage 패턴을 '팝업 닫기용'에서 '투자자 선택용'으로 수정. §5 체크리스트: 마이그레이션 번호 V33/V34로 수정. |
| `docs/breakdown/announcements.md` | 특이사항 4: localStorage 패턴을 팝업 닫기 재사용에서 제외. 체크리스트: 마이그레이션 번호 V33/V34로 수정. |

### 2. 검증 결과
- `git diff --check`: PASS
- `git status --short`: 7개 허용 파일만 수정됨
- 미해결 제품 gate: 0개
- BLOCKED SF: 0개

### 3. 다음 단계
- Master 승인 후 구현 SF 배정 진행

---

## 2026-10-23 — 잔여 모순 최종 정정 (Master 2차 검토 후속)

### 1. 배경
앞선 정정 로그(112-133행)에서 "모든 항목 해결"로 기록했으나, 실제로는 다음 항목들이 남아 있었음:
- spec §6 프라이빗 모드 행: 메모리 변수 구현 지시 잔존
- architecture §5.1: sessionStorage/메모리/키/범위 등 저장소 구현 상세 잔존
- architecture §4.4: 미사용 정렬 대안 표(생성일/관리자 순서/종료임박) 잔존
- architecture §9: '구현 금지' 모순 문단과 hard delete 별도 gate 문구 잔존
- breakdown-research §1: Flyway V26/V27 잔존

### 2. 이번 정정 내용
| 문서 | 정정 내용 |
|------|-----------|
| `docs/spec/announcements.md` | §6 프라이빗 모드 행: 저장 기술 언급 제거, 관측 동작만 기술(이번 방문만 유지, 재방문 초기화; 다시보기안함은 DB 영구) |
| `docs/architecture/announcements.md` | §5.1 전체 재작성: 저장소/키/범위 제거, 관측 동작만 남김. §4.4 표 삭제, 확정 한 줄만. §9 '구현 금지' 문단 삭제, 기존 RecordStatus(ACTIVE/INACTIVE/CANCELLED) + deleted_at 패턴 준용으로 통합. |
| `docs/breakdown-research/announcements.md` | §1 Flyway: V26/V27 → V33/V34(구현 직전 재확인). "닫기" 액션: '세션 범위 휘발성 상태' → '구현 저장소 비특정, 관측 동작만 요구' |
| `docs/work-log/announcements.md` | 본 부록 추가 (과거 기록 변경 없음) |

### 3. 검증 결과
- `git diff --check`: PASS (4개 허용 파일)
- `git status --short`: 4개 허용 파일만 수정됨
- 전체 검색: localStorage/sessionStorage/메모리 저장 구현 지시(닫기 동작 관련) 없음, V26/V27 없음, 우선순위 대안 표 없음, 모순 삭제정책 없음
- 미해결 제품 gate: 0개
- BLOCKED SF: 0개