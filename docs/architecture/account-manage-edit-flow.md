# 계좌 관리 메뉴·폼 편집 흐름 architecture (account-manage-edit-flow)

## 데이터 흐름
```
SecuritiesManage (view: idle|menu|create|edit)
  ├─ view=menu  → AccountActionGroup (번호 계좌별 수정 + 계좌추가 1개)
  │                 └─ 계좌추가 → openAdd(): 미입력 활성 계좌(가장 오래된) 있으면
  │                              setView({kind:'edit', accountId:fillTarget}) 아니면 {kind:'create'}
  ├─ view=create → AccountForm(target=create) → POST /securities-accounts
  └─ view=edit   → AccountForm(target=edit)
                    ├─ 저장(번호 입력 있음) → ConfirmDialog → 확인 시에만 PATCH (번호 교체/최초 입력 공통)
                    ├─ 저장(번호 없음)      → 바로 PATCH (accountNumber 미포함)
                    └─ 삭제                  → ConfirmDialog → DELETE → 성공 시 onDone(close)
```
`panel` + `expandedKey`를 제거하고 `view` 하나로 상호배타를 강제한다.

## API·DB 영향
- `PATCH /securities-accounts/{id}`: `accountNumber` 처리 규칙만 변경 — 기존 "암호문 존재 시 409" 제거, 유효값 검증+암호화 교체 저장(계좌 id 유지). 미전달=무변경, 공백=400(지우기 미지원), 10~16자리 하이픈 허용 정규화.
- DB 스키마·Flyway 변경 없음. `SecuritiesAccount.setAccountNumber` 유지(의미만 "설정/교체"로). 투자자 행 `PESSIMISTIC_WRITE` 락 + `entityManager.refresh` + 소유권 검증 흐름 불변.
- 409(청약 이력, 취소 포함) 삭제 보호 불변. `SubscriptionExistenceCheck` 불변. 마스킹 응답·감사 로그·평문 미노출 원칙 불변.

## 모듈 경계
- UI: `features/securities-manage`(SecuritiesManage·AccountForm·AccountRow·styles) + `entities/securities-account`(기존 훅/api-client 재사용, 변경 없음) + `shared/components/ConfirmDialog` 재사용.
- Backend: `securitiesaccountmanagement`의 Service(update 분기)·DTO/Entity/Controller 주석만. 다른 도메인·브로커 모듈 호출부 변경 없음.

## 리스크
- 번호 동시 교체(null→번호 2건): 행 락으로 직렬화되나 409 대신 마지막 쓰기 승. 테스트(concurrentPatchNullToNumberBothSucceedUnderRowLock)로 고정.
- 기존 번호를 모른 채 교체하면 증권사 실제 계좌와 어긋날 수 있음 → 저장 전 ConfirmDialog 경고로 사용자 확인(이력은 같은 계좌에 유지됨).
- 브로커는 호출마다 DB에서 번호를 복호화해 읽으므로 stale cache 위험 없음(해외 DTO·토큰에 번호 미포함).
