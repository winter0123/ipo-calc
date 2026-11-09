# 아키텍처 설계: 계좌 존재 여부 기반 등록/재사용 (account-presence)

## 개요
기존 `securities-account-management`에 계좌번호 선택적 입력, 존재 기반 재사용(가장 오래된 활성 계좌), 조건부 비활성화 추가. 레이어 구조 유지, 변경 최소화.

## 변경 대상 컴포넌트

### 1. Domain Layer
**SecuritiesAccount** (`domain.model.securitiesaccount.SecuritiesAccount`)
- `accountNumber`: `@Column(name="account_number", nullable=true, length=255)` 변경
- `setAccountNumber(encrypted, now)` 메서드 추가(사후 입력용)
- `rotateAccountNumber` 기존 유지

### 2. Repository Layer
**SecuritiesAccountRepository** (`repository.securitiesaccount.SecuritiesAccountRepository`)
```java
// 투자자+증권사+ACTIVE+deletedAt null 중 가장 오래된 계좌 1개 조회(재사용용)
Optional<SecuritiesAccount> findFirstByInvestorIdAndSecuritiesFirmIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
    Long investorId, Long securitiesFirmId, RecordStatus status);
```

### 3. Service Layer
**SecuritiesAccountService** (`service.securitiesaccount.SecuritiesAccountService`)

**register**: `accountNumber` optional. 미제공/공백 시 위 쿼리로 재사용 조회 → 있으면 **기존 계좌 그대로 반환(`updatedAt` 변경 없음)**, 없으면 `accountNumber=null`로 신규 생성. 번호 제공 시 기존 검증+암호화 후 신규 생성. **동시성**: `@Transactional` + `LockModeType.PESSIMISTIC_WRITE`로 투자자 행 잠금 후 조회/생성 원자 처리. 예외 캐치/재조회/rollback-only TX 금지.

**update**: `accountNumber` optional. 미전달=무변경, 공백=400, 기존 암호문 존재=409("이미 계좌번호가 등록되어 있습니다"), 유효값=정규화+검증+암호화 저장 후 `setAccountNumber` 호출.

**getAccountNumber**: `accountNumber==null`이면 400 "계좌번호가 등록되지 않았습니다. 계좌 수정에서 계좌번호를 입력해 주세요." 복호화 전 null 체크 필수, **감사 로그 기록 안 함**(평문 반환 성공 시에만 기록).

**deactivate**: 해당 계좌의 `subscriptionRepository.existsBySecuritiesAccountId(id)`만 체크. true=409 "연결된 청약 이력이 있어 비활성화할 수 없습니다", false=진행. 동일 투자자+증권사 타 계좌 존재 여부는 개별 DELETE 차단 안 함.

**toMaskedResponse**: `accountNumber==null`이면 `null` 반환, 아니면 복호화+마스킹.

**DTO 변경**:
- `SecuritiesAccountResponse`: `accountNumber: String`(null 허용), `reused` 필드 없음. `@JsonInclude(JsonInclude.Include.ALWAYS)`로 null 키 포함 강제
- `SecuritiesAccountUpdateRequest`: `accountNumber: String` 추가(optional)
- `SecuritiesAccountRegisterRequest`: 필드 동일, 서비스에서 null/공백 허용

### 4. Controller Layer
**SecuritiesAccountController** (`controller.securitiesaccount.SecuritiesAccountController`)
- `register`/`update`/`getAccountNumber`/`deactivate` 응답/에러 메시지 반영
- `set-default` 엔드포인트 없음(이미 제거됨)

### 5. Infrastructure Layer
- `AccountNumberMasker.mask(null)` → `null` 안전(기존 유지)
- `AccountNumberEncryptor`: 서비스 레벨에서 null 가드 후 호출

### 6. BrokerIntegration 연동
- `ApiCredentialService.register`: 진입 시 `account.getAccountNumber()==null` 체크 → 400 "계좌번호가 등록되지 않아 브로커 연동을 할 수 없습니다. 계좌 수정에서 계좌번호를 입력해 주세요."
- `BrokerSyncService.preview`: 동일 체크 → 400 "계좌번호가 등록되지 않아 조회할 수 없습니다. 계좌 수정에서 계좌번호를 입력해 주세요."
- `ApiCredentialAssembler.toResponse`: `accountNumber==null`이면 `maskedAccountNumber=null` 전달(Masker null-safe)
- `EncryptionRotationService`: 기존 `encrypted==null||isBlank()` null-skip 재사용, 변경 불필요

### 7. Frontend 연동
- `types.ts`: `accountNumber?: string`, `SecuritiesAccount.accountNumber: string|null`, `reused` 없음
- `AccountForm`: 등록 optional, placeholder "계좌번호(선택, 미입력 시 '미입력')", 수정 모드에서 번호 없으면 입력 필드/있으면 마스킹+변경 불가 안내
- `SecuritiesManage`: 기존 `AccountRow` 재사용, `accountNumber===null`이면 "미입력" 배지, 번호 없는 계좌에 "번호 입력" 버튼(수정 모드 진입)
- `AccountSelector`: `null`이면 "증권사명 - 미입력" 표시

## DB 마이그레이션 (V42)
```sql
ALTER TABLE securities_accounts ALTER COLUMN account_number DROP NOT NULL;
-- 기존 암호문/ID/FK 보존, 데이터 변환 없음
```

## 동시성 처리
투자자 행 `PESSIMISTIC_WRITE` 락으로 `register`/`update`/`deactivate` 한 트랜잭션 내 직렬화. `findFirstBy...OrderByCreatedAtAscIdAsc` 조회 후 분기. DB 유니크 제약/예외 캐치/`synchronized`/rollback-only TX 재조회 모두 금지.

## Jackson 직렬화
전역 `spring.jackson.default-property-inclusion=NON_NULL` 여부 확인. `NON_NULL`이면 `SecuritiesAccountResponse.accountNumber` 필드에 `@JsonInclude(JsonInclude.Include.ALWAYS)` 강제 적용. 프론트 `undefined` 우회 금지, 키 반드시 포함.

## 의존성 추가
- `SubscriptionRepository`: `boolean existsBySecuritiesAccountId(Long)` 필요(없으면 추가)

## 테스트 전략
- Service: register/update/getAccountNumber/deactivate 각 AC별 정상/실패/경계/권한/동시성
- Controller: HTTP 요청/응답, 에러 코드/메시지, Jackson null 키 포함 확인
- Repository: 신규 쿼리 메서드 동작
- Broker: 자격정보 등록/preview 400 차단, Assembler null-safe
- Frontend: AccountForm optional/검증, SecuritiesManage "미입력" 배지/버튼, AccountSelector "미입력" 표시