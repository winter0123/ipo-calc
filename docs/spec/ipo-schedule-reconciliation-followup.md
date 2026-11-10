# 스펙: 공모주 일정 정정·철회 후속 (ipo-schedule-reconciliation-followup)

> 기준: research의 확인 사실. 대규모 새 화면·API 없음. 기존 보호 로직 유지 + 철회 감지 결함 최소 수정.

## 1. 사용자 목표

- 출처 불명 기존 listingDate는 자동 정정하지 않는다(관리자 수동·출처불명 값 보존).
- ustockplus 보조 일정은 추적값(last_imported_listing_date)=현재값일 때만 정정하고, 정정 시 listingOpenPrice=null로 초기화해 재수집을 유도한다.
- listingDate가 null이면 보조 일정으로 채울 수 있다.
- SimwonAmericaCorp.(id=20)을 공식 원공시 근거로 철회 처리: INACTIVE+deletedAt, withdrawnReceiptNo=원공시(20261012000149) 저장, 투자 이력 보존.

## 2. 기능 요구사항 (FR)

| ID | 요구사항 |
|----|---------|
| FR-01 | mergeSupplementalSchedule: listingDate는 null이거나 현재값==last_imported_listing_date일 때만 정정. 추적값이 null(출처불명·수동)이면 보존 |
| FR-02 | 관리자 listingDate 입력 시 IpoService가 protectListingDate() 호출 → last_imported_listing_date=null, 이후 자동 정정 차단 |
| FR-03 | 보조 일정 listingDate 정정 시 listingOpenPrice=null, last_imported_listing_date=신규값 |
| FR-04 | rm=철인 원공시 감지 → withdraw(corpCode, detail). 같은 receipt 재수집은 SKIPPED_WITHDRAWN으로 차단, 반복 철회는 멱등(save 없이 성공), 다른 receipt는 재추진 허용 |
| FR-05 | withdraw 조건: corpCode + ACTIVE + syncSource!=null + subscriptionStartDate 일치. 하나라도 불일치면 자동 철회하지 않고 recordFailure |
| FR-06 | 프런트: 한국날짜 기준 당일부터 상장완료(listed), listingDate null이면 '상장일 미정', 한국 자정과 화면복귀(focus/visibilitychange) 시 ipoKeys.list 재조회 |
| FR-07 | 시초가는 listing_date<오늘 대상. 앱 내 재시도 없이 다음 배치가 D+1 이후 재조회 |

## 3. Acceptance Criteria (AC)

| AC | 검증 조건 |
|----|----------|
| AC-01 | IpoScheduleReconciliationTest.importedDateChangesButManualAndLegacyDatesArePreserved PASS |
| AC-02 | IpoScheduleReconciliationTest.withdrawalMatchesOfferingAndBlocksSameReceipt PASS |
| AC-03 | IpoScheduleReconciliationTest.withdrawIsIdempotentWhenAlreadyWithdrawn PASS |
| AC-04 | IpoSyncServiceTest.withdrawnReceiptMustMatchBeforeDeactivation / differentReceiptCannotWithdrawAnotherOffering PASS |
| AC-05 | OpenDartCorpDisclosureClientTest.searchByDateRange_filtersOutWithdrawalNoticeKeepsOriginalWithRmIron PASS |
| AC-06 | Master 슬롯4 관련 4클래스 77건 / checkstyleMain / checkstyleTest / bootJar PASS (Master 직접 확인) |
| AC-07 | 프런트 36건 / typecheck / lint / build PASS (Master 직접 확인) |
| AC-08 | (운영 적용 후 확인) id=20이 GET에서 제외되고, 반복 동기화 실행에도 재생성되지 않음 |

## 4. 비범위

- 신규 관리자 화면·API, 배치 스케줄 변경, 회사 단위 영구 차단, DS001/DS006 외 데이터 소스
- 운영 쓰기·배포는 사용자 승인 후 별도 실행
