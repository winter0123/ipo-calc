# account-presence Planning 작업 로그

## 작업 개요
- **일자**: 2026-11-09
- **작업자**: Planning 워커 (OpenCode/OpenRouter 무료 모델)
- **공간**: `/Users/joy/orca/workspaces/ipo-calc/planning-account-presence`
- **목적**: 사용자 승인 번호 없는 계좌 체크 등록 기능 실제 코드 조사 및 구현 계약 작성(제품 코드 수정 없음)

## 조사 내역
1. **기존 코드 분석**: `SecuritiesAccount` 엔티티(`account_number` not null V41), 등록/수정/조회/비활성화 API, `SecuritiesAccountOwnershipVerifier`→`OwnershipGuard` 2단계 소유권, 브로커 연동(`ApiCredentialService`/`BrokerSyncService`/`EncryptionRotationService`/`ApiCredentialAssembler`), 프론트 `AccountForm`/`SecuritiesManage`/`AccountSelector`/`InvestorGate`
2. **요구사항 매핑**: 체크박스 ON→재사용(가장 오래된 활성 계좌)/생성, OFF→개별 비활성화(청약 이력만 체크), 번호 optional, 사후 1회 입력, 동시성 `PESSIMISTIC_WRITE`, Jackson null 키 강제 포함, 기본계좌 기능 삭제(V41), `reused`/토스트 없음, `AccountRow` 재사용
3. **문서 6종 재작성**: research/spec/architecture/breakdown-research/breakdown/work-log 각 30~80줄, 구현 코드/JSON 예시/미정 사항 제거, 확정된 결정만 반영

## 확정된 제약사항
1. V42 마이그레이션(`account_number` nullable), 기존 암호문/ID/FK 보존
2. `SubscriptionRepository.existsBySecuritiesAccountId` 필요(없으면 추가)
3. Jackson 전역 `NON_NULL`이면 `SecuritiesAccountResponse.accountNumber`에 `@JsonInclude(ALWAYS)` 강제
4. 동시성: 투자자 행 `PESSIMISTIC_WRITE` 락만 사용, 예외 캐치/재조회/rollback-only TX/`synchronized` 금지
5. `set-default` 엔드포인트 V41 제거 → 전체 문서에서 기본계좌 기능 삭제
6. `reused` 필드/토스트 없음, POST 재사용 시 201 유지
7. 비활성화: 청약 이력만 체크, 동일 투자자+증권사 타 계좌 존재는 개별 DELETE 차단 안 함(UI 체크박스에서만)
8. `AccountRow` 신규 생성 금지, 기존 재사용
9. `flywayMigrate` 태스크 금지, 실제 가이드 명령만 사용
10. 검증 안 된 PASS 표기 금지, `git diff --check`만 PASS, 테스트 NOT_RUN
11. OCR 영향 없음, 브로커 등록/preview 외부 호출 전 400, 조회 null-safe, rotation 기존 null-skip 재사용

## 검증 계획
- 백엔드: 슬롯 1, Master 슬롯 4, 실제 가이드 명령으로 test/checkstyle
- 프론트엔드: pnpm test/lint/typecheck/build
- 공통: `git diff --check` PASS, `git diff --stat` SF 범위 내만

## 다음 단계 (워커 배정 시)
1. **SF-0**: V42 마이그레이션 + Entity nullable 변경 (단독 선행)
2. **SF-1**: 백엔드 전체(Service/Controller/Repository/DTO/Broker/테스트) — SF-0 후
3. **SF-2**: 프론트엔드 전체(AccountForm/SecuritiesManage/AccountSelector/types/테스트) — SF-1 API 계약 확정 후 병렬 가능

## 작업 완료 확인
- [x] 6개 문서 재작성 완료(각 30~80줄, 구현 코드/JSON/미정 제거)
- [x] 제품 코드 수정 없음
- [x] 커밋하지 않음(워킹트리 상태)
- [x] 무료 모델(Nemotron 3 Ultra) OpenCode 경로
- [x] `git diff --check` 통과 확인
- [x] 날짜: 2026-11-09