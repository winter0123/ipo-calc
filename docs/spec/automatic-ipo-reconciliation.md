# 스펙: 자동 IPO 재점검 (automatic-ipo-reconciliation)

> 사용자 승인 계획: 04:30 KST 기존 배치에 "활성 자동공모 원공시 재점검 + 90일 밖 철회 점검 + 배포 후 자동복구"를 통합한다. 관리자 로그인·버튼 개입 0.

## 1. 사용자 목표

관리자 조작 없이 매일 배치가 (1) 90일 밖 포함 활성 자동공모의 원공시 rm=철을 재점검해 철회를 반영하고, (2) 일정 정정을 추적값 보호 규칙으로 반영하며, (3) 새 버전 배포 후 비동기 복구를 1회 수행한다.

## 2. 기능 요구사항 (FR)

| ID | 요구사항 |
|----|---------|
| FR-01 | 기존 04:30 KST `IpoSyncScheduler` → `IpoSyncService.sync(daysBack)` 안에 `reconcileActiveOfferings()` 단계를 통합하고, 신규 수집보다 먼저 실행한다(회사 단위 upsert가 옛 공모를 덮어쓰지 못하게). 회사 단위 upsert는 동일 corp ACTIVE 복수 건을 감지하면 덮어쓰지 않고 실패 기록 후 보류(FR-11). 기존 `runLock`(ReentrantLock) 재사용 — 이미 실행 중이면 `IpoSyncBusyException`, 별도 락 신설 금지 |
| FR-02 | 점검 대상: `status=ACTIVE AND syncSource IS NOT NULL AND deletedAt IS NULL`. listingDate 유무·상장일 경과 무관(상장 지난 공모도 누락 없이 점검) |
| FR-03 | 회사(corp_code) 단위로 묶어 DS001(corp_code, 전체 기간) 1회 + DS006(corp_code, 기간 제한) 1회만 호출하고 같은 회사의 대상 건에 재사용. 대상 건당이 아니라 회사당 2회 상한 |
| FR-04 | 철회 확정 절차: DS001 결과에서 rm에 "철" 포함인 항목 선별 → 해당 rcept_no와 일치하는 DS006 상세 채택(없으면 `철회점검: 원공시 DS006 상세 없음` 실패 기록, 철회 보류) → corp_code + 원공시 rcept_no + subscriptionStartDate 3자 일치하는 ACTIVE 건만 `withdraw()`. 하나라도 불일치하면 자동 철회 금지 |
| FR-05 | 일정누락과 철회 구별: DS006 빈 응답·sbd 파싱 실패·종료일 이상·API 오류는 "일정누락"(recordFailure)이며 철회로 처리하지 않는다. 철회는 rm=철 확인 + FR-04 매칭 성공 때만. 빈/누락 응답은 성공으로도 기록하지 않는다(대상 공모 확인 불가 → 실패 기록·다음 실행 재시도) |
| FR-06 | 같은 원공시 receipt 재수집 차단(`existsByCorpCodeAndWithdrawnReceiptNo` SKIPPED_WITHDRAWN 유지). 다른 receipt 재추진 허용(신규 ACTIVE 건 생성, V44에서 `source_receipt_no` 원공시 저장 및 `uk_ipo_corp_code_source_receipt_active`로 동일 공모 중복만 차단). 철회된 건은 INACTIVE+deletedAt+withdrawnReceiptNo, 이력(Subscription/Allocation/Sale) 보존 |
| FR-07 | 청약 시작/종료·환불·상장일은 수집 원본 추적값(`last_imported_subscription_start_date`/`last_imported_subscription_end_date`/`last_imported_refund_date`/`last_imported_listing_date`)과 현재값이 같거나 현재값이 null일 때만 자동 정정한다(정정 시 listingOpenPrice=null, 추적값=신규값). 수동/출처불명(추적값 null)은 보호하고, 기존 출처 불명 행은 백필하지 않는다. OpenDART 청약일 정정도 `syncFromOpenDart`의 추적값 보호 규칙으로 처리한다 |
| FR-08 | 신규 공시 수집 오류와 기존 공모 철회 점검은 격리 실행 — 한쪽 예외가 다른쪽을 중단시키지 않는다. OpenDART 인증오류·020은 같은 키를 쓰므로 두 단계 모두 중단하고 재시도 정책(5/10/20분) 따름. 철회 점검 실패는 `reason` 접두 "철회점검: "로 구분 기록, aborted 플래그 변경 금지 |
| FR-09 | 배포 후 자동복구 1회(`app.ipo-sync.enabled=true`일 때만 등록, 기본 true): `ApplicationReadyEvent` 리스너가 `ipoSyncExecutor`로 비동기 1회 제출. 재점검 예외는 SUCCESS를 만들지 않고 failure≥1로 기록한다. 복구 알고리즘 키는 코드 상수 `ipo-reconciliation-v1`로 고정(매 배포 SHA/버전 문자열 금지). 키에 대한 `POST_DEPLOY_RECOVER` status=SUCCESS 행이 있으면 스킵. 부분 실패(FAILED)면 완료 기록을 남기지 않아 다음 실행(04:30 배치 또는 재기동)이 전수 재점검으로 재시도하고, 04:30 전수 성공도 같은 복구 키를 SUCCESS로 기록한다. busy(runLock 충돌)·실행기 거부는 무해 종료. 관리자 세션·버튼 사용 금지 |
| FR-10 | 소프트삭제 이력 영향 보정(최소): 단건 수익 `ProfitCalculationService.calculateForSale`와 `AllocationService`의 ipo 조회를 `findById`로 교정해 철회 건의 ipoName/listingDate/fixedPrice 대체를 복원. `IpoDetailService.getInsight`(공개 상세)와 `/api/ipos` 및 청약등록 검증은 soft-deleted 제외 유지 |
| FR-11 | 동일 corp ACTIVE 자동공모가 2건 이상 감지되면 upsert로 덮어쓰지 않고 `철회점검: 동일 회사 ACTIVE 복수 건` 실패 기록 후 보류 |

