# 계좌 존재 여부 기반 등록/재사용 (account-presence) 세부 기능 분해

## Epic 개요
- **슬러그**: `account-presence`
- **목적**: 체크박스 ON/OFF로 계좌 재사용/생성/비활성화, 번호 사후 1회 입력, 최소 변경 구현
- **승인**: 2026-11-09 사용자 요구사항 확정

## SF 분해표
| SF | 제목 | 범위 | 선행 | 병렬성 |
|----|------|------|------|--------|
| SF-0 | 문서 계약 확정 | Phase 0 문서 최종 검토/승인, 구현 범위·AC·동시성·에러메시지 확정 | - | 단독 선행 |
| SF-1 | 백엔드 전체 + 테스트 | DB 마이그레이션(V42)/Entity nullable, Service/Controller/Repository/DTO/Broker/테스트 | SF-0 | 백엔드 단독 |
| SF-2 | 프론트엔드 전체 + 테스트 | AccountForm/SecuritiesManage/AccountSelector/types/테스트 + 핵심 체크박스 AC | SF-1 계약 후 | SF-1과 병렬 가능 |

별도 SF-3/4 금지. 테스트는 각 SF에 포함.

## SF 상세

### SF-0: 문서 계약 확정
**범위**: 연구/명세/아키텍처/분해 문서 최종 검토, 구현 계약(범위/AC/동시성/에러 메시지/검증 명령) 확정, Master 승인
**비범위**: 제품 코드 수정, DB 마이그레이션, Entity 변경
**AC**: 
- AC-SF0-01: Phase 0 문서 6종(research/spec/architecture/breakdown-research/breakdown/work-log) 내용 모순 없음
- AC-SF0-02: 구현 범위·AC·동시성·에러 메시지·검증 명령이 spec/architecture/breakdown과 일치
- AC-SF0-03: Master 승인 후 SF-1/SF-2 배정 가능
**산출물**: 승인된 Phase 0 문서 6종
**특이사항**: DB 마이그레이션·Entity 변경은 SF-1 범위로 통합

---

### SF-1: 백엔드 전체 구현 및 테스트
**범위**:
- DB 마이그레이션(V42) `account_number` nullable, `SecuritiesAccount.accountNumber` `@Column(nullable=true)`
- `SecuritiesAccountService.register`: `accountNumber` optional. 미제공/공백 시 `findFirstBy...OrderByCreatedAtAscIdAsc`로 가장 오래된 활성 계좌 1개 재사용(**기존 계좌 그대로 반환, `updatedAt` 변경 없음**), 없으면 `accountNumber=null` 신규 생성. 제공 시 기존 검증+암호화 후 신규 생성. **투자자 행 `PESSIMISTIC_WRITE` 락**으로 동시성 직렬화.
- `update`: `accountNumber` optional. 미전달=무변경, 공백=400, 기존 암호문=409("이미 계좌번호가 등록되어 있습니다"), 유효값=정규화+검증+암호화 저장(`setAccountNumber`).
- `getAccountNumber`: `accountNumber==null`이면 400 "계좌번호가 등록되지 않았습니다. 계좌 수정에서 계좌번호를 입력해 주세요." 복호화 전 null 체크, **감사 로그 기록 안 함**(평문 반환 성공 시에만 기록).
- `deactivate`: `subscriptionRepository.existsBySecuritiesAccountId(id)`만 체크. true=409 "연결된 청약 이력이 있어 비활성화할 수 없습니다", false=진행. 타 계좌 존재 여부 차단 안 함.
- `SecuritiesAccountRepository`: 신규 쿼리 1개 추가(`findFirstBy...OrderByCreatedAtAscIdAsc`)
- DTO: `SecuritiesAccountResponse` `accountNumber` nullable + `@JsonInclude(ALWAYS)`, `reused` 없음. `SecuritiesAccountUpdateRequest` `accountNumber` 추가.
- 브로커: `ApiCredentialService.register`/`BrokerSyncService.preview` 진입 전 `accountNumber==null` 체크 400. `ApiCredentialAssembler` null-safe. `EncryptionRotationService` 기존 null-skip 재사용.
- 테스트: Service/Controller/Repository 각 AC별, 동시성(PESSIMISTIC_WRITE), Jackson null 키 포함
**비범위**: 프론트엔드, 기본계좌 기능(제거됨)
**AC**: spec.md AC-001~013 매핑
**산출물**: V42 마이그레이션, Service, Controller, Repository, DTOs, Broker 3개 서비스, *Test.java
**특이사항**: `SubscriptionRepository.existsBySecuritiesAccountId` 필요, 동시성 `PESSIMISTIC_WRITE`만, Jackson `@JsonInclude(ALWAYS)` 강제, 백엔드 슬롯 1/Master 슬롯 4, 실제 가이드 명령만

---

