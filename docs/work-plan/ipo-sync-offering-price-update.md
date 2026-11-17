# 작업 계획: Dart 동기화 시 공모가 변경 반영 및 공모가 표기 개선

## 목표
- OpenDART 동기화 시 기존 IPO 레코드의 공모가(밴드 가격, 확정가)가 변경되었을 때 DB에 반영되지 않는 문제 수정
- 공모가 표기 시 확정가 없으면 밴드 범위(하단~상단)로 표시, 둘 다 없으면 "미정"

## 범위
### 포함
1. 백엔드 `IpoSyncPersistence.java`: 기존 자동 동기화 레코드가 있고 `isSameOffering()`이 false(다른 접수번호/시작일/정정 아님)인 경우에도 밴드 가격(`lowerBandPrice`, `upperBandPrice`)과 공모 주식 수(`offeringShareCount`)를 갱신하도록 수정
2. 백엔드 `IpoSyncPersistence.java`: `source_receipt_no`는 철회 매칭 보호를 위해 절대 덮어쓰지 않음
3. 백엔드 `IpoSyncPersistence.java`: `fixedPrice`는 `finalizedReport`(발행조건확정 공시)일 때만 갱신 (확정가 보호)
4. 프론트엔드 `SubscriptionScheduleList.tsx`: `priceLabel()` 수정 — 확정가 > 밴드 범위(하단~상단) > "미정" 우선순위

### 미포함
- 수동 등록 레코드(`syncSource == null`) 수정 (기존 보호 로직 유지)
- `subscriptionStartDate` 등 날짜 필드 갱신 로직 변경 (기존 `canAutoOverwrite` 유지)
- 기타 IPO 필드(청약 기간, 배정일 등) 갱신 범위 변경

## 변경 파일
- `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoSyncPersistence.java`
- `ui/src/features/subscription-list/SubscriptionScheduleList.tsx`
- `ui/src/features/subscription-list/SubscriptionScheduleList.test.tsx` (테스트 추가)
- `backend/src/test/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoSyncServiceTest.java` (테스트 추가)

## 테스트 계획
### 백엔드 (IpoSyncServiceTest)
1. 기존 자동 동기화 레코드가 있고 새 공시(다른 rceptNo, 일반 공시)로 밴드 가격이 변경된 경우 → `lowerBandPrice`/`upperBandPrice` 갱신, `source_receipt_no` 미변경, `fixedPrice` 미변경
2. `finalizedReport=false`인 경우 `fixedPrice`가 갱신되지 않음
3. `finalizedReport=true`인 경우 `fixedPrice`가 갱신됨

### 프론트엔드 (SubscriptionScheduleList.test.tsx)
1. `fixedPrice`가 있고 `lowerBandPrice`/`upperBandPrice`도 있으면 → 확정가만 표시
2. `fixedPrice`가 null이고 밴드 가격이 있으면 → "하단가~상단가 원" 표시
3. `fixedPrice`도 없고 밴드 가격도 없으면 → "미정"

## 완료 조건
- 관련 단위 테스트 전원 통과
- `git diff --check` 통과
- 백엔드 빌드·타입 검사 통과
- 프론트엔드 타입 검사·lint·build 통과
- Master 승인 후 커밋

## 위험 요소
- `HELD_UNDETERMINED` 케이스에서 밴드 가격을 갱신하면 철회 매칭(`existsByCorpCodeAndWithdrawnReceiptNo`)에 영향을 줄 수 있음 → `source_receipt_no`는 절대 변경하지 않음으로써 방지
- `fixedPrice`를 섣불리 변경하면 수동 입력값이 덮어써질 수 있음 → `finalizedReport`일 때만 갱신
