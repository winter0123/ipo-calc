# 세부 기능 분해: 공모주 일정 정정·철회 후속 (ipo-schedule-reconciliation-followup)

> 구현은 미커밋(V43 포함). 운영 적용은 사용자 승인 대기. 상태는 실제 diff 기준.

## 1. SF 목록

| SF | 제목 | 범위 | 상태 |
|----|------|------|------|
| SF-1 | 철회 감지·멱등성 | processCompany rm철 분기, withdraw() 사전 체크, SKIPPED_WITHDRAWN 재생성 차단 | 코드 완료·미커밋, Master 검증 PASS |
| SF-2 | 일정 보호·병합 회귀 | mergeSupplementalSchedule 추적값 보호, protectListingDate, listingOpenPrice 초기화, 프런트 phase/재조회 | 코드 완료·미커밋, Master 검증 PASS |
| SF-3 | 운영 적용 | V43 배포·마이그레이션 후 id=20 철회, GET 제외·재생성 없음 확인 | 미실행(승인 대기) |

## 2. SF 상세

### SF-1 (완료, 미커밋)
- IpoSyncService.processCompany(): rm철이면 DS006 detail을 원공시 rcept_no 일치분으로 선택, 매칭 실패 시 recordFailure 후 계속
- IpoSyncPersistence.withdraw(): receipt/start null→false, 이미 철회→true(멱등), ACTIVE + syncSource!=null + subscriptionStartDate 일치만 철회
- AC: withdrawnReceiptMustMatchBeforeDeactivation, differentReceiptCannotWithdrawAnotherOffering, withdrawnSelectsDetailByOriginalReceiptNotLatest, withdrawnWithRealSbdParsingSelectsCorrectDetail, withdrawalMatchesOfferingAndBlocksSameReceipt, withdrawIsIdempotentWhenAlreadyWithdrawn (Master 확인 시 PASS)

### SF-2 (완료, 미커밋)
- Ipo.mergeSupplementalSchedule(): listingDate는 null이거나 ==last_imported_listing_date일 때만 정정, 정정 시 listingOpenPrice=null
- IpoService: 관리자 listingDate 입력 시 protectListingDate()
- 프런트 getCurrentPhase: 한국날짜 기준 당일부터 listed, listingDate null → listing-unknown('상장일 미정'), 한국 자정+화면복귀 재조회
- AC: importedDateChangesButManualAndLegacyDatesArePreserved 등 백엔드 77건, 프런트 36건 (Master 확인 시 PASS)

### SF-3 (미실행)
- 범위: V43 적용·배포 후 id=20 1건만 공식 원공시 근거로 철회, GET 제외·반복 실행 재생성 없음 확인
- 나머지 36건: 공식 일정 대조 미실시 → 추가 보정 근거 미확인
- AC-08: 운영 적용 후 id=20 GET 제외 확인, 반복 동기화로 재생성 없음

## 3. 작업 순서

1. SF-1/SF-2 커밋·배포 (승인 후)
2. V43 마이그레이션 적용
3. 관리자 동기화 또는 04:30 KST 배치 실행 → id=20 철회 확인
4. GET에서 id=20 제외 확인, 반복 실행 재생성 없음 확인

## 4. 위험 요소

| 위험 | 완화 |
|------|------|
| 출처불명 날짜 자동 정정 | 추적값 규칙(위) |
| 수동 레코드 오철회 | syncSource==null 차단 |
| 운영 쓰기·배포 무승인 실행 | 승인 전까지 실행 금지. 근거 확인은 완료 상태 |
