# 상장일 시초가 자동 수집 아키텍처 (listing-open-price)

> **Epic**: Epic 21 (상장일 시초가로 매도단가 자동 채움, listing-open-price)
> **작성일**: 2026-10-16 (planning 워커)
> **기준 문서**: `docs/research/listing-open-price.md`(2026-10-16), `docs/spec/listing-open-price.md`
> **설계 원칙**: 기존 클라이언트·배치 패턴(`DataGoKrApiProperties`, `HttpClientSupport`, `IpoSyncScheduler`, `@ConditionalOnProperty`) 재사용. 새 인증키·새 HTTP 인프라를 만들지 않는다.

## 1. 데이터 흐름

```
[매일 14:30 KST, 시세 배치 (app.open-price-sync.enabled)]
  ipos에서 대상 선별: status='ACTIVE'
    AND stock_code IS NOT NULL
    AND listing_date IS NOT NULL AND listing_date <= 어제
    AND listing_open_price IS NULL
        → 대상 종목별 1호출:
          GET https://apis.data.go.kr/1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2
            ?serviceKey=${DATA_GO_KR_SERVICE_KEY}&resultType=json
            &basDt=<listing_date YYYYMMDD>&likeSrtnCd=<stock_code>
        → 응답 item 중 srtnCd 일치 행의 mkp(>0, 문자열→Long 파싱)를 채택
        → ipos.listing_open_price 갱신 (NULL인 행만, 멱등)

[ipos 테이블]
  listing_open_price BIGINT NULL (원 단위)

[조회 API — 기존 변경 없음, 필드 추가만]
  GET /ipos, GET /ipos/{id} → IpoResponse.listingOpenPrice 노출

[프론트]
  ipoSchema(.nullish()) → useIpo/useIpos 캐시
  useSaleForm: 매도단가 기본값 = listingOpenPrice ?? resolveIpoPrice(fixedPrice, allocationPrice)
  SaleBasicFields: 안내 문구 = 시초가/공모가 분기
  (캡처 값·isDirty 우선순위는 기존 동작 유지)
```

상장일(D)의 시세는 **D+1 영업일 13:00 이후**에 API에 반영된다(research §4, 공식 페이지). 그래서 배치 시각을 오후로 두고, 아직 반영 안 됐으면 값이 NULL 그대로 → 다음 날 배치가 재시도한다(멱등, 추가 상태 관리 없음).

## 2. DB 변경 (마이그레이션 V29)

```sql
-- backend/src/main/resources/db/migration/V29__add_ipo_listing_open_price.sql
-- 상장일 시초가(시가) 컬럼 추가 (Epic 21)
-- listing_open_price: 상장 첫 거래일 정규시장 시가(원). 공공데이터포털 주식시세정보에서 배치 수집
ALTER TABLE ipos ADD COLUMN listing_open_price BIGINT NULL;

-- 종가(clpr)는 저장하지 않는다(U-2 결정, 2026-10-16: 시가만 저장, 필요해지면 그때 추가).

COMMENT ON COLUMN ipos.listing_open_price IS '상장일 시가(시초가, 원). 주식시세정보 배치 수집. 미수집 시 NULL';
```

| 항목 | 결정 | 근거 |
|---|---|---|
| 타입 | `BIGINT` | 원 단위 금액. 기존 `fixed_price` 등과 동일 체계(V5) |
| NULL | 허용 | 미수집·상장 전·종목코드 없음 상태가 정상이기 때문(spec R-2) |
| 기본값 | 없음(NULL) | 기존 행 영향 없음(V17 배정일·환불일 추가 방식 동일) |
| COMMENT | V25 체계에 맞춰 추가 | V25 정책(ipos 컬럼 COMMENT 관리) |
| 인덱스 | 불필요 | 배치가 `listing_open_price IS NULL` 조건으로 조회하지만 대상 수가 적고, 기존 status/listing_date 인덱스(있다면) 활용. 별도 인덱스 추가는 드리프트 검토 시 |
| **종가 저장(U-2)** | **저장하지 않음(2026-10-16 결정)** | 결정 근거: 시가만으로 이번 범위(매도단가 기본값) 충분. 종가는 필요해지면 그때 컬럼을 추가한다(그때가면 API 재호출로 재확보 가능 — 하루 1회 배치 특성상 과거 날짜 종가는 재조회로 복구된다) |

