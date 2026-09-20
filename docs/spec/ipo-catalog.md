# IPO 기준정보 관리 기능 명세

## 개요
청약(Subscription)이 참조할 공모주 기준정보를 일관되게 등록·조회할 수 있는 기능을 제공한다.
MVP에서는 사용자 수동 입력 및 CSV 가져오기 중심으로 제공하며, 외부 API 자동 수집 기능은 범위 밖이다.

## 용어 정의
- **IPO**: 공모주 기준정보 엔티티 (여러 Subscription이 참조하는 기준정보 성격)
- **내부 ID**: Long 타입의 자동증가 기본키 (UUID를 사용하지 않으며 기존 프로젝트 일관성 유지)
- **종목코드**: 한국거래소 종목코드(6자리 숫자 등) — 동일 종목명(재상장, 유사종목명) 구분용 식별자 (선택사항)
- **희망밴드**: 희망공모가 하한~상한 범위
- **확정가**: 확정공모가 (확정 전에는 null 허용)

## 도메인 모델
### IPO 엔티티
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| id | Long | Y | 내부 식별자 (자동증가) |
| companyName | String | Y | 기업명 |
| ipoName | String | Y | 공모주명 |
| stockCode | String | N | 종목코드 (동일 종목명 회차 구분용, 중복 검사에서 null은 제외) |
| roundLabel | String | N | 회차 구분 라벨 (예: 재상장, 2차 — 종목코드 대안/보완용) |
| lowerBandPrice | Long | N | 희망공모가 하한 (원 단위) |
| upperBandPrice | Long | N | 희망공모가 상한 (원 단위) |
| fixedPrice | Long | N | 확정공모가 (원 단위, 확정 전 null) |
| subscriptionStartDate | LocalDate | Y | 청약 시작일 |
| subscriptionEndDate | LocalDate | Y | 청약 종료일 |
| listingDate | LocalDate | N | 상장일 (미정 시 null) |
| offeringShareCount | Long | N | 공모주식수 |
| status | String | Y | 상태 (ACTIVE/INACTIVE) |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | 생성 시각 |
| updatedAt | Timestamp | Y | 수정 시각 |

> **참고**: 조사 문서의 결론을 따라 MVP에서는 외부 API 자동 수집 기능을 구현하지 않으며, 수동 입력과 CSV 일괄 등록만 제공한다. 외부 데이터 수집은 법적/기술적 검증 완료 이후 단계에서 별도 조사 후 고려한다.

## API 엔드포인트 목록
### 1. IPO 등록
- **POST** `/ipos`
- **요청 본문**:
  ```json
  {
    "companyName": "기업명",
    "ipoName": "공모주명",
    "stockCode": "123456 (선택)",
    "roundLabel": "재상장 (선택)",
    "lowerBandPrice": 10000,
    "upperBandPrice": 12000,
    "fixedPrice": 11000,
    "subscriptionStartDate": "2026-10-01",
    "subscriptionEndDate": "2026-10-02",
    "listingDate": "2026-10-15",
    "offeringShareCount": 1000000
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "companyName": "기업명",
    "ipoName": "공모주명",
    "stockCode": "123456",
    "roundLabel": "재상장",
    "lowerBandPrice": 10000,
    "upperBandPrice": 12000,
    "fixedPrice": 11000,
    "subscriptionStartDate": "2026-10-01",
    "subscriptionEndDate": "2026-10-02",
    "listingDate": "2026-10-15",
    "offeringShareCount": 1000000,
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T10:00:00Z"
  }
  ```
- **응답** (실패 - 필수값 누락/날짜 순서 오류/가격 범위 오류): 400 Bad Request
- **응답** (실패 - 중복): 409 Conflict

### 2. IPO 목록 조회
- **GET** `/ipos`
- **쿼리 파라미터**: 없음 (모든 인증된 사용자가 조회 가능)
- **응답**:
  ```json
  [
    {
      "id": 1,
      "companyName": "기업명",
      "ipoName": "공모주명",
      "stockCode": "123456",
      "roundLabel": null,
      "lowerBandPrice": 10000,
      "upperBandPrice": 12000,
      "fixedPrice": 11000,
      "subscriptionStartDate": "2026-10-01",
      "subscriptionEndDate": "2026-10-02",
      "listingDate": "2026-10-15",
      "offeringShareCount": 1000000,
      "status": "ACTIVE",
      "createdAt": "2026-09-20T10:00:00Z",
      "updatedAt": "2026-09-20T10:00:00Z"
    }
  ]
  ```