## 3. Acceptance Criteria (AC)

| AC | 검증 조건 |
|----|----------|
| AC-01 | 90일 밖(상장일 경과 포함) ACTIVE 자동공모가 철회 점검 대상 목록에 포함된다(단위 테스트) |
| AC-02 | 회사당 DS001 1세트(모든 total_page 페이지)·DS006 1세트만 호출되고 같은 회사의 여러 건이 호출을 공유한다(mock verify). 빈 응답만으로는 삭제하지 않는다 |
| AC-03 | Simwon(corp02038432) 시나리오: rm=철 원공시 → DS006 rcept_no 일치 + sbd 2026-11-05 일치 → id 철회, subscriptionStartDate 불일치 → 철회 금지 + 실패 기록 |
| AC-04 | 철회 판정에 쓰인 실패(빈 응답 등)로는 INACTIVE 되지 않는다(일정누락≠철회) |
| AC-05 | 같은 회사 ACTIVE 2건 감지 시 덮어쓰기 없이 실패 기록 |
| AC-06 | 신규 공시 수집 실패(예외)와 철회 점검 실패가 서로를 중단시키지 않는다(격리 테스트). 인증오류/020은 양쪽 중단 + retryable |
| AC-07 | `POST_DEPLOY_RECOVER`: SUCCESS 기록 없으면 비동기 1회 실행, 있으면 스킵, FAILED 시 미완료 → 재호출 시 재시도, `runLock` 충돌 시 busy로 조용히 종료(예외 전파 없음) |
| AC-08 | 수동 등록(syncSource=null)·출처불명 listingDate는 철회·정정에서 보호 |
| AC-09 | 소프트삭제 후 단건 수익 조회에서 ipoName/listingDate가 복원되고 투자자별 수익·내역조회의 기존 결과가 불변 |
| AC-10 | 기존 테스트(IpoScheduleReconciliationTest, IpoSyncServiceTest, IpoSyncSchedulerTest 등) 전부 PASS, checkstyleMain/checkstyleTest/bootJar PASS |
| AC-11 | `ipo_sync_runs`에 실행·복구 키·상태·대상수·성공/실패수·실패JSONB가 기록되고 부분 실패 건이 다음 실행에 재시도된다. `DAILY_RECONCILE` SUCCESS는 매일 기록되고(당일 복구 키 완료 포함), `POST_DEPLOY_RECOVER` SUCCESS는 `ipo-reconciliation-v1`당 정확히 1건만 허용된다(부분 유니크 인덱스) |
| AC-12 | 공개 API 호출 상한: 배치당 회사당 2회 + 기존 90일 수집 호출 이외의 신규 호출 없음 |

## 4. 비범위

- 관리자 화면/API 추가, 설정 UI, 다중 서버 배타 제어(현재 단일 systemd 인스턴스 전제)
- V43 수정, 나머지 36건 개별 공식 일정 대조, 운영 DB 직접 쓰기(문서 확정 후 SF1 승인 필요)
- 철회 건의 hard delete, `Subscription/Allocation/Sale` 스키마 변경

## 5. 실패·재시도 정책

- 건별 실패: 기록 후 계속(철회 점검·신규 수집 각각 독립). 전체 중단: 인증오류·020만.
- 부분 실패(FAILED)는 완료 기록 없음 → 04:30 배치 또는 재기동 시 재시도. SUCCESS만 완료로 판정.
