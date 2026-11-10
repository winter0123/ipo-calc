# 연구: 자동 IPO 재점검 (automatic-ipo-reconciliation)

> 2026-11-10 워커 직접 검증 기준. 운영 쓰기·배포·코드 수정 없음. 공식 OpenDART 가이드 2건 + 라이브 호출 2회(메모리 내 키, 출력 없음) + 소스 코드·마이그레이션 확인.

## 1. 공식 API 확인 (DS001 공시검색, 2026-11-10)

출처: https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001

- `corp_code`는 선택(STRING(8)). "고유번호(corp_code)가 없는 경우 검색기간은 3개월로 제한" — 반대로 corp_code를 지정하면 3개월 제한이 적용되지 않는다. Simwon 실측: bgn_de=20260101, end_de=20261110(10개월) → status 000, page_no 1, total_page 1, total_count 2.
- `pblntf_detail_ty=C001`(증권신고-지분증권) 필터를 걸어도 철회신고서가 함께 반환된다(Simwon 실측: 원공시 20261012000149 rm=철 + 철회신고서 20261030000200 rm=빈값, 2건). 클라이언트 `isEquitySecuritiesReport()`가 report_nm 정규화 후 "증권신고서(지분증권)"만 남긴다 → 철회신고서 제외, 원공시(rm=철) 보존(코드 확인).
- 페이지네이션: page_count 1~100(기본 10, 최대 100), page_no 1~n, 응답에 page_no/page_count/total_count/total_page. 기존 `OpenDartCorpDisclosureClient`는 PAGE_COUNT=100 + total_page 루프 구현 일치.
- rm=철 의미(공식 가이드): "본 보고서는 철회(간주)되었으니 관련 철회신고서를 참고하시기 바람".
- 요청 제한 020: "일반적으로 20,000건 이상의 요청에 대해 발생" — 실제 한도는 다를 수 있음. 인증 010/011/012/901 → 인증 예외, 020 → 요청 제한 예외.

## 2. DS006(지분증권 주요정보) 확인

출처: https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS006&apiId=2020054 (`/api/estkRs.json`)

- rcept_no 지정 조회가 아니라 corp_code + bgn_de/end_de(접수일자) 조건 조회다.
- Simwon 실측(20261001~20261110): status 000, 일반사항 원공시 1건만 — rcept_no=20261012000149, sbd=2026년 11월 05일, pymd=2026년 11월 09일.
- 응답은 group[] 배열, rcept_no별로 묶인다. 호출자는 원공시 rm=철인 경우 rcept_no 일치분만 채택해야 한다(방어 보완, Simwon 응답이 원인은 아님).

## 3. 기존 코드 확인 사실

- 04:30 KST `IpoSyncScheduler.syncDaily` → `IpoSyncService.sync(daysBack)`(기본 90일) → `runExclusively`/`runLock`(ReentrantLock, 동일 스레드 재진입) 안에서 DS001 수집 → 회사별 DS006 → `IpoSyncPersistence.persist/withdraw` → `IpoScheduleMergeService.merge`.
- `IpoSyncScheduler.runSync`는 aborted+retryable 시 5/10/20분 백오프로 최대 3회 재시도. 정상 종료(부분 실패 포함)·인증오류·020은 재시도 안 함.
- `IpoScheduleMergeService.merge`: listingDate는 null이거나 현재값==last_imported_listing_date일 때만 정정(정정 시 listingOpenPrice=null, lastImportedListingDate=신규값). 수동/출처불명(추적값 null) 보호. ustockplus 과거30~미래59일 병합.
- `withdraw()`: ACTIVE + syncSource!=null + subscriptionStartDate 일치만 철회(INACTIVE+deletedAt+withdrawnReceiptNo). 같은 receipt 이미 철회 → 멱등 true. `persist()`는 existsByCorpCodeAndWithdrawnReceiptNo로 재생성 차단. 다른 receipt 재추진 허용.
- `IpoDetail.sourceRceptNo`: `findLatestOfferingDocument`가 투자설명서(PROSPECTUS) 우선, 없으면 증권신고서(지분증권) 최신을 채택 — 원공시 접수번호와 다를 수 있다.
- 상장 지난 ACTIVE 자동공모도 `findByCorpCodeAndStatusAndDeletedAtIsNull(...ACTIVE)`에 잡히므로 점검 대상에서 누락 없음. 현재 90일 밖 점검 경로는 없다.
- 소프트삭제 영향(실코드): GET /api/ipos(`findAllByDeletedAtIsNull...`)·청약등록 `validateIpoExists`·`IpoDetailService`·`IpoDetailSyncService`는 soft-deleted 제외(의도). 내역조회 `InvestmentHistoryReadModelLoader`(findById/findAllById)·`DashboardAggregationService`(findAllById)·투자자별 수익(findAllById)은 포함(정상). 단건 수익 `ProfitCalculationService.calculateForSale`와 `AllocationService`는 `findByIdAndDeletedAtIsNull` → ipo=null(단건 ipoName/listingDate 누락, allocatedAmount null 시 fixedPrice 대체 불가).
- V43(`last_imported_listing_date`, `withdrawn_receipt_no`)은 PR500 배포 완료. 수정 금지.
- 배포는 systemd JAR 단일 인스턴스(2026-11-10 daily-log 기준). runLock은 JVM 인메모리라 인스턴스 간 배타 불가.

## 4. 미해결·제약 (2026-11-10 Master 검토 보정 반영)

- 원공시 receipt 저장수단이 없다는 기존 판단은 오류였다. `IpoSyncPersistence.persist`의 DS006 `detail.getRceptNo()`는 이미 존재하고, SF1에서 ipos에 `source_receipt_no`를 추가해 새 수집부터 저장하기로 했다(기존 `ipo_details.source_rcept_no`는 투자설명서 우선 정책이라 원공시 receipt로 맹신하지 않는다).
- 동일 회사(corp_code) ACTIVE 공모 2건 동시 보유는 DB 부분 유니크(`uk_ipo_corp_code_active`)가 막고 있었으나, 재추진(다른 receipt 신규 ACTIVE 건 허용)과 FR-11 감지를 위해 V44에서 `(corp_code, source_receipt_no) WHERE ACTIVE` 유니크로 대체하기로 했다. 동일 이름+시작일 중복은 V6 `uk_ipo_name_start_date_active`가 계속 차단한다.
- 나머지 36건 공식 일정 대조는 이번 범위 밖(근거 없음).
- 운영 쓰기·배포·SF1 구현은 별도 단계. 본 문서는 계약 확정용.

## 5. 공식 URL 목록

- DS001 공시검색: apiGrpCd=DS001&apiId=2019001 / DS006 지분증권 주요정보: apiGrpCd=DS006&apiId=2020054
