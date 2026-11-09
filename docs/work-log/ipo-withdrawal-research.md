# 작업 로그: SimwonAmericaCorp. 취소 IPO 달력/목록 잔류 조사

> 일자: 2026-11-09. 읽기 전용 연구/계획 워커. 코드 패치/POST/PATCH/DELETE/API 키 출력/운영 DB 접속 금지.

## 1. 작업 개요

사용자 신고: SimwonAmericaCorp. 취소 IPO가 달력/목록에 남는다. 원인 조사 및 개선안 마련. 소유 파일: docs/research/ipo-withdrawal-reconciliation.md, docs/work-log/ipo-withdrawal-research.md 두 개만.

## 2. 읽은 소스 경로와 행 근거

| # | 파일 | 행 | 확인 내용 |
|---|---|---|---|
| 1 | `backend/.../service/ipo/sync/IpoSyncPersistence.java` | 27-80 | persist(): corpCode ACTIVE 조회, 없으면 새 ACTIVE 생성, 취소 미확인 |
| 2 | `backend/.../service/ipo/sync/IpoSyncService.java` | 288-383 | processCompany(): DS006 max rceptNo 선택, rm 미확인 |
| 3 | `backend/.../opendart/OpenDartCorpDisclosureClient.java` | 206-230 | filterAndDeduplicate(): rm 미검사, 최신 rcept_dt 1건 |
| 4 | `backend/.../opendart/dto/OpenDartDisclosureItem.java` | 49, 56-63 | rm 필드 존재, isFinalizedReport()만 사용 |
| 5 | `backend/.../service/ipo/IpoService.java` | 36-40 | list(): deletedAt만 필터, status 미필터 |
| 6 | `backend/.../repository/ipo/IpoRepository.java` | 19, 25 | findAllByDeletedAtIsNull, findByCorpCodeAndStatusAndDeletedAtIsNull(ACTIVE) |
| 7 | `backend/.../domain/model/ipo/Ipo.java` | 99-103, 106-140 | deactivate(): INACTIVE+deletedAt, syncFromOpenDart: status 미변경 |
| 8 | `backend/.../domain/model/ipo/Ipo.java` | 38-68 | mergeSupplementalSchedule(): null만 채움 |
| 9 | `backend/.../sync/ustockplus/UstockPlusCalendarClient.java` | 71-103 | 예외 시 빈 리스트, 취소 오인 아님 |
| 10 | `backend/.../sync/IpoScheduleMergeService.java` | 71-131 | ustockplus 장애 시 0건 반환 |
| 11 | `backend/.../controller/ipo/IpoController.java` | 110-114 | deactivate(): DELETE, INACTIVE+deletedAt |
| 12 | `ui/src/features/ipo-calendar/buildCalendarEvents.ts` | 56-124 | status 미필터, 모든 IPO를 이벤트로 생성 |
| 13 | `ui/src/widgets/IpoCalendar/IpoCalendar.tsx` | 78-83 | ipos 그대로 전달, status 미필터 |
| 14 | `ui/src/entities/ipo/api-client.ts` | 6-8 | /ipos 전체 조회, status 미필터 |
| 15 | `ui/src/pages/IpoDetailPage/IpoDetailPage.tsx` | 45-60 | 날짜 기반 상태, DB status 미참조 |
| 16 | `backend/.../accountauthentication/domain/model/RecordStatus.java` | 1-8 | ACTIVE/INACTIVE/CANCELLED 열거 |

## 3. 조사 질문별 답변

### Q1. DART list의 철회신고서/정정신고서 report_nm/rm 최종보고서 처리 및 DS006이 원본만 남기는지
- `filterAndDeduplicate()`는 `isEquitySecuritiesReport()`로 정규화 후 "증권신고서(지분증권)" 포함 항목만 필터, 정정·발행조건확정도 포함됨 (OpenDartCorpDisclosureClient.java:208-209).
- 같은 corp_code 내 `rcept_dt`가 최신인 1건만 채택 (라인 213-219). `rm`은 미검사.
- `processCompany()`는 DS006 응답에서 `rceptNo`가 가장 큰(최신) 건을 선택 (IpoSyncService.java:334-340). 원본(최초 보고서)만 남기는 게 아니라 최신 접수번호 건을 선택.
- `OpenDartDisclosureItem.rm` 필드는 존재하지만 사용되지 않음 (라인 49). Master 확인: `rm`에 "철=본보고서는철회(간주)되었으니관련철회신고서참고" 정의가 있으나 코드에서 미참조.

### Q2. IpoSyncPersistence는 없어진 IPO 비활성화하는지, 기존 INACTIVE를 자동 재활성화하는지, 식별 컬럼은
- 비활성화하지 않음. `findByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, ACTIVE)`만 조회 (IpoSyncPersistence.java:45). CANCELLED/INACTIVE는 찾지 못함.
- INACTIVE 자동 재활성화도 직접 하지 않음. 대신 ACTIVE-only 조회에서 못 찾으면 새 ACTIVE 레코드를 생성해 효과적으로 재활성화 (라인 73-74).
- 식별: `corpCode` 우선 매칭. 실패하면 `subscriptionStartDate` + 이름 정규화 매칭 (라인 58-66). `roundLabel`, `rceptNo`는 식별에 사용되지 않음. `stockCode`는 null 가능.

### Q3. 변경일정 덮어쓰기/날짜 null 지우기/환불일 재계산/지난 청약기간 조회 제외
- `syncFromOpenDart()`: subscriptionStart/EndDate, allotmentDate, refundDate는 null이 아니면 덮어씀 (Ipo.java:129-136). fixedPrice는 null일 때만 설정 (라인 123-125).
- 날짜 null 지우기는 없음. `mergeSupplementalSchedule()`도 null만 채워 기존 값을 보존 (Ipo.java:50-61). 단 사용자 일정 변경 요구와 충돌하므로 보조 출처 소유 날짜에 한해 최신 근거로 갱신하는 개선안 필요 (연구 문서 6절).
- 환불일: `toResponse()`에서 refundDate 없으면 subscriptionEndDate + 2영업일로 estimated 계산 (IpoService.java:312-323).
- 지난 청약기간 조회 제외: `findAllByDeletedAtIsNull()`은 날짜 기준 필터링 없음 (IpoService.java:37, IpoRepository.java:19). 모든 ACTIVE IPO가 조회 범위에 포함됨.

### Q4. ustockplus 누락/장애/빈 응답을 취소로 오인하지 않고 명시 철회 증거만 반영하는 안
- `UstockPlusCalendarClient.fetchScheduleDates()`는 예외/빈 응답 시 WARN 로그만 남기고 빈 리스트 반환 (라인 96-103). 취소로 오인하지 않음.
- `IpoScheduleMergeService.merge()`도 ustockplus 호출 실패 시 0건 반환, OpenDART 결과 유지 (IpoScheduleMergeService.java:78-81, 100-101).
- 명시 철회 증거는 DART `rm` 필드에서만 수집 가능하나 현재 미확인. ustockplus는 취소 신호로 사용하지 않음.

### Q5. 수동 등록 보호와 관리자 임시 비활성화 수단 및 다음 sync 덮임 위험
- 수동 등록(`syncSource == null`)은 `persist()`에서 SKIPPED_MANUAL로 보호 (IpoSyncPersistence.java:48-50).
- 관리자 임시 비활성화: `deactivate()`는 status=INACTIVE + deletedAt=now (Ipo.java:99-103). `list()`는 deletedAt 필터로 제외 (IpoService.java:37).
- 다음 sync 덮임 위험: 비활성화된 레코드는 ACTIVE-only 조회에서 찾지 못해 새로 생성될 수 있음 (재활성화 위험, Q2 참조).

### Q6. Simwon은 미국 나스닥 상장으로 국내 공모 아닌 범위 혼입 가능성
- 회사명에 "America" 포함, stockCode=null. OpenDART DS001은 국내 증권신고서 대상.
- 회사명 America/stockCode=null만으로 해외 판단 금지. 공식 원문 확인 필요. 별도 검토 필요.

### Q7. 취소 기록은 물리 삭제하지 않고 기존 청약 배정 매도 이력 보존, 달력/신규 청약 제외와 상세 철회 표시 방법
- 현재 `deactivate()`는 소프트 삭제(deletedAt 설정). CANCELLED 상태는 enum에 존재하지만 IPO 전용 취소 로직은 없음 (RecordStatus.java:4-7, Ipo.java에 cancel() 메서드 없음).
- 청약·배정·매도 이력은 별도 테이블(subscriptions, allocations, sales)에 존재. 물리 삭제 금지는 현재 설계와 일치.
- 달력 제외: `buildCalendarEvents()`에서 `status === 'ACTIVE'` 필터 추가 필요 (buildCalendarEvents.ts:56-124).
- 상세 철회 표시: `IpoDetailPage.getStatus()`는 날짜 기반, DB status 미참조 (IpoDetailPage.tsx:45-60). status=CANCELLED 시 "취소됨" 표시 추가 필요.

### Q8. 재추진은 같은 회사와 동일 공모 라운드 구분, 오래된 정정이나 구분 불가 일괄 취소 위험
- 현재 최신 `rceptNo`만 선택하므로 오래된 정정은 자연스럽게 제외 (IpoSyncService.java:334-340).
- rm(철회) 확인 없이는 철회된 공시도 최신으로 선택될 위험.
- 동일 회사·동일 라운드는 `corpCode`+`subscriptionStartDate`로 식별. `roundLabel`은 식별 미사용.
- 구분 불가 일괄 취소 위험: 현재 개별 건별 처리이므로 일괄 취소 경로는 없음.

### Q9. 관련 외부 공식 문서 DART/OpenDART에서 취소/정정 정보 수집 가능성 검증
- DS001의 `rm` 비고에 철회 정보가 있을 수 있음 (Master 확인 근거: 공식 OpenDART 가이드).
- DART 원문 웹 도구 접근 불가로 2차 요약만 있음. 불확실하면 미확인으로 표시 (연구 문서 4절 미확인 항목 참조).
- 새 API 스펙은 미수립.

## 4. 동기화 범위 구분 (전체 API 목록 조회 vs DS001 공시 검색 기간)

DS001 `searchRecentEquitySecuritiesCompanies(daysBack)`는 "최근 N일 내 접수 공시"를 검색하는 것이지, 전체 IPO API 목록을 통째로 조회하는 것은 아니다. 범위 동기화(`syncByMonthRange`)도 월별 DS001 검색 기간으로 제한된다. 이 둘을 혼동하면 "전체 목록 재조회"로 오인할 수 있다.

## 5. 검증 결과 요약

| 검증 항목 | 결과 | 근거 |
|---|---|---|
| 코드 위치 확인 (실제 파일·행 읽음) | PASS | 위 16개 파일·행 확인 완료 |
| `git diff --check` | NOT_RUN | 코드 수정 없음, 문서 보정만 수행 |
| 백엔드 테스트 실행 | NOT_RUN | 코드 수정 없음 |
| UI 테스트 실행 | NOT_RUN | 코드 수정 없음 |

## 6. 다음 단계

Master 별도 검증 후, 코드상 재생성 위험(3-1~3-4)에 대한 최소 수정안 검토 및 테스트 추가 착수 여부 결정. 코드 패치는 이 워커의 범위 아님(읽기 전용).