엔티티: `com.ipocalc.ipocatalog.domain.model.ipo.Ipo`에 `private Long listingOpenPrice;` 추가(Lombok getter/setter 패턴 기존 필드 준수).

## 3. 시세 클라이언트 설계

> 패키지: `com.ipocalc.ipocatalog.service.ipo.sync.openprice`(가칭 — `IpoSyncScheduler` 등이 있는 `service.ipo.sync` 하위). 패키지 최종명은 구현 시 Master 확정.

| 클래스 | 책임 |
|---|---|
| `StockPriceClient` | `getStockPriceInfo` 호출·파싱. 생성자는 `KasiSpecialDayClient`와 동일 2형식(테스트용 RestClient 주입 / 운영용 Builder) |
| `OpenPriceApiException` | 비정상 응답 코드·파싱 실패용 예외(KASI의 `KasiApiException` 대응) |
| 빈 등의 config | `KasiClientConfig`와 동일: `RestClient.builder()` + `HttpClientSupport.requestFactory()` |

**재사용**:
- `DataGoKrApiProperties`(base-url `https://apis.data.go.kr`, service-key `${DATA_GO_KR_SERVICE_KEY:}`) — 그대로 주입. 새 설정 클래스 금지.
- `HttpClientSupport.requestFactory()`(연결 5초·읽기 15초).
- `HttpClientSupport.RequestThrottle` — 종목당 호출 간 최소 200ms(초당 한도 미확인에 대한 자체 안전장치, research §6).

**호출부**:
- 엔드포인트 상수: `GET /1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2` — **V2 확정(2026-10-16 Master 실측 200 OK). 구 V1 경로는 같은 키로 403이므로 쓰지 않는다.**
- 쿼리: `serviceKey`, `resultType=json`, `numOfRows=10`, `pageNo=1`, `basDt`, `likeSrtnCd`.
- 파싱: KASI와 동일한 공공데이터포털 봉투(`response.header.resultCode`가 `"00"`이면 정상 — **실측 확인, 2026-10-16**), `response.body.items.item`의 단일 객체/배열/빈 문자열 3형태 모두 처리(`ACCEPT_SINGLE_VALUE_AS_ARRAY` 패턴 재사용). **item의 모든 값은 JSON 문자열이다(예: `"mkp": "27400"`) — 숫자 필드는 Long 파싱 필수.**
- **응답 검증**: `srtnCd == 요청 stockCode`인 행만 채택(likeSrtnCd 부분 일치 방어, spec E-21-10). `basDt`도 요청값과 대조(경고 로그).
- **키 검증**: `properties.isServiceKeyMissing()`이면 `IllegalStateException`(KASI 동일) — 배치가 WARN 후 중단(재시도 없음).
- **예외 메시지·로그 금지 항목**: ServiceKey가 든 요청 URI, 쿼리스트링, 원인 예외 메시지(URI 포함 가능) — 클래스명만 기록(KASI `KasiSpecialDayClient.java:102-105`와 동일).

## 4. 응답 DTO·프론트 스키마 변경

| 층 | 변경 | 파일 |
|---|---|---|
| 엔티티 | `Ipo.listingOpenPrice` (`Long`) | `domain/model/ipo/Ipo.java` |
| 응답 DTO | `IpoResponse.listingOpenPrice` + `from()`에 매핑 | `controller/ipo/dto/IpoResponse.java` (기존 `fixedPrice` 옆 배치) |
| 프론트 Zod | `ipoSchema`에 `listingOpenPrice: z.number().nullish()` 추가 | `ui/src/entities/ipo/types.ts` (기존 `refundDate`의 `.nullish()` 패턴 준수) |
| 등록/수정 요청 DTO | **변경 없음** — 사용자가 시초가를 직접 등록·수정하는 경로를 만들지 않는다(수동 입력은 배치·기존 값만) | `IpoRegisterRequest`/`IpoUpdateRequest` |

종가(`listing_closing_price`)는 저장하지 않는다(U-2 결정, 2026-10-16) — 필요가 생기면 위 표 방식으로 컬럼을 추가하는 후속 변경으로 다룬다.

## 5. 배치 스케줄러

| 항목 | 결정 | 근거 |
|---|---|---|
| 클래스 | `IpoOpenPriceSyncScheduler` + 동기화 서비스(클라이언트 호출·대상 선별·저장 분리) | `IpoSyncScheduler`/`MarketHolidaySyncScheduler` 패턴 |
| 시각 | **매일 14:30 KST** (`@Scheduled(cron="0 30 14 * * *", zone="Asia/Seoul")`) | D+1 13:00 이후 반영(research §4). 기존 04:30 일정·05:00 상세 동기화와 충돌 없음 |
| 대상 | `status='ACTIVE' AND stock_code IS NOT NULL AND listing_date <= :어제 AND listing_open_price IS NULL` | 멱등 재시도(spec R-4, E-21-9) |
| 호출 수 | 종목당 1호출(필터 없이 날짜별 대량 조회 아님) | 개발 한도 일 10,000 대비 여유 |
| 스로틀 | `RequestThrottle(200ms)` | 초당 한도 미확인에 대한 안전장치 |
| 재시도(앱 내) | 앱 안 재시도 없음 — 해당 종목 건너뛰고 다음 날 배치가 재조회(멱등) | 기존 패턴 |
| 다음 주기 폴백 | 재시도 실패해도 다음 날 배치가 같은 대상을 다시 본다(멱등) | 상태 테이블 없음 |
| 성공 로그 | 건수 요약(대상/성공/0건/실패) + 건별 실패는 ERROR | 기존 `IpoSyncResult`류 요약 패턴 참고, 별도 DTO 필수 아님 |
| 테이블 변경 | 없음(멱등 UPDATE만) | 최소 변경 |

### 5.1 설정 키와 테스트에서 끄는 방법

```yaml
# application.yml
app:
  open-price-sync:
    enabled: true        # app.ipo-sync.enabled 등 기존 matchIfMissing=true 패턴
```

```java
@ConditionalOnProperty(name = "app.open-price-sync.enabled", havingValue = "true", matchIfMissing = true)
public class IpoOpenPriceSyncScheduler { ... }
```

```kotlin
// build.gradle.kts — 기존 tasks.test 시스템 속성 패턴에 한 줄 추가 (build.gradle.kts:55-63)
tasks.test {
    systemProperty("app.holiday-sync.enabled", "false")
    systemProperty("app.ipo-sync.enabled", "false")
    systemProperty("app.open-price-sync.enabled", "false")   // 추가
    systemProperty("app.notification.enabled", "false")
    systemProperty("user.timezone", "Asia/Seoul")
}
```

`application-test.yml`에도 `app.open-price-sync.enabled: false`를 둔다(기존 `app.datagokr.service-key: ""`와 함께 — 테스트에서 키 없음 상태 고정). `@Profile`은 기존 배치와 마찬가지로 쓰지 않는다(스위치 키 하나로 통일).

## 6. 키 미설정·403 시 동작

| 상황 | 감지 | 동작 |
|---|---|---|
| 키 미설정(빈 문자열) | `isServiceKeyMissing()` | `IllegalStateException` → 스케줄러 WARN 로그("설정 오류, 재시도 안 함") 후 종료. **앱 기동은 정상**(빈 키 허용 방식 `${DATA_GO_KR_SERVICE_KEY:}` 유지) |
| 403 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`(현재 상태) | HTTP 403 또는 응답 에러 코드 30 | ERROR 로그(재시도 없음) 후 중단. 매도 폼은 공모가 기본값으로 정상 동작 |
| 일일 한도 초과(22) | 응답 에러 코드 22 | ERROR 후 중단, 다음 일일 주기 |
| 일시 장애(01/04/05, 네트워크) | 예외/코드 | 앱 안 재시도 없음 — 해당 종목 건너뛰고 다음 날 배치가 재조회(멱등) |

핵심: **시세 기능의 실패가 서버 기동·IPO 조회·매도 등록·기타 동기화를 절대 막지 않는다.** 스케줄러 빈이 켜져 있어도 호출 실패는 자기 로그에서 끝난다.

## 7. 매도 폼 통합 규칙 (프론트)

기존 `useSaleForm.ts:138-159`의 기본값 effect 구조를 유지하고 값 선택만 바꾼다.

```ts
// before (useSaleForm.ts:145-148, 153)
const basis = resolveIpoPrice(ipo?.fixedPrice ?? allocationData.ipoFixedPrice, allocation.allocationPrice)
if (!getFieldState('price').isDirty && basis != null) setValue('price', basis)

