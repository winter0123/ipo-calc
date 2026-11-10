# 작업 로그: 자동 IPO 재점검 (automatic-ipo-reconciliation)

> append-only. 2026-11-10 SF0(계약 확정) 기록. 제품 코드·current-status·daily-log 미수정. 커밋/push/운영 쓰기/배포 없음.

## 2026-11-10 (SF0)

### 역할·범위

- planning 워커(depth1), 브랜치 winter0123/automatic-ipo-reconciliation. 다른 작업자 변경/untracked 보존(`.playwright-cli/`, `docs/operations/simwon-withdrawal-procedure.md` 미변경).
- 범위: 문서 5개만 작성(research/spec/architecture/breakdown/work-log). 제품 코드·테스트·current-status·daily-log 미수정. 커밋/push 없음. `git diff --check`만 실행.

### 읽기(실코드 확인 목록)

- AGENTS.md, docs/project-agent-guide.md, roadmap/current-status, ipo-schedule-reconciliation-followup 4종(research/spec/architecture/breakdown).
- 백엔드: `IpoSyncScheduler`(04:30 KST, `IpoSyncService.sync(daysBack)`, 재시도 5/10/20분·3회·인증/020 제외), `IpoSyncService`(`runExclusively`/`runLock` ReentrantLock, doSync 2단계: DS001 수집→회사별 DS006→persist/withdraw, mergeSchedule), `IpoSyncPersistence`(persist: SKIPPED_WITHDRAWN/SKIPPED_MANUAL, withdraw: ACTIVE+syncSource!=null+subscriptionStartDate 일치만), `IpoSyncStateManager`(메모리 상태), `Ipo`/`IpoDetail`/`IpoRepository`/`IpoDetailRepository`, `IpoScheduleMergeService`, `DartIpoFilingClient`(findLatestOfferingDocument: PROSPECTUS 우선), `OpenDartCorpDisclosureClient`(PAGE_COUNT=100, total_page 루프, isEquitySecuritiesReport), `OpenDartEquitySecuritiesClient`(estkRs.json, group[] rcept_no별).
- 마이그레이션: V5(ipos), V6(uk_ipo_name_start_date_active 부분 유니크 stock_code IS NULL만), V12(sync_source/last_synced_at), V36(action_audit_logs: actor_admin_id NOT NULL → 배치 기록 재사용 불가), V43(last_imported_listing_date, withdrawn_receipt_no — PR500 배포 완료, 수정 금지). corp_code 유니크 제약 없음 확인.
- 소프트삭제 사용처(실코드): InvestmentHistoryReadModelLoader(findById/findAllById, soft-deleted 포함), DashboardAggregationService(findAllById), ProfitCalculationService.calculateForInvestor(findAllById) vs calculateForSale(findByIdAndDeletedAtIsNull), AllocationService(findByIdAndDeletedAtIsNull), IpoDetailService/IpoDetailSyncService/IpoService/청약등록 검증(findByIdAndDeletedAtIsNull).
- daily-log 2026-11-10: 배포는 systemd JAR 단일 인스턴스(backend Ansible 배포, UI Caddy). runLock JVM 인메모리 전제.

### OpenDART 공식 가이드 확인

- DS001 공시검색: https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001
  - corp_code는 선택(STRING(8)); "고유번호(corp_code)가 없는 경우 검색기간은 3개월로 제한". corp_code 지정 시 3개월 제한 미적용.
  - pblntf_detail_ty=C001(증권신고-지분증권), page_count 1~100(기본 10, 최대 100), page_no 1~n, 응답 page_no/page_count/total_count/total_page.
  - rm 철 의미: "본 보고서는 철회(간주)되었으니 관련 철회신고서(철회간주안내)를 참고하시기 바람".
  - 메시지: 020(요청제한, 일반적으로 20,000건 이상), 010/011/012/901(인증), 013(데이터 없음=정상), 021(회사수 최대 100).
- DS006 지분증권 주요정보: https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS006&apiId=2020054 (`/api/estkRs.json`, corp_code+bgn_de/end_de 기간 조건, rcept_no 지정 조회 불가).

