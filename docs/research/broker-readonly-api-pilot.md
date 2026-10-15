# 증권사 조회 전용(read-only) API 연동 파일럿 조사 (broker-readonly-api-pilot)

> **문서 종류**: Phase 0 `research`. 코드 미작성. 이 문서의 사실 중 외부 API에 관한 부분은 2026-09-29 Master 사전조사(WebSearch 확인 결과)에만 근거하며, 그 외는 "미확인"으로 명시했다. 실제 착수 전에 KIS Developers API 문서 원문과 공식 GitHub 저장소(`koreainvestment/open-trading-api`)를 직접 열람해 확정해야 하는 항목이 다수 있다.
>
> **대상 Epic**: Epic 18(가칭) — `docs/roadmap.md` 1.1절 2번 항목.
> **사용자 요청 원문**: "매도등록이랑 비용등록은 증권사에서 조회전용으로 자동으로 못 가져오나? 증권사 api 연결할 때는 모두 다 무조건 조회전용으로 연결해. 절대 다른 건 하지 말고 전부 다 조회만 해오면 돼. 일단 증권사 하나만 시범적으로 해보자." / "시중 증권사 목록 다 db 테이블 하나 만들어서 공통코드처럼 넣어놓으면 되잖아, 사용자랑 매칭만 추가해주는 식으로 가야지."

## 1. 조사 목적

증권사 API를 **조회 전용(read-only)** 으로만 연동해 매도 기록·비용 기록의 자동 채움을 시도할 수 있는지 판단하고, 파일럿 대상이 될 증권사 1곳의 기술적 실현 가능성·한계를 규명한다. 동시에 사용자 요청("시중 증권사 목록을 공통코드처럼 DB에 넣어두고 사용자는 매칭만")을 받기 위한 `securities_firms` 테이블 확장 방향과, 계좌별 브로커 인증정보 연결 구조의 후보를 조사한다.

## 2. 조사 대상

- 파일럿 대상 선정 근거(한국투자증권 KIS Developers REST API)
- 인증 흐름: 앱키·앱시크릿 발급, 접근토큰 발급·갱신, 사용자 연결 절차
- "조회 전용"을 보장하는 기술적 수단(엔드포인트 분리 / 권한 레벨 / 코드 레벨 강제)
- 매도·비용 자동 채움 대상이 되는 응답 필드의 존재 여부
- Rate limit · 무료 여부 · 모의투자(비실계좌) 환경
- 다른 증권사 API 후보 비교(확장 참고용, 이번 범위 밖)
- `securities_firms` 테이블 확장 및 시드 데이터 설계
- `SecuritiesAccount` ↔ 브로커 인증정보 연결 구조
- 보안·리스크 평가

## 3. 출처

**외부(2026-09-29 Master 사전조사, WebSearch 확인)**
- KIS Developers(`apiportal.koreainvestment.com`)는 REST 기반(2022년 4월부터 확장, 국내주식·해외주식·채권·파생상품 지원). 공식 GitHub `koreainvestment/open-trading-api` 존재 확인.
- 신청 절차(algolab.co.kr, beetlekim.com 등 여러 가이드에서 일관 확인): ① 한국투자증권 실계좌 보유 → ② 홈페이지 [서비스신청]-[Open API]-[KIS Developers]에서 인증서 로그인 + 휴대폰 인증 → ③ 사용할 계좌번호 선택 및 계좌 비밀번호로 계좌 인증 → ④ 신청 접수·승인 후 앱키(App Key)·앱시크릿(App Secret) 발급 → ⑤ 앱키/시크릿으로 접근토큰(access token) 발급받아 API 호출.
- **앱키는 발급 시 선택한 계좌 하나에만 유효**하다(다른 계좌에 같은 앱키 사용 시 오류).
- 조회(잔고·체결내역 등)는 GET, 주문(매수/매도)은 POST로 **엔드포인트가 분리**돼 있다(여러 소스 일관).

**코드베이스**
- `backend/src/main/resources/db/migration/V3__create_securities_firms_table.sql`
- `backend/src/main/java/com/ipocalc/securitiesfirmcatalog/**` (Epic 3)
- `backend/src/main/java/com/ipocalc/securitiesaccountmanagement/**` (Epic 4, `infrastructure.encryption.AccountNumberEncryptor` / `AccountNumberMasker`)
- `backend/src/main/java/com/ipocalc/salemanagement/domain/model/sale/Sale.java`, `V9__create_sales_table.sql` (Epic 8)
- `backend/src/main/java/com/ipocalc/costrecording/domain/model/cost/Cost.java`, `V10__create_costs_table.sql` (Epic 9)
- `backend/src/main/resources/db/migration/V5__create_ipos_table.sql`, `V7__create_subscriptions_table.sql`, `V8__create_allocations_table.sql` (자동 채움 매칭 키 확인용)
- `backend/src/main/resources/application.yml` (Flyway / `ddl-auto: validate` / `app.encryption.key` / 로깅 레벨)
- `backend/build.gradle.kts` (Spring Boot 3.3.4, `spring-boot-starter-web`만 존재 — `RestClient` 사용 가능, WebClient 없음)
- `docs/roadmap.md` 1.1절, `docs/work-plan.md` 9·22·23·26·37장
- `docs/research/securities-firm-catalog.md`, `docs/research/securities-account-management.md`, `docs/research/sale-management.md`

## 4. 확인한 내용

### 4.1 파일럿 대상 선정 근거 — 한국투자증권 1순위

| 근거 | 내용 | 확실성 |
|---|---|---|
| REST 기반 | 웹 백엔드(Spring Boot)에서 직접 호출 가능한 HTTP/JSON 인터페이스 | 확인(외부) |
| 공식 문서·예제 | 공식 GitHub 저장소 존재(명세·샘플 확인 가능) | 확인(외부) |
| 인증 절차 문서화 수준 | 발급 절차가 여러 가이드에서 일관되게 안내됨 | 확인(외부) |
| 조회/주문 분리 | 조회는 GET, 주문은 POST로 엔드포인트가 분리됨 | 확인(외부) |
| 계좌 단위 앱키 | 앱키가 계좌 1개에 한정 → 우리 `SecuritiesAccount` 단위와 1:1 정합 | 확인(외부) |

반면 다른 증권사는 웹 백엔드에서 바로 붙이기 어려운 미확인 항목이 많다(4.6절). **1개사 파일럿 요건에 가장 부합하는 선택은 한국투자증권이다.**