### 3. IPO 상세 조회
- **GET** `/ipos/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "companyName": "기업명",
    "ipoName": "공모주명",
    "stockCode": "123456",
    "roundLabel": null,
    "lowerBandPrice": 10000,
    "upperBandPrice": 12000,
    "fixedPrice": 11000,
    "subscriptionStartDate": "2026-10-01",
    "subscriptionEndDate": "2026-10-02",
    "listingDate": "2026-10-15",
    "offeringShareCount": 1000000,
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T10:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 4. IPO 수정
- **PATCH** `/ipos/{id}`
- **경로 파라미터**: id (Long 타입)
- **요청 본문**:
  ```json
  {
    "companyName": "변경된기업명",
    "ipoName": "변경된공모주명",
    "stockCode": "변경된종목코드",
    "roundLabel": "변경된회차라벨",
    "lowerBandPrice": 11000,
    "upperBandPrice": 13000,
    "fixedPrice": 12000,
    "subscriptionStartDate": "2026-10-01",
    "subscriptionEndDate": "2026-10-03",
    "listingDate": "2026-10-16",
    "offeringShareCount": 2000000
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "companyName": "변경된기업명",
    "ipoName": "변경된공모주명",
    "stockCode": "변경된종목코드",
    "roundLabel": "변경된회차라벨",
    "lowerBandPrice": 11000,
    "upperBandPrice": 13000,
    "fixedPrice": 12000,
    "subscriptionStartDate": "2026-10-01",
    "subscriptionEndDate": "2026-10-03",
    "listingDate": "2026-10-16",
    "offeringShareCount": 2000000,
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T11:00:00Z"
  }
  ```
- **응답** (실패 - 유효성 오류): 400 Bad Request
- **응답** (실패 - 중복): 409 Conflict
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 5. IPO 비활성화 (논리 삭제)
- **DELETE** `/ipos/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공): 204 No Content — **연결된 Subscription이 있어도 비활성화(논리 삭제)는 항상 허용된다.** 물리 삭제(레코드 완전 제거)만 금지되며, 이 엔드포인트는 물리 삭제를 수행하지 않으므로 연결된 청약 여부와 무관하게 성공한다.
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 6. IPO CSV 일괄 등록
- **POST** `/ipos/import`
- **요청**: `multipart/form-data`, `file` 필드에 CSV 파일 (헤더: companyName,ipoName,stockCode,roundLabel,lowerBandPrice,upperBandPrice,fixedPrice,subscriptionStartDate,subscriptionEndDate,listingDate,offeringShareCount)
- **요청 예시 (CSV 내용)**:
  ```csv
  companyName,ipoName,stockCode,roundLabel,lowerBandPrice,upperBandPrice,fixedPrice,subscriptionStartDate,subscriptionEndDate,listingDate,offeringShareCount
  기업A,공모주A,123456,,10000,12000,11000,2026-10-01,2026-10-02,2026-10-15,1000000
  기업B,공모주B,,,,,,2026-11-01,2026-11-02,,500000
  ```
- **응답** (성공):
  ```json
  {
    "successCount": 2,
    "failureCount": 0,
    "failures": []
  }
  ```
- **응답** (부분 성공 — 일부 행 실패):
  ```json
  {
    "successCount": 1,
    "failureCount": 1,
    "failures": [
      { "rowNumber": 3, "reason": "청약 종료일이 시작일보다 빠름" }
    ]
  }
  ```
- **응답** (실패 - 빈 파일/헤더 오류/파싱 불가): 400 Bad Request

## Acceptance Criteria
**AC-001**: IPO 등록 성공
- Given: 유효한 기업명, 공모주명, 청약 시작일/종료일이 제공됨
- When: POST /ipos 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 신규 IPO가 생성됨

**AC-002**: IPO 등록 실패 (필수값 누락)
- Given: 기업명, 공모주명, 청약 시작일/종료일 중 하나 이상이 누락됨
- When: POST /ipos 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-003**: IPO 등록 실패 (청약 종료일이 시작일보다 빠름)
- Given: subscriptionEndDate가 subscriptionStartDate보다 이른 날짜로 제공됨
- When: POST /ipos 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-004**: IPO 등록 실패 (희망밴드 하한이 상한보다 큼)
- Given: lowerBandPrice가 upperBandPrice보다 큰 값으로 제공됨
- When: POST /ipos 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-005**: IPO 등록 실패 (중복)
- Given: 동일한 종목코드(또는 종목코드 없이 동일한 공모주명+청약 시작일)의 IPO가 존재함
- When: POST /ipos 요청이 전송됨
- Then: 409 Conflict 응답이 반환됨

**AC-006**: 동일 종목명으로 여러 회차 등록 성공
- Given: 기존과 동일한 공모주명이지만 종목코드 또는 회차 라벨이 다른 데이터가 제공됨
- When: POST /ipos 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 별도의 IPO 레코드로 생성됨

**AC-007**: IPO 목록 조회 성공
- Given: 하나 이상의 IPO가 등록됨
- When: GET /ipos 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 모든 IPO 목록이 포함됨

**AC-008**: IPO 상세 조회 성공
- Given: 존재하는 IPO ID가 제공됨
- When: GET /ipos/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 IPO의 상세 정보가 반환됨

**AC-009**: IPO 상세 조회 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 IPO ID가 제공됨
- When: GET /ipos/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-010**: IPO 수정 성공
- Given: 존재하는 IPO ID와 유효한 수정 데이터가 제공됨
- When: PATCH /ipos/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, IPO 정보가 업데이트됨