### 라이브 확인 (메모리 내 키 로딩, 값 출력 없음, 최소 2회 호출)

- DS001(corp_code=02038432, bgn_de=20260101, end_de=20261110, C001, page_count=100): status 000, page_no 1, page_count 100, total_count 2, total_page 1. 항목: 철회신고서 20261030000200(rm 빈값) + 원공시 증권신고서(지분증권) 20261012000149(rm=철). C001 필터에도 철회신고서가 반환되며, 클라이언트 isEquitySecuritiesReport() 필터가 철회신고서를 걸러내고 원공시(rm=철)를 보존함을 재확인("디듀프 때문에 Simwon 원공시 사라짐" 과거 주장은 거짓).
- DS006(corp_code=02038432, bgn_de=20261001, end_de=20261110): status 000. 일반사항 원공시 1건만 — rcept_no=20261012000149, sbd=2026년 11월 05일, pymd=2026년 11월 09일.
- 계정한도(020)·인증(010/011/012/901) 오류 없음. 키는 로그/URL/예외/보고에 출력하지 않음. 한도 초과 시 중단한다는 지침대로 추가 호출 없음.

### 산출 문서

- docs/research/automatic-ipo-reconciliation.md (43줄)
- docs/spec/automatic-ipo-reconciliation.md (51줄)
- docs/architecture/automatic-ipo-reconciliation.md (70줄)
- docs/breakdown/automatic-ipo-reconciliation.md (57줄)
- docs/work-log/automatic-ipo-reconciliation.md (본 파일)

### 확정 계약 요약

- 통합: 04:30 KST 기존 `IpoSyncService.sync(90)`에 Step 2(활성 자동공모 재점검) 삽입. `runLock` 재사용, Step 1(신규 공시 수집) 예외·aborted와 격리(FR-08). 상장일 경과·90일 밖 포함 ACTIVE+syncSource!=null 전수.
- 호출 상한: 회사당 DS001(corp_code, 전체기간) 1회 + DS006(corp_code, 전체기간) 1회, corpCache로 같은 회사의 여러 ACTIVE 건에 공유(AC-02/12).
- 철회 특정: DS001 rm에 "철" 포함 → rcept_no == DS006 원공시 상세 → corp_code + subscriptionStartDate 일치하는 ACTIVE 건만 `withdraw()`(INACTIVE+deletedAt+withdrawnReceiptNo). 일정누락(빈 응답/파싱 실패/API 오류)은 recordFailure만, 철회 처리 금지(FR-05). 같은 receipt 재생성 차단(SKIPPED_WITHDRAWN), 다른 receipt 재추진 허용(신규 ACTIVE 건), 이력(Subscription/Allocation/Sale) 보존.
- 일정정정: lastImportedListingDate==현재값 또는 null일 때만 listingDate 반영(정정 시 listingOpenPrice=null), 수동/출처불명(추적값 null) 보호(FR-07). OpenDART 출처 일정(청약일 등)은 기존 `syncFromOpenDart` 규칙 유지.
- 배포 후 자동복구: `ApplicationReadyEvent` 리스너(신규 PostDeployReconciliationRunner)가 `IpoSyncExecutorConfig` 스레드 풀로 비동기 1회 실행. SUCCESS 완료는 `ipo_sync_runs`(run_type='POST_DEPLOY_RECOVER', app_version, status='SUCCESS') 행 존재로 판정. FAILED(부분 실패)는 미완료 → 다음 실행(04:30 배치 또는 재기동)이 전수 재점검(멱등). 관리자 로그인/버튼 사용 금지.
- 최소 스키마(V44 `ipo_sync_runs`): run_type/app_version/status/started_at/finished_at/total_targets/succeeded/failed/failures JSONB + (run_type, app_version) WHERE status='SUCCESS' 부분 유니크 인덱스. action_audit_logs는 actor_admin_id NOT NULL이라 재사용 불가. ipos 컬럼 추가 없음.
- 식별: 기존 `ipo_details.source_rcept_no`는 투자설명서(PROSPECTUS) 우선 채택이라 원공시와 불일치가 일반적 → 원칙 불가(지분증권 신고서 계열 + DS001에서 rm=철 실측 확인 시에만 보조 힌트). ipos source_rcept_no 컬럼은 persist 시점에 receipt 확정 수단이 없어 미채택(근거 architecture §4). 없는 기존 건은 DS006 sbd == subscriptionStartDate AND corp당 ACTIVE 자동공모 정확히 1건일 때만 연결, 그 외 `철회점검: 유일 매칭 실패` 보류.
- 회사 단위 새 공모 upsert: 철회(INACTIVE)는 단건 조회 제외 → 재추진 시 새 건 생성(허용). 철회 없이 동일 corp ACTIVE 2건은 덮어쓰기 금지, `철회점검: 동일 회사 ACTIVE 복수 건` 실패 기록 후 보류(FR-11/AC-05).
- 소프트삭제 내역조회: InvestmentHistory·대시보드·투자자별 수익은 findAllById/findById라 영향 없음. 단건 수익 ProfitCalculationService.calculateForSale·AllocationService는 findByIdAndDeletedAtIsNull → ipo=null(단건 ipoName/listingDate 누락, allocatedAmount null이면 fixedPrice 대체 불가) → SF1에서 findById로 교정(FR-10/AC-09). IpoDetailService 404는 유지(공개 상세=철회 건 비공개). IpoDetailSyncService는 철회 건 상세 동기화 스킵(유지).
- 멀티인스턴스: 현재 systemd JAR 단일 인스턴스 전제. runLock(JVM)은 인스턴스 간 배타 불가 — 동시 실행 시 DART 이중 호출되나 철회 멱등이라 결과 안전, SUCCESS는 부분 유니크 인덱스로 1건 수렴. Kubernetes/다중 인스턴스 전환 시 DB 분산 락 선행 필요(본 계약 미보장).