> **중요 한계**: 아래 모든 "확인(외부)"은 **개념 수준의 확인**이며, 실제 엔드포인트 경로·HTTP 메서드·파라미터명·응답 필드명·권한 한계·rate limit 수치는 **미확인**이다. 착수 전 API 문서 원문 열람이 선행돼야 한다.

### 4.2 인증 흐름

#### (1) 앱키·앱시크릿 발급

사용자가 **직접 한국투자증권 홈페이지에서** 발급받는다. 절차는 3절 "출처"에 적힌 ①~⑤와 동일하다.

- 우리 서비스는 발급 과정에 개입하지 않는다.
- **계좌 비밀번호는 우리 서비스가 절대 받지 않는다.** (계좌 인증은 KIS 홈페이지에서 사용자가 수행) — 보안상 중요한 설계 제약.
- 앱키·앱시크릿을 발급받은 뒤, 사용자가 우리 서비스에 직접 입력한다.

#### (2) 앱키의 계좌 바인딩 — 구조적 강점

"앱키는 발급 시 선택한 계좌 하나에만 유효"하다는 확인된 사실은 우리 도메인에 그대로 맞아떨어진다.

```text
SecuritiesAccount (사용자 실제 계좌) 1건
  └─ 브로커 인증정보 1세트 (앱키·앱시크릿·토큰)

→ SecuritiesAccount 1 : BrokerCredential 1  (UNIQUE)
```

한 사용자가 한투 계좌를 2개 연결하려면 앱키 2세트가 필요하다. 이 불편은 우리 서비스의 설계 결함을 미리 막아 준다(계좌 단위 격리가 브로커 측에서도 강제됨 → 한 계좌의 앱키로 다른 계좌를 조회하려는 시도 자체가 브로커에서 거부됨).

#### (3) 접근토큰 발급·갱신

- 앱키·앱시크릿으로 접근토큰을 발급받아 API를 호출한다(확인된 사실).
- **토큰 유효기간(만료 시각), 갱신(refresh) 절차의 존재 여부, 재발급 시 계좌 재인증 필요 여부 — 모두 미확인.** 실계좌 정보를 요구하는 재발급 절차일 경우 토큰 만료마다 사용자 개입이 필요할 수 있어, 자동 동기화 UX 설계에 직접 영향을 준다.
- 이 세 가지가 확인되기 전에는 "매도/비용 자동 채움"의 자동화 수준(완전 자동 vs 사용자 승인형 반동기)을 확정할 수 없다.

#### (4) 사용자 연결 절차(안)

```text
1) 사용자: 한국투자증권 실계좌를 이미 보유 (없으면 개설 — 이 서비스 밖)
2) 사용자: KIS 홈페이지에서 실계좌 인증 + KIS Developers 앱키/앱시크릿 발급
3) 사용자: 우리 서비스 화면에서 [증권계좌 선택] → [앱키/앱시크릿 입력]
4) 우리 서비스: 앱키/앱시크릿 암호화 저장 → 즉시 1회 연결 검증(토큰 발급 + 잔고 조회 등 조회 전용 호출)
5) 이후: 사용자가 [동기화]를 누르거나 배치로 조회 → 미리보기 → 사용자 확정 → Sale/Cost 기록 생성
```

### 4.3 조회 전용 보장을 기술적으로 강제하는 방법

#### (1) 브로커 측 확인된 사실과 한계

- 조회(GET)와 주문(POST)이 엔드포인트 수준에서 분리돼 있다 → **주문 메서드만 쓰지 않으면 조회 전용 사용이 성립한다**는 것이 확인된 부분.
- **앱키 발급 단계에서 "조회 권한만" 선택하는 옵션이 있는지는 미확인.** 이 옵션이 없으면 앱키는 기술적으로 주문 권한도 보유할 수 있다. 따라서 "앱키 권한 차단"에만 의존할 수 없다.

#### (2) 설계·구현 레벨 강제안 (권고 — 5개 장치)

| # | 장치 | 내용 | 방어 대상 |
|---|---|---|---|
| A | **인터페이스에 조회 메서드만 선언** | 브로커 어댑터 공개 인터페이스에 조회(잔고/체결/일별주문체결) 메서드만 존재하게 하고, 주문·취소·정정 메서드는 아예 정의하지 않는다. 구현 클래스에도 대응 메서드를 만들지 않는다 | 개발자 실수·리팩터링으로 주문 코드가 들어오는 경로 |
| B | **아웃바운드 HTTP 메서드 제한** | 브로커 클라이언트 패키지에서 `post()`/`put()`/`patch()`/`delete()` 호출이 0건임을 아키텍처 테스트(ArchUnit) 또는 정적 검사로 강제. 브로커 호출은 `get()`만 허용 | 실수로 다른 HTTP 메서드가 섞이는 경우 |
| C | **경로 allowlist + 고정 base URL** | base URL은 백엔드 설정값으로 고정하고 **사용자 입력으로 바꿀 수 없게** 한다. 출구(egress) 프록시/방화벽에서 브로커 호스트 + 조회 경로 prefix만 허용 | SSRF, 사용자 조작으로 다른 엔드포인트 호출 |
| D | **시크릿·토큰은 백엔드 전용** | 앱키·앱시크릿·토큰은 브라우저로 절대 전달하지 않는다. 조회 API 컨트롤러 응답에도 포함하지 않는다 | XSS·브라우저 로그·devtools 노출 |
| E | **호출 감사 로그 + 아웃바운드 모니터링** | 모든 브로커 아웃바운드 호출에 (시각, 계좌 ID, HTTP 메서드, 경로, 상태코드)만 남기고 시크릿은 남기지 않는다. 주문 경로가 한 건이라도 관측되면 알림 | 침해·오설정 조기 감지 |

#### (3) 미충족 항목

- **장치 C·E는 감지·차단이 아니라 방어 심화다.** E(감사 로그)는 관측만 하고 요청을 막지 못하며, C의 네트워크 레벨 차단은 이 애플리케이션만으로는 완전히 보장할 수 없다. **1차 안전장치는 "우리 코드가 주문 API를 아예 호출하지 않는 것"(A·B)** 이고, 브로커 측 앱키 권한 제한은 **미확인**이다. 이 두 가지를 사용자에게 명시적으로 고지해야 한다("주문 권한이 기술적으로 부여될 수 있으나 이 서비스는 주문 API 코드를 보유하지 않는다").

### 4.4 매도·비용 자동 채움 가능성

#### (1) 필드 매핑 후보

