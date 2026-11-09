# 계좌 존재 여부 기반 등록/재사용 (account-presence) 조사 문서

## 조사 목적
투자자별 증권사 체크박스 ON 시 활성 계좌가 있으면 재사용(가장 오래된 것), 없으면 `accountNumber=null` 계좌 생성. 체크박스 OFF 시 해당 계좌만 비활성화(청약 이력 없으면 허용, 있으면 409). 가짜 번호 저장 금지, UI는 "미입력" 표시. 번호 사후 1회 입력(`null`→실제), 기존 번호 교체/삭제 불가. 동일 증권사 실계좌 추가는 기존대로 허용. 소유권 검증 필수.

## 기존 구현 현황
- **엔티티**: `SecuritiesAccount` — `account_number` `nullable=false` (V41)
- **등록**: `POST /securities-accounts` — `accountNumber` 필수, 10~16자리, AES-256-GCM 암호화
- **수정**: `PATCH /securities-accounts/{id}` — `accountNumber` 무시, `accountName`/`securitiesFirmId`만 변경
- **조회**: 목록/상세 마스킹, `GET /{id}/account-number` 평문+감사로그
- **비활성화**: `DELETE /{id}` — 소프트 삭제, `SecuritiesAccountOwnershipVerifier`→`OwnershipGuard` 2단계 검증
- **브로커**: `ApiCredentialService.register`/`BrokerSyncService.preview` 계좌번호 평문 필요, `EncryptionRotationService` 이미 null-skip
- **프론트**: `AccountForm` 등록 필수/수정 숨김, `SecuritiesManage` 목록, `AccountSelector` 드롭다운, `InvestorGate` `/securities`는 `selfGuided=true`로 계좌 없어도 진입

## 핵심 결론
1. **DB**: V42 `account_number` nullable 변경, 기존 암호문/ID/FK 보존
2. **등록**: `accountNumber` optional. 미제공/공백 시 해당 투자자+증권사 `status=ACTIVE`+`deletedAt null` 중 `createdAt`/`id` 가장 오래된 계좌 1개 재사용(신규 생성 안 함). 번호 있으면 기존 검증 후 신규 생성. 응답 201 유지, `reused` 필드 없음
3. **수정**: `PATCH`에 `accountNumber` optional 추가. 미전달=무변경, 공백=400, 기존 암호문 존재=409("이미 계좌번호가 등록되어 있습니다"), 유효값=암호화 저장
4. **계좌번호 조회**: `accountNumber=null`이면 400("계좌번호가 등록되지 않았습니다. 계좌 수정에서 계좌번호를 입력해 주세요.")
5. **비활성화**: `DELETE` 시 해당 계좌의 `Subscription` 존재 여부만 체크. 존재=409, 미존재=허용. 동일 투자자+증권사 타 계좌 존재 여부는 개별 DELETE 차단 사유 아님(UI 체크박스 OFF에서만 차단)
6. **동시성**: 투자자 행 `PESSIMISTIC_WRITE` 락으로 한 트랜잭션 내 직렬화. 예외 캐치/재조회/rollback-only TX/`synchronized` 금지
7. **Jackson**: `accountNumber:null` 키 응답에 포함 필수. 전역 `NON_NULL`이면 DTO 필드 `@JsonInclude(ALWAYS)` 강제
8. **브로커**: 자격정보 등록/preview 진입 전 `accountNumber=null` 체크 후 400. 조회는 null-safe, rotation 기존 null-skip 재사용
9. **OCR**: 종목명/코드 기반이라 계좌번호 없어도 영향 없음

## 제약사항
- `set-default` 엔드포인트 V41에서 제거됨 → 기본계좌 기능 전체 문서에서 삭제
- `SubscriptionRepository.existsBySecuritiesAccountId` 필요(없으면 추가)
- 응답에 `accountNumber:null` 키 보장, 프론트 `undefined` 우회 금지
- 브로커 등록/preview는 외부 호출 전 400 차단, 조회 null-safe, rotation 기존 null-skip 재사용
- POST 공백은 미입력 처리, PATCH 공백은 400 에러