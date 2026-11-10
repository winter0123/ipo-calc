# 세부 기능 분해: 자동 IPO 재점검 (automatic-ipo-reconciliation)

> SF0 = 본 계약 문서(완료). SF1 = 구현(Master 2026-11-10 보정 계약으로 구현 완료). SF2 = 운영 적용(배포 후 확인).
> 2026-11-10 Master 보정 우선 사항: ① DS001 전 페이지 조회(1세트)·회사당 호출 공유, ② SUCCESS 부분 유니크는 POST_DEPLOY_RECOVER만, ③ 복구 키 `ipo-reconciliation-v1` 고정(04:30 성공도 완료 처리), ④ ipos.source_receipt_no 추가·기존 건은 유일 매칭만 연결·재점검을 신규 수집보다 먼저 실행, ⑤ 청약 시작/종료·환불·상장일 추적값 보호(백필 금지), ⑥ profitcalculation/allocationmanagement IPO 참조 조회 findById 교정, ⑦ 문서 4종 보정 허용(재승인 게이트 없음), ⑧ 검증은 db-slot.sh reset 1 + `DB_NAME=ipo_slot_1 ./gradlew test checkstyleMain checkstyleTest spotlessJavaCheck bootJar`.

## 1. SF 목록

| SF | 제목 | 범위 | 상태 |
|----|------|------|------|
| SF0 | 계약 확정 | research/spec/architecture/breakdown/work-log 5문서, 공식 API·라이브 확인 | 완료(본 문서) |
| SF1 | 자동 재점검 구현 | 아래 SF1-1~4 + V44 + 이력 영향 보정(FR-10) | 구현 완료(Master 검증 대기) |
| SF2 | 운영 적용 | V44 마이그레이션 배포 후 POST_DEPLOY_RECOVER 1회 실행 확인, Simwon(id20) GET 제외·재실행 멱등 확인 | 미착수(승인 대기) |

## 2. SF1 상세

### SF1-1 스키마·모델 (V44)
- `V44__ipo_sync_runs.sql`: `ipo_sync_runs` 테이블 + `uk_ipo_sync_runs_success` 부분 유니크 인덱스(architecture §2). ipos 컬럼 변경 없음, V43 수정 금지. Flyway 검증: 로컬/테스트 DB `validate` PASS.
- `IpoSyncRun` 엔티티(신규, ipocatalog 도메인) + `IpoSyncRunRepository`(findSuccessByRunTypeAndAppVersion 등). 기존 `IpoDetail`/JSONB 패턴.

### SF1-2 Step 2 재점검 (04:30 배치 통합, FR-01~08)
- `IpoReconciliationService`(신규, ipocatalog.service.ipo.sync): `reconcileActiveOfferings(LocalDate)`.
- `IpoSyncService.sync(90)`의 doSync에 Step 2 호출 삽입 — Step 1 예외/aborted와 무관하게 실행(FR-08), 건별 TransactionTemplate, `runLock`은 기존 것 그대로.
- 쿼리 신규: `IpoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(ACTIVE)` (listingDate 무관, 상장 경과 포함).
- corpCache(Map<corpCode, DS001/DS006 응답>)로 회사당 2회 상한. rm=철 항목 → rcept_no == DS006 detail.rcept_no → subscriptionStartDate 일치하는 ACTIVE 건만 `IpoSyncPersistence.withdraw()`.
- 철회 분기 실패는 `recordFailure(reason="철회점검: ...")`로 기록, `IpoSyncResult.failures`에 포함하되 aborted/retryable 불변.
- ustockplus 일정 정정은 기존 `IpoScheduleMergeService` 그대로(FR-07). 추가 코드 없음.

### SF1-3 배포 후 자동복구 (FR-09)
- `PostDeployReconciliationRunner`(신규): `ApplicationReadyEvent` 수신 → 현 app_version에 SUCCESS 없으면 `IpoSyncExecutorConfig` executor로 비동기 제출. 있으면 즉시 반환.
- app_version 소스: 명시적 설정값(`app.release-version`, 없으면 `spring.application.version` 또는 build.gradle version `0.0.1-SNAPSHOT`). 배포용 버전 문자열은 Master가 SF1-3 착수 시 확정(예: `2026-11-10-pr500`).
- 완료/부분 실패는 `ipo_sync_runs`에 DAILY/POST_DEPLOY_RECOVER 공통으로 기록. 부분 실패 = FAILED(미완료).

### SF1-4 이력 내역 보정 (FR-10, 소규모)
- `ProfitCalculationService.calculateForSale`: `findByIdAndDeletedAtIsNull` → `findById`.
- `AllocationService`(line ~194): 동일 교정.
- 단건 수익·배정 상세에서 철회 건 ipoName/listingDate/fixedPrice 복원. 기존 테스트 기대값 갱신 확인.

## 3. 테스트 목록 (구현 시 TDD)

| 테스트 | 검증 |
|--------|------|
| 신규 `AutomaticReconciliationTest` | AC-01,02,03,05,06,08,11 (mock OpenDART, Simwon 확정값 corp02038432/원공시20261012000149/sbd 2026-11-05/pymd 2026-11-09) |
| 신규 `PostDeployReconciliationRunnerTest` | AC-07 (SUCCESS 존재 시 스킵, FAILED 시 재시도, busy 시 무해 종료) |
| `IpoSyncServiceTest` 확장 | AC-06 격리, AC-11 DAILY_RECONCILE 기록 |
| `ProfitCalculationServiceTest` 확장 | AC-09 (소프트삭제 건 fixedPrice 대체 복원) |
| 기존 회귀 | IpoScheduleReconciliationTest, IpoSyncSchedulerTest, IpoScheduleMergeServiceTest 전부 PASS |
| 검증 | `./gradlew :backend:test`, checkstyleMain/checkstyleTest, bootJar, `git diff --check` |

## 4. 작업 순서·리스크

1. SF1-1 → SF1-2 → SF1-3 → SF1-4 (순서 의존)
2. 리스크: corp 무제한 DS001 대형 회사 page>1 → 기존 total_page 루프 재사용. 대량 ACTIVE + 020 → 즉시 중단(재시도 정책 기존 값).
3. 리스크: `ApplicationReadyEvent`가 readiness probe보다 먼저 실행될 수 있음 → 비동기 제출이므로 트래픽 영향 없음. executor 거부 시 로그만 남기고 FAILED 기록 없이 다음 04:30 배치에 위임.
4. 운영 쓰기·배포·V44 적용은 SF2 별도 승인. SF1 완료 후 Master diff 검증 → PR.

## 5. 완료 조건 (SF1)

- AC-01~12 PASS, checkstyle/bootJar PASS, Master 직접 검증 후 병합. 워커 보고는 증거로 쓰지 않음.