`Sale`(V9)과 `Cost`(V10)의 실제 컬럼을 기준으로 한다.

| 우리 필드 | 의미 | 브로커 응답 대응값 | 상태 |
|---|---|---|---|
| `sales.allocation_id` | 연결 배정 (FK, NOT NULL) | **브로커 응답에 대응 개념이 없음** | 직접 매핑 불가 (4.4-2 참조) |
| `sales.quantity` | 매도수량(주) | 체결수량 | 미확인(필드명) |
| `sales.price` | 매도단가(원) | 체결단가 | 미확인(필드명) |
| `sales.amount` | 매도금액(원) | 체결금액(또는 계산) | 미확인(필드명, 계산 가능성) |
| `sales.sale_date` | 매도일시 | 체결일시 | 미확인(필드명) |
| `sales.fee` | 수수료(원) | 수수료 | **미확인 — 본 조사에서 확정 못 함** |
| `sales.tax` | 세금(원) | 거래세 | **미확인 — 본 조사에서 확정 못 함** |
| `sales.other_cost` | 기타비용(원) | 대응 개념 없음 | 자동 채움 불가(사용자 입력 유지) |
| `costs.subscription_id` | 연결 청약 (FK, NOT NULL) | 브로커 응답에 대응 개념 없음 | 4.4-2 참조 |
| `costs.cost_type` | 비용종류(자유 텍스트) | 매핑 테이블 필요(브로커 비용코드 → 우리 문자열) | 미확인 |
| `costs.amount` | 금액(원) | 비용금액 | 미확인(필드명) |
| `costs.incurred_at` | 발생일시 | 비용 발생일시 | 미확인(필드명) |

Master 사전조사에서 **수수료·세금 등 구체적 필드명은 이번 조사로 확정하지 못했다.** 공식 GitHub 저장소에 실제 명세/예제가 있으나 본 워커는 웹 접근이 없어 열람하지 못했다. **착수 전에 Master가 원문을 직접 열람해 확정해야 하는 최우선 항목이다.**

#### (2) 구조적 매칭 문제 — 자동 채움의 진짜 관건

`Sale.allocation_id`와 `Cost.subscription_id`는 NOT NULL FK라 브로커 응답을 곧바로 넣을 수 없다. 이 서비스의 도메인 체인은 다음과 같다.

```text
securities_accounts
        |
        v
  subscriptions ------> allocations ------> sales
        |                      ^
        v                      |
      costs -------------------+
        |
        v
  ipos (stock_code)
```

즉 브로커 응답(계좌 + 종목코드 + 일시)을 우리 내부 ID(`allocation_id`, `subscription_id`)로 번역하려면 위 체인을 따라 매칭해야 한다.

브로커 체결내역에 없는 값(우리 쪽 내부 ID)을 채우려면 **브로커 거래 → 우리 배정** 매칭 규칙이 필요하다. 가능한 키와 그 한계:

| 후보 매칭 키 | 데이터베이스 근거 | 한계 |
|---|---|---|
| (증권계좌, 종목코드, 청약일자 근사) | `subscriptions.securities_account_id` + `ipos.stock_code` + `subscriptions.subscribed_at` | `ipos.stock_code`는 **NULL 허용**이다. 종목코드를 입력하지 않은 IPO는 매칭 자체가 불가능 |
| 종목코드만 | `ipos.stock_code` | 이 서비스의 목적은 공모주지만 스키마상 일반 주식도 들어갈 수 있음. 계좌를 거르지 않으면 타 계좌 거래가 섞임 |
| 브로커 거래 고유 ID 저장 | 없음 | `sales`에 외부 거래 식별자 컬럼이 **없다**. 중복 등록 방지를 위해 신규 컬럼(`external_transaction_id`)이 필요할 수 있음 — **신규 컬럼 추가 여부는 설계 단계에서 결정 필요** |

**추가로 확인해야 할 사실(전부 미확인)**: 브로커 응답의 종목코드 형식(6자리/8자리, 접두·접미어), 매도/매수 구분 플래그 존재 여부, 체결일시 타임존(국내 거래소 기준 KST) 처리 방식, 수수료/세금이 별도 필드인지 반올림 포함 금액인지.

#### (3) 자동 "등록" vs 자동 "채움" — 사람 결정 필요

- **자동 등록(브로커 조회 결과가 곧바로 `sales`/`costs` 행이 됨)**: 사용자가 검토할 기회가 없어 오탐·중복이 즉시 금융 기록에 반영된다. `docs/work-plan.md` 22·23장(데이터 무결성, 금융 기록 삭제·보존 정책)의 원칙에 어긋난다.
- **자동 채움(조회 결과를 폼에 미리 채워 넣고 사용자가 확정)**: 검증 기회를 사람이 갖는다. **본 조사의 권고는 자동 채움 방식이다.** 자동 등록은 사용자 승인이 있을 때만 검토한다.
- 중복 등록 방지: 이미 수동으로 등록한 `Sale`이 있으면 브로커 거래를 다시 가져와도 중복 행이 생긴다. → 중복 판정 키(계좌+종목코드+일시+수량+단가 근사) 또는 외부 거래 ID 저장이 필요하다.

#### (4) 비용(Cost) 자동 채움의 별도 난점

`Cost`는 "청약/취득 단계 비용"이고, 브로커 조회로 자연스럽게 얻는 것은 **매도 단계 비용**이다. 이 둘은 개념이 다르다(`docs/work-plan.md` 13·26장). 매도 수수료는 `Sale.fee`/`Sale.tax`에 들어가는 것이 맞고, `Cost`로 중복 기록하면 수익 계산이 이중 차감될 수 있다(→ E10 `ProfitCalculator` 드리프트). 따라서 **1차 파일럿 범위는 `Sale`의 조회 전용 자동 채움으로 한정하고, `Cost`는 채움 대상에서 제외**하는 것을 권고한다. 이 결정은 spec 단계에서 명시해야 한다.

### 4.5 운영 조건 (Rate limit · 무료 여부 · 모의투자 환경)

| 항목 | 상태 | 영향 |
|---|---|---|
| Rate limit (호출/초, 일 한도) | **미확인** | 배치 동기화 주기 설계 불가 |
| API 사용료 | **미확인** (무료라는 확인은 없음) | 비용 게이트 필요 |
| 모의투자/비실계좌 테스트 환경 | **미확인** | 실계좌 없이 개발·테스트 불가능하면 CI 설계가 바뀜 |
| 샌드박스 토큰 발급 절차 | **미확인** | — |
| API 장애 시 서비스 영향 | 조회 실패로 격리 가능(쓰기 없음) | 설계상 안전 |