### 검증 기록

- `git diff --check`: PASS(2026-11-10 실행, 출력 없음).
- 문서 존재·줄수: 5개 생성 확인(43/51/70/57/본파일).
- 백엔드 테스트/빌드: NOT_RUN(제품 코드 미변경이 계약 범위). 구현 검증은 SF1에서 `./gradlew :backend:test`, checkstyleMain/checkstyleTest, bootJar, git diff --check로 수행 예정.
- 운영 쓰기·배포·DB 변경: NOT_RUN(승인 대기). Simwon(id20) 철회는 SF2에서 배포 후 자동복구로 처리 예정(관리자 개입 없음).
- PASS: 문서 5개 생성·공식 URL·근거 파일·AC/테스트 목록 명시. FAIL: 없음. BLOCKED: 없음.

### 남은 작업

- Master 검토 → SF1 구현(사용자 승인 필요) → SF2 운영 적용(V44 배포 후 1회 복구 확인, Simwon id20 GET 제외·반복 실행 멱등 확인).
- 참고: SF1-3의 배포 버전 문자열(`app.release-version`)은 Master가 배포 단위를 확정해야 함(예: 2026-11-10-pr500). V44는 SF1-1에서 작성.

## 2026-11-10 (SF1)

### 역할·범위

- backend 워커(depth1), 브랜치 winter0123/automatic-ipo-reconciliation. 다른 작업자 변경/untracked 보존(`docs/current-status.md`, `docs/daily-log/2026_11_10.md`, `.playwright-cli/`, `docs/operations/simwon-withdrawal-procedure.md` 미변경).
- 범위: ipocatalog 백엔드 구현 + `profitcalculation`·`allocationmanagement`의 IPO 참조 조회 findById 교정(Master 명시 허용) + V44 신규 마이그레이션 + 관련 테스트/픽스처 + 문서 4종(research/spec/architecture/breakdown) 사실 보정(Master 허용). 커밋/push/merge/배포/운영 DB 쓰기 없음. 추가 워커 생성 없음.
- Master 지시(msg_ae86f1e643d3)에 따라 SF1에서 문서 확정사항을 아래처럼 보정했다.

