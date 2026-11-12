# 계좌 관리 메뉴·폼 편집 흐름 breakdown (account-manage-edit-flow)

단일 SF로 구현(규모 작음). 순서: 백엔드 서비스 → UI state/메뉴 → 폼 → 테스트/증적/문서.

## SF-1 서버 409 교체 제한 제거
- 범위: `SecuritiesAccountService.update`의 "기존 암호문 존재=409" 분기 제거, DTO/Javadoc/Controller 주석, Entity 주석(최초 1회→설정/교체).
- 비범위: DB 마이그레이션, null 지우기 지원, 삭제 409, 브로커 모듈.
- AC: 기존 번호 보유 계좌 PATCH(신규 번호) → 200 + 같은 id + mask(신규번호) + DB 암호문이 신규번호. 미전달=무변경, 공백=400.
- 테스트: SecuritiesAccountControllerTest 교체 제한 2건 → 200 계약으로 갱신, 동시성 케이스(null→번호 2건)는 둘 다 200·마지막 쓰기로 갱신.

## SF-2 UI 단일 state(View) 전환 + 메뉴/삭제 재구성
- 범위: SecuritiesManage `view` 단일 state(idle/menu/create/edit), AccountActionGroup "번호 계좌 수정 + 회사 계좌추가 1개, 삭제 제거, 미입력 시 `미입력 #<id>` 대상 표시", 계좌추가 → openAdd(미입력 채움/새 생성 분기).
- 비범위: 스타일 토큰 변경, entities 스키마 변경.
- AC: AC-1~AC-8. 토글/ESC/저장·취소·삭제 후 idle로 정리.

## SF-3 수정 폼 번호 교체 UX + 인폼 삭제 + 확인 다이얼로그
- 범위: AccountForm — 기존 번호 있으면 빈 입력칸+마스킹 안내, 저장 시 번호 있으면 ConfirmDialog 확인 전 PATCH 0회, 확인 후 1회, 취소 시 입력 유지. 수정 모드 저장/취소/삭제 버튼, 삭제는 ConfirmDialog(409 → 폼 유지 + 서버 메시지). 미입력 채우기 폼 동일 버튼.
- 비범위: 마스킹 값 저장(금지), 생성 폼 삭제 버튼.
- AC: AC-4·AC-9·AC-10·AC-12.

## SF-4 검증·증적·문서
- 검증: `scripts/db-slot.sh reset 1` → `cd backend && DB_NAME=ipo_slot_1 ... ./gradlew test checkstyleMain checkstyleTest spotlessJavaCheck bootJar --rerun-tasks` PASS(1197건, 0 실패). UI `pnpm run test`(962건)/`typecheck`/`lint`(기존 4경고)/`format:check`/`build` PASS. `git diff --check` PASS.
- 증적: 모의 로컬 백엔드(슬롯1, dummy 시드)로 375/1280 화면 확인, `docs/screenshots/account-manage-edit-flow/`에 캡처. console error 0.
- 문서: 본 research/spec/architecture/breakdown/work-log 5종(각 40줄 이내).

## 주의
- `./gradlew spotlessApply` 금지(수정 파일만 `spotlessJavaCheck` 관점으로 교정).
- 마스터 문서·current-status 편집 금지(Master 소유).
