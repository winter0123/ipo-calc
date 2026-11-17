# 작업 로그: Dart 동기화 시 공모가 변경 반영 및 공모가 표기 개선

## 2026-11-17
- 이슈: 공모주일정에서 dart가져오기 시 공모가가 변경되어도 기존 DB 데이터가 바뀌지 않음
- 이슈: 공모가 표기 시 확정가 없으면 밴드 가격(하단~상단)으로 표시해야 함

### 변경 파일
1. `backend/src/main/java/com/ipocalc/ipocatalog/domain/model/ipo/Ipo.java`
   - `syncBandAndShares()` 도메인 메서드 추가: 밴드 가격과 공모 주식 수만 갱신 (source_receipt_no, fixedPrice 미변경)
2. `backend/src/main/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoSyncPersistence.java`
   - `persist()`에서 `isSameOffering()`이 false(다른 접수번호/정정 아님)인 경우 HELD_UNDETERMINED 반환 대신 밴드 가격·주식 수만 갱신하고 UPDATED 반환
   - `source_receipt_no`는 철회 매칭 보호를 위해 절대 덮어쓰지 않음
3. `backend/src/test/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoSyncServiceTest.java`
   - 테스트 3건 추가: 밴드 가격 갱신, fixedPrice 미갱신(일반 공시), fixedPrice 갱신(발행조건확정 공시)
4. `backend/src/test/java/com/ipocalc/ipocatalog/service/ipo/sync/IpoScheduleReconciliationTest.java`
   - 기존 `persistHoldsPriceSharesCompanyNameAndReceiptWhenOfferingCannotBeDistinguished` 테스트를 새 동작에 맞게 수정
5. `ui/src/features/subscription-list/SubscriptionScheduleList.tsx`
   - `priceLabel()`: 확정가 > 밴드 범위(하단~상단 원) > 미정 순으로 표시
6. `ui/src/features/subscription-list/SubscriptionScheduleList.test.tsx`
   - 밴드 범위 표시 테스트 1건 추가

### 검증 결과
- 백엔드 `com.ipocalc.ipocatalog.service.ipo.sync.*` 테스트 전체 통과 (245개)
- 프론트엔드 `SubscriptionScheduleList.test.tsx` 전체 통과 (24개)
- 백엔드 spotless 포맷 적용 완료
- UI 타입 검사(`tsc -b`) 통과
- UI eslint 통과
- `git diff --check` 통과

### 안전 장치
- `source_receipt_no`는 절대 변경하지 않음 (철회 매칭 보호)
- `fixedPrice`는 `finalizedReport`(발행조건확정 공시)일 때만 갱신 (확정가 보호)
- 수동 등록 레코드(`syncSource == null`)는 기존 보호 로직 유지 (SKIPPED_MANUAL)