// after (개념 — 시초가 우선, 없으면 기존 규칙)
const fallback = resolveIpoPrice(ipo?.fixedPrice ?? allocationData.ipoFixedPrice, allocation.allocationPrice)
const priceDefault = ipo?.listingOpenPrice ?? fallback
if (!getFieldState('price').isDirty && priceDefault != null) setValue('price', priceDefault)
```

- **우선순위(spec R-3)**: 캡처 값(applyCapture effect — 선언 순서상 뒤에 실행, 기존 유지) > 사용자 고침(isDirty) > 시초가 > 공모가.
- 캡처·증권사 연동 코드(`CaptureImport`, `/broker-sync`)는 **변경하지 않는다.**
- `SaleBasicFields.tsx:60-64` 안내 문구만 시초가/공모가 분기로 교체(문안은 SF-3에서 확정, spec U-6).
- `SaleEditDialog.tsx`는 건드리지 않는다(시초가 미참조 확정).
- `useIpo`·`useIpos` 캐시·쿼리 키는 변경 없다(응답에 필드가 추가될 뿐).

## 8. 관리자 수동 동기화 — **결정됨(2026-10-16): 만들지 않는다**

- **결정: 관리자 수동 동기화 엔드포인트·버튼을 만들지 않는다.** 배치가 멱등·매일 재시도 구조라 값이 필요하면 최대 1일을 기다리면 된다.
- 장래에 필요성이 생기면 기존 `POST /ipos/sync`(관리자 "지금 가져오기", `IpoSyncStatusResponse` 패턴)에 시세 동기화를 연결하는 안을 우선 검토한다 — 새 화면·새 엔드포인트보다 기존 흐름 재사용.

## 9. 테스트 전략

| 대상 | 방법 |
|---|---|
| `StockPriceClient` | `MockRestServiceServer`(`RestClient.builder().build()`에 `bindTo()` — OpenDART SF-1과 동일, 추가 의존성 없음). 픽스처: 정상 1건 JSON / item 배열 / `items=""`(0건) / 에러 코드 30 / `mkp="0"` — **픽스처 값은 실측대로 전부 문자열**(`"mkp": "27400"`, `"srtnCd": "123456"` 형태)로 작성 |
| 배치 서비스 | 대상 선별 쿼리(repository 통합 테스트, H2/기존 테스트 DB), 저장 멱등(NULL만 UPDATE), stock_code NULL 제외, 403 시 중단 |
| 스케줄러 스위치 | `app.open-price-sync.enabled=false`에서 빈 미생성 확인 |
| `IpoResponse` | 시초가 매핑 직렬화 테스트(기존 DTO 테스트 패턴 확장) |
| `ipoSchema` | 기존 Zod 테스트에 `listingOpenPrice` 포함/미포함(구버전 응답) 파싱 |
| `useSaleForm` | 기존 `useSaleForm` 테스트 패턴 확장: 시초가 있음/없음/0, isDirty 유지, 캡처 우선 — AC-2105~2109 |
| 실네트워크 | 금지. 실호출은 게이트(a)의 Master 수동 1회(2026-10-16 완료) — 이후 테스트는 전부 목 서버 |

검증 명령: `DB_PASSWORD=ipo_local_password ./gradlew test --rerun && ./gradlew build`(guide-backend 기존), 프론트는 기존 vitest.

## 10. 보안·운영 주의

- `serviceKey`는 쿼리 파라미터 — URI·예외 메시지·로그에 남기지 않는다(§3, 기존 KASI 원칙).
- 라이선스: 공공누리 4유형(상업적 이용금지) — research §8. 결정됨(2026-10-16): 비영리 전제 유지, 영리화 시 §12 참조.
- 시세 데이터는 저장 후 재배포하지 않는다(내부 기본값 용도).
- 모니터링: 인프라 알림 채널이 없으므로 ERROR 로그 기반 파악(기존 배치와 동일한 수준).

## 11. 리스크

| 리스크 | 완화 |
|---|---|
| 응답 스키마 | ~~미확정~~ → **2026-10-16 실측으로 필드·문자열 타입 확정. 픽스처는 문자열 기준으로 작성(해소)** |
| V1/V2 URL | **V2 확정(200), V1 폐기(403) — 상수 1곳에 고정(해소)** |
| likeSrtnCd 오매칭 | 응답 필터(`srtnCd` 일치) 필수 |
| D+1 13:00 반영 지연 | 배치 시각 14:30 + NULL 재시도 |
| 배치 실패 은폐 | 요약 ERROR 로그 + 실패 건별 ERROR |

---

## 12. 영리화 시 대안 (참고 — 현 시점은 비영리 전제)

영리로 전환하면 이 API를 그대로 쓸 수 없다(공공누리 4유형, 상업적 이용·재배포 금지). 그때의 출처 전환 방향:

- **채택 방향**: 사용자 본인의 증권사 API 키(이미 있는 한국투자·키움·LS 조회 전용 연동)로 그 사용자 화면에 필요한 상장일 시세만 조회한다 — 우리가 시세를 모아 재배포하지 않는 구조. 연동하지 않은 사용자는 공모가 기본값 + 캡처 OCR(#452)로 채운다(기존 폴백 유지).
- 다른 대안(참고, 유료): KRX 데이터 이용 계약, 코스콤·에프앤가이드 등 시세 벤더 계약. 각 증권사 API 약관은 영리화 시점에 원문 확인 필요.
- **설계 영향**: 시세 조회는 `StockPriceClient`(§3 단일 경유점) 한 곳에 모으므로 출처를 바꿔도 매도 폼·DTO·DB는 그대로다. 출처 교체 비용은 클라이언트 구현 1곳으로 제한된다.

## 미결정 사항

| # | 항목 | 설명 | 결정 | 상태 |
|---|---|---|---|---|
| U-1 | V1/V2 URL·응답 필드 실측 | 게이트(a) 실호출 완료 | **V2 고정, item 15종·문자열 타입 확정(2026-10-16)** | **결정됨** |
| U-2 | 종가(`listing_closing_price`) 저장 | §2 반영 | **저장하지 않음 — 시가만(2026-10-16)** | **결정됨** |
| U-3 | 관리자 수동 동기화 | §8 반영 | **만들지 않는다(2026-10-16)** | **결정됨** |
| U-4 | 라이선스 4유형 인지 | 영리화 시 대안 절 참조 | **비영리로 사용하므로 4유형 조건에서 그대로 사용(2026-10-16)** | **결정됨** |
| U-5 | 배치 시각 14:30 KST | D+1 13:00 반영에 맞춘 제안 | **14:30 KST 유지(2026-10-16)** | **결정됨** |
| U-6 | 패키지·클래스명 | `service.ipo.sync.openprice`·`StockPriceClient`(가칭) | Master 구현 시 | 미결정 |
