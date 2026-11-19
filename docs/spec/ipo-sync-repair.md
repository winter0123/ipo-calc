# IPO-Sync-Repair 명세 (Phase 0)

> **Epic**: E17 후속 결함 수선 (GitHub #507)
> **범위**: Phase 0 문서. 시행은 `docs/breakdown/ipo-sync-repair.md` 작성과 Master 승인 후
> **기준**: `docs/research/ipo-sync-repair.md`, `docs/spec/ipo-schedule-sync.md`, `docs/spec/automatic-ipo-reconciliation.md`
> **언어**: 한국어
> **상태**: Phase 0 초안

## 1. 목표

uStockPlus 89일 창 위반으로 중단된 TO_BE_LISTED 수집을 복구하고, DART 정정 증거와 희망가·확정가 반영 규칙을 원문 검증 기반으로 정의한다.

## 2. 요구사항과 Acceptance Criteria

### SF-1 uStockPlus 창 복원

- R-1: `IpoScheduleMergeService` 조회 창을 `a64429a8`의 `today-30/+59`(차이 정확히 89일)로 복원. ±90일 창은 폐기.
- AC-507-01: 실제 HTTP mocking(`MockRestServiceServer`) 클라이언트로 TO_BE_LISTED 수집·저장이 성공. 기존 `mapsThreeTypesAndIgnoresOthers` 유지.
- AC-507-02: `today-30/+59` 요청이 `IllegalArgumentException`을 던지지 않음 (`acceptsExactly89DayRange`와 동일 경계).
- AC-507-03: 수동 레코드(`syncSource==null`)는 상장일·청약·환불 모두 수정 불가 (`merge_skipsManualRecord_whenMatchedByStockCode` 유지).
- AC-507-04: `last_imported_*`와 같거나 현재 null인 날짜만 자동 정정 — 재실행·과거 공시 후퇴 방지.

### SF-2 DART 증거

- R-2: 같은 공모 회차의 증거 사용. DS006이 없어도 DS001 선택 접수번호로 원문 조회 허용.
- R-3: 원문/요약 출처 분리 — 원문 파서가 검증한 값만 `bandPriceSourceReceiptNo` / `fixedPriceSourceReceiptNo`에 기록.
- R-4: 역순 max 선택(`receivedOn→rceptNo`)과 필드별 최신값. 확정가는 정정 후 열의 주당 값만.
- AC-507-05: 원본/정정/확정 접수번호(`20261008000649`→`20261030000616`→`20261103000312`)는 원문 접근 제한으로 미확인 표기. `9400~11000`/`11000`은 테스트용 후보로 명시.
- AC-507-06: 임의 숫자 두 개 결합, 액면가·총액·정정 전 값은 확정가·밴드로 반영 불가.

### SF-3 희망가·확정가

- R-5: 희망가는 실제 위치의 양수 범위(하단/상단)만. 확정가는 정정 후 열의 주당 값만 저장.
- AC-507-07: 실제 확인 값만 DB에 반영. 미확인 값은 기록만 하고 저장하지 않음.
- AC-507-08: 항목별 접수번호(`sourceReceiptNo`) 저장으로 재실행·과거 공시 후퇴 방지. 수동값 보존.

### SF-4 운영 반영

- R-6: 운영 데이터는 미리보기 이후 별도 승인. IPO ID 보존(물리삭제 금지, soft delete). 정정·철회·재추진 시 일정 보호(`automatic-ipo-reconciliation` FR-04/06/07 재사용).
- AC-507-09: 실패 재현 절차 문서화 — `merge()` 실행 → WARN "ustockplus 일정 조회 실패" + `merged=0` 확인.

## 3. 비범위

소스 수정, 운영 DB 접근, 커밋/푸시, breakdown 작성(승인 후).

## 4. 검증 명령 (구현 단계)

- `./gradlew :backend:test --tests '*UstockPlusCalendarClientTest*'`
- `./gradlew :backend:test --tests '*IpoScheduleMergeServiceTest*'`
- `./gradlew :backend:test --tests '*IpoReconciliationServiceTest*'`