### 구현 요약

- V44__ipo_sync_runs_and_receipt_tracking.sql: ipos에 `source_receipt_no`(신규 수집부터 저장), `last_imported_subscription_start_date`/`last_imported_subscription_end_date`/`last_imported_refund_date` 추가. `ipo_sync_runs`(run_type/app_version/status/started_at/finished_at/total_targets/succeeded/failed/failures JSONB) 생성. SUCCESS 부분 유니크는 `POST_DEPLOY_RECOVER`만(`uk_ipo_sync_runs_recover_success`, app_version 기준). DAILY_RECONCILE SUCCESS는 매일 기록 가능. `uk_ipo_corp_code_active` DROP → `uk_ipo_corp_code_source_receipt_active`(활성 (corp_code, source_receipt_no) 유니크, 재추진 허용)로 대체 — 근거: FR-06 다른 receipt 재추진 허용·FR-11 감지 요구와 V6 `uk_ipo_name_start_date_active`가 여전히 이름+시작일 중복을 차단함을 확인했다.
- 재점검을 신규 수집보다 먼저 실행하도록 `IpoSyncService.doSync`에 `IpoReconciliationService.reconcileActiveOfferings()`를 Step 1로 삽입(공시 upsert가 옛 공모를 덮어쓰지 못하게). FR-08 격리: 재점검 예외는 신규 수집을 막지 않고, 인증/020은 두 단계 모두 중단(4xx가 아닌 동일 키 사용). reason에 `철회점검: ` 접두.
- `IpoReconciliationService`: ACTIVE+syncSource!=null+deletedAt=null 전수(상장일 경과 무관) → 회사별 그룹화 → DS001 전체기간(전 페이지, `pblntf_detail_ty=C001`, 회사별 디듀프 없이 전건) + DS006 전체기간 각 1세트(corpCache 불필요·회사당 1세트씩). rm에 '철' 포함 원공시만 처리, DS006 rcept_no 일치 상세만 채택, 청약 시작일 null/빈 응답/API 오류는 철회 없이 실패 기록. sourceReceiptNo가 있으면 원공시 접수번호 3자 매칭, null(레거시)이면 sbd 유일 매칭(corp당 ACTIVE 1건 전제, FR-11이 복수 건을 선차단). 같은 청약일+다른 접수번호는 `철회점검: 원공시 구분 불가`로 보류. 복수 ACTIVE는 DS 호출 없이 `철회점검: 동일 회사 ACTIVE 복수 건` 보류.
- `IpoSyncRunRecorder`(IpoSyncRun/IpoSyncRunFailure/IpoSyncRunRepository): DAILY_RECONCILE(매일, 전수 성공 시 SUCCESS·부분 실패는 FAILED)·POST_DEPLOY_RECOVER(복구 키 `ipo-reconciliation-v1` 고정) 공통 기록. DAILY 성공 시 복구 키 미완료면 POST_DEPLOY_RECOVER SUCCESS를 함께 기록. FAILED는 완료로 치지 않아 다음 04:30/재기동이 전수 재점검. executor 거부·busy는 기록 없이 무해 종료.
- `PostDeployReconciliationRunner`: ApplicationReadyEvent → `ipoSyncExecutor`로 비동기 제출, 복구 키 SUCCESS 존재 시 스킵, busy/예외는 삼키고 로그만.
- `Ipo.syncFromOpenDart`/`mergeSupplementalSchedule`: 청약 시작/종료·환불·상장일을 `last_imported_*` 추적값 보호 규칙으로 통합(현재값 null 또는 추적값==현재값이면 정정, 정정 시 추적값 갱신·listingOpenPrice=null). 기존 출처 불명 행 백필 없음. 관리자 갱신(`IpoService.update`) 경로에 청약 시작/종료·상장일 protect 호출 추가.
- `IpoSyncPersistence.persist`: 회사별 ACTIVE 자동공모 2건 이상 감지 시 upsert 덮어쓰기 없이 `BLOCKED_MULTIPLE`(FR-11 보류). 신규 건은 sourceReceiptNo/추적값을 함께 저장, 갱신 건은 추적값 보호 규칙으로 정정. `withdraw`는 findAll 기준으로 멱등 유지.
- `ProfitCalculationService.calculateForSale`, `AllocationService.findIpo`: `findByIdAndDeletedAtIsNull` → `findById`(FR-10, 이력 조회/수익계산 보존). `IpoDetailService` 등 공개 경로는 soft-deleted 제외 유지.
- 문서 4종(research/spec/architecture/breakdown) Master 보정 반영(원공시 receipt 저장수단 오류, 복구 키 고정, Success 부분 유니크 POST_DEPLOY-only, 재점검 선행 순서, 청약/환불 추적값 보호).

