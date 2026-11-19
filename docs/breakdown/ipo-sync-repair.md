# 상장일 동기화 복구 (ipo-sync-repair) — Breakdown

## Epic 요약
- **목표**: `IpoScheduleMergeService`의 ustockplus 조회 범위를 기존 `today.minusDays(30) ~ today.plusDays(59)`(총 89일, uStockPlus API 제한과 일치)로 복원하고, obsolete range 설명을 수정한다.
- **배경**: 커밋 1bc43c24(±60일), 57e9bb7d(청약일 기준 ±90일)로 변경되면서 API의 89일 제한을 초과할 수 있는 범위가 되었다. 원본 로직(a64429a8 이전 코드)으로 복원한다.
- **범위**: `IpoScheduleMergeService.java`의 `merge()` 메서드 날짜 계산 로직, Javadoc, 동일 패키지의 `IpoScheduleMergeServiceTest.java` 검증 추가.

---

## 작업 분해 (SF)

### 작업 1: 상장일 동기화 범위 복원 (SF-1) ✅ **완료**
- **범위**: `IpoScheduleMergeService.merge()` 메서드의 날짜 계산 로직을 `today.minusDays(30)` ~ `today.plusDays(59)`로 되돌림
- **비범위**: uStockPlusCalendarClient의 89일 제한 로직 변경 금지, 구독일(SubscriptionDate) 기반 조회 로직 완전 제거
- **AC**:
  1. `merge(LocalDate today)` 메서드가 `today.minusDays(30)`을 startDate로, `today.plusDays(59)`을 endDate로 사용
  2. Javadoc에 "오늘 기준 과거 30일 ~ 미래 59일(양끝 포함 90일, 두 날짜의 차이 89일)" 명시, ustockplus API의 조회 기간 제한(최대 89일 차이, 즉 양끝 포함 90일)을 로컬에서 검증하여 준수한다고 기술
  3. 청약일(minStart/maxStart) 기반 분기 로직 제거
  4. uStockPlusCalendarClient의 89일 제한(`MAX_RANGE_DAYS = 89`)과 일치하는지 검증
- **선행 조건**: 없음 (독립 작업)
- **수정 대상**:
  - `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoScheduleMergeService.java`
- **테스트·검증**:
  - `IpoScheduleMergeServiceTest`에 정확한 시작/종료일 검증 테스트 추가 (`merge_integrationWithRealClient_verifiesQueryParamsAndListingDateSaved`)
  - `UstockPlusCalendarClientTest`의 89일 제한 테스트가 통과하는지 확인

---

### 작업 2: 원문 자료 정리 (SF-2)
- **범위**: 실제 DART 원문 원신고/정정/발행조건확정 3종 공시의 필드 근거 및 실표 fixture 확보
- **비범위**: 코드 수정 없음, 문서화만
- **AC**:
  1. `docs/research/ipo-sync-repair.md`에 DS001(원신고/정정/발행조건확정) 공시별 주요 필드 매핑표 작성
  2. 각 공시 종류별 수집 대상 필드(공모밴드, 확정공모가, 청약일, 배정일, 환불일, 상장일, 주관사, 공모주식수 등) 출처 명시
  3. 공개 DART 열람본 발췌 자료 확보. OpenDART ZIP 내부 XML과의 대조는 추가 검증
  4. 정정 공시 시 rceptNo 연결 고리(원공시 접수번호) 및 우선순위 규칙 문서화
- **선행 조건**: SF-1 완료 후
- **수정 대상**: `docs/research/ipo-sync-repair.md` (신규 생성/보완)

---

### 작업 3: 파서·검증 로직 개선 (SF-3)
- **범위**: SF-2 근거에 맞춰 밴드·확정가 파서 개선(액면가/총액 제외, 정정 후 값 우선)
- **비범위**: IpoScheduleMergeService 병합 로직 변경 없음, `DartOfferingTermsParser` 등 파서 모듈만
- **AC**:
  1. `DartOfferingTermsParser`가 원신고/정정/발행조건확정 3종을 모두 파싱
  2. 액면가(parValue), 총액(totalAmount) 등은 공모가 밴드/확정가에서 제외
  3. 정정 후 열의 단일 확정가만 반환. 최신 접수번호의 저장 반영은 SF-4에서 검증
  4. 발행조건확정 공시는 확정공모가 확정 시점에만 반영, 밴드는 원신고/정정값 유지
  5. 파서 단위 테스트로 각 공시 종류별 fixture 검증 통과
- **선행 조건**: SF-2 완료 후
- **수정 대상**:
  - `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/opendart/DartOfferingTermsParser.java` (또는 관련 파서)
  - `backend/src/test/java/.../DartOfferingTermsParserTest.java`

---

### 작업 4. 최신 정정공시 반영과 기존 데이터 보호

- 수정 대상은 `IpoSyncService`, `IpoReconciliationService`, `IpoSyncPersistence` 및 관련 테스트다.
- 신규 수집과 재점검에서 확인한 같은 공모 회차의 최신 DS001 접수번호로 원문을 내려받는다. 날짜가 변경되는 정정도 처리할 수 있도록 최초제출일과 기존 접수번호 등으로 회차를 검증한다.
- DS006 상세는 출처를 유지하며 일정 보완에 사용한다. 최신 접수번호의 DS006 상세가 없어도 해당 원문에서 검증된 값은 반영한다. 상장일의 보조 출처는 uStockPlus다.
- 원신고·기재정정의 희망가와 발행조건확정의 확정가는 항목별로 출처 접수번호를 유지한다. 이전 공시로 되돌아가지 않도록 하고, 빈 값과 서로 다른 회차의 공시는 기존 값을 변경하지 않는다.
- 회귀 테스트에서 늦게 도착한 정정, DS006 누락, 재실행, 수동 수정값 보호, 철회와 재추진, 기존 투자 이력의 IPO ID 보존을 확인한다.

### 작업 5. 운영 데이터 변경 후보 비교

- SF-1과 SF-4 검증 후 운영 DB 또는 승인된 스테이징 사본에서 기존 값과 재수집 후보를 읽기 전용으로 비교한다.
- IPO ID, 접수번호, 상장·청약·환불일, 희망가, 확정가, 변경 이유를 기록한다. 수동 수정 및 동일 회차를 확인할 수 없는 건은 보류한다.
- 운영 적용 방법과 대상은 비교 결과를 검토한 뒤 승인받는다. 적용 후 달력 표시와 재실행 결과를 확인한다.

작업 순서는 SF-2 → SF-3 → SF-4이며, SF-5는 SF-1과 SF-4에 의존한다.

## 테스트 정리

### IpoScheduleMergeServiceTest (SF-1 검증 완료)
- **기존 단위 테스트(17개)**: 매칭 우선순위, 수동값 보호, 날짜 채우기 규칙, 예외 복구 등 본래 검증 의도 유지
- **신규 통합 테스트(1개)**: `merge_integrationWithRealClient_verifiesQueryParamsAndListingDateSaved`
  - 실제 `UstockPlusCalendarClient` + `MockRestServiceServer` 사용
  - 고정 TODAY(2026-10-15)에서 정확한 쿼리 파라미터(`startDate=2026-09-15`, `endDate=2026-12-13`) 검증
  - TO_BE_LISTED fixture 날짜가 Ipo의 `listingDate`에 저장되고 `saveAndFlush` 호출까지 검증
- **불필요한 중복 제거**: `argThat` 반복 검증 테스트 2개(`merge_callsClientWithExact30DaysPastAnd59DaysFutureRange`, `merge_dateRangeEquals89DaysMatchingUstockPlusApiLimit`)는 통합 테스트로 대체되어 삭제

### UstockPlusCalendarClientTest (기존 유지, 10개)
- `rejectsRangeOver89DaysAndReversedRange`: 90일 초과 시 `IllegalArgumentException`
- `acceptsExactly89DayRange`: 정확히 89일 차이(양끝 포함 90일)는 허용
- 헤더, 쿼리 파라미터, 에러 응답 처리 등 검증

---

## 승인 게이트
- **Master 승인 필요**: 작업 1 구현 전 (아키텍처 변경 아님, 단순 복원이지만 범위 확인 차원) — **완료**
- **사용자 승인 필요**: 없음 (기존 동작 복원)
- **DB 마이그레이션**: SF-5에서 별도 승인 게이트 필요

---

## 완료 조건 (Definition of Done)
- [x] `IpoScheduleMergeService.merge()`가 `today.minusDays(30) ~ today.plusDays(59)` 사용
- [x] Javadoc에 "오늘 기준 과거 30일 ~ 미래 59일(양끝 포함 90일, 두 날짜의 차이 89일)" 명시
- [x] 청약일 기반 분기 로직 완전 제거
- [x] `IpoScheduleMergeServiceTest`에 정확한 날짜 범위 호출 검증 통합 테스트 추가
- [x] 기존 17개 단위 테스트 모두 통과
- [x] `UstockPlusCalendarClientTest` 10개 테스트 모두 통과
- [x] `checkstyleMain`, `checkstyleTest`, `spotlessCheck` 통과
- [x] `git diff --check` 통과
- [x] 커밋·푸시·배포 **하지 않음** (Master 검증 후 사용자 승인 대기)
- [ ] SF-2~SF-5는 향후 별도 작업으로 진행