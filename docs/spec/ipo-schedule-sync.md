# 공모주 청약 일정 자동 동기화 명세 (ipo-schedule-sync)

> **Epic**: E17 (공모주 청약 일정 자동 동기화, ipo-schedule-sync)
> **범위**: SF-1(OpenDART 클라이언트) 구현 워커를 위한 상세 명세. SF-2~5는 개요 수준.
> **기준 문서**: `docs/research/ipo-schedule-sync.md`(특히 7절 최종 결정), `docs/breakdown/ipo-schedule-sync.md`, `docs/spec/ipo-catalog.md`, `docs/architecture/ipo-catalog.md`
> **언어**: 한국어 (표준 IT 용어 한정)
> **상태**: 착수 전 게이트 (a)(b) 충족(2026-09-29) — SF-1 구현 착수

## 문서 개요

이 문서는 Epic 17 "공모주 청약 일정 자동 동기화"의 구현 명세다. 공모주 청약/공모가/주간사 일정을 외부 API에서 자동 수집해 `Ipo` 기준정보를 채우고, 사용자가 청약 등록 시 목록에서 선택할 수 있도록 한다.

데이터소스는 `docs/research/ipo-schedule-sync.md` 7절(2026-09-29 확정)에 따라 **OpenDART DS006(증권신고서-지분증권)**을 기본 소스로 하고, **api.ustockplus.com**(네이버 u+stock 백엔드, 비공식)으로 갭 필드(상장예정일·배정일·환불일)를 보완하는 하이브리드 구조다.

이번 세션에 구현하는 것은 **SF-1(OpenDART 클라이언트)**만이며, SF-2~5는 개요·인터페이스 경계 수준으로 쓴다.

## 목차