### 테스트

- 신규: `AutomaticReconciliationTest`(실파서→배치 진입→DB, Simwon 실공시 fixture: corp 02038432/원공시 20261012000149/sbd 2026-11-05/pymd 2026-11-09. ① 90일 밖 포함 철회 ② 재실행 멱등·DAILY 2회 SUCCESS/POST_DEPLOY 1건 ③ 빈 API 결과 삭제 금지 ④ DS006 빈 응답은 철회 아님·재기동 복구 성공 ⑤ 동일 회사 복수 건 보류 ⑥ 원공시 구분 불가 보류 ⑦ 추적값 일치 정정(소스receipt 유지) ⑧ 출처 불명/수동 보호 ⑨ 관리자 입력 보호 ⑩ 이력(Subscription/Allocation/Sale) 보존.
- 신규: `IpoReconciliationServiceTest`(회사당 DS001/DS006 1세트 mock verify, 빈 응답 비철회, 레거시 유일 매칭 철회/불일치 보류, 복수 건 DS 호출 없음), `PostDeployReconciliationRunnerTest`(SUCCESS 존재 스킵/미완료 실행/busy·예외·실행기 거부 무해 종료).
- 신규/확장: `IpoScheduleReconciliationTest`(상장일 정정 시 시초가 재수집 대상 복귀, 환불 추적값 규칙, 복수 ACTIVE persist 차단), `IpoSyncServiceTest`(재점검 예외 격리·인증 중단 시 신규 수집 중단), `ProfitCalculationServiceTest`(철회 건 ipoName/listingDate/fixedPrice 복원), `IpoRepositoryTest`(동일 receipt 활성 유니크 위반 + 다른 receipt 재추진 허용 — uk_ipo_corp_code_active 폐기 반영), `IpoSyncServiceMonthRangeTest` 목 메서드 교체(findAll...List).
- 기존 테스트 전부 유지·통과(IpoScheduleReconciliationTest, IpoSyncSchedulerTest, IpoScheduleMergeServiceTest, IpoSyncServiceTest, ProfitCalculationServiceTest 등 1166개).

### 검증

- `scripts/db-slot.sh reset 1` 후 `cd backend && DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test checkstyleMain checkstyleTest spotlessJavaCheck bootJar --console=plain`: **PASS**(테스트 1166건 실패 0, checkstyle WARN 0, spotlessJavaCheck PASS, bootJar 산출). `git diff --check`: PASS.
- `./gradlew spotlessApply` 미실행. 포맷은 spotless와 동일한 google-java-format 1.22.0을 변경 파일(11개)에만 직접 적용해 확인.
- 슬롯 1 재사용 전 reset 준수, 공유 DB(ipo_calc) 테스트 미사용.

### 사건·주의

- 2026-11-10 SF1 초반에 dev 공유 DB(ipo_calc)의 `ipos_pkey`에 임시 COMMENT probe를 실행한 뒤 즉시 원상태(NULL)로 복구했다(마이그레이션에 없던 주석이므로 회복 가능성 확인). 이후 모든 DB 검증은 슬롯 1에서만 수행했다.
- 기존 `IpoRepositoryTest.saveDuplicateActiveCorpCodeThrowsDataIntegrityViolationException` 기대값은 V44 결정(uk_ipo_corp_code_active 폐기·재추진 허용)과 양립할 수 없어, 동일 receipt 유니크 위반/다른 receipt 허용 테스트로 교체했다(의도적 계약 반영).
- Master 보정 문서 반영으로 `uk_ipo_corp_code_active` 폐기 → 운영 데이터에서 동일 회사 ACTIVE 복수 건이 이미 존재한다면 배포 시 FR-11 보류로 나타나며 자동 덮어쓰기는 되지 않는다. 운영 적용·V44 배포·Simwon(id20) 처리는 SF2(배포 후 확인)로 분리.

### 남은 작업

- Master diff 검증 → 병합 여부 결정(커밋/push/배포 금지 상태 유지). 병합 후 슬롯 회수. SF2(V44 적용·POST_DEPLOY_RECOVER 확인)는 별도 승인 필요.
- 검증 한계: OpenDART 실호출·운영 DB 확인 없음(계약대로). `app.release-version` 문자열은 제거되고 복구 키 `ipo-reconciliation-v1`로 고정됨.

## 2026-11-10 (SF1 → Master 반려 반영, 완료 조건 재검증)

### 역할·범위

- 동일 브랜치에서 Master 슬롯4 검증(test/checkstyle/spotless/bootJar --rerun-tasks PASS) 뒤 코드 검토에서 나온 반려 사항을 최소 범위로 반영했다. 슬롯 1·동시 워커 없음·커밋/push/배포 없음·dev 공유 DB 접근 없음. 새 기능 추가 없이 결함 수정만 수행했다.

### 반영 내역 (Master 메시지 1~6, 로깅 규칙)

1. P0: `IpoSyncService.reconcileStep` catch(Exception)이 빈 정상 `IpoReconcileResult`를 반환하던 것을 수정 — 예외 시 `failed≥1` + `failures=[{corpCode=null, reason:"철회점검: 재점검 단계 예외: <클래스명>"}]`를 담고 `IpoSyncServiceTest.recoverAfterDeploy_reconcileExceptionIsReportedAsFailure_notSuccess`(recordRecover 캡처로 failed≥1 확인)로 검증. 통합 측면은 `IpoSyncRunRecorderTest.failedReconcileLeavesFailedRowAndNoSuccess`(실제 DB: FAILED 1행·SUCCESS 0행)와 `recordRecover`/`recordDaily` 반환값이 실제 DB의 SUCCESS 존재 여부를 따르는 구현으로 확인.
2. P0: `IpoSyncPersistence.persist` 동일 공모 판정 가드 추가(`isSameOffering`) — (a) source_receipt_no 일치, (b) 레거시(receipt 없음)이면서 청약 시작일 일치, (c) 정정/발행조건확정 공시 근거 + 청약 시작일 일치일 때만 갱신. 구분 불가하면 가격·주식수·회사명·접수번호를 포함한 기존 값 전체 불변 + `HELD_UNDETERMINED` 반환 → 수집 단계 실패 기록("신규 공모/정정 구분 불가: 자동 갱신 보류"). 레거시만 source 연결(정정 포함 갱신 경로), 추적 receipt 있는 행은 철회 매칭 근거 유지를 위해 덮어쓰지 않음. 같은 회사 재추진은 옛 건 철회 후 새 id 생성(`repromotionCreatesNewRowAfterWithdrawal`). 테스트: IpoScheduleReconciliationTest에 HELD_UNDETERMINED(서로 다른 receipt/날짜·정정근거 없음), 정정근거 동일공모 갱신 시 receipt 불변, receipt 일치 갱신, 레거시 연결, 정정이라도 날짜 다르면 보류 추가.
3. P1: `IpoReconciliationService.processCorp` 전체를 `doProcessCorp`로 감싸 회사별 예상 밖 RuntimeException을 클래스명만 실패 기록하고 계속한다. 인증/020 즉시 중단은 유지. 테스트: `runtimeExceptionInOneCompanyDoesNotStopOtherCompanies`(첫 회사 RuntimeException → 실패, 둘째 Simwon rm=철 → 철회, 철회 건수가 유지됨).
4. P1: 빈 DS001/DS006 응답이나 대상 공모와 연결되는 유효 공시가 없으면 성공이 아니라 "철회점검: 대상 공모 확인 불가 (빈 응답 또는 매칭 없음)" 실패 기록. 성공은 추적 receipt 일치 상세+정정/원공시(rm≠철)가 확인될 때만(레거시는 청약 시작일 일치 상세 기준). 테스트: `emptyDs001ResponseCountsAsUnverifiedFailure_notSuccess`, `validNonWithdrawalFilingConfirmsSuccess`, `emptyApiResults_deleteNothing_andFailureRestartRecovers`(1단계 기대값: ACTIVE 유지·SUCCESS 0·FAILED 1로 수정).
5. P1: `PostDeployReconciliationRunner`에 `@ConditionalOnProperty(name = "app.ipo-sync.enabled", havingValue = "true", matchIfMissing = true)` 추가(스케줄러와 동일 조건). 테스트: `PostDeployReconciliationRunnerDisabledTest`(false → runner/scheduler 빈 미생성), `PostDeployReconciliationRunnerEnabledTest`(true → 빈 생성). 미설정 기본 true는 스케줄러와 동일한 annotation 의미로 동작한다.
6. P1: `IpoSyncRunRecorder` — saveAndFlush+부분유니크 충돌 삼키기를 제거하고, 리포지토리의 `@Modifying INSERT ... ON CONFLICT DO NOTHING`(원자)로 DB에 쓰며, 삽입 여부와 `isRecoverComplete()` 실제 조회를 함께 반환한다. 영속화 실패·직렬화 실패는 예외로 전파되어 성공으로 표시되지 않는다. 자체 `TransactionTemplate` 내장(테스트의 수동 생성에서도 동일하게 동작). 테스트: `IpoSyncRunRecorderTest`(FAILED/SUCCESS 실제 DB 검증, POST_DEPLOY SUCCESS 1건 수렴, DAILY SUCCESS 2건 연속 기록, 부분 실패 시 복구 키 미완료).
7. 로깅: 재점검·수집 흐름의 `e.getMessage()` 로그/사유를 클래스명(+status 코드만)으로 교체. DART 메시지·URL·키가 로그에 남지 않는다.
8. dev 공유 DB 접근 및 probe 없음. 슬롯 1만 사용.

### 검증

- `scripts/db-slot.sh reset 1` 후 `cd backend && DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test checkstyleMain checkstyleTest spotlessJavaCheck bootJar --rerun-tasks --console=plain`: **PASS**. 테스트 **1189건, 실패 0건**(--rerun-tasks). checkstyle WARN 0, spotlessJavaCheck PASS, bootJar 산출. `git diff --check`: PASS.
- 신규 테스트명: `recoverAfterDeploy_reconcileExceptionIsReportedAsFailure_notSuccess`, `runtimeExceptionInOneCompanyDoesNotStopOtherCompanies`, `validNonWithdrawalFilingConfirmsSuccess`, `emptyDs001ResponseCountsAsUnverifiedFailure_notSuccess`, `IpoSyncRunRecorderTest`(4건), `PostDeployReconciliationRunnerDisabledTest`/`EnabledTest`, IpoScheduleReconciliationTest 6건 추가.
- 문서: architecture/spec 최소 사실 보정(확인 불가=실패, runner enabled 조건, 동일공모 판정). 커밋/push/배포 없음.

### 주의·해석

- 4번 보정으로 빈/미확인 응답만 있는 활성 자동공모는 매일 FAILED로 기록된다(성공 아님). 동일 현상이 수동/출처불명 행에는 실패로 남을 수 있으며, 이는 "정상 완료 허구 금지" 지시를 따른 결과다. 성공 처리는 추적 receipt/시작일로 연결되는 유효 정상 공시가 존재할 때만 가능하다.
- `uk_ipo_corp_code_active` 폐기 결정(재추진 허용·FR-11 감지)은 유지된다.
- SF2(배포 후 POST_DEPLOY_RECOVER 확인, Simwon id20)는 별도 승인 필요. 이 작업은 커밋/push/배포를 포함하지 않는다.
