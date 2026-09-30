# 공모주 청약 일정 자동 동기화 — 세부 기능 분해 (ipo-schedule-sync breakdown)

> **Epic**: Epic 17 (공모주 청약 일정 자동 동기화, ipo-schedule-sync)
> **분야**: 백엔드 위주(SF-1~4), 프론트엔드(SF-5)
> **작성일**: 2026-09-29 (Master가 사용자와의 실시간 조사·결정을 바탕으로 직접 작성)
> **근거 문서**: `docs/research/ipo-schedule-sync.md`(특히 7절 최종 결정), `docs/roadmap.md` 1.1절·6절(E17 카드)
> **의존 관계**: 없음(Epic 5 IPO 기준정보 위에 얹는 구조). Epic 19 SF-2(달력 뷰)가 이 Epic의 산출물을 기다린다.

---

## 이 Epic의 특이사항 (분해 전에 반드시 인지)

1. **기존 `Ipo` 엔티티(Epic 5)에 배정일·환불일 필드가 없다.** 현재 필드: `companyName`, `ipoName`, `stockCode`, `roundLabel`, `lowerBandPrice`, `upperBandPrice`, `fixedPrice`, `subscriptionStartDate`, `subscriptionEndDate`, `listingDate`, `offeringShareCount`, `status`. `listingDate`는 이미 있지만 지금까지는 사용자가 수동 입력했다 — 이번 Epic은 이 필드에 **자동으로 값을 채우는 배치**를 추가하는 것이지 스키마 구조 자체는 거의 그대로 쓴다. **배정일·환불일은 신규 컬럼**이 필요하다(`allotmentDate`, `refundDate`).
2. **이미 CSV 일괄 등록 기능이 있다.** `IpoCsvImportService`, `IpoImportResponse`/`IpoImportFailure` DTO가 Epic 5에 존재한다 — "여러 건을 한 번에 upsert하고 건별 성공/실패를 보고하는" 패턴이 이미 검증돼 있다. 이번 배치 동기화 서비스는 **이 패턴을 최대한 재사용**한다(엔드포인트만 "파일 업로드"에서 "외부 API 호출"로 바뀔 뿐, 파싱 후 저장 로직은 유사).
3. **두 외부 소스를 병합해야 한다.** OpenDART(청약일·공모가·주간사)와 ustockplus.com(상장예정일·배정일·환불일)의 응답을 합쳐 하나의 `Ipo` 레코드를 만들어야 하고, 두 소스의 종목 식별자 체계가 다르다(OpenDART=회사고유번호 `corp_code`, ustockplus=`stockCode`). **이 매핑이 이 Epic에서 가장 리스크가 큰 지점**이다(SF-3).
4. **비공식 API(ustockplus) 장애를 견뎌야 한다.** ustockplus 호출이 실패해도 OpenDART 데이터만으로 서비스가 정상 동작해야 한다(`docs/research/ipo-schedule-sync.md` 7.3절 4번 항목).
5. **수동 입력 데이터를 보호해야 한다.** 자동 동기화가 사용자가 이미 수동으로 입력·수정한 IPO 레코드를 덮어쓰면 안 된다.

---

## 착수 전 Master 확인 필요 (구현 게이트)

이 Epic은 `docs/research/ipo-schedule-sync.md`가 실제 API 원문을 상당 부분 확인했지만, 아직 남은 미확인 항목이 있다. **SF-1 착수 전 아래를 Master가 확인해 breakdown을 보정한다**:

```
[게이트] OpenDART DS006 API 인증키를 실제로 발급받아 호출 테스트를 1회 해봤는가?
    ─ 완료 → SF-1 착수 가능(실제 응답 스키마로 DTO 필드 확정)
    ─ 아니면 → 대기 (인증키 발급 후 실제 응답 1건을 확보하고 시작)

[게이트] ustockplus.com API의 Origin/Referer 헤더 방식이 여전히 유효한지 재확인했는가?
    ─ 완료 → SF-2 착수 가능
    ─ 아니면 → 대기 (비공식 API라 스펙이 바뀌었을 수 있음, 착수 직전 재확인 필수)
```

---

## 세부 기능(SF) 개요

총 **5개 SF**로 분해한다. 패턴은 다른 Epic과 동일하게 "외부 연동 클라이언트 → 데이터 정규화·병합 → 배치 스케줄러 → 조회 API → 프론트 선택 UI" 순서로 나눈다.

```
SF-1 (OpenDART 클라이언트: DS001 공시검색 + DS006 지분증권 조회)
  → SF-2 (ustockplus 클라이언트: 갭 필드 보완)
       → SF-3 (두 소스 정규화·병합 + upsert 서비스, 기존 CSV import 패턴 재사용)
            → SF-4 (배치 스케줄러 + 재시도/알림)
            → SF-5 (프론트: 청약 등록 화면에서 IPO 선택 드롭다운으로 전환 + GET /ipo-schedule 엔드포인트, Epic 19 SF-2 달력이 이 엔드포인트에 의존)
```

| SF | 내용 | 선행 SF |
|---|---|---|
| SF-1 | OpenDART 클라이언트(공시검색 + 증권신고서-지분증권) | 없음 (단, 위 게이트 필요) |
| SF-2 | ustockplus.com 클라이언트(갭 필드 보완) | 없음, SF-1과 병렬 가능 (단, 위 게이트 필요) |
| SF-3 | 정규화·병합·upsert 서비스 | SF-1 + SF-2 |
| SF-4 | 배치 스케줄러(주기 실행, 재시도, 실패 알림) | SF-3 |
| SF-5 | 프론트 — 청약 등록 화면 IPO 선택 드롭다운 전환, `GET /ipo-schedule` 응답 계약 확정 | SF-4 (엔드포인트 존재 필요) |

---

## SF-1: OpenDART 클라이언트 — 공시검색(DS001) + 증권신고서-지분증권(DS006)

**범위**: `com.vibeaiteam.ipocatalog`(또는 신규 `iposchedulesync`) 패키지에 OpenDART 호출 클라이언트를 만든다. 2단계 조회 구조를 구현한다: ① DS001 공시검색으로 최근 N일 내 "증권신고서(지분증권)" 제출 회사 목록을 얻는다 → ② 각 회사의 `corp_code`로 DS006 지분증권 API를 호출해 상세 필드를 얻는다.

**산출물 파일 경로** (패키지명은 spec 단계에서 확정, 잠정 `ipoexternal` 또는 `ipocatalog.sync`):
- `service/ipo/sync/opendart/OpenDartCorpDisclosureClient.java` — DS001 공시검색 호출(보고서명 필터: "증권신고서(지분증권)")
- `service/ipo/sync/opendart/OpenDartEquitySecuritiesClient.java` — DS006 지분증권 상세 조회
- `service/ipo/sync/opendart/dto/OpenDartDisclosureItem.java`, `OpenDartEquitySecurityDetail.java` — 응답 DTO(실제 API 응답 필드 그대로 매핑: `sbd`, `pymd`, `sband`, `asand`, `asstd`, `stkcnt`, `slprc`, `slta`, `actnmn` 등)
- `infrastructure.config.OpenDartApiProperties` — `crtfc_key`(인증키)를 환경변수로 주입(예: `OPENDART_API_KEY`), base URL 고정

**특이사항**:
- `RestClient`(Spring 6.1 기본, `spring-boot-starter-web`에 이미 포함 — `docs/research/broker-readonly-api-pilot.md` 7절에서 확인된 것과 동일한 결론)를 사용한다. `WebClient`는 `spring-webflux` 추가가 필요하므로 쓰지 않는다.
- 인증키는 `application.yml`에 `${OPENDART_API_KEY:}` 형태로 빈 값 허용 후, 호출 시점에 비어 있으면 명확한 예외를 던진다(기존 `APP_ENCRYPTION_KEY` 패턴과 동일 원칙).
- DS001 호출 시 `bgn_de`/`end_de`는 스케줄러가 넘기는 조회 기간(예: 최근 7일 신규 공시)을 사용한다 — 이 Epic이 "향후 3개월치 일정"을 보여주는 것과, "최근 며칠 새 새로 공시된 종목을 찾는 것"은 다른 축이라는 점에 주의(전자는 조회 결과 필터링, 후자는 신규 발견 로직).
- **Rate limit 미확인**(research 6절) — 재시도 로직은 SF-4에서 다루되, 클라이언트 자체에 요청 간 최소 간격(예: 200ms)을 기본 안전장치로 넣는다.

**AC 초안**:
- AC-1701: DS001로 최근 N일 내 지분증권 증권신고서 제출 회사 목록을 정상 조회한다
- AC-1702: DS006으로 특정 회사의 청약일·공모가·주간사 정보를 정상 조회한다
- AC-1703: 인증키 미설정 시 명확한 예외 메시지로 실패한다(무한 재시도 없음)
- AC-1704: OpenDART가 빈 결과를 반환해도(신규 공시 없음) 정상 처리된다(에러 아님)

---

## SF-2: ustockplus.com 클라이언트 — 갭 필드 보완

**범위**: `TO_BE_LISTED`(상장예정일), `ALLOTMENT_DATE`(배정일), `REFUND_DATE`(환불일)를 `api.ustockplus.com/v2/ipo/calendars`에서 조회하는 클라이언트를 만든다.

**산출물 파일 경로**:
- `service/ipo/sync/ustockplus/UstockPlusCalendarClient.java`
- `service/ipo/sync/ustockplus/dto/UstockPlusCalendarItem.java` — `stockCode`, `stockName`, `calendarStockType`, `startDate`, `endDate`, `extraInfo`
- `infrastructure.config.UstockPlusApiProperties` — Origin/Referer 헤더 값을 상수로 고정(`docs/research/ipo-schedule-sync.md` 7.3절에서 확인한 값)

**특이사항**:
- **이 클라이언트는 실패해도 전체 배치가 죽지 않아야 한다.** 호출부를 try-catch로 감싸고, 실패 시 해당 필드만 null로 남기고 OpenDART 데이터는 그대로 저장한다(SF-3에서 병합 시 반영).
- `x-ratelimit-*` 응답 헤더(초당 15회)를 존중해 클라이언트에 rate limiter를 둔다(Guava `RateLimiter` 등 이미 의존성에 있는 라이브러리 우선 검토, 없으면 간단한 세마포어/딜레이로 구현 — 새 의존성 추가는 지양).
- **비공식 API임을 클래스·설정 주석에 명시한다**(장애 시 담당자가 원인을 바로 알 수 있게). 예: `// 비공식 API — ustockplus/네이버가 자사 프론트 전용으로 제공, 공식 계약 없음. 장애 시 OpenDART만으로 폴백.`
- `calendarStockType`별로 다른 날짜 의미를 가지므로, 응답을 파싱할 때 이 필드로 분기해 `Ipo` 엔티티의 어느 필드에 매핑할지 결정한다.

**AC 초안**:
- AC-1710: `TO_BE_LISTED`/`ALLOTMENT_DATE`/`REFUND_DATE` 타입의 항목이 각각 올바른 필드로 매핑된다
- AC-1711: ustockplus 호출이 실패(네트워크 오류·403·스펙 변경)해도 예외가 상위로 전파되지 않고 로그만 남긴다
- AC-1712: rate limit을 초과하지 않도록 호출 간격이 제어된다

---

## SF-3: 정규화·병합·upsert 서비스

**범위**: SF-1·SF-2의 결과를 하나의 `Ipo` 레코드로 병합하고, 기존 `IpoRepository`에 upsert한다. **`IpoCsvImportService`의 기존 upsert/중복 판정/결과 보고 패턴을 최대한 재사용**한다(신규 로직을 처음부터 설계하지 않는다).

**산출물 파일 경로**:
- `service/ipo/sync/IpoSyncMergeService.java` — OpenDART 결과(회사고유번호 키)와 ustockplus 결과(종목코드 키)를 `stockCode` 기준으로 매칭·병합
- `domain/model/ipo/Ipo.java` — **필드 추가**: `allotmentDate`(LocalDate, nullable), `refundDate`(LocalDate, nullable), `syncSource`(String, nullable — "MANUAL"/"OPENDART"/"OPENDART+USTOCKPLUS" 등 출처 기록), `lastSyncedAt`(LocalDateTime, nullable)
- `backend/src/main/resources/db/migration/V{n}__add_ipo_sync_columns.sql` — 위 컬럼 추가 마이그레이션(NOT NULL 없이 전부 nullable로, 기존 행 영향 없음)
- `service/ipo/sync/IpoSyncResult.java` (+ `IpoSyncFailure.java`) — `IpoImportResponse`/`IpoImportFailure`와 동일한 패턴의 결과 DTO(신규/갱신/실패 건수 집계)

**특이사항 — 이 Epic에서 가장 주의가 필요한 부분**:
- **매칭 키**: 1순위 `stockCode`(종목코드, OpenDART 응답에는 없을 수 있어 회사명으로 보완 필요 — DS006 응답에 종목코드가 직접 없다면 DS001의 회사 목록에서 `stock_code` 필드를 같이 가져와야 한다. **이 부분은 SF-1 구현 시 실제 API 응답을 보고 정확한 필드를 확정해야 한다** — research 단계에서 DS001 상세 응답 필드까지는 조사하지 못했다).
- **수동 입력 보호**: `Ipo.syncSource`가 `null`(=사용자가 수동 생성)인 레코드는 자동 동기화가 **절대 덮어쓰지 않는다**. `syncSource`가 채워진(자동 생성/갱신된) 레코드만 자동 갱신 대상이다. 사용자가 자동 생성된 레코드를 수동으로 수정하면 어떻게 처리할지(수정 시점부터 그 레코드를 자동 갱신 대상에서 제외할지)는 spec에서 결정 필요 — 1차로는 "필드 단위가 아니라 레코드 단위로 최초 생성 주체만 구분"하는 단순 규칙으로 시작할 것을 권장한다.
- **중복 판정**: `(stockCode, subscriptionStartDate)` 복합 유니크로 동일 IPO를 식별한다(같은 종목이 여러 차례 공모하는 경우 — 회차 구분 `roundLabel`이 있으므로 완전히 새 레코드가 맞다).
- 병합 시 OpenDART 필드가 있으면 OpenDART를 우선하고(더 공식적인 소스), ustockplus는 OpenDART가 못 주는 필드(상장예정일·배정일·환불일)만 채운다 — 두 소스가 같은 필드에 다른 값을 줄 경우의 충돌 처리는 이번 범위에서 발생하지 않는다(필드가 겹치지 않게 설계했으므로).

**AC 초안**:
- AC-1720: OpenDART·ustockplus 결과가 `stockCode` 기준으로 정확히 병합된다
- AC-1721: 신규 IPO는 `syncSource`가 채워진 채로 생성된다
- AC-1722: `syncSource`가 null인(수동 생성) 레코드는 동기화 대상에서 제외된다
- AC-1723: 동일 `(stockCode, subscriptionStartDate)`가 이미 있으면 신규 생성 대신 갱신한다
- AC-1724: ustockplus 필드가 없어도(null) OpenDART 필드만으로 정상 upsert된다

---

## SF-4: 배치 스케줄러 — 주기 실행, 재시도, 실패 알림

**범위**: SF-3의 병합 서비스를 정기 실행하는 스케줄러를 추가한다.

**산출물 파일 경로**:
- `service/ipo/sync/IpoSyncScheduler.java` — `@Scheduled` 기반(Spring Boot 기본 제공, 새 의존성 불필요)
- `service/ipo/sync/IpoSyncRetryPolicy.java` — 지수 백오프 재시도(연구 문서 제안: 5분→10분→20분, 최대 3회)

**특이사항**:
- 스케줄 주기: research 결론(향후 3개월치를 1일 1회) 채택. `@Scheduled(cron = "...")`로 하루 1회, 향후 3개월 범위를 매번 전량 재동기화(증분 동기화보다 단순하고, upsert가 idempotent하므로 안전).
- 실패 알림은 이 프로젝트에 기존 알림 인프라(Slack/이메일 등)가 없으므로, **1차 범위는 로그 레벨 ERROR + `IpoSyncResult`를 별도 테이블 또는 로그 파일에 기록하는 수준**으로 최소화한다(운영자가 로그를 보고 파악). 실제 알림 채널 연동은 이 Epic 범위 밖으로 명시.
- 로컬 개발 환경에서 스케줄러가 자동 실행되지 않도록(테스트 시 실제 외부 API를 계속 호출하면 rate limit 소모) `@Profile` 또는 설정 플래그로 스케줄러 on/off를 제어한다.

