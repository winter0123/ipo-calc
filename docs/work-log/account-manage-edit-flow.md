# 계좌 관리 메뉴·폼 편집 흐름 (account-manage-edit-flow) work-log

> append-only. 2026-11-11 작업분.

## 2026-11-11 구현·검증
- 브랜치 `winter0123/account-manage-edit-flow`. Master 미커밋 변경(docs/current-status.md, docs/daily-log/2026_11_11.md, .playwright-cli/, docs/operations/simwon-withdrawal-procedure.md) 보존, 손대지 않음.
- 조사: `BrokerSyncService`/`ApiCredentialService`/`ApiCredentialAssembler` 모두 `SecuritiesAccount.getAccountNumber()`를 호출마다 DB에서 복호화. 토큰만 캐시, 계좌번호 캐시 없음 → 번호 교체 후 다음 브로커 호출에 새 번호 사용. 임의 자격증명 삭제·리셋 없음.
- 백엔드: `SecuritiesAccountService.update` 409(기존 번호 교체) 제거(사용자 명시 승인). 미전달=무변경·공백=400(지우기 미지원) 유지, 락/소유권/마스킹/id 보존 유지. 테스트 2건(updateSecuritiesAccountWithExistingAccountNumberChangesNumberAndKeepsId, patchAccountNumberWhenAlreadyExistsChangesNumberKeepingSameId) 200 계약으로 갱신 + 동시성 케이스를 concurrentPatchNullToNumberBothSucceedUnderRowLock(둘 다 200)으로 갱신.
- UI: SecuritiesManage `view` 단일 state 전환, AccountActionGroup에서 삭제 제거·번호 계좌 수정만·계좌추가 1개·미입력 채움 대상 `미입력 #<id>` 명시, openAdd 채움/생성 분기. AccountForm: 기존 번호는 마스킹 안내+빈칸, 번호 입력 저장 시 ConfirmDialog(이력 유지 경고) 확인 전 PATCH 0회/취소 입력 유지/확인 후 1회, 수정 모드 저장/취소/삭제 버튼, 삭제 409 시 서버 메시지 폼 표시·폼 유지.
- 검증 결과(실제 실행): `scripts/db-slot.sh reset 1` → `cd backend && DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test checkstyleMain checkstyleTest spotlessJavaCheck bootJar --rerun-tasks --console=plain` BUILD SUCCESSFUL, 1197 tests 0 failures/errors. 첫 실행에서 `spotlessJavaCheck`(Controller Javadoc 줄바꿈)·`checkstyleMain`·동시성 테스트 1건 FAIL → 수정 후 재실행 PASS. UI: `pnpm run test` 108 files/962 tests PASS, `typecheck` PASS, `lint` 오류 0(기존 경고 4건, 무관한 allocation-register/IpoCalendar), `format:check`(수정 파일 4건 `--write` 후 PASS), `build` PASS, `git diff --check` PASS.
- 모의 로컬 백엔드(슬롯1, APP_SIGNUP_MODE=OPEN, dummy 데이터) + Playwright로 375px/1280px 확인: 미입력 메뉴 `["계좌추가"]`+`미입력 #<id>`, 번호 계좌 메뉴 `["수정","계좌추가"]`, 수정 폼 삭제 버튼 노출, 확인 다이얼로그 문구·취소 후 입력 유지(1234-5678-90), console error 0건. 캡처 14장 `docs/screenshots/account-manage-edit-flow/`.
- 남은 것: git commit/push 및 배포 NOT_RUN(워커 금지, Master 몫). 운영 DB 마이그레이션 없음(스키마 변경 0). 슬롯1은 reset 후 반환.

## 브로커 번호 캐시 조사 결론 (보고)
- 자격증명(앱키/시크릿/토큰)은 계좌번호와 무관하게 별도 저장·캐시되며, 번호는 매번 DB에서 복호화해 사용. stale cache 위험 없음. 단, 번호 교체 시에도 기존 청약·매도 이력은 같은 accountId에 남으며 브로커 연동은 새 번호로 동작함 — 폼 경고 문구가 이 의미를 사용자에게 알림.