이 다섯 항목 중 **모의투자 환경 미지원**은 구현 전략에 가장 큰 영향을 준다. 실계좌 없이 개발하면 E2E/통합 테스트를 네트워크 호출 없이 구성해야 하고(어댑터 계층에 포트/어댑터 패턴 + WireMock/Stub), 실계좌 호출은 수동 검증 절차로 분리해야 한다.

### 4.6 다른 증권사 후보 비교 (확장 참고용 — 이번 파일럿 범위 밖)

| 증권사 | 확인된 사실 | 웹 백엔드 호출 가능성(판정) | 조회 API 지원 |
|---|---|---|---|
| 한국투자증권 | REST(확인). 조회/주문 분리(확인) | 가능 | 확인(개념 수준) |
| 키움증권 | 기존 Open API+는 Windows 전용 모듈 설치 방식(OCX/COM 계열로 **보인다** — 확정 아님). `openapi.kiwoom.com`에 "KIWOOM REST API" 포털이 검색에 잡힘 | 구 방식이면 **불가능**. REST가 있다면 가능 | **미확인** |
| NH투자증권 | — | 미확인 | **미확인** |
| KB증권 | — | 미확인 | **미확인** |
| 미래에셋증권 | — | 미확인 | **미확인** |
| 삼성증권 | — | 미확인 | **미확인** |

위 표에서 "미확인"은 **이번 조사에서 확인하지 못했다**는 뜻이며, "없다"는 뜻이 아니다. 다른 증권사의 실제 API 제공 여부·형태는 향후 확장 시 별도 조사 대상이다. 한국투자증권과 NH투자증권의 관계(동일 법인 리브랜칭 여부)도 **미확인**이다 — 시드 데이터 설계에서 이름 충돌을 다뤄야 할 수 있으므로(4.7절) 확인이 필요하다.

### 4.7 `securities_firms` 테이블 확장 설계안

#### (1) 현재 상태

```sql
-- V3__create_securities_firms_table.sql (실측)
id, name, external_code, business_registration_number, status, deleted_at, created_at, updated_at
CREATE UNIQUE INDEX uk_securities_firm_name_active ON securities_firms(name, status);
CREATE INDEX idx_securities_firm_status ON securities_firms(status);
```

- **시드 데이터 없음.** V3 마이그레이션에 `INSERT`가 없고, 모든 행은 사용자가 `POST /securities-firms`로 직접 만든다(`SecuritiesFirmService.register`). (운영 DB에 지금 어떤 행이 들어 있는지는 6절 #19에서 별도 확인이 필요하다.)
- `SecuritiesFirm`은 **계정 소유 엔티티가 아니라 글로벌 기준정보**이며, 계정 소유권 검증(`SecurityUtils`)이 적용되지 않는다. 계좌 등록 시 `securitiesFirmId`만 넘기면 된다.
- `external_code VARCHAR(255)`는 nullable이고 **현재 사용되지 않는다**(등록 DTO에서만 받는다).

#### (2) `external_code` 재사용 검토 — 권고: **재사용하지 않는다**

| 안 | 내용 | 평가 |
|---|---|---|
| A. `external_code`를 브로커 API 코드 전용으로 재정의 | 의미 변경 | ❌ 위험. 기존 사용자 입력값과 의미 충돌. "금융투자협회 표준코드" 등 다른 용도의 값이 이미 들어갈 수 있어 되돌릴 수 없음 |
| B. `api_external_code` 컬럼을 **신규 추가** | 기존 `external_code`는 그대로 두고 별도 컬럼 | ✅ **권고.** 되돌릴 수 있고 의미가 분명 |
| C. `broker_api_providers` 기준정보 테이블을 신설하고 `securities_firms.api_provider_id` FK로 연결 | 어댑터 메타데이터를 별도 관리 | ✅ 구조적으로 가장 정합적이나 파일럿 규모에 과함. 확장 시 도입 후보로 기록 |

**권고 = B.** 파일럿은 `api_provider`(어떤 어댑터인지 식별) + `api_supported`(조회 API 연동 대상인지) 두 축만 최소화한다.

#### (3) 추가 컬럼 제안 (설계안 — 마이그레이션은 spec/architecture 승인 후 별도 SF)

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `api_supported` | BOOLEAN | NOT NULL DEFAULT FALSE | 조회 전용 API 연동 대상 증권사인가 |
| `api_provider` | VARCHAR(50) | NULL | 사용할 브로커 어댑터 식별자(파일럿은 단일 값, 예: `KIS`) |
| `api_auth_type` | VARCHAR(50) | NULL | 인증 방식. 파일럿은 앱키/앱시크릿 기반(`APP_KEY_SECRET`). 유형 확장 대비 |
| `api_readonly_only` | BOOLEAN | NOT NULL DEFAULT TRUE | **이 서비스가 해당 증권사를 조회 전용으로만 사용함을 명시** |
| `api_external_code` | VARCHAR(255) | NULL | 브로커 측 증권사 식별 코드(4.7-2의 B안) |

마이그레이션 주의사항(코드베이스 실측 기반):

- `spring.jpa.hibernate.ddl-auto: validate` + `spring.flyway.validate-on-migrate: true`이므로 **엔티티와 스키마가 어긋나면 기동이 실패한다**. 엔티티 필드 추가와 마이그레이션은 반드시 같은 SF에서.
- 기존 행이 있을 수 있으므로 새 NOT NULL 컬럼은 `DEFAULT`를 두고 추가한다(기존 행이 `FALSE`/`TRUE` 기본값으로 채워진다).
- `uk_securities_firm_name_active`은 **partial 인덱스가 아니라 `(name, status)` 전체 유니크**다. 즉 어떤 증권사를 `deactivate` 하면 `status='INACTIVE'`가 되어 같은 이름의 `ACTIVE` 행을 새로 만들 수 있다. → 시드 INSERT 전에 기존 비활성 행의 존재 여부를 확인해야 한다(4.7-5).

#### (4) 시드 데이터 초안(후보 목록)

**아래는 "시드할 증권사 이름 후보" 초안이며, 확정된 사실이 아니다.** 시드 적재 전 아래를 반드시 수행해야 한다.

- 정식 명칭·사업자등록번호는 금융감독원/금융투자협회 등 권위 있는 출처에서 확인 (현재 **미확인** → `business_registration_number`는 NULL로 두고 나중에 보강)
- 조회 API 지원 여부는 **한국투자증권(확인)·키움증권(REST는 미확인) 외 전부 미확인**
- `api_provider`는 실제 어댑터가 있는 증권사에만 채우고, 그 외는 NULL

| # | 증권사명(후보) | 조회 API 지원 | `api_supported` | `api_provider` | 비고 |
|---:|---|---|---|---|---|
| 1 | 한국투자증권 | 확인(개념 수준) | TRUE | `KIS` | 파일럿 대상. 계좌당 앱키 1세트 |
| 2 | 키움증권 | 미확인(REST 포털 존재는 검색에 잡힘) | FALSE | NULL | 구 Open API+는 Windows 전용으로 보임 |
| 3 | NH투자증권 | 미확인 | FALSE | NULL | 한국투자증권과의 관계 미확인 — 이름 충돌 주의 |
| 4 | 미래에셋증권 | 미확인 | FALSE | NULL | |
| 5 | 삼성증권 | 미확인 | FALSE | NULL | |
| 6 | KB증권 | 미확인 | FALSE | NULL | |
| 7 | 신한투자증권 | 미확인 | FALSE | NULL | |
| 8 | 하나증권 | 미확인 | FALSE | NULL | 정식 명칭 미확인 |
| 9 | 대신증권 | 미확인 | FALSE | NULL | |
| 10 | 유안타증권 | 미확인 | FALSE | NULL | |
| 11 | 한화투자증권 | 미확인 | FALSE | NULL | |
| 12 | SK투자증권 | 미확인 | FALSE | NULL | |
| 13 | 메리츠증권 | 미확인 | FALSE | NULL | |
| 14 | 토스증권 | 미확인 | FALSE | NULL | |
| 15 | 신영증권 | 미확인 | FALSE | NULL | |

> 위 명칭은 **일반적으로 통용되는 명칭이며 시드의 정본이 아니다.** 정식 명칭 확인 전 시드 적재는 금지. 여러 이름 후보(예: "하나증권" vs "하나금융투자증권")가 존재할 수 있어 유니크 인덱스 충돌 위험이 있다.

#### (5) "시드 목록 + 사용자 매칭"으로 전환할 때의 마이그레이션 전략

현재는 "사용자가 직접 증권사를 만든다" 구조이고, 목표는 "시드된 목록에서 선택한다"이다. 이미 만들어진 행이 있을 수 있으므로 다음 전략을 권고한다.

1. **시드는 idempotent하게 적재한다.** `INSERT ... ON CONFLICT (name, status) DO NOTHING`(또는 동일) 형태로, `uk_securities_firm_name_active`에 의존한다. 중복 실행에도 안전해야 한다.
2. **기존 사용자 행을 지우지 않는다.** 이미 사용자가 만든 증권사 행은 그대로 두고, 이름이 시드와 일치하면 `api_supported`/`api_provider`만 백필(backfill)한다. 이름이 다르면(오타, 별칭) **자동 병합하지 않는다** — 합치면 기존 계좌의 참조 의미가 바뀐다.
3. **동일 firm 다중 행 문제**: 한 사용자가 이미 "한국투자증권"을 직접 만들었고 시드에도 같은 이름이 들어가는 경우, `ON CONFLICT`가 시드 적재를 무시하므로 결과적으로 중복이 생기지 않는다(유니크 인덱스가 막아 준다). 반대로 이미 `INACTIVE` 행이 있으면 `ACTIVE` 시드 행이 새로 생겨 **같은 이름 2행**이 될 수 있다 → 적재 전 `SELECT`로 사전 확인하거나, 시드 적재 시 `status='ACTIVE'` 행이 없을 때만 삽입하는 조건을 넣는다.
4. **기존 계좌의 `securities_firm_id`는 변경하지 않는다.** 시드는 신규 선택지 제공일 뿐 기존 참조를 재배치하지 않는다.
5. **등록 API 정책 변경은 별도 결정이다.** 사용자 임의 증권사 생성을 계속 허용할지("커스텀 증권사"), 시드 목록 선택만 허용할지("공통코드")는 spec에서 결정한다. `work-plan.md` 9장에 이미 "증권사와 계좌는 별도 도메인"이라는 원칙이 있어, 커스텀 허용도 구조적으로는 가능하다. **본 조사는 이 결정에 대해 의견을 제시하지 않고 결정 필요로 남긴다.**

### 4.8 사용자 계정 연결(매칭) 설계안

#### (1) 브로커 인증정보는 `SecuritiesAccount`에 직접 두지 않는다 — 신규 테이블 권고

`securities_accounts`에 앱키/앱시크릿 컬럼을 추가하는 안은 **비권고**다. 근거:

- `securities_accounts`는 `account_number`(암호화), `account_name`, `is_default` 등 **도메인 식별 정보**를 담는 엔티티이고, 인증정보(시크릿·토큰)는 **인프라 자격증명**이다. 수명 주기(토큰 갱신·회수·무효화)가 다르다.
- `SecuritiesAccountRegisterRequest`/`UpdateRequest`/`Response` DTO에 시크릿이 섞이면 **응답 DTO에 실수로 시크릿이 노출될 위험**이 생긴다. 기존 마스킹 규칙(`AccountNumberMasker`)도 깨진다 — 참고로 현재 `SecuritiesAccountResponse`는 필드명이 `accountNumber`지만 실제로는 `AccountNumberMasker.mask()` 결과를 담는다(평문이 아님). 응답 DTO에 시크릿이 섞이면 "암호문을 담는 필드"와 "마스킹을 담는 필드"가 같은 DTO에 공존해 리뷰·검증 난이도가 급격히 올라간다.
- 브로커 어댑터가 여러 개로 늘어나면(키움, KB 등) 컬럼 확장 경쟁이 시작된다.
- 시크릿을 담는 테이블은 **백필·권한·감사 정책이 기준정보 테이블과 다르다**(계정 소유권 검증 필수, 접근 로깅, 마스킹).

제안 스키마(설계안):

```sql
-- 개념 설계안(코드 아님). 파일명·버전은 spec/architecture 승인 후 확정.
CREATE TABLE securities_account_api_credentials (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    securities_account_id     BIGINT NOT NULL,
    api_provider              VARCHAR(50)  NOT NULL,   -- 'KIS' 등 어댑터 식별자
    app_key_encrypted         TEXT         NOT NULL,
    app_secret_encrypted      TEXT         NOT NULL,
    access_token_encrypted    TEXT         NULL,      -- 있으면 저장, 없으면 매번 재발급 (선택)
    token_expires_at          TIMESTAMP    NULL,      -- 만료 시각. 미확인이라 스키마만 준비
    status                    VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at                TIMESTAMP    NULL,
    created_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_api_cred_account_provider
    ON securities_account_api_credentials (securities_account_id, api_provider)
    WHERE status = 'ACTIVE';
ALTER TABLE securities_account_api_credentials
    ADD CONSTRAINT fk_api_cred_account
    FOREIGN KEY (securities_account_id) REFERENCES securities_accounts (id) ON DELETE RESTRICT;
```

핵심 지점:

- **1:1 제약**: 계좌 1건에 브로커 인증정보 1세트(계좌당 앱키 1세트라는 외부 제약과 정합).
- **계정 소유권 검증 필수**: `securities_accounts` → `investors` → `accounts` 체인으로 `SecurityUtils` 검증 경로를 따른다. Epic 3(글로벌 기준정보)과 달리 이 엔티티는 **계정 소유 엔티티**다.
- **응답 DTO에는 절대 평문 시크릿을 넣지 않는다.** 연결 상태(`connected`/`expired`/`error`), 연결된 계좌의 마스킹된 계좌번호, 마지막 동기화 시각만 노출한다.
- **토큰 저장 여부는 미확인 항목에 따라 결정한다.** 토큰 유효기간·갱신 방식이 확인되면 저장(성능) 또는 미저장(안전, 호출 시 재발급) 중 선택한다. **기본값은 미저장 + 요청 시 재발급**을 권고한다(분석 대상: 토큰 유효기간이 길면 매 호출마다 발급이 rate limit에 걸릴 수 있음 — 미확인).

#### (2) 암호화 인프라 재사용

`securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor`는 **AES-256-GCM, JDK 내장 `javax.crypto`만 사용, `app.encryption.key`(환경변수 `APP_ENCRYPTION_KEY`)로 32바이트 키 주입, 매 호출 랜덤 12바이트 IV, 결과는 `Base64(IV + ciphertext)` self-contained**이다. 이 구현은 시크릿 저장에 그대로 재사용할 수 있다(라이브러리 추가 없음, D-SA-001 결정을 그대로 따름).

다만 **재사용 시 손봐야 할 부분**이 있다.

| 항목 | 현재 | 권고 |
|---|---|---|
| 키 분리 | `app.encryption.key` 단일 대칭키가 계좌번호와 앱시크릿을 **동시에** 보호 | **별도 키 분리 권고**(`app.encryption.broker-key`). 단일 키 유출 시 계좌번호와 앱시크릿이 함께 노출됨 |
| 클래스 이름 | `AccountNumberEncryptor` | 시크릿까지 쓰면 이름이 부정확. 공용 `SecretStringEncryptor`로 추출하거나, 기존 클래스는 그대로 두고 brokerage 용 인스턴스를 별도 빈으로 두는 방식 검토 |
| 키 미설정 시 | 빈 키 허용, 실제 호출 시 `IllegalStateException` | 동일 정책 유지(테스트 컨텍스트 기동 방해 방지). 단, 시크릿 테이블은 **키 미설정 상태로 서비스 기동되면 안 되는지** 별도 판단 대상(연결 기능 자체를 비활성화하는 게 안전) |
| 로그 | `application.yml`에 `org.hibernate.SQL: DEBUG`, `org.hibernate.type.descriptor.sql.BasicBinder: TRACE` | DB에 저장되는 값은 암호문이므로 바인딩 로그가 평문을 노출하지는 않는다. **단, 등록 요청 DTO를 로깅하는 경로가 없어야 하며**, 시크릿 평문이 로그·예외 메시지·감사 로그에 들어가지 않도록 한다 |

#### (3) 계좌번호의 재사용 문제 (중요 미해결)

브로커 조회 API가 **계좌번호 파라미터(`cant` 등)를 요구할 가능성이 높으나 미확인**이다. 요구된다면 우리 서비스는 이미 암호화해 둔 `securities_accounts.account_number`를 복호화해 파라미터로 사용해야 한다.

- 복호화는 `AccountNumberEncryptor.decrypt()`로 가능하고, `AccountNumberMasker`로 마스킹 규칙이 이미 정해져 있다.
- **하지만 이것은 `docs/work-plan.md` 37장이 "초기 MVP에서 하지 않는 것"으로 명시한 "증권사 계좌 자동 연동"에 해당하는 스코프 확장이므로, `docs/roadmap.md` 1.1절의 사용자 승인과 함께 진행해야 한다.** roadmap 1.1절 2번 항목이 그 승인이므로 이번 스코프에 포함된다고 본다 — 단, **계좌번호의 복호화 목적이 "표시"에서 "외부 브로커 API 전송"으로 확장되는 점을 보안 검토에서 별도로 다뤄야 한다.**
- 브로커가 요구하는 계좌번호 **형식이 증권사별로 다를 수 있으므로**(하이픈 포함/제외 등) 형식 정규화 규칙이 필요하다 — 미확인.

### 4.9 보안·리스크

| # | 위험 | 등급 | 근거·완화 |
|---|---|---|---|
| R1 | 앱키/앱시크릿 탈취 | **낮음(조회 전용 한정)** | 조회 전용이므로 자금 이동 불가. 앱키는 계좌 1개에 한정되어 피해 범위도 1계좌로 제한. 암호화 저장·응답 미노출·로그 금지가 1차 방어 |
| R2 | 개인정보/금융정보 유출 | **중간** | 조회 전용이어도 잔고·보유종목·거래내역은 민감 금융정보다. 계정 소유권 검증, 접근 로깅, 마스킹, 최소 접근 원칙 적용 |
| R3 | 서비스가 실수로 주문 API 호출 | **낮음(구조적으로 차단)** | 4.3의 A·B 장치(인터페이스에 조회만 선언 + 아웃바운드 HTTP 메서드 제한 테스트). **단, 앱키 자체에 주문 권한이 부여될 가능성은 미확인**이므로 "완전 차단"이 아니라 "코드 레벨 차단"으로 규정해야 함 |
| R4 | SSRF — 사용자 입력으로 브로커 URL 조작 | **중간** | base URL 고정 + 사용자 입력 불가 + 아웃바운드 allowlist(4.3-C) |
| R5 | 브로커 API 스펙 변경·장애 | **중간** | 조회 실패는 쓰기 부재로 금전 영향이 없다. 응답 파싱이 관용적이어야 한다(알 수 없는 필드는 무시, 없는 필드는 nullable) — 브로커 계층에 `BrokerApiException`을 두고 우리 도메인으로 스펙 변화가 새지 않게 격리한다 |
| R6 | 자동 채움으로 금융 기록 오염 | **높음(자동 등록 시)** | 자동 등록 대신 **사용자 확정형 자동 채움**을 기본값으로(4.4-3). 중복 판정 키 필요 |
| R7 | 수익 계산 이중 차감 | **높음** | 매도 수수료를 `Sale.fee`와 `Cost`에 이중 기록 금지(4.4-4). `ProfitCalculator` 계약 변경 여부를 spec에서 명시 |
| R8 | 토큰 만지로 동기화 중단 | **중간** | 토큰 유효기간/갱신 **미확인**. 만료 시 사용자에게 "재연결" 안내하는 UX 필요. 자동 갱신 가능 여부는 확인 후 결정 |
| R9 | 세무상 증빙 | **낮음~중간** | 조회로 채운 값이 실제 세금 신고용 증빙으로 쓰이는지는 본 서비스 범위 밖. 자동 채움 값에 "브로커 조회" 출처 표시를 남길지 결정 필요 |
| R10 | 실계좌 없이 개발 불가 | **중간(확인 시)** | 모의투자 환경 **미확인**. 미지원이면 포트/어댑터 + 스텁으로 네트워크 격리 테스트 구성 |

**리스크 등급 요약**: 조회 전용 원칙 덕분에 **금전 이동 위험(R1·R3)은 구조적으로 낮다.** 이 연동의 실질 위험은 "기록 정확성"(R6·R7)과 "정보 보안"(R2·R4)에 집중되어 있다. 이 구분을 사용자에게 그대로 보고해야 한다.

## 5. 제약사항

1. **조회 전용 원칙이 예외 없이 적용된다.** 매수/매도/정정/취소 등 주문 실행 권한은 어떤 상황에서도 요청하거나 사용하지 않는다. `docs/roadmap.md` 1.1절 2번.
2. **웹 검색을 수행하지 않았다.** 이 워크스페이스에는 실시간 웹 검색 도구가 없다. 외부 사실은 전부 3절(출처)에 적인 Master 사전조사(2026-09-29) 범위로 한정했다.
3. **KIS Developers API 문서 원문과 공식 GitHub 저장소를 열람하지 못했다.** 필드명·경로·rate limit·토큰 수명·모의투자 환경은 전부 미확인이다.
4. **`docs/work-plan.md` 37장**의 "초기 MVP 제외" 항목(자동 증권사 로그인, 증권사 계좌 자동 연동)은 `docs/roadmap.md` 1.1절의 사용자 결정으로 이번에 해제 대상이 된다. 단, "실제 증권사 API를 통한 **매매**"는 **해제되지 않았다** — 조회 전용 원칙이 그 근거다.
5. **코드 미작성.** 이 문서는 조사 결과이며 구현 설계는 spec/architecture 단계에서 확정한다.
6. 계좌번호 암호화 인프라는 `accountauthentication`/`securitiesaccountmanagement` 경로에 위치한다. brokerage 계층이 이를 참조하면 패키지 간 의존이 생기므로, 공용 `infrastructure`로 이동할지는 **설계 단계 결정 사항**.

## 6. 불확실한 점

| # | 불확실한 점 | 영향도 | 비고 |
|---:|---|---|---|
| 1 | **체결내역 응답의 수수료·세금 등 구체적 필드명** | 높음 | 착수 전 GitHub 저장소 원문 직접 열람 필수. 이게 확정돼야 자동 채움 범위가 정해진다 |
| 2 | **앱키 발급 단계의 조회 전용 권한 옵션 존재 여부** | 높음 | 없으면 "코드 레벨 차단"이 유일한 방어. 사용자 고지에 영향 |
| 3 | 접근토큰 유효기간·갱신 방법·재발급 시 계좌 재인증 필요 여부 | 높음 | 자동화 수준(완전 자동 vs 사용자 승인형)을 결정 |
| 4 | 조회 API가 계좌번호 파라미터를 요구하는지 | 높음 | 요구 시 `account_number` 복호화 후 외부 전송이 발생. 보안 검토 필수 |
| 5 | rate limit(초/일) | 중간 | 배치 주기 설계 불가 |
| 6 | 모의투자·비실계좌 테스트 환경 지원 여부 | 중간 | 미지원 시 테스트 구조가 크게 달라짐 |
| 7 | 응답 필드의 종목코드 형식(6자리/8자리, 접두·접미어) | 중간 | `ipos.stock_code`와의 매칭 규칙에 직결 |
| 8 | 응답의 매도/매수 구분 플래그 존재 여부 | 중간 | 매도만 자동 채움하려면 필수 |
| 9 | 체결일시 타임존(KST) 처리 | 낮음~중간 | `sale_date TIMESTAMP` 저장 규칙과 대조 필요 |
| 10 | 수수료·세금이 별도 필드인지 총액 반올림인지 | 중간 | 4.4-1의 필드 매핑 정확도에 직결 |
| 11 | 모의투자 환경/샌드박스 토큰 발급 절차 | 중간 | — |
| 12 | 키움증권 REST API(`openapi.kiwoom.com`)의 실제 성격·제공 범위 | 낮음(확장 시 높음) | 이번 파일럿 범위 밖 |
| 13 | NH투자증권과 한국투자증권의 법인 관계(리브랜칭 여부) | 낮음(시드 시 중간) | 시드 이름 충돌·중복 판정에 영향 |
| 14 | 증권사 정식 명칭·사업자등록번호 | 중간(시드 시) | 권위 있는 출처 확인 필요. 없으면 `business_registration_number`는 NULL |
| 15 | `Sales`에 외부 거래 식별자를 저장할 컬럼 추가 여부 | 중간 | 중복 등록 방지의 핵심. 미추가 시 근사 매칭만 가능 |
| 16 | 기존 IPO 데이터 중 `stock_code`가 NULL인 비율 | 중간 | stock_code가 없으면 브로커 거래 매칭이 불가능. 실제 비율은 운영 데이터 확인 필요 |
| 17 | 사용자 임의 증권사 생성(커스텀)을 계속 허용할지 | 중간 | "공통코드" 요구와 상충. 사용자 결정 필요 |
| 18 | 자동 채움 값을 세무 증빙으로 볼 수 있는지 | 낮음 | 서비스 범위 밖. 출처 표시 여부 결정 필요 |
| 19 | 기존 사용자 데이터 중 이미 생성된 `securities_firms` 행의 실제 내용 | 중간 | 시드 적재 충돌 판정에 필요. 운영 DB 확인 필요 |

## 7. 개발에 미치는 영향

1. **새 도메인 패키지**: `brokerintegration`(또는 유사)에 조회 전용 어댑터·DTO·예외를 둔다. 공개 인터페이스에 조회 메서드만 선언한다(4.3-A).
2. **HTTP 클라이언트**: `spring-boot-starter-web`만 의존성이므로 `RestClient`(Spring 6.1 기본)를 사용한다. `WebClient`는 `spring-webflux` 추가가 필요하므로 쓰지 않는다. base URL은 설정값 고정, 사용자 입력 금지.
3. **아키텍처 테스트**: 브로커 패키지에 `post/put/patch/delete` 호출 0건, 주문 관련 식별자 문자열 0건을 검증하는 ArchUnit 또는 정적 검사 테스트를 추가한다(4.3-B).
4. **신규 마이그레이션**: `securities_firms` API 메타데이터 컬럼 추가 + 시드 적재(idempotent) + `securities_account_api_credentials` 생성. `ddl-auto: validate`이므로 엔티티와 같은 SF에서 작업한다.
5. **보안**: `SecurityUtils` 기반 계정 소유권 검증을 brokerage 계층에도 적용. 응답 DTO에 시크릿·토큰 미포함. 계좌번호 마스킹 정책(`AccountNumberMasker`)을 brokerage 응답에도 일관 적용.
6. **테스트**: 네트워크 호출 없는 스텁/어댑터로 조회 전용 보장 테스트(어떤 메서드도 없음), 중복 채움 방지 테스트, 계정 격리(타 계좌 연동정보 접근 403) 테스트, 시크릿 미노출 테스트를 우선 작성한다.
7. **문서**: 이 조사 → `docs/spec/broker-readonly-api-pilot.md` → `docs/architecture/broker-readonly-api-pilot.md` → `docs/breakdown/broker-readonly-api-pilot.md` 순서로 Phase 0을 이어간다. 착수 전 6절의 "높음" 항목 4개가 해소돼야 한다.

## 8. 결론

1. **한국투자증권은 조회 전용 연동 파일럿의 1순위 후보로 기술적으로 실현 가능하다.** REST 기반이며 공식 문서·예제 저장소가 존재하고, 조회(GET)/주문(POST) 엔드포인트가 분리돼 있어 조회 전용 사용이 성립한다. 앱키가 계좌 1개에 한정된다는 제약은 오히려 우리 `SecuritiesAccount` 1:1 구조와 맞아떨어져 이 계층의 설계를 단순하게 만든다.
2. **매도 자동 채움은 "가능성이 있으나 필드 스펙 미확정"이다.** 수수료·세금 필드명이 확정되지 않아 "수수료·세금까지 자동으로 채워지는가"는 **아직 답할 수 없다.** 공식 GitHub 원문 열람이 최우선 선행 작업이다. 비용(`Cost`) 자동 채움은 개념 불일치(매도 비용 vs 청약 비용)와 이중 차감 위험 때문에 **1차 범위에서 제외**하는 것을 권고한다.
3. **조회 전용은 브로커 권한이 아니라 우리 코드 구조로 강제해야 한다.** 앱키 발급 단계의 조회 전용 옵션은 미확인이다. 따라서 "브로커 클라이언트에 조회 메서드만 존재하게 한다 → 아웃바운드 HTTP 메서드를 GET으로 제한한다 → base URL 고정·allowlist → 시크릿은 백엔드 전용 → 호출 감사 로그"의 5단 조합을 설계 레벨 필수 요건으로 삼아야 한다.
4. **증권사 공통코드는 `securities_firms` 확장으로 처리할 수 있다.** `external_code`는 용도가 달라 재사용하지 않고 `api_supported`/`api_provider`/`api_readonly_only` 등 별도 컬럼을 추가하는 안을 권고한다. 시드 적재는 idempotent하게 하고 기존 사용자 행은 병합하지 않는다. 증권사 정식 명칭·사업자등록번호는 권위 있는 출처에서 확인하기 전까지는 시드의 정본이 아니다.
5. **브로커 인증정보는 `securities_accounts`가 아니라 신규 `securities_account_api_credentials` 테이블에 계좌 1:1로 보관한다.** 암호화는 `AccountNumberEncryptor`(AES-256-GCM)를 재사용하되 키를 분리하는 것을 권고한다.
6. **이 연동의 실질 위험은 금전 이동이 아니라 기록 정확성과 정보 보안이다.** 조회 전용이므로 자금 이동 위험은 구조적으로 낮고, 위험은 자동 채움에 의한 금융 기록 오염·수익 이중 차감·민감정보 유출에 집중된다.

## 9. 착수 전 추가로 확인할 항목 (Master가 다음 라운드에서 수행)

아래는 이 워커가 웹 접근 권한이 없어 수행하지 못한 확인이다. **착수 전에 해소되어야 한다.**

1. `github.com/koreainvestment/open-trading-api` 원문 직접 열람 — **체결내역(일별주문체결조회 등) 응답의 실제 필드명 목록**: 체결수량·체결단가·체결금액·수수료·거래세·매도/매수 구분 플래그·종목코드·체결일시
2. `apiportal.koreainvestment.com` — 앱키 발급 화면에 **조회 전용/주문 권한 분리 옵션**이 있는지, 없는지
3. 접근토큰 발급 응답의 `access_token`·`expires_in` 등 필드와 **갱신 방법**
4. 조회 엔드포인트가 **계좌번호 파라미터**를 요구하는지, 요구한다면 요구 형식
5. **rate limit 정책**(초당/일별) 및 초과 시 응답 코드
6. **모의투자·비실계좌 테스트 환경** 존재 여부와 샌드박스 토큰 발급 절차
7. 조회 전용 사용 시 **API 사용료** 여부(무료인지, 유료인지)
8. `openapi.kiwoom.com`의 실제 성격(확장 시 필요 — 이번 파일럿 범위 밖)
9. 국내 주요 증권사의 **정식 명칭·사업자등록번호**(시드 데이터 정본 확정에 필요)
10. 기존 운영 데이터의 (a) 사용자 생성 `securities_firms` 행 내용, (b) `ipos.stock_code` NULL 비율
