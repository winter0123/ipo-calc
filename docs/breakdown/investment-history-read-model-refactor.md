# 투자내역 read model 리팩토링 Breakdown

> Epic: `application-refactoring` 후속 트랙
> 이 문서의 SF-1만 이번 시작 작업이다. 다음 단계는 SF-1 검증 이후 새로 승인한다.

## SF-1 — 페이지 투자내역 배치 로더 분리

### 범위

- 소유: `backend/src/main/java/com/ipocalc/investmentqueryapi/service/investmenthistory/`의 목록 조회 로더와 query service.
- 테스트: `backend/src/test/java/com/ipocalc/investmentqueryapi/service/investmenthistory/InvestmentHistoryQueryServiceTest.java` 및 필요한 로더 단위 테스트.
- 연구/spec/architecture: `docs/research/investment-history-read-model-refactor.md`, `docs/spec/investment-history-read-model-refactor.md`, `docs/architecture/investment-history-read-model-refactor.md`.
- 계정 범위, API/DTO 및 페이지 조건은 변경하지 않는다. 상세 조회의 참조 데이터 로딩만 전담 로더로 옮긴다.

### Acceptance Criteria

Spec의 모든 AC를 만족한다. 특히 빈 페이지와 한 페이지의 batch repository 호출, allocation→sale→subscription 매핑, 현재 계정 조건, page metadata, 상세 조회의 allocation/sale 및 참조 이름 결과를 검증한다.

### 선행 조건

현재 기준 커밋과 깨끗한 작업 트리를 확인한다. API/DTO/controller와 소유권 verifier 및 repository 선언을 검토한다.

### 검증

- 먼저 관련 테스트를 슬롯 DB에서 실행한다.
- 그 뒤 backend 가이드의 전체 `test --rerun`, `checkstyleMain`, `checkstyleTest`, `spotlessCheck`, `build` 명령을 실행한다.
- DB 비밀번호는 로컬 `.envrc`의 개발용 값을 사용하고 출력·기록하지 않는다. DB 슬롯 4는 다른 사용 중이 없음을 확인한 후에만 reset한다.
- 외부 DART/증권 API와 운영 DB는 사용하지 않는다.

### 완료 산출

Diff 검토, 실행 명령과 PASS/FAIL/NOT_RUN/BLOCKED 결과, 변경된 파일, 다음 후보 및 미해결 위험을 해당 Epic work-log와 `docs/current-status.md`에 기록한다.
