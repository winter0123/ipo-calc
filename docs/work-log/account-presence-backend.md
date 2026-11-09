# account-presence 백엔드 구현 작업 로그

## 작업 일자
2026-11-09

## 작업 목적
docs/spec/account-presence.md 및 architecture/breakdown의 SF-1 구현: 계좌 존재 여부 기반 등록/재사용 기능

## 구현 내용

### 1. DB 마이그레이션 (V42)
- `backend/src/main/resources/db/migration/V42__optional_securities_account_number.sql` 생성
- `account_number` 컬럼을 nullable로 변경, 기존 암호문/ID/FK 보존

### 2. 도메인 엔티티 변경
- `SecuritiesAccount.java`: `accountNumber` 필드 `@Column(nullable=true)`로 변경
- `setAccountNumber(encrypted, now)` 메서드 추가 (사후 입력용)

### 3. Repository 계층
- `SecuritiesAccountRepository.java`: `findFirstByInvestorIdAndSecuritiesFirmIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAscIdAsc` 메서드 추가 (가장 오래된 활성 계좌 조회용)
- `InvestorRepository.java`: `findByIdForUpdate(Long id)` 메서드 추가 (`@Lock(LockModeType.PESSIMISTIC_WRITE)`로 동시성 제어용)
- `SubscriptionRepository.java`: `existsBySecuritiesAccountId(Long securitiesAccountId)` 메서드 추가

### 4. Port/Adapter 패턴 (순환 의존성 방지)
- `SubscriptionExistenceCheck.java` (securitiesaccountmanagement 패키지): 인터페이스 정의
- `SubscriptionExistenceCheckAdapter.java` (subscriptionmanagement 패키지): 구현체

### 5. Service 계층 (SecuritiesAccountService)
- `register`: accountNumber optional 처리
  - 미제공/공백 시: 기존 활성 계좌 재사용(가장 오래된 것) 또는 accountNumber=null로 신규 생성
  - 제공 시: 기존 검증·암호화 후 신규 생성
  - 투자자 행 PESSIMISTIC_WRITE 락으로 동시성 제어
- `update`: accountNumber optional 필드 추가
  - 미전달=무변경, 공백=400, 기존 암호문 존재=409, 유효값=검증·암호화 저장
- `getAccountNumber`: accountNumber==null 시 400 에러, 감사 로그 기록 안 함
- `deactivate`: 청약 이력 존재 여부만 체크 (`subscriptionExistenceCheck.existsBySecuritiesAccountId`)

### 6. DTO 변경
- `SecuritiesAccountUpdateRequest`: `accountNumber` 필드 추가
- `SecuritiesAccountResponse`: `accountNumber`에 `@JsonInclude(JsonInclude.Include.ALWAYS)` 추가 (null 키 포함 강제)

### 7. Broker Integration 수정
- `ApiCredentialService.register/replace`: 계좌번호 없으면 400으로 외부 호출 전 차단
- `BrokerSyncService.preview`: 계좌번호 없으면 400으로 외부 호출 전 차단
- `ApiCredentialAssembler.toResponse`: accountNumber null-safe 처리

### 8. 테스트 수정
- `SecuritiesAccountControllerTest`: 새로운 AC에 맞게 기존 테스트 수정
  - `registerSecuritiesAccountWithMissingRequiredFieldsReturns400ValidationError`: accountNumber 미제공 시 201 생성으로 변경
  - `updateSecuritiesAccountIgnoresAccountNumberChangeInRequest`: 기존 번호 있을 때 변경 시도 시 409로 변경
- `InputLimitsTest`: 유효한 투자자/증권사 생성 후 테스트하도록 수정
- `SubscriptionExistenceCheck` 관련 테스트는 기존 테스트로 커버됨

## 검증 결과
- `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --rerun`: **PASS** (1121 tests)
- `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`: **PASS**
- `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew build`: **PASS**
- `git diff --check`: **PASS** (whitespace issues 없음)

## 변경 파일 목록
### 신규 생성
- `backend/src/main/resources/db/migration/V42__optional_securities_account_number.sql`
- `backend/src/main/java/com/ipocalc/securitiesaccountmanagement/service/securitiesaccount/SubscriptionExistenceCheck.java`
- `backend/src/main/java/com/ipocalc/subscriptionmanagement/repository/subscription/SubscriptionExistenceCheckAdapter.java`

### 수정
- `backend/src/main/java/com/ipocalc/securitiesaccountmanagement/domain/model/securitiesaccount/SecuritiesAccount.java`
- `backend/src/main/java/com/ipocalc/securitiesaccountmanagement/repository/securitiesaccount/SecuritiesAccountRepository.java`
- `backend/src/main/java/com/ipocalc/securitiesaccountmanagement/service/securitiesaccount/SecuritiesAccountService.java`
- `backend/src/main/java/com/ipocalc/securitiesaccountmanagement/controller/securitiesaccount/dto/SecuritiesAccountResponse.java`
- `backend/src/main/java/com/ipocalc/securitiesaccountmanagement/controller/securitiesaccount/dto/SecuritiesAccountUpdateRequest.java`
- `backend/src/main/java/com/ipocalc/investormanagement/repository/investor/InvestorRepository.java`
- `backend/src/main/java/com/ipocalc/subscriptionmanagement/repository/subscription/SubscriptionRepository.java`
- `backend/src/main/java/com/ipocalc/brokerintegration/service/credential/ApiCredentialService.java`
- `backend/src/main/java/com/ipocalc/brokerintegration/service/credential/ApiCredentialAssembler.java`
- `backend/src/main/java/com/ipocalc/brokerintegration/service/sync/BrokerSyncService.java`
- `backend/src/test/java/com/ipocalc/securitiesaccountmanagement/controller/securitiesaccount/SecuritiesAccountControllerTest.java`
- `backend/src/test/java/com/ipocalc/accountauthentication/controller/InputLimitsTest.java`

## 비고
- `@SuppressWarnings("checkstyle:LineLength")` 사용: `findOldestActiveAccount` 메서드에서 Spring Data JPA 메서드명이 100자 초과하여 불가피하게 사용
- `spotlessApply` 실행으로 포맷 자동 정리 (spotlessCheck 통과)
- 순환 의존성 방지를 위해 Port/Adapter 패턴 적용 (AccountCredentialDeletion 패턴 재사용)
## 후속 수정 (차단 결함)
- register null/blank 분기 전 validateAccountName 유지, 256자 계좌명 400.
- update/deactivate 투자자 락 후 refresh + deletedAt 재검증.
- null 등록 재사용 toMaskedResponse(reused)로 증권사명 유지.
- AC 회귀 테스트 추가(병렬 POST/PATCH, 소유권 403, 청약 이력 409 등).
- Broker null 계좌번호 400/외부 호출 0, assembler null-safe, rotation null skip 회귀 추가.
- 검증: slot1 test 1138 PASS, checkstyleMain/Test 0, build PASS.

## 테스트·Javadoc 보완
- concurrentRegisterCreatesOnlyOneAccount에 successCount==10/conflictCount==0 assertion 추가.
- 두 concurrent 테스트의 latch await를 30초 유한 대기로 변경.
- SecuritiesAccount.setAccountNumber Javadoc을 한국어로 정리.
- 대상 테스트 34건 PASS, checkstyleMain/Test 0, git diff --check origin/main PASS.
