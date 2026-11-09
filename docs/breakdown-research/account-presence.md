# 계좌 존재 여부 기반 등록/재사용 (account-presence) 세부 분해 조사

## 목적
`docs/spec/account-presence.md` AC를 구현 최소 단위(SF)로 분해. 기존 코드 영향 범위, 의존성, 리스크 구체화.

## SF 후보

### SF-0: DB 마이그레이션 및 엔티티 nullable 변경 (선행 필수)
**범위**: V42 `account_number` nullable, `SecuritiesAccount.accountNumber` `@Column(nullable=true)`, 기존 암호문/ID/FK 보존
**영향 파일**: `V42__make_account_number_nullable.sql`, `SecuritiesAccount.java`
**검증**: 마이그레이션 후 기존 데이터 조회/복호화 정상, 신규 null 계좌 insert/select 정상
**리스크**: 낮(단순 스키마 변경)

### SF-1: 백엔드 전체 + 테스트 (SF-0 후 단독)
**범위**:
- `SecuritiesAccountService`: register/update/getAccountNumber/deactivate 구현(위 아키텍처 명세대로)
- `SecuritiesAccountController`: 응답/에러 반영
- `SecuritiesAccountRepository`: 신규 쿼리 1개 추가
- DTO: `SecuritiesAccountResponse`(accountNumber nullable, @JsonInclude(ALWAYS), reused 없음), `SecuritiesAccountUpdateRequest`(accountNumber 추가)
- 브로커 연동: `ApiCredentialService`/`BrokerSyncService`/`ApiCredentialAssembler` null-safe 가드
- 테스트: Service/Controller/Repository 각 AC별, 동시성(PESSIMISTIC_WRITE), Jackson null 키 포함
**영향 파일**: Service, Controller, Repository, DTOs, Exception, Broker 3개 서비스, *Test.java
**의존성**: SF-0 완료 필수, `SubscriptionRepository.existsBySecuritiesAccountId` 필요(없으면 추가)
**동시성**: 투자자 행 `PESSIMISTIC_WRITE` 락으로 직렬화 확정
**검증 명령(실제 가이드 명령만)**:
```bash
DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests "*SecuritiesAccount*" --rerun-tasks
DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests "*ApiCredentialService*" --rerun-tasks
DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests "*BrokerSyncService*" --rerun-tasks
DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests "*ApiCredentialAssembler*" --rerun-tasks
DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew checkstyleMain checkstyleTest --rerun-tasks
```
백엔드 슬롯 1, Master 슬롯 4 사용

### SF-2: 프론트엔드 전체 + 테스트 (SF-1 API 계약 확정 후 병렬)
**범위**:
- `AccountForm.tsx`: 등록 optional, placeholder "계좌번호(선택, 미입력 시 '미입력')", 검증 수정, 수정 모드 번호 없으면 입력/있으면 마스킹+변경 불가
- `SecuritiesManage.tsx`: 기존 `AccountRow` 재사용, `accountNumber===null` "미입력" 배지, 번호 없는 계좌 "번호 입력" 버튼(수정 모드 진입)
- `AccountSelector.tsx`: `null`이면 "증권사명 - 미입력"
- `types.ts`/`api-client.ts`: `accountNumber?: string`, `SecuritiesAccount.accountNumber: string|null`, `reused` 없음
- 테스트: AccountForm/SecuritiesManage/AccountSelector 각 AC별
**영향 파일**: AccountForm, SecuritiesManage, AccountSelector, types, api-client, api.ts, *.test.tsx
**의존성**: SF-1 API 계약(응답 스키마, 에러 메시지) 확정 후
**검증 명령**:
```bash
cd ui && pnpm run test --run securities-manage AccountForm
cd ui && pnpm run test --run investment-history AccountSelector
cd ui && pnpm run lint && pnpm run typecheck && pnpm run build
```

## SF 의존성 그래프
```
SF-0 (DB/Entity)
    ↓
SF-1 (Backend All + Tests) ←→ SF-2 (Frontend All + Tests)  [SF-1 계약 확정 후 병렬]
```
별도 SF-3/4 금지. 테스트는 각 SF에 포함.

## 파일 소유권 분리
| SF | 백엔드 | 프론트엔드 |
|----|--------|------------|
| SF-0 | V42.sql, SecuritiesAccount.java | - |
| SF-1 | Service, Controller, Repository, DTOs, Broker 3개, *Test.java | - |
| SF-2 | - | AccountForm, SecuritiesManage, AccountSelector, types, api-client, *.test.tsx |

## 확정된 사항(미정/선택 제거)
- `reused` 필드/토스트 없음, POST 재사용 시 201 유지
- 동시성: `PESSIMISTIC_WRITE` 락만 사용, 예외 캐치/재조회/rollback-only TX/`synchronized` 금지
- 비활성화: 청약 이력만 체크, 타 계좌 존재는 개별 DELETE 차단 안 함
- Jackson: `@JsonInclude(ALWAYS)`로 `accountNumber:null` 키 강제 포함
- 기본계좌(set-default) 기능 전체 문서에서 삭제(V41 제거)
- `AccountRow` 신규 생성 권고 삭제, 기존 재사용
- `flywayMigrate` 태스크 금지, 실제 가이드 명령만
- 검증 안 된 PASS 표기 금지, `git diff --check`만 PASS, 테스트 NOT_RUN