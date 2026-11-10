# 아키텍처: 자동 IPO 재점검 (automatic-ipo-reconciliation)

> 기존 모듈 확장만. 새 레이어 없음. V44 = ipos 식별/추적 컬럼 + ipo_sync_runs + 인덱스 조정(V43 수정 금지).
## 1. 데이터 플로우 (04:30 KST, 기존 루프에 단계 삽입)

```text
IpoSyncScheduler.syncDaily → IpoSyncService.sync(90) → runExclusively(runLock)
  Step 1 (신규, 선행): reconcileActiveOfferings(today) — 신규 수집 전에 철회를 반영한다
    (회사 단위 upsert가 옛 공모를 덮어쓰지 못하게. Master 2026-11-10 보정)
    1. ACTIVE + syncSource!=null + deletedAt=null, listingDate 무관(상장 경과 포함), 회사별 그룹화
    2. corp_code별: DS001(corp_code, 전체기간, 전 페이지) 1세트 + DS006(corp_code, 전체기간) 1세트
    3. DS001 rm에 "철" 포함 항목 → rcept_no별 → FR-04 매칭 → withdraw() (건별 TransactionTemplate)
    4. 예외는 격리. 인증/020은 두 단계 모두 중단 (FR-08)
  Step 2 (기존): DS001 90일 검색 → 회사별 DS006 → persist/withdraw (신규 공시 수집)
    └ ustockplus 정정: mergeSupplementalSchedule 규칙(FR-07), 기존 IpoScheduleMergeService 재사용
```

배포 후 자동복구: `ApplicationReadyEvent` 리스너(신규 `PostDeployReconciliationRunner`, `app.ipo-sync.enabled=true`일 때만 등록, 기본 true)가 `IpoSyncExecutorConfig` 스레드 풀로 `recoverAfterDeploy()` 비동기 제출. 성공 판정 = `ipo_sync_runs` (run_type='POST_DEPLOY_RECOVER', app_version='ipo-reconciliation-v1', status='SUCCESS') 행 존재(부분 유니크 인덱스로 키당 1건, 삽입은 `ON CONFLICT DO NOTHING` 원자 쿼리). 04:30 배치의 전수 성공도 같은 복구 키를 SUCCESS로 기록한다. 재점검 단계 예외는 성공 기록을 남기지 않고 failure≥1로 기록해 다음 실행이 재시도한다. busy(runLock 충돌)·실행기 거부는 무해 종료하고 완료 기록을 남기지 않아 다음 실행이 재시도한다.

## 2. 최소 스키마 (V44__ipo_sync_runs.sql)

```sql
-- ipos: source_receipt_no(VARCHAR 20, nullable), last_imported_subscription_start_date/
--       last_imported_subscription_end_date/last_imported_refund_date (DATE, nullable) 추가
-- ipos: uk_ipo_corp_code_active DROP → uk_ipo_corp_code_source_receipt_active
--       (corp_code, source_receipt_no) WHERE status='ACTIVE' AND source_receipt_no IS NOT NULL
--       (동일 receipt 재삽입만 차단, 재추진 허용. V6 uk_ipo_name_start_date_active는 유지)
CREATE TABLE ipo_sync_runs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  run_type VARCHAR(30) NOT NULL,        -- DAILY_RECONCILE | POST_DEPLOY_RECOVER
  app_version VARCHAR(100) NOT NULL,  -- POST_DEPLOY_RECOVER의 복구 알고리즘 키(ipo-reconciliation-v1 고정). DAILY도 관측용으로 같은 키 기록
  status VARCHAR(10) NOT NULL,        -- SUCCESS | FAILED (부분 실패=FAILED)
  started_at TIMESTAMP NOT NULL, finished_at TIMESTAMP NULL,
  total_targets INT NULL, succeeded INT NULL, failed INT NULL,
  failures JSONB NULL,                 -- [{"ipoId":1,"corpCode":"...","reason":"철회점검: ..."}]
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_ipo_sync_runs_recover_success
  ON ipo_sync_runs (app_version) WHERE run_type = 'POST_DEPLOY_RECOVER' AND status = 'SUCCESS';
```

- "배치 완료 버전" = 위 SUCCESS 행으로 판정(`POST_DEPLOY_RECOVER`만). `DAILY_RECONCILE`는 관측용.
- 부분 실패 재시도 = FAILED는 미완료. 다음 실행은 실패 건 선별 없이 **전수 재점검**(멱등·저비용). failures JSONB는 관측·검증용.
- 멀티인스턴스: 단일 systemd JAR 전제. runLock은 JVM 인메모리라 인스턴스 간 배타 불가 — 동시 실행 시 DART 이중 호출되나 철회 멱등이라 결과 안전. SUCCESS는 부분 유니크 인덱스로 1건 수렴. 다중 인스턴스 전환 시 DB 분산 락 선행 필요(본 계약 미보장).
- ipos에 식별(source_receipt_no)·추적 컬럼 4개 추가(신규 수집부터 저장, 백필 없음). JSON 열은 ipo_details 기존 JSONB 패턴 동일.
- persist 동일공모 판정: sourceReceiptNo 일치 또는 (레거시/정정공시 근거와 청약 시작일 일치)만 같은 공모. 구분 불가 시 가격·주식수·회사명·receipt를 포함한 기존 값 전체 불변 + 실패 보류.

## 3. 트랜잭션·격리

| 작업 | 범위 | 예외 시 |
|------|------|---------|
| Step 1 회사별 persist/withdraw | 건별(TransactionTemplate, 기존) | rollback-only + recordFailure, 계속 |
| Step 1 회사별 reconcile | 건별(TransactionTemplate) | 인증/020 외 회사별 RuntimeException은 클래스명만 실패 기록 후 계속. failures JSONB + recordFailure("철회점검: "), 계속 |
| 회사별 reconcile 확인 불가 | 빈 DS001/DS006·매칭 없음 | 성공 아님: "철회점검: 대상 공모 확인 불가" 실패 기록 → FAILED·재시도(정상 공시가 없으면 절대 SUCCESS 아님) |
| 신규 수집 전체 aborted | 기록, 재점검은 이미 선행 완료 | FR-08 격리. 인증/020이면 재점검도 실패 기록 → FAILED(재시도) |
| POST_DEPLOY_RECOVER | runLock 내 비동기 | 부분 실패 → FAILED, 완료 아님 |

## 4. 식별·재사용 평가 (Master 2026-11-10 보정)

- `ipo_details.source_rcept_no` 재사용: **원칙 불가**. 상세 동기화가 투자설명서(PROSPECTUS) 우선 채택이라 원공시와 불일치가 일반적.
- ipos.source_receipt_no(V44 추가) **채택**: `IpoSyncPersistence.persist`의 DS006 `detail.getRceptNo()`를 새 수집부터 ipos에 저장한다. 철회 매칭은 원공시 rm=철 + DS001/DS006 접수번호 일치 + 청약 시작일 일치 3자로 하고, 행의 `source_receipt_no`가 원공시와 다르면(재추진·다른 회차) 철회하지 않는다. 같은 청약일인데 접수번호가 다르면 `철회점검: 원공시 구분 불가`로 보류한다.
- 기존 건(추적값 없음) 연결: sbd == subscriptionStartDate AND corp당 ACTIVE 자동공모 정확히 1건일 때만. 복수/불일치 → `철회점검: 유일 매칭 실패`/`청약 시작일 불일치` 보류.
- 회사 단위 새 공모 upsert: INACTIVE는 단건 조회 제외 → 재추진 시 새 건 생성(허용). 철회 없이 재추진(드묾)은 corp ACTIVE 2건 → FR-11/AC-05 감지·보류(덮어쓰기 금지).

## 5. 소프트삭제 영향과 보정(FR-10)

| 경로 | 조회 | 영향 | 결정 |
|------|------|------|------|
| GET /api/ipos · 청약등록 검증 · IpoDetailService | findById/findAllByDeletedAtIsNull | 철회 건 404/제외 | 유지(의도) |
| 내역조회 InvestmentHistoryReadModel · 대시보드 집계 · 투자자별 수익 | findById/findAllById | 포함, 정상 | 유지 |
| 단건 수익 ProfitCalculationService · AllocationService 194 | findByIdAndDeletedAtIsNull | ipo=null → ipoName/listingDate 누락, fixedPrice 대체 불가 | **findById로 교정(SF1)** |
| IpoDetailSyncService | findAllByDeletedAtIsNull... | 철회 건 상세 동기화 스킵 | 유지(호출 절약) |

## 6. 검증 포인트 / 의존성

- 단위: mock OpenDART(DS001/DS006) — Simwon 확정값(corp02038432, 원공시20261012000149, sbd 2026-11-05, pymd 2026-11-09).
- 통합: `IpoSyncServiceTest` 확장 + 신규 `AutomaticReconciliationTest`.
- 호출 상한: 회사당 DS001+DS006 2회. 020 감지 시 즉시 중단(재시도 5/10/20분 기존 정책). total_page>1이면 기존 루프 재사용.