**AC-011**: IPO 수정 실패 (유효성 오류)
- Given: 존재하는 IPO ID와 유효하지 않은 데이터(날짜 순서 오류, 가격 범위 오류 등)가 제공됨
- When: PATCH /ipos/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-012**: IPO 수정 실패 (중복)
- Given: 다른 IPO와 중복되는 종목코드(또는 공모주명+청약 시작일)로 수정 시도함
- When: PATCH /ipos/{id} 요청이 전송됨
- Then: 409 Conflict 응답이 반환됨

**AC-013**: IPO 수정 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 IPO ID와 수정 데이터가 제공됨
- When: PATCH /ipos/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-014**: IPO 비활성화 성공 (연결된 청약 없음)
- Given: 존재하는 IPO ID와 연결된 Subscription이 없음
- When: DELETE /ipos/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, IPO의 status가 INACTIVE로 변경되며 deletedAt이 설정됨

**AC-015**: IPO 비활성화 성공 (연결된 청약 있음)
- Given: 존재하는 IPO ID와 연결된 Subscription이 하나 이상 있음
- When: DELETE /ipos/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, IPO의 status가 INACTIVE로 변경됨 (논리 삭제는 연결된 청약 여부와 무관하게 항상 허용됨 — 물리 삭제만 금지되며 이 엔드포인트는 물리 삭제를 수행하지 않음). 기존 연결된 Subscription은 그대로 유지됨

**AC-016**: IPO 비활성화 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 IPO ID가 제공됨
- When: DELETE /ipos/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-017**: 비활성화된 IPO는 신규 청약 등록 시 선택 불가
- Given: INACTIVE 상태의 IPO가 존재함
- When: 청약 등록 시 IPO 선택 목록이 제공됨
- Then: INACTIVE 상태의 IPO는 선택 목록에 포함되지 않음

**AC-018**: CSV 일괄 등록 성공
- Given: 유효한 형식의 CSV 파일이 제공됨
- When: POST /ipos/import 요청이 전송됨
- Then: 200 OK 응답이 반환되고, successCount에 생성된 건수가 포함되며 각 행이 IPO로 생성됨

**AC-019**: CSV 일괄 등록 부분 성공
- Given: 일부 행에 유효성 오류가 있는 CSV 파일이 제공됨
- When: POST /ipos/import 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 유효한 행은 생성되며 실패 행은 failures 배열에 행 번호와 사유와 함께 반환됨 (유효한 행까지 롤백하지 않음)

**AC-020**: CSV 일괄 등록 실패 (빈 파일/헤더 오류)
- Given: 빈 파일 또는 필수 헤더가 누락된 CSV 파일이 제공됨
- When: POST /ipos/import 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

## 엣지케이스
- 종목코드(stockCode)가 null인 경우 중복 검사에서 제외하고, 공모주명+청약 시작일 조합으로 중복 검사
- 회차 라벨(roundLabel)이 null인 경우 중복 검사에 영향 없음 (구분 보조용)
- 확정공모가(fixedPrice)가 null인 경우 (확정 전) 허용 — 확정 후 수정으로 입력 가능
- 상장일(listingDate)이 null인 경우 (미정) 허용
- 희망밴드 하한/상한 중 하나만 입력된 경우 허용 (둘 다 있을 때만 하한 ≤ 상한 검증)
- 공모주식수(offeringShareCount)가 null인 경우 허용, 입력 시 0보다 커야 함
- 가격이 음수인 경우 400 Bad Request
- 상태 필드는 ACTIVE 또는 INACTIVE만 허용
- 삭제된 IPO(deletedAt이 설정된 경우)는 조회 API에서 제외되어야 함
- 매우 긴 기업명/공모주명 (255자 초과)에 대한 처리
- 특수 문자가 포함된 기업명/공모주명 처리
- CSV에서 날짜 형식이 ISO-8601(YYYY-MM-DD)이 아닌 경우 해당 행 실패 처리
- CSV에서 숫자 필드에 숫자가 아닌 값이 있는 경우 해당 행 실패 처리
- CSV 파일 크기 상한 초과 시 400 Bad Request (상한은 아키텍처 단계에서 확정)

## 데이터 흐름
### IPO ← Subscription 참조 관계도
```
Subscription (Many) ---> IPO (One)
```
- 청약은 정확히 하나의 IPO를 참조함
- 하나의 IPO는 여러 Subscription(여러 투자자/계좌)으로부터 참조될 수 있음
- IPO의 삭제(비활성화)는 참조 무결성을 유지하기 위해 논리 삭제로만 처리 (물리 삭제 금지)
- Subscription의 IPO 필드는 외부 키 제약 조건으로 참조 무결성 보장
- IPO 비활성화 시, 기존 Subscription은 유지되지만 신규 청약 생성 시 해당 IPO는 선택 불가
