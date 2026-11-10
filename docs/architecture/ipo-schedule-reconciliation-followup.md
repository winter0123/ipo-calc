# 아키텍처: 공모주 일정 정정·철회 후속 (ipo-schedule-reconciliation-followup)

> 기존 모듈 확장만. 새 모듈·레이어 없음. 데이터 플로우와 보호 규칙 중심.

## 1. 데이터 플로우

```text
OpenDART DS001 → searchRecent/searchByDateRange
  → 지분증권 필터(isEquitySecuritiesReport) 선행, 철회신고서 제외, 원공시(rm=철) 보존
  → corp_code별 최신 rcept_dt 1건 디듀프
DS001 건 → IpoSyncService.processCompany()
  → stock_code 있으면 유상증자 간주, 건너뜀
  → DS006 조회(회사/기간 조건)
  → rm=철이면 detail을 원공시 rcept_no 일치분으로 선택(방어 보완). 실패 시 recordFailure 후 계속
  → 일반 건 → 최신 rceptNo 선택
rm=철 → IpoSyncPersistence.withdraw(corpCode, detail)
  → receipt/start null → false / 이미 철회됨 → true(멱등, save 없음)
  → ACTIVE + syncSource!=null + subscriptionStartDate 일치만 철회 → INACTIVE + deletedAt + withdrawnReceiptNo
일반 건 → IpoSyncPersistence.persist()
  → existsByCorpCodeAndWithdrawnReceiptNo → SKIPPED_WITHDRAWN(재생성 차단)
  → syncSource==null(수동) → SKIPPED_MANUAL → CREATED/UPDATED
OpenDART 루프 완료 → IpoScheduleMergeService.merge(today)
  → ustockplus 과거30일~미래59일 fetch, stockCode/회사명 정규화 매칭(정확히 1건만)
  → Ipo.mergeSupplementalSchedule(): stockCode는 null 아니고 현재값과 다르면 갱신,
    allotment/refund는 null만, listingDate는 null이거나 ==last_imported_listing_date일 때만 정정
    (정정 시 listingOpenPrice=null, lastImportedListingDate=신규값)
```

## 2. 데이터 모델 (V43, 미커밋·운영 미배포)

V43__ipo_schedule_provenance.sql: ipos에 last_imported_listing_date(DATE, 추적값)와 withdrawn_receipt_no(VARCHAR(20)) 2개 컬럼 추가.

## 3. 시초가 수집 경로

시초가 수집(IpoOpenPriceSyncService)은 Ipo.updateListingOpenPrice()만 호출하며 protectListingDate()를 호출하지 않는다. protectListingDate()는 관리자 listingDate 입력(IpoService.update)에서만 호출된다. 대상: ACTIVE + stockCode NOT NULL + listing_date < 오늘 + listingOpenPrice IS NULL. 앱 내 재시도 없이 매일 14:30 KST 배치가 D+1 이후 재조회(일정 동기화는 04:30 KST).

## 4. 트랜잭션 경계

| 작업 | 범위 | 예외 시 |
|------|------|---------|
| 회사별 DS006→persist/withdraw | 건별 독립(TransactionTemplate) | rollback-only + recordFailure, 계속 진행 |
| ustockplus 병합 | 건별 독립(TransactionTemplate) | WARN 로그만, 다음 종목 계속 |

## 5. 리스크와 완화

| 리스크 | 완화 |
|--------|------|
| rm철인데 DS006 원공시 receipt 매칭 실패 | recordFailure 후 계속, 자동 변경 보류 |
| 수동 레코드 오철회 | syncSource==null이면 withdraw false |
| 출처불명 listingDate 자동 정정 | 추적값==현재값일 때만 정정 |
| 같은 receipt 철회 후 재생성 | SKIPPED_WITHDRAWN 차단 |

## 6. 검증 포인트

Master 슬롯4: 관련 4클래스 77건 / checkstyleMain / checkstyleTest / bootJar PASS(확인). 프런트 36건 / typecheck / lint / build PASS(확인). 본 문서 작성 워커는 코드를 실행하지 않았다.