### SF-2: 프론트엔드 전체 구현 및 테스트 (+ 핵심 체크박스 AC)
**범위**:
- `AccountForm.tsx`: 등록 모드 계좌번호 optional, placeholder "계좌번호(선택, 미입력 시 '미입력')", 입력 시 10~16자리 검증/미입력 시 통과. 수정 모드: 번호 없으면 입력 필드 활성화, 있으면 마스킹 표시+"이미 등록된 계좌번호입니다" 안내.
- `SecuritiesManage.tsx`: 기존 `AccountRow` 재사용. `accountNumber===null` "미입력" 배지. 번호 없는 계좌 "번호 입력" 버튼(수정 모드 진입).
- `AccountSelector.tsx`: `accountNumber===null`이면 "증권사명 - 미입력" 표시.
- `types.ts`/`api-client.ts`: `SecuritiesAccountRegisterRequest.accountNumber?: string`, `SecuritiesAccountUpdateRequest.accountNumber?: string`, `SecuritiesAccount.accountNumber: string|null`, `reused` 없음.
- 테스트: AccountForm/SecuritiesManage/AccountSelector 각 AC별
**비범위**: OCR/캡처/InvestorGate/기타 feature 수정
**핵심 체크박스 AC (필수)**:
- AC-CB-01: native checkbox + 증권사 라벨 렌더링
- AC-CB-02: 체크 시 계좌번호 생략 POST(미제공/공백) 전송
- AC-CB-03: 기존 활성 계좌 있으면 체크된 상태로 표시(재사용)
- AC-CB-04: 등록 중 해당 증권사 행 잠금(로딩/비활성화), 실패 시 원상 복구 + role=alert 안내
- AC-CB-05: 단일 계좌 해제 시 확인 DELETE, 취소 시 체크 유지 409 보존
- AC-CB-06: 복수 계좌 해제 시도 시 안내 후 개별 관리 유도, 체크 유지
- AC-CB-07: 실계좌 추가 버튼 보존(기존대로 여러 실계좌 등록 가능)
- AC-CB-08: 브로커 연동 진입 시 계좌번호 미입력이면 "계좌번호를 먼저 입력해 주세요" 안내 후 차단
**AC**: 
- AC-SF2-01: 등록 폼 계좌번호 미입력 시 전송 성공
- AC-SF2-02: 등록 폼 계좌번호 입력 시 10~16자리 검증/오류 인라인 표시
- AC-SF2-03: 목록 "미입력" 배지 표시
- AC-SF2-04: 번호 없는 계좌 "번호 입력" 버튼→수정 모드 진입
- AC-SF2-05: 번호 있는 계좌 수정 시 마스킹+변경 불가 안내
- AC-SF2-06: 드롭다운 "증권사명 - 미입력" 표시
- AC-CB-01~08: 위 핵심 체크박스 AC 모두 충족
**산출물**: AccountForm, SecuritiesManage, AccountSelector, types, api-client, api.ts, *.test.tsx
**특이사항**: `AccountRow` 신규 분리 금지, 기존 재사용. `reused` 토스트 없음.

---

## 구현 순서
1. **SF-0** (단독 선행: 문서 계약 확정)
2. **SF-1** + **SF-2 병렬** (SF-1 API 계약 확정 후 SF-2 시작, 파일 충돌 없음: 백엔드/프론트엔드 분리)

---

## 검증 명령 요약
**백엔드 (SF-1)**: `DB_NAME=ipo_slot_1 DB_PASSWORD=ipo_local_password ./gradlew test --tests "*SecuritiesAccount*" --rerun-tasks` 등 5개 태스크 + `checkstyleMain checkstyleTest`
**프론트엔드 (SF-2)**: `cd ui && pnpm run test --run securities-manage AccountForm && pnpm run test --run investment-history AccountSelector && pnpm run lint && pnpm run typecheck && pnpm run build`
**공통**: `git diff --check` PASS, `git diff --stat` SF 범위 내만. 테스트 NOT_RUN, `flywayMigrate` 태스크 금지.

---

## 위험 요소
| 위험 | 대응 |
|------|------|
| 동시 등록 경합 | `PESSIMISTIC_WRITE` 락 직렬화, 테스트 검증 |
| 복호화 NPE | 복호화 전 `account.getAccountNumber()!=null` 가드 |
| Jackson null 키 누락 | `@JsonInclude(ALWAYS)` 강제, 컨트롤러 테스트 확인 |
| SubscriptionRepository 부재 | SF-1 전 메서드 추가 확인 |
| 브로커 NPE | Assembler/SyncService null 가드, 단위 테스트 |

---

## 변경 이력
| 날짜 | 버전 | 내용 |
|------|------|------|
| 2026-11-09 | 1.0 | 초기 작성(수정 지침 반영) |
| 2026-11-09 | 1.1 | SF-0 문서계약확정으로 변경, DB마이그레이션 SF-1 통합, SF-2 핵심 체크박스 AC 추가, 구현순서 갱신 |