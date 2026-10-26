# IPO 동기화 회사 처리 중복 조사

> 기준: main `5855945a` 및 첫 리팩토링 SF 적용 작업 트리 (2026-10-26)
> 목적: 최근 N일 조회와 월 범위 조회가 공유하는 회사 단위 처리 규칙을 확인한다.

## 확인된 흐름

`IpoSyncService`에는 최근 N일 조회용 `processCompany`와 월 범위 조회용 `processCompanyWithRange`가 있다. 두 경로 모두 stock_code가 있는 건을 제외하고, DS006 상세를 가져와 최신 접수번호를 고르고, 청약일 파싱/범위를 검사한 다음 건별 `TransactionTemplate`로 upsert한다. 인증 오류와 OpenDART 020은 전체 실행을 중단하고, 그 외 DS006 API 오류와 잘못된 상세 데이터는 해당 회사 실패로 기록하며 다음 회사로 진행한다.

두 메서드의 주요 차이는 DS006 client 호출 인자(전체 상세 vs 지정 기간)다. 월 범위 테스트는 월별 `searchByDateRange`, 제한된 상세 기간, 인증/020/일반 API 실패와 일정 병합을 확인한다. 일반 서비스 테스트는 생성·갱신·수동 레코드 보호·날짜 파싱·회사 실패·중단과 일정 병합 계약을 다룬다.

## 위험

- 공통화 중 일반 DS006 예외를 실행 중단으로 바꾸거나, 인증/020을 회사 단위 실패로 약화할 수 있다.
- 최신 `rceptNo` 선택, 날짜 오류 조건, 회사 단위 독립 transaction, 실패 카운터가 달라질 수 있다.
- 로그 문구보다 `IpoSyncResult`의 `aborted`, `retryable`, failure count 및 repository 결과가 계약 기준이다.
- OpenDART 키 등 민감정보를 로그에 추가하지 않는다.

## 제안

회사별 처리를 하나의 내부 함수로 통합하고 DS006 fetch 동작만 함수 인자로 전달한다. `sync(int)`와 `syncByMonthRange(...)`의 public API, 회사/월 루프, lock, 일정 merge, repository 및 domain 규칙은 변경하지 않는다.

## 후속 조사 — 영속화 경계

회사별 상세 fetch·파싱은 `processCompany`에서 끝나며 transaction은 그 다음에 시작한다. transaction 내부의 기존/수동 IPO 판별, 새 IPO 생성, 자동 레코드 갱신은 `IpoSyncPersistence`로 옮길 수 있다. transaction은 coordinator가 계속 열고 닫으므로 외부 네트워크 호출은 transaction 바깥에 남는다. persistence는 `CREATED`/`UPDATED`/`SKIPPED_MANUAL` 결과를 반환하고 coordinator가 기존 결과 카운터를 갱신한다.
# IPO sync 추가 중복 조사

기존 SF-1에서 두 실행 경로의 회사별 검증·상세 선택·건별 저장은 `processCompany`로 통합됐다. 현재 코드의 `doSync`, `doSyncForMonth`, `emptyResult`는 같은 0 카운터·빈 failures·미중단 초기 상태를 각각 빌드한다. 결과 필드 초기화만 같은 helper로 합칠 수 있으며 실행 경로나 실패 처리 정책과 독립적이다. `IpoSyncServiceTest.sync_emptyDs001Result_returnsEmptyResult`와 `IpoSyncServiceMonthRangeTest.syncByMonthRange_emptyCompanies_returnsEmptyResult`가 결과 초기값 회귀 기준이다.