**AC 초안**:
- AC-1730: 스케줄러가 설정된 주기로 SF-3 서비스를 호출한다
- AC-1731: 실패 시 지수 백오프로 최대 3회 재시도한다
- AC-1732: 3회 실패 후에도 서비스는 죽지 않고 다음 스케줄 주기에 다시 시도한다
- AC-1733: 로컬/테스트 프로필에서는 스케줄러가 비활성화된다

---

## SF-5: 프론트 — 청약 등록 화면 IPO 선택 드롭다운 전환 + 조회 API

> **폐기(2026-09-30)**: `GET /ipo-schedule`는 만들지 않는다. 동기화된 공모주는 `ipos`에 저장되고 기존 `GET /ipos`를 청약 등록 폼과 달력이 그대로 쓴다. 아래는 초기 설계 기록이다.

**범위**: 백엔드에 `GET /ipo-schedule`(가칭, 계정 소유권 검증 불필요 — 전역 데이터)을 신설하고, 프론트 청약 등록 화면(`SubscriptionRegisterForm`)이 기존 IPO 목록 조회를 이 엔드포인트로 전환한다.

**산출물 파일 경로**:
- `backend`: `controller/ipo/IpoScheduleController.java`(신규) 또는 기존 `IpoController`에 `GET /ipo-schedule` 메서드 추가 — 향후 N개월치 IPO 목록 반환(쿼리 파라미터로 기간 지정 가능)
- `frontend`: `ui/src/features/subscription-register/` 기존 IPO 선택 드롭다운이 이미 있다면(SF-1 research에서 확인됨 — "청약 등록: 투자자, 증권계좌, 공모주를 드롭다운으로 선택") 데이터 소스만 이 신규 엔드포인트로 교체. UI 구조 변경은 최소화한다.

**특이사항**:
- 이 엔드포인트는 **Epic 19 SF-2(공모주 일정 달력)도 그대로 재사용**한다 — 두 화면이 같은 API를 공유하므로 중복 엔드포인트를 만들지 않는다.
- 기존 청약 등록 폼이 어떤 API로 IPO 목록을 가져오고 있었는지(Epic 14 산출물) 먼저 확인 후, 그 자리를 교체하는 방식으로 최소 diff를 유지한다.

**AC 초안**:
- AC-1740: `GET /ipo-schedule`이 향후 N개월치 IPO 일정을 반환한다(계정 소유권 검증 없음)
- AC-1741: 청약 등록 화면의 IPO 선택 드롭다운이 자동 동기화된 목록을 보여준다
- AC-1742: 자동 동기화 목록에 없는 IPO도 수동 등록 경로(기존 Epic 5 등록 폼)로는 여전히 추가 가능하다(자동화가 수동 경로를 막지 않음)

---

## 참고

- **문서 의존성**: `docs/research/ipo-schedule-sync.md`(전체, 특히 7절), `docs/roadmap.md` 1.1절·6절
- **모듈 소유권**: 이 Epic의 신규 서비스는 기존 `ipocatalog`(Epic 5) 패키지를 확장하는 형태로 두거나 별도 하위 패키지(`ipocatalog.sync`)로 분리한다 — 정확한 패키지 경로는 spec 단계에서 확정.
- **검증 명령**: `docs/guide-backend.md` 2절 그대로(`DB_PASSWORD=vibe_local_password ./gradlew test --rerun && ./gradlew build`). 외부 API를 호출하는 클라이언트 테스트는 실제 네트워크 호출 없이 WireMock 또는 목(mock) 서버로 작성한다(CI/로컬 테스트가 외부 API 가용성에 의존하지 않도록).
- **금지사항**: ustockplus 호출 실패를 이유로 OpenDART 데이터 저장까지 막지 않는다(SF-2 특이사항 참고). 스케줄러가 테스트/CI 환경에서 자동 실행되지 않게 한다(SF-4).
