# 연구: SimwonAmericaCorp. 취소 IPO가 달력/목록에 남는 원인과 개선안

> 기준일: 2026-11-09. 읽기 전용 연구. 코드/운영 데이터 수정 금지. 새 API 스펙 미수립.

## 1. 확인 사실 (운영 공개 API, 읽기만)

`GET https://ipocalc.duckdns.org/api/ipos` 응답 (2026-11-09):
- id20, companyName/ipoName: SimwonAmericaCorp., stockCode: null
- subscriptionStartDate: 2026-11-05, end: 2026-11-06, refundDate: 2026-11-09, estimated: false
- status: ACTIVE, updatedAt: 2026-11-09

사용자 신고: "취소인데 달력/목록에 남는다." 위 응답은 status=ACTIVE로 반환된다.
Simwon이 실제로 한 번 비활성화됐다 재생성됐는지는 미확인이므로, "현재 원인 확정"이 아닌 **코드상 재생성 위험**을 확정 수준으로 둔다.

## 2. 관련 소스 코드 위치 (실제로 읽은 파일과 행)

| 파일 | 주요 행 | 용도 |
|---|---|---|
| `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoSyncPersistence.java` | 27-80 | persist(): corpCode ACTIVE 조회 후 갱신/생성, 취소 미확인 |
| `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoSyncService.java` | 288-383 | processCompany(): DS006 max rceptNo 선택, rm 미확인 |
| `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/opendart/OpenDartCorpDisclosureClient.java` | 206-230 | filterAndDeduplicate(): rm 미검사, 최신 rcept_dt 1건만 |
| `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/opendart/dto/OpenDartDisclosureItem.java` | 49, 56-63 | rm 필드 존재, isFinalizedReport()만 사용 |
| `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/IpoService.java` | 36-40 | list(): deletedAt만 필터, status 미필터 |
| `backend/src/main/java/com/ipocalc/ipocatalog/repository/ipo/IpoRepository.java` | 19, 25 | findAllByDeletedAtIsNull, findByCorpCodeAndStatusAndDeletedAtIsNull(ACTIVE만) |
| `backend/src/main/java/com/ipocalc/ipocatalog/domain/model/ipo/Ipo.java` | 99-103, 106-140 | deactivate(): INACTIVE+deletedAt, syncFromOpenDart: status 미변경 |
| `backend/src/main/java/com/ipocalc/ipocatalog/domain/model/ipo/Ipo.java` | 38-68 | mergeSupplementalSchedule(): null만 채움, 덮어쓰지 않음 |
| `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/ustockplus/UstockPlusCalendarClient.java` | 71-103 | 예외 시 빈 리스트, 취소 오인 아님 |
| `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoScheduleMergeService.java` | 71-131 | merge(): ustockplus 장애 시 빈 리스트, 0건 반환 |
| `backend/src/main/java/com/ipocalc/ipocatalog/controller/ipo/IpoController.java` | 110-114 | deactivate(): DELETE 엔드포인트, INACTIVE+deletedAt |
| `ui/src/features/ipo-calendar/buildCalendarEvents.ts` | 56-124 | status 미필터, 모든 IPO를 이벤트로 생성 |
| `ui/src/widgets/IpoCalendar/IpoCalendar.tsx` | 78-83 | ipos 그대로 전달, status 미필터 |
| `ui/src/entities/ipo/api-client.ts` | 6-8 | /ipos 전체 조회, status 미필터 |
| `ui/src/pages/IpoDetailPage/IpoDetailPage.tsx` | 45-60 | 날짜 기반 상태, DB status 미참조 |

## 3. 코드상 재생성 위험 (Simwon 실제 재생성은 미확인)

### 3-1. IpoSyncPersistence가 취소/비활성화 IPO를 다시 ACTIVE로 생성할 위험 (코드상 확정)
`IpoSyncPersistence.persist()`는 `findByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, RecordStatus.ACTIVE)`만 조회한다 (라인 45). 취소(CANCELLED) 또는 비활성화(INACTIVE+deletedAt)된 레코드는 ACTIVE-only 조회에서 찾을 수 없다. 이후 이름+시작일 2차 매칭도 `RecordStatus.ACTIVE`만 조회한다 (라인 60). 둘 다 실패하면 새 ACTIVE 레코드를 생성한다 (라인 73-74). 따라서 관리자가 비활성화/취소한 후 다음 자동 동기화가 돌면 해당 IPO가 부활해 다시 달력/목록에 나타날 수 있다.

### 3-2. OpenDART rm(철회 비고) 필드를 확인하지 않음 (코드상 확정)
`OpenDartDisclosureItem`에 `rm` 필드가 존재한다 (라인 49). Master 확인 근거: 공식 OpenDART DS001 가이드에 `rm` 비고에 "본보고서는철회(간주)되었으니관련철회신고서참고" 정의가 있음. 하지만 `filterAndDeduplicate()`는 `isEquitySecuritiesReport()`만 필터하고 `rm`을 검사하지 않는다 (라인 206-230). `processCompany()`도 DS006의 최대 `rceptNo`만 선택하고 `rm`을 보지 않는다 (IpoSyncService.java:334-340). 따라서 철회 공시가 최신 접수번호로 선택되어도 취소로 인식되지 않는다. rm 철공시를 단순 skip만 하면 기존 ACTIVE가 남아, 해당 공모건의 상태 전이를 함께 반영해야 한다.

### 3-3. IpoService.list()의 status 필터 부재는 방어 공백, 이번 ACTIVE 잔존 직접 원인은 아님
`list()`는 `findAllByDeletedAtIsNullOrderByCreatedAtDesc()`를 호출한다 (라인 37). `deactivate()`는 `status=INACTIVE`와 `deletedAt=now`를 함께 설정하므로 (Ipo.java:99-103) 현재 DELETE 경로는 목록에서 제외된다. status 필터 부재는 향후 CANCELLED + deletedAt=null에 대한 방어 공백이며, 이번 ACTIVE 잔존의 직접 원인은 아니다.

### 3-4. 프런트엔드 달력/목록이 status를 필터하지 않음 (코드상 확정)
`buildCalendarEvents()`는 전달받은 모든 IPO에 대해 이벤트를 생성하며 status를 확인하지 않는다 (buildCalendarEvents.ts:56-124). `IpoCalendar.tsx`도 `ipos`를 그대로 전달받는다 (IpoCalendar.tsx:78). 백엔드가 ACTIVE로 내려주면 프런트는 무조건 표시한다.

## 4. 아직 미확인

| 항목 | 미확인 이유 |
|---|---|
| Simwon이 이전에 deactivate/취소된 이력이 있는지 | 운영 DB 접근 금지, 조회 전용 API만 확인 |
| OpenDART DS001 응답에 Simwon의 `rm` 필드가 철회 표시로 들어오는지 | DART 원문 웹 도구 접근 불가, 2차 요약만 있음 |
| Simwon이 미국 나스닥 상장으로 OpenDART DS001 범위 밖인지 | 회사명 "America"/stockCode=null만으로 해외 판단 금지. 공식 원문 확인 필요 |
| `deactivate()` 외에 IPO를 CANCELLED 상태로 만드는 코드 경로 | 현재 코드에 IPO 전용 cancel() 메서드 없음 |

## 5. 최소 수정안 (읽기 전용 검토, 코드 패치 금지)

1. `IpoSyncPersistence.persist()`에서 CANCELLED/INACTIVE + deletedAt IS NOT NULL인 기존 레코드를 확인해 이미 취소·비활성화된 IPO는 갱신/재생성하지 않도록 한다.
2. rm 철공시를 단순 skip만 하면 기존 ACTIVE가 남으므로, 해당 공모건의 상태 전이를 함께 반영한다. 회사 전체 영구 차단은 금지하며, 원공시·공모건 식별 근거를 확인하고 재추진은 새 건으로 처리한다.
3. `IpoService.list()`에서 `RecordStatus.ACTIVE`만 조회하도록 필터를 추가한다(기존 `deactivate()`는 deletedAt을 이미 설정하므로 영향 적음).
4. 프런트엔드 `buildCalendarEvents()` 또는 `IpoCalendar.tsx`에서 `status === 'ACTIVE'`인 IPO만 이벤트로 생성한다.
5. IPO 전용 취소(논리 취소, status=CANCELLED, deletedAt 미설정 보존) 도메인 메서드 `cancel()`을 추가해 청약·배정·매도 이력은 보존하되 달력/목록에서 제외한다.

## 6. 안전한 후속 개선 (일정 변경 요구 반영)

- `mergeSupplementalSchedule()` 현행 유지 주장은 사용자의 일정 변경 요구와 충돌. 보조 출처(ustockplus)가 소유한 날짜(allotment/refund/listing)에 한해 최신 근거로 갱신하고, 수동 입력 날짜는 보호한다.
- 소유 출처·접수 버전 확인 필요. 값 누락과 철회를 구분하고, 오래된 정보 역행을 방지한다.
- 연기 후 미정이면 old date 노출 방지: 명시적 철회 증거로만 clear한다.
- 수동 등록 보호(`syncSource == null`)는 기존 로직 유지. ustockplus 장애/빈 응답은 이미 빈 리스트로 처리돼 취소로 오인하지 않음.
- 재추진 시 같은 회사·동일 라운드 구분은 `corpCode`+`subscriptionStartDate`로 식별. `roundLabel`은 식별 미사용.

## 7. 동기화 범위 구분 (전체 API 목록 조회 vs DS001 공시 검색 기간)

DS001 `searchRecentEquitySecuritiesCompanies(daysBack)`는 "최근 N일 내 접수 공시"를 검색하는 것이지, 전체 IPO API 목록을 통째로 조회하는 것은 아니다. 범위 동기화(`syncByMonthRange`)도 월별 DS001 검색 기간으로 제한된다. 이 둘을 혼동하면 "전체 목록 재조회"로 오인할 수 있다.

## 8. 필요 테스트 (NOT_RUN, 미실행)

- `IpoSyncServiceTest`: 취소(CANCELLED) 상태 기존 레코드가 sync 시 재생성되지 않는 테스트 추가.
- `IpoSyncPersistenceTest`: ACTIVE가 아닌 기존 레코드 조회 시 SKIP 또는 보존 로직 테스트 추가.
- `OpenDartCorpDisclosureClientTest`: `rm` 필드에 철회 표시가 있을 때 해당 항목이 동기화에서 제외되는 테스트 추가.
- `IpoServiceTest`: `list()`가 `RecordStatus.ACTIVE`만 반환하는 테스트 추가.
- `buildCalendarEvents.test.ts`: `status !== 'ACTIVE'` IPO가 달력 이벤트에서 제외되는 테스트 추가.
- 전체: `./gradlew test` (슬롯4), `pnpm exec vitest run` (UI) 실행 필요. 현재 NOT_RUN.

## 9. 검증 결과 요약

| 검증 항목 | 결과 | 근거 |
|---|---|---|
| 코드 위치 확인 (실제 파일·행 읽음) | PASS | 위 15개 파일·행 확인 완료 |
| `git diff --check` | NOT_RUN | 코드 수정 없음, 문서 보정만 수행 |
| 백엔드 테스트 실행 | NOT_RUN | 코드 수정 없음 |
| UI 테스트 실행 | NOT_RUN | 코드 수정 없음 |