1. [개요](#1-개요)
2. [용어](#2-용어)
3. [데이터소스별 필드 매핑](#3-데이터소스별-필드-매핑)
4. [SF-1: OpenDART 클라이언트 — 상세 구현 명세](#4-sf-1-opendart-클라이언트--상세-구현-명세)
5. [SF-2: ustockplus.com 클라이언트 — 개요](#5-sf-2-ustockpluscom-클라이언트--개요)
6. [SF-3: 정규화·병합·upsert 서비스 — 개요](#6-sf-3-정규화병합upsert-서비스--개요)
7. [SF-4: 배치 스케줄러 — 개요](#7-sf-4-배치-스케줄러--개요)
8. [SF-5: 프론트 연동 — 개요](#8-sf-5-프론트-연동--개요)
9. [착수 전 게이트](#9-착수-전-게이트)
10. [핵심 설계 원칙 (AC/규칙)](#10-핵심-설계-원칙-ac규칙)
11. [Acceptance Criteria (AC-1701~1704)](#11-acceptance-criteria-ac-17011704)
12. [엣지 케이스](#12-엣지-케이스)
13. [미정·확인 필요 사항](#13-미정확인-필요-사항)

---

## 1. 개요

### 1.1 목표
공모주 청약/공모가/주간사/상장예정일/배정일/환불일 일정을 외부 API에서 자동으로 수집하여 `Ipo` 기준정보(Epic 5)를 채운다. 사용자는 청약 등록 시 기존 직접 입력 방식 외에 자동 동기화된 목록에서 선택할 수 있다.

### 1.2 데이터소스 (확정 — `docs/research/ipo-schedule-sync.md` 7절)
| 우선순위 | 소스 | API | 담당 필드 | 인증 | 비고 |
|---|---|---|---|---|---|
| 1차 (기본) | 금융감독원 OpenDART | `DS006`(증권신고서-지분증권) | 청약기일(sbd), 납입기일(pymd), 공모가(slprc), 주간사(actnmn), 수량(stkcnt), 청약공고일(sband), 배정공고일(asand), 배정기준일(asstd), 모집총액(slta) | `crtfc_key`(무료, 직접 발급) | 공식·안정적. 회사고유번호 기준 조회 → DS001 공시검색 전단계 필요 |
| 2차 (보완) | api.ustockplus.com (네이버 u+stock 백엔드) | `GET /v2/ipo/calendars` | 상장예정일(TO_BE_LISTED), 배정일(ALLOTMENT_DATE), 환불일(REFUND_DATE) | 불필요 (Origin/Referer 헤더만 고정) | 비공식·미계약. OpenDART가 못 주는 갭 필드만 보완. 호출 실패 시 OpenDART 데이터만으로 서비스 유지 |

38.co.kr·네이버 화면 직접 크롤링은 `docs/research/ipo-schedule-sync.md` 7.2절에서 **기각**됨(저작권·이용약관 리스크).

### 1.3 2단계 호출 구조 (OpenDART)
OpenDART는 회사고유번호(`corp_code`) 기준 조회이므로, "앞으로 청약할 종목 전체 목록"을 얻으려면 2단계 구조가 필요하다(`docs/research/ipo-schedule-sync.md` 7.1절 확인):

1. **DS001(공시정보 → 공시검색)**: 최근 N일 내 "증권신고서(지분증권)" 제출 회사 목록을 조회 → 각 회사의 `corp_code`와 `stock_code` 획득
2. **DS006(증권신고서-지분증권)**: 각 `corp_code`로 상세 필드(sbd, pymd, slprc, actnmn 등) 조회

이 구조에서 DS001 응답의 `stock_code` 필드가 SF-3의 종목코드 매핑 핵심이 된다(DS006 응답에는 `stock_code`가 직접 포함되지 않을 수 있음 — 7.3절 및 SF-1 §4.4 참고).

### 1.4 SF 간 의존성
```
SF-1 (OpenDART 클라이언트) ──┐
                             ├──→ SF-3 (병합·upsert) → SF-4 (스케줄러) → SF-5 (프론트 API)
SF-2 (ustockplus 클라이언트) ──┘
```
SF-1과 SF-2는 병렬 가능하지만, 두 클라이언트의 **결과 병합**(SF-3)은 이 둘을 모두 필요로 한다.

## 2. 용어

| 용어 | 정의 |
|------|------|
| `corp_code` | OpenDART 기업 고유번호(8자리). DS001/DS006 조회 키. 종목코드와 다름 |
| `stockCode` | 한국거래소 종목코드(6자리 숫자 문자열). `Ipo.stockCode` 컬럼, 두 소스 매핑 키 |
| `sbd` | DS006 응답 필드: 청약기일 (청약 시작일) |
| `pymd` | DS006 응답 필드: 납입기일 (청약 납입 마감일) |
| `slprc` | DS006 응답 필드: 모집(매출)가액 (= 공모가. 자세한 의미는 §4.6 재검증) |
| `stkcnt` | DS006 응답 필드: 증권수량 (= 공모주식수) |
| `actnmn` | DS006 응답 필드: 인수인명 (= 주간사) |
| `TO_BE_LISTED` | ustockplus `calendarStockType` 값: 상장예정일 |
| `ALLOTMENT_DATE` | ustockplus `calendarStockType` 값: 배정일 |
| `REFUND_DATE` | ustockplus `calendarStockType` 값: 환불일 |
| `syncSource` | 자동 동기화 출처 기록 필드 (SF-3 신규 컬럼). `null` = 수동 등록 |
| `RestClient` | Spring 6.1+ `org.springframework.web.client.RestClient`. `spring-boot-starter-web`에 포함. WebClient는 금지 |
| `MockRestServiceServer` | `spring-boot-starter-test`에 포함된 `RestClient` 목 서버. WireMock이나 새 의존성 없이 `RestClient.builder().build()`에 `bindTo()`로 테스트 가능 |

## 3. 데이터소스별 필드 매핑

### 3.1 OpenDART DS006 응답 필드 → Ipo 엔티티 매핑

> **근거**: `docs/research/ipo-schedule-sync.md` 7.1절 (Master WebFetch 직접 확인). OpenDART 응답 스키마는 `status`(응답 코드) + `list`(결과 배열) 구조이며, 각 요소의 필드명은 아래와 같다.

**7.1절 확인된 갭**: DS006 응답에 **상장예정일(listing date) 필드가 없다**. `listingDate`는 ustockplus `TO_BE_LISTED`에서 보완받는다(§3.2).

### 3.2 ustockplus 캘린더 응답 필드 → Ipo 엔티티 매핑 (SF-2 범위)

> **근거**: `docs/research/ipo-schedule-sync.md` 7.3절. 응답 구조: `ipoCalendarResponses[]` 배열, 각 항목은 `stockCode`, `stockName`, `calendarStockType`, `startDate`, `endDate`, `extraInfo{finalOfferPrice, lockUpPromiseRatio, instCompetitiveness}`.

| ustockplus 필드 | 의미 | Ipo 엔티티 필드 | calendarStockType 값 | 비고 |
|---|---|---|---|---|
| `stockCode` | 종목코드 | `stockCode` | — | DS006 `corp_code`와 다른 체계. SF-3 매핑 키로 사용 |
| `calendarStockType` = `TO_BE_LISTED` | 상장예정일 | `listingDate` | TO_BE_LISTED | startDate 사용 |
| `calendarStockType` = `ALLOTMENT_DATE` | 배정일 | `allotmentDate` | ALLOTMENT_DATE | SF-3 신규 컬럼 |
| `calendarStockType` = `REFUND_DATE` | 환불일 | `refundDate` | REFUND_DATE | SF-3 신규 컬럼 |
| `startDate` / `endDate` | 날짜 범위 | 개별 필드에 매핑 | calendarStockType별 분기 |  |
| `extraInfo.finalOfferPrice` | 최종공모가 | `fixedPrice` 후보 | EXAMINATION_REQUESTED 등 | 미정, DS006 slprc와 충돌 가능성 |

### 3.3 두 소스의 식별자 차이 (핵심 리스크)

| 소스 | 식별자 | 타입 | 매핑 전략 |
|---|---|---|---|
| OpenDART DS001/DS006 | `corp_code` | 8자리 숫자 문자열 | 종목코드가 없을 때 회사명으로 보완 |
| ustockplus | `stockCode` | 6자리 숫자 문자열 | `Ipo.stockCode`와 직접 매핑 |

DS006 응답에 `stock_code` 필드가 있는지 **실제 API 호출로 확인 필요**(7.3절 "미정, 확인 필요"). DS001 공시검색 응답에서 `stock_code`를 함께 조회하면 매핑이 가능하나, 필드명과 존재 여부는 샘플 응답으로 재검증 필요.

## 4. SF-1: OpenDART 클라이언트 — 상세 구현 명세

### 4.1 패키지·파일 경로 후보 (Master 최종 결정)
기존 패키지 컨벤션 `com.ipocalc.ipocatalog.service.ipo.{subfeature}`를 따르는 후보:

| 후보 | Java 패키지 | 장점 |
|---|---|---|
| 후보 A (권장) | `com.ipocalc.ipocatalog.service.ipo.sync.opendart` | `service.ipo.sync.opendart` 하위 패키지로 외부 연동 클라이언트를 기존 ipocatalog service 계층에 배치. `dto`는 `com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto`에 배치 |
| 후보 B | `com.ipocalc.ipocatalog.infrastructure.opendart` | architecture 문서의 `infrastructure` 레이어와 정렬. 하지만 기존 코드에서 `ipocatalog` 패키지에는 `infrastructure` 하위 패키지가 없음(다른 Epic의 `securitiesaccountmanagement.infrastructure` 참조) |

> **Master 결정 표시**: ✅ 후보 A (`com.ipocalc.ipocatalog.service.ipo.sync.opendart`)를 기본으로 하지만, Master가 직접 패키지 경로를 확정한다.

### 4.2 산출물 파일 목록 (breakdown §SF-1과 동일)

> **패키지**: `com.ipocalc.ipocatalog.service.ipo.sync.opendart` (후보 A, Master 최종 결정)

```
backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/opendart/
├── OpenDartCorpDisclosureClient.java          — DS001 공시검색 호출(보고서명 필터: "증권신고서(지분증권)")
├── OpenDartEquitySecuritiesClient.java         — DS006 지분증권 상세 조회
├── OpenDartApiProperties.java                  — 설정 프로퍼티 (crtfc_key, base-url). 같은 패키지에 둔다(infrastructure.config는 ipocatalog에 없음)
└── dto/
    ├── OpenDartDisclosureItem.java             — DS001 응답 개별 항목 (corp_code, corp_name, stock_code)
    └── OpenDartEquitySecurityDetail.java         — DS006 응답 개별 항목 (sbd, pymd, sband, asand, asstd, stksen, stkcnt, slprc, slta, actnmn)
```

| 클래스 | 책임 | AC |
|---|---|---|
| `OpenDartCorpDisclosureClient` | DS001 공시검색 호출 | AC-1701 |
| `OpenDartEquitySecuritiesClient` | DS006 지분증권 상세 조회 | AC-1702, AC-1703, AC-1704 |
| `OpenDartApiProperties` | API 키·베이스 URL 설정 | AC-1703 |

> **참고**: `OpenDartApiProperties`는 기존 `@Value("${app.encryption.key:}")` 패턴(`AccountNumberEncryptor.java:35`)을 따른다. `@ConfigurationProperties`는 현재 프로젝트에 사용되지 않음(확인: grep `@ConfigurationProperties` 0건).

### 4.3 2단계 호출 구조

**Step 1 — DS001(공시검색)**: `corp_code` 목록 획득
- 엔드포인트: `GET https://opendart.fss.or.kr/api/list.json` (JSON). `apiGrpCd=DS001`은 `docs/research/ipo-schedule-sync.md` 7.1절에 기록됨
- 요청 파라미터: `crtfc_key`, `bgn_de`(YYYYMMDD), `end_de`(YYYYMMDD), `pblntf_detail_ty=C001`(증권신고서-지분증권 분류, 동작 확인), `page_no`, `page_count`
- 응답 필드 (2026-09-29 실측): `status`, `message`, `page_no`, `page_count`, `total_count`, `total_page`, `list[]`. `list` 항목 필드: `corp_code`, `corp_name`, `stock_code`, `corp_cls`, `report_nm`, `rcept_no`, `flr_nm`, `rcept_dt`, `rm`
- 클라이언트 필터: report_nm에 "증권신고서(지분증권)"이 포함된 항목만 남긴다. 같은 corp_code의 정정·발행조건확정 공시는 report_nm 접두어([기재정정] 등)를 제거해 한 건으로 합치고 rcept_dt가 가장 최신인 항목을 채택한다.

**Step 2 — DS006(지분증권 상세)**: 각 `corp_code`로 필드 조회
- 엔드포인트: `GET https://opendart.fss.or.kr/api/estkRs.json` (JSON). `apiGrpCd=DS006`, `apiId=2020054` — `docs/research/ipo-schedule-sync.md` 7.1절에 기록됨
- 요청 파라미터: `crtfc_key`, `corp_code`(8자리), `bgn_de`, `end_de`
- 응답: `status` 필드 + `group[]` 배열. 각 원소는 `{title, list[]}` 구조. title: "일반사항","증권의종류","인수인정보","자금의사용목적","매출인에관한사항","일반청약자환매청구권"

**요청 간 최소 간격**: 200ms (`Thread.sleep` 또는 `RestClient` 인터셉터). research 6절 "Rate limit 미확인" → 클라이언트 자체 안전장치로 적용(SF-4가 재시도 정책 담당).

### 4.4 설정 프로퍼티
```yaml
# application.yml
app:
  opendart:
    api-key: ${OPENDART_API_KEY:}      # 환경변수, 기본값 빈 문자열
    base-url: https://opendart.fss.or.kr  # 고정
```
```java
// OpenDartApiProperties.java (후보)
// 프로젝트 기존 패턴: AccountNumberEncryptor.java:35의 @Value("${...:}") 방식
// @ConfigurationProperties는 사용되지 않음(확인: grep 0건)
@Component
public class OpenDartApiProperties {

  private final String apiKey;
  private final String baseUrl;

  public OpenDartApiProperties(
      @Value("${app.opendart.api-key:}") String apiKey,
      @Value("${app.opendart.base-url:https://opendart.fss.or.kr}") String baseUrl) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public boolean isApiKeyMissing() {
    return apiKey == null || apiKey.isBlank();
  }
}
```
- `apiKey`가 빈 문자열이면 호출 시점에 `IllegalStateException("OpenDART API 키가 설정되지 않았습니다. OPENDART_API_KEY 환경변수를 확인하세요.")` 발생(AC-1703)
- 무한 재시도 없음: 예외 발생 즉시 전파

### 4.5 RestClient 사용 원칙
- **사용**: `org.springframework.web.client.RestClient` (Spring 6.1+, `spring-boot-starter-web`에 포함 — `build.gradle.kts:23` 확인)
- **금지**: `WebClient` (`spring-webflux` 의존성 없음 — `build.gradle.kts`에 없음 확인)
- `RestClient.builder().baseUrl(baseUrl).defaultHeader("Content-Type", "application/json").build()` 패턴

### 4.6 DTO 필드 매핑 (DS006 응답 → Ipo 엔티티)

> **근거**: `docs/research/ipo-schedule-sync.md` 7.1절 및 scratchpad 실측(2026-09-29). DS006 응답은 평면 `list`가 아닌 **group[] 배열** 구조이며, 각 group은 `{title, list[]}`를 갖는다. DTO 설계는 이 group 구조를 반영하지만, 기존 클래스명(`OpenDartDisclosureItem`, `OpenDartEquitySecurityDetail`)은 유지한다.

| DS006 필드 | 의미 | Ipo 엔티티 필드 | 타입 | 매핑 근거 | 비고 |
|---|---|---|---|---|---|
| `sbd` | 청약기일 | `subscriptionStartDate`, `subscriptionEndDate` | LocalDate | 7.1절 | **결정 D-2(2026-09-30)**: 기간 문자열("2026년 11월 05일 ~ 2026년 11월 06일")이면 그대로 시작일·종료일로 쓴다. 단일 날짜이면 종료일 = 시작일의 **다음 증권 영업일(+1영업일)**. 청약은 항상 2영업일간 진행한다는 사용자 규칙이다(§4.9). |
| `pymd` | 납입기일 | 미정 | — | 7.1절 | 현재 Ipo 엔티티에 없음 → 미정, 확인 필요. 날짜 형식: "2026년 11월 10일" 또는 "-"(null) |
| `sband` | 청약공고일 | 미정 | — | 7.1절 | 미정. 날짜 형식: "2026년 11월 05일" 또는 "-"(null) |
| `asand` | 배정공고일 | 미정 | — | 7.1절 | 미정. 날짜 형식: "2026년 11월 10일" 또는 "-"(null) |
| `asstd` | 배정기준일 | 미정 | — | 7.1절 | 미정. 날짜 형식: "2026년 11월" 또는 "-"(null) |
| `stksen` | 증권종류 | 미정 | — | 7.1절 | "주식/채권" 구분. group "증권의종류" 내 필드 |
| `stkcnt` | 증권수량 | `offeringShareCount` | Long | 7.1절 | 공모주식수로 매핑. 숫자 쉼표 포함 문자열("2,200,000") 파싱 |
| `slprc` | 모집(매출)가액 | `lowerBandPrice`, `upperBandPrice` (둘 다 같은 값) | Long | 7.1절 | **결정 D-1(2026-09-30)**: 증권신고서 단계 값은 확정 공모가가 아닐 수 있으므로 `fixedPrice`에는 넣지 않고 밴드(희망가) 쪽에만 넣는다. 확정가는 수동 입력이 채운다. 쉼표 포함 문자열("10,000") 파싱. |
| `slta` | 모집총액 | 미정 | Long | 7.1절 | 총 모집 금액. 숫자 쉼표 포함 문자열("22,000,000,000"). "-" = null |
| `actnmn` | 인수인명(주간사) | 미정(신규 컬럼 필요?) | String | 7.1절 | SF-3 컬럼 추가 범위에 포함되지 않음 → 미정 |
| `corp_code` | 회사고유번호 | `corpCode` (`ipos.corp_code`, V11) | String | 7.1절 | 자동 동기화 레코드 식별 키. 컨트롤러/DTO 노출은 SF-3에서 결정 |
| `corp_name` | 기업명 | `companyName` | String | 7.1절 추정 | DS001 응답에서 확인 예정. DS006에 포함 여부 미정 |
| `group` | DS006 응답 그룹 구분 | — | — | 7.1절 | "일반사항","증권의종류","인수인정보" 등. 각 group 내 list[]에 필드 포함 |

### 4.7 OpenDART 응답 status 코드 처리

| status 값 | 의미 | 처리 방식 | 근거 |
|---|---|---|---|
| `"000"` | 정상 | `list` 배열을 정상 파싱 | 2026-09-29 실측: 정상 응답에서 status="000", message="정상" |
| `"013"` | 조회된 데이터 없음 | 빈 결과로 처리(에러 아님, AC-1704) | **DS006 실측**: OpenDART API에서 "조회된 데이타가 없습니다." 메시지 반환. DS001에서도 동일 코드 사용 시 동일 처리 |
| `"010"`, `"011"`, `"012"`, `"901"` | 미등록 키 / 사용할 수 없는 키 / 접근 불가 IP / 계정 개인정보 보유기간 만료 | `OpenDartAuthenticationException` 발생(재시도 무의미, 동기화 즉시 중단) | **결정 D-5(2026-09-30)**: OpenDART 공식 개발가이드 상태 코드표로 확인 |
| `"020"` | 요청 제한 초과(통상 일 20,000건 이상) | `OpenDartApiException` 발생. 동기화 즉시 중단, 다음 주기에 재시도 | 공식 문서: 정확한 일일 한도는 명시되지 않음 |
| `"800"` | 시스템 점검 | `OpenDartApiException` 발생(일시 장애, SF-4 재시도 대상) | 공식 문서 |
| 기타(`021`, `100`, `101`, `900` 등) | 그 외 오류 | `OpenDartApiException` 발생 | 공식 문서 |

> **주의(2026-09-30 갱신)**: 위 코드 의미는 OpenDART 공식 개발가이드 상태 코드표로 확인했다. "000"/"013"은 2026-09-29 실측으로도 확인됐다.

### 4.9 청약 종료일 계산과 증권 영업일 (결정 D-2, D-6)

- 청약은 항상 2영업일간 진행한다(사용자 규칙). `sbd`가 기간(`~`)이면 그 기간을 그대로 쓴다. 단일 날짜이면 `subscriptionEndDate` = 그 날짜의 **다음 증권 영업일**.
- 증권 영업일 = 토·일과 한국거래소(KRX) 휴장일을 제외한 날. `BusinessDayCalculator`(`ipocatalog.service.ipo.sync`)가 판단한다.
- **결정 D-6(2026-09-30): 휴장일은 DB 테이블에 저장하고 월 1회 배치로 갱신한다.** 클래스패스 리소스 `krx-holidays.txt`는 폐기한다.
  - **출처 조사**: KRX는 휴장일을 기계가 읽을 수 있게 주는 공식 API가 없다(KRX Data Marketplace OPEN API 목록에 없음, 홈페이지는 엑셀 다운로드뿐). 공식·무료·자동승인인 **한국천문연구원 특일 정보**(공공데이터포털 `B090041/openapi/service/SpcdeInfoService/getRestDeInfo`, 개발계정 일 10,000회)로 법정 공휴일·대체공휴일·임시공휴일·선거일을 받고, 이 API에 없을 수 있는 KRX 고유 휴장일은 코드 규칙으로 보완한다.
  - **테이블** `market_holidays`(Flyway V13): `id` IDENTITY PK, `holiday_date DATE NOT NULL UNIQUE`, `name VARCHAR(100) NOT NULL`, `source VARCHAR(20) NOT NULL`(`SEED`|`KASI_API`|`RULE`), `created_at TIMESTAMP NOT NULL`. 물리 삭제·소프트 삭제 컬럼 없음(참조 데이터, 배치가 삭제하지 않음). V13이 아래 2026년 목록을 `source='SEED'`로 넣는다.
  - **KRX 규칙 보완(`source='RULE'`)**: (1) 매년 5월 1일(근로자의 날), (2) 연말 휴장일 = 해당 연도의 마지막 평일(12월 31일이 평일이면 12월 31일, 토·일이면 직전 금요일). 배치가 대상 연도마다 계산해 넣는다.
  - **월 배치**: 매월 1일 03:00(KST)에 올해와 내년 전체를 `solYear`로 조회(연도당 1호출, `_type=json`, `numOfRows=100`)해 `isHoliday=Y`인 날짜를 `holiday_date` 기준으로 upsert한다. 테이블에서 사라진 날짜는 삭제하지 않는다. 같은 날짜가 이미 있으면 `name`만 갱신하고 `source`는 유지한다(SEED를 KASI_API가 덮어쓰지 않음, 단 이름은 갱신).
  - **인증키**: 환경변수 `DATA_GO_KR_SERVICE_KEY`(`application.yml`의 `${DATA_GO_KR_SERVICE_KEY:}` 빈 값 허용 방식, OpenDART 키와 동일한 lazy 검증). 키가 없으면 배치는 WARN 로그만 남기고 건너뛴다(앱 기동 실패 금지). 키는 URL 쿼리에 실리므로 URI·예외 메시지·로그에 남기지 않는다.
  - **응답 형태 주의**: `response.body.items.item`은 결과가 1건이면 객체, 여러 건이면 배열, 0건이면 `items`가 빈 문자열(`""`)로 올 수 있다. 셋 다 처리한다. `locdate`는 `20260101` 형태 정수. 실제 응답은 키 발급 후에만 확인 가능하므로 테스트 픽스처는 공식 명세 기반이며 실측 검증은 키 발급 후 수동 1회 실행으로 한다.
  - **실패 처리**: 한 연도 호출이 실패해도 다른 연도는 계속한다. 실패는 로그 ERROR로 남기고 기존 테이블 데이터를 그대로 쓴다. 조회 대상 연도의 행이 하나도 없으면 계산기는 주말만 제외하고 **WARN 로그**를 남긴다.
  - **스케줄링**: `@EnableScheduling`을 켜는 설정 클래스를 이 작업에서 만든다(SF-4가 재사용). 배치 빈은 `@ConditionalOnProperty(name = "app.holiday-sync.enabled", havingValue = "true", matchIfMissing = true)`로 켜고 끈다(기존 코드에 `@Profile` 관례가 없다). 테스트 설정(`application-test.yml`)과 로컬 실행에서는 `false`로 둔다(E-10, AC-1733과 동일 원칙). 수동 실행용 서비스 메서드는 별도로 두되 엔드포인트는 만들지 않는다.
  - **계산기 캐시**: 청약 종료일 계산은 하루 1회 동기화 중 수십 번 호출되므로, 계산기는 호출마다 DB를 치지 않고 필요한 연도 단위로 조회한 결과를 메모리에 캐시한다(배치가 갱신하면 캐시 무효화).
- 2026년 SEED 휴장일(Master 작성, 사용자 검증 필요 — 월 배치가 공식 데이터로 보정): 01-01, 02-16, 02-17, 02-18, 03-02(삼일절 대체), 05-01, 05-05, 05-25(부처님오신날 대체), 06-03(지방선거), 08-17(광복절 대체), 09-24, 09-25, 09-28(추석 대체), 10-05(개천절 대체), 10-09, 12-25, 12-31.

### 4.8 테스트 원칙

- **실제 네트워크 호출 금지**: `OpenDartCorpDisclosureClient` 및 `OpenDartEquitySecuritiesClient` 테스트는 `MockRestServiceServer`(spring-boot-starter-test에 포함, 추가 의존성 없음)로 작성. `RestClient.builder().build()`에 `bindTo()`로 HTTP 응답을 가로채어 테스트.
- **테스트 케이스**:
  1. `status: "000"` + 정상 `list` → DTO 정상 매핑 (AC-1701, AC-1702)
  2. `status: "013"` → 빈 리스트 반환, 예외 없음 (AC-1704)
  3. API 키 누락 → `IllegalStateException` (AC-1703)
  4. 200ms 간격: 연속 호출 시 스레드 슬립 검증
  5. DS001 → DS006 흐름: `OpenDartCorpDisclosureClient` 결과의 `corp_code`가 `OpenDartEquitySecuritiesClient` 호출 파라미터로 전달되는지 검증
  6. 테스트 픽스처: `src/test/resources`에 `ds001-c001.json`, `ds006-optonix.json`, `ds006-empty.json`을 복사하여 키 없이 사용 (keys 필드 제거)

- 요청 URI와 쿼리스트링(crtfc_key 포함)은 로그와 예외 메시지에 절대 남기지 않는다.
- RestClient에 연결/읽기 타임아웃을 명시 설정한다(값은 구현 시 결정, 기본 무한대 금지).
- 모든 OpenDART 호출은 조회 전용이다.

### 4.9 기존 코드와의 연동
- `IpoRepository` (`com.ipocalc.ipocatalog.repository.ipo`) — SF-3에서 upsert 시 사용
- `IpoCsvImportService`의 `IpoImportResponse`/`IpoImportFailure` 패턴(`com.ipocalc.ipocatalog.controller.ipo.dto`) — SF-3 `IpoSyncResult`/`IpoSyncFailure` DTO 패턴으로 재사용 권장
- `application.yml`의 `app.encryption.key: ${APP_ENCRYPTION_KEY:}` 패턴과 동일한 `${...:}` 빈 값 허용 방식 따름

## 5. SF-2: ustockplus.com 클라이언트 — 개요

- **입력**: 조회 기간 `startDate`, `endDate` (YYYY-MM-DD). `Origin: https://ustock.naver.com`, `Referer: https://ustock.naver.com/...` 헤더 고정. rate limit: 초당 15회(`x-ratelimit-replenish-rate: 15`).
- **출력**: `List<UstockPlusCalendarItem>`(`stockCode`, `stockName`, `calendarStockType`, `startDate`, `endDate`, `extraInfo`). `TO_BE_LISTED`=상장예정일, `ALLOTMENT_DATE`=배정일, `REFUND_DATE`=환불일.
- **의존 SF**: 없음 (SF-1과 병렬). 단, 착수 게이트: ustockplus 헤더 방식 재확인 필요(`breakdown` §착수 전 게이트 (b)).
- **설계 원칙**: 호출 실패 시 예외 상위 전파 금지, `try-catch` 후 log만. OpenDART 데이터 저장에 영향 없음. 비공식 API임을 주석 명시. `UstockPlusApiProperties`는 헤더 상수 고정.
- **breakdown 링크**: `docs/breakdown/ipo-schedule-sync.md` §SF-2.

## 6. SF-3: 정규화·병합·upsert 서비스 — 개요

- **입력**: `OpenDartEquitySecurityDetail` 목록(Dart) + `UstockPlusCalendarItem` 목록(ustockplus). 각 IPO는 `(stockCode, subscriptionStartDate)` 복합키로 식별.
- **출력**: `IpoSyncResult`(`successCount`, `failureCount`, `failures[]` — `IpoImportResponse` 패턴 재사용). 신규 IPO는 `syncSource="OPENDART"` 혹은 `"OPENDART+USTOCKPLUS"`로 생성.
- **의존 SF**: SF-1 + SF-2 (두 클라이언트 결과 이어받기).
- **핵심 규칙**: `syncSource == null`(수동 등록)인 Ipo는 절대 덮어쓰지 않음. `allotmentDate`, `refundDate`, `syncSource`, `lastSyncedAt` 4개 컬럼을 `Ipo` 엔티티에 추가(Migration은 SF-3 구현 단계에서 설계 — 이 spec에서는 설계하지 않음). `stockCode` 매핑은 DS001 응답 필드 확인 필요(§3.3 미정).
- **breakdown 링크**: `docs/breakdown/ipo-schedule-sync.md` §SF-3.

## 7. SF-4: 배치 스케줄러 — 개요

- **입력**: 없음. `@Scheduled(cron="...")`로 하루 1회, 최근 3개월 범위 DS001+DS006 호출 → SF-3 병합 서비스에 전달.
- **출력**: `IpoSyncResult`를 로그 ERROR 레벨 + 별도 기록. 3회 재시도(5분→10분→20분 지수 백오프) 후 실패면 다음 스케줄 주기에 재시도.
- **의존 SF**: SF-3 (병합·upsert 서비스 호출).
- **설계 원칙**: 로컬/테스트 프로필에서 비활성화(`@Profile`). 실제 알림 인프라(Slack/이메일) 없으므로 로그 기반 최소화. rate limit 존중(OpenDART 200ms, ustockplus 15req/s).
- **breakdown 링크**: `docs/breakdown/ipo-schedule-sync.md` §SF-4.

## 8. SF-5: 프론트 연동 — 개요

> **폐기(2026-09-30)**: `GET /ipo-schedule`는 만들지 않는다. 자동 동기화된 공모주는 `ipos` 테이블에 저장되므로 기존 `GET /ipos`를 청약 등록 폼과 달력(Epic 19 SF-2, 병합 완료)이 그대로 쓴다. 아래 내용은 초기 설계 기록이다.

- **입력**: `GET /ipo-schedule?start=YYYY-MM-DD&end=YYYY-MM-DD` (전역 데이터, 계정 소유권 검증 불필요).
- **출력**: 향후 N개월치 IPO 일정 목록(JSON 배열). `IpoResponse` + 신규 필드(`allotmentDate`, `refundDate` 등) 포함.
- **의존 SF**: SF-4 (스케줄러 → DB 저장 → 조회 가능). Epic 19 SF-2(달력 뷰)도 동일 엔드포인트 재사용.
- **설계 원칙**: 기존 `GET /ipos`와 분리하여 신규 엔드포인트 `GET /ipo-schedule` 추가. 자동 동기화 목록에 없는 IPO도 기존 수동 등록 폼으로 추가 가능(automated ≠ manual block).
- **breakdown 링크**: `docs/breakdown/ipo-schedule-sync.md` §SF-5.

## 9. 착수 전 게이트

SF-1 구현 시작 전 **Master가 아래 세 항목을 확인**해야 한다(`docs/breakdown/ipo-schedule-sync.md` §착수 전 Master 확인 필요와 동일):

### (a) OpenDART 인증키 발급 + 실제 호출 테스트 (1회)
- `opendart.fss.or.kr/uss/umt/EgovMberInsertView.do`에서 `crtfc_key` 발급
- 발급한 키로 DS001(공시검색) + DS006(지분증권)을 각각 1회 호출하여 정상 응답 확인
- **완료** → SF-1 착수 가능 (실제 응답 스키마로 DTO 필드 확정). **2026-09-29 실측 완료**
- **미완료** → 대기 (인증키 발급 후 실제 응답 1건을 확보하고 시작)

### (b) DS006 실제 응답 샘플 1건 확보
- DS006 응답의 `status` 값, `group[]` 배열 내 필드명과 타입, `list`가 빈 배열인 경우의 응답 구조 확인
- DS001 응답의 `stock_code` 필드 존재 여부와 `corp_name` 필드명 확인 → SF-3 매핑 로직 결정에 직결
- DS001 응답 `status` 값(정상/데이터 없음) 확인
- **완료** → §4.6, §4.7의 "미정" 표시된 필드 매핑을 실제 응답으로 재검증. **2026-09-29 실측 완료**
- **미완료** → DTO 필드는 샘플로 재검증 필요 (구현 전 착수 불가)

### (c) ustockplus 헤더 재확인 (SF-2 전제)
- `Origin: https://ustock.naver.com`, `Referer: https://ustock.naver.com/...` 헤더 고정 방식 재확인
- **완료** → SF-2 착수 가능
- **미완료** → 대기

> **주의**: §4.6, §4.7, §13-M의 "미정, 확인 필요" 표시는 모두 착수 전 게이트(a)+(b)+(c) 완료 시 실제 API 응답으로 교체된다. 게이트 미충족 상태에서 SF-1 구현을 시작하면 DTO 필드가 틀려서 재작업 위험이 있다.

## 10. 핵심 설계 원칙 (AC/규칙)

| # | 원칙 | 적용 시점 | 근거 |
|---|---|---|---|
| R-1 | 수동 등록 IPO는 덮어쓰지 않는다 | SF-3 upsert | `Ipo.syncSource == null`이면 사용자가 직접 생성한 것 → 자동 동기화에서 제외. `syncSource`가 채워진 레코드만 자동 갱신 대상 |
| R-2 | 중복 키: `(stockCode, subscriptionStartDate)` | SF-3 upsert | breakdown §SF-3 AC-1723. 동일 종목의 여러 회차 공모는 roundLabel로 구분, 별도 레코드 |
| R-3 | ustockplus 실패가 OpenDART 데이터 반영을 막지 않는다 | SF-2 + SF-3 | breakdown 특이사항 #4, research 7.3절. ustockplus 호출 실패 시 해당 필드만 null, OpenDART 데이터는 그대로 저장 |
| R-4 | OpenDART가 빈 결과여도 에러가 아니다 | SF-1 | AC-1704. `status: "013"` 또는 `list: []`는 정상 처리(신규 공시 없음) |
| R-5 | 인증키 누락 시 즉시 예외, 무한 재시도 금지 | SF-1 | AC-1703. `${OPENDART_API_KEY:}` 빈 값 → 호출 시 `IllegalStateException` |
| R-6 | 외부 API 호출은 요청 간 최소 200ms 간격 | SF-1 | research 6절 rate limit 미확인 → 클라이언트 자체 안전장치 |

## 11. Acceptance Criteria (AC-1701~1704)

> **AC 번호·의미 원본**: `docs/breakdown/ipo-schedule-sync.md` §SF-1 AC 초안. 번호·의미를 변경하지 않으며 Given/When/Then로 구체화.

**AC-1701**: DS001로 최근 N일 내 지분증권 증권신고서 제출 회사 목록을 정상 조회한다
- Given: 유효한 `OPENDART_API_KEY`가 설정되어 있고, DS001 공시검색 API가 정상 응답(status `"000"`)한다
- When: `OpenDartCorpDisclosureClient.searchRecentEquitySecuritiesCompanies(LocalDate.now().minusDays(7))`가 호출된다
- Then: 응답에서 "증권신고서(지분증권)" 보고서명으로 필터링된 회사 목록(`corp_code`, `corp_name`, `stock_code` 포함)을 반환한다

**AC-1702**: DS006으로 특정 회사의 청약일·공모가·주간사 정보를 정상 조회한다
- Given: 유효한 API 키와 유효한 `corp_code`가 있다. DS006 API가 `status: "000"` 응답이다
- When: `OpenDartEquitySecuritiesClient.getEquitySecurityDetail(corp_code)`가 호출된다
- Then: DS006 응답 필드(`sbd`, `pymd`, `sband`, `asand`, `asstd`, `stksen`, `stkcnt`, `slprc`, `slta`, `actnmn`)를 `OpenDartEquitySecurityDetail` DTO로 정확히 매핑하여 반환한다

**AC-1703**: 인증키 미설정 시 명확한 예외 메시지로 실패한다(무한 재시도 없음)
- Given: `OPENDART_API_KEY` 환경변수가 비어 있다 (또는 `app.opendart.api-key`가 빈 문자열)
- When: DS001 또는 DS006 API 호출이 시도된다
- Then: 즉시 `IllegalStateException("OpenDART API 키가 설정되지 않았습니다. OPENDART_API_KEY 환경변수를 확인하세요.")`를 발생시킨다. 재시도하거나 백그라운드 루프에서 호출하지 않는다

**AC-1704**: OpenDART가 빈 결과를 반환해도(신규 공시 없음) 정상 처리된다(에러 아님)
- Given: API 키가 유효하고, 최근 7일 내 지분증권 공시가 없어 DS001이 `status: "013"`(조회된 데이터 없음) 또는 `list: []`를 반환한다
- When: `OpenDartCorpDisclosureClient.searchRecentEquitySecuritiesCompanies(...)`가 호출된다
- Then: 빈 `List<OpenDartDisclosureItem>`을 반환한다. 예외를 발생시키지 않는다

> **SF-2~5 AC**: `docs/breakdown/ipo-schedule-sync.md` §각 SF별 AC 초안 참조(AC-1710~1742). SF-2~5는 개요 수준이므로 이 spec에서는 번호만 언급.

## 12. 엣지 케이스

| # | 상황 | 처리 방식 | 적용 SF |
|---|---|---|---|
| E-1 | DS001 조회 시 `page_no` 페이지가 여러 페이지 | 페이지네이션 루프로 전체 `corp_code` 수집 후 DS006 순회 | SF-1 |
| E-2 | DS006 응답 `list`가 빈 배열 | 빈 리스트 반환, 예외 없음(AC-1704) | SF-1 |
| E-3 | DS001 응답에 `stock_code`가 없는 기업 | **결정 D-3(2026-09-30)**: `stock_code`가 빈 문자열이거나 null이면 IPO로 취급해 동기화하고, 값이 있으면 유상증자로 보고 건너뛴다(동기화 대상 아님). 개발 DB/실측으로 몇 건 대조해 가설을 확인한다. ustockplus 매핑은 SF-2 보류로 이번 범위 밖 | SF-1/SF-3 |
| E-4 | ustockplus 호출 실패(403/500/스펙 변경) | `try-catch` 후 log WARN, 해당 필드(null)로 저장. OpenDART 데이터 unaffected | SF-2 |
| E-5 | 동일 종목의 여러 회차 공모주 | `(stockCode, subscriptionStartDate)` 복합키로 구분. roundLabel로 추가 구분 | SF-3 |
| E-6 | 수동 등록 IPO와 자동 동기화 결과 충돌 | `syncSource == null`인 레코드는 건너뜀(R-1). 자동 생성 레코드만 갱신 | SF-3 |
| E-7 | OpenDART 응답에서 청약 종료일 필드 미확인 | `sbd`는 기간 문자열("2026년 11월 05일 ~ 2026년 11월 06일") 형태로 제공되며, **결정 D-2(2026-09-30)**: 기간이면 그대로 쓰고 단일 날짜면 종료일 = +1 증권 영업일(§4.9) | SF-1/SF-3 |
| E-8 | DS006 `slprc`가 총액인지 per-share인지 | **결정 D-1(2026-09-30)**: `fixedPrice`에 매핑하지 않는다. 밴드(희망가) 쪽에만 매핑(§4.6 표 참고) | SF-1 |
| E-9 | ustockplus rate limit 초과 | `x-ratelimit-*` 헤더 존중, 클라이언트-level rate limiter 도입 가능성 | SF-2 |
| E-10 | 스케줄러가 로컬/CI에서 실행 | `@Profile`로 비활성화(AC-1733) | SF-4 |

## 13. 미정·확인 필요 사항

| # | 항목 | 확인 방법 | 담당/시점 |
|---|---|---|---|
| M-1 | OpenDART DS001 정확한 URL 경로 및 요청 파라미터(보고서명 필터 방식) | OpenDART 개발가이드 공시검색(DS001) 원문에서 URL·요청 파라미터 확인 | Master 착수 전 게이트(a) **완료(2026-09-29)** |
| M-2 | DS006 API의 정확한 URL/apiId(`apiGrpCd=DS006&apiId=2020054`) | OpenDART 개발가이드 DS006 상세 페이지 원문 확인 | Master 착수 전 게이트(a) **완료(2026-09-29)** |
| M-3 | DS006 응답 `status` 코드 값("000"/"013"/"011"/"012" 등) | 실제 호출 후 응답 확인 | Master 착수 전 게이트(b) **완료(2026-09-29)** |
| M-4 | DS006 응답 필드 정확한 명세(sbd, pymd, sband, asand, asstd, stksen, stkcnt, slprc, slta, actnmn) | 실제 호출 후 JSON 응답 구조 확인 | Master 착수 전 게이트(b) **완료(2026-09-29)** |
| M-5 | DS001 응답에 `stock_code` 필드 존재 여부와 정확한 필드명 | 실제 호출 후 응답 확인 | Master 착수 전 게이트(b) **완료(2026-09-29)** |
| M-6 | `slprc`(모집가액)가 per-share 공모가인지, 총액인지 | 실제 응답 값과 공모가 비교 | Master 착수 전 게이트(b) **완료(2026-09-29)** |
| M-7 | `sbd`(청약기일)가 청약 시작일인지, 종료일인지, 단일 날짜인지 | 실제 응답 값 확인 | Master 착수 전 게이트(b) **완료(2026-09-29)** |
| M-8 | `pymd`(납입기일), `sband`(청약공고일), `asand`(배정공고일), `asstd`(배정기준일), `slta`(모집총액)의 Ipo 엔티티 매핑 | 추후 SF-3 컬럼 추가 논의 필요(현재는 미정) | SF-3 설계 시 |
| M-9 | ustockplus Origin/Referer 헤더 방식이 여전히 유효한지 | 착수 직전 재확인(curl 재현) | SF-2 착수 전 게이트 |
| M-10 | `actnmn`(주간사/인수인명) → Ipo 엔티티 `underwriter` 컬럼 추가 여부 | SF-3 컬럼 추가 논의 필요(현재는 미정) | SF-3 설계 시 |
| M-11 | OpenDART rate limit(초당 호출 제한)·일일 한도 | OpenDART 개발가이드 확인 | **결정 D-4(2026-09-30)**: 공식 문서에 정확한 일일 한도가 없다(통상 20,000건 초과 시 status 020). 사용자 결정으로 **하루 1회 실행**으로 시작하고 200ms 호출 간격 유지. 020 응답 시 그날 동기화 중단 |
| M-12 | 테스트 HTTP 목 서버: WireMock 추가 의존성 여부 | `MockRestServiceServer`(spring-boot-starter-test에 포함)로 대체 가능. 추가 의존성 없이 검증 가능 | SF-1 구현 시 |
| M-13 | `(stockCode, subscriptionStartDate)` 중복 키 문제 및 IPO/유상증자 구분 | 기존 DB(V5/V6)는 stock_code가 있으면 stock_code 유니크, 없으면 (ipo_name, subscription_start_date) 유니크다. 따라서 기존 키 사용은 가능하다. 자동 동기화 레코드의 식별 키로 `ipos.corp_code`(VARCHAR(8), nullable, ACTIVE 한정 부분 유니크 `uk_ipo_corp_code_active`)를 추가하기로 **2026-09-29 사용자가 결정했고 V11로 구현·병합됐다(#194)**. 개명·재상장에 안전하다. | SF-3 전에 Master 결정 **완료** |
| M-14 | 금융위원회_주식발행정보(data.go.kr, GetStocIssuInfoService_V3)로 실제 상장일 자동 보정 | **결정 D-7(2026-09-30)**: 도입하지 않는다. 상장 후·영업일 하루 지연 갱신이라 사후 보정용일 뿐 청약 일정/배정/환불일을 채워 주지 못하고, 공공누리 2유형(상업적 이용 시 예탁결제원 정보이용계약 필요)이라 얻는 것 대비 부담이 크다. 상장일은 수동 입력을 유지한다 | 사용자 결정 완료 |

---

> **문서 버전 기록**: 2026-09-29 작성. SF-1 착수 전 게이트(a)+(b) 완료 시 §4.6, §4.7, §13-M 항목 일부를 실제 API 응답으로 교체하여 갱신한다.
