# 통합 투자 화면 회귀 조사 (unified-investment-regression)

> 목적: `/ipos/:ipoId/manage`(바로팜 0153R0, 미래에셋 계좌)에서 상단 투자내역·청약·배정·매도 3영역 동시 실패의 공통 원인 경로와 백엔드 DTO↔Zod 계약 차이 전수 조사. 수정 금지.

## 1. 전체 호출 경로
```
TradeManagePage → useInvestmentHistory(size:1) → GET /investment-history
  → useInvestmentHistoryDetail → GET /investment-history/{id}
  → useQuery(source: subscription/allocation/sale[]) → 개별 GET
백엔드: Controller → QueryService → Repository(deletedAt IS NULL) → ReadModelLoader(배치) → Assembler(DTO 조립)
Zod: apiGetParsed → investmentHistoryResponseSchema → investmentHistoryItemSchema
```

## 2. 핵심 결함: verifyIpoIdOwnership 잘못된 권한 검증 (확정)
**파일**: `backend/.../InvestmentHistoryOwnershipVerifier.java:46-57`
```java
public void verifyIpoIdOwnership(Long ipoId) {
  Long currentAccountId = securityUtils.getEffectiveAccountId();
  boolean exists = subscriptionRepository.existsByIpoIdAndInvestorAccountId(ipoId, currentAccountId);
  if (!exists) throw new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다.");
}
```
**문제**: IPO는 공용 카탈로그 필터인데 "현재 계정 소유 청약 중 해당 IPO 건 존재 여부"로 403 판단. TradeManagePage는 신규등록 전에도 `ipoId` 필터로 조회 → **본인 청약 0건이면 항상 403 → 세 섹션 전체 잠금**.
**근거**: `TradeManagePage.tsx:154-166` `historyFilters`에 `ipoId` 포함, `contextReady` 시 쿼리 실행. `InvestmentHistoryQueryService.java:66-67` 모든 필터에 대해 `verifyIpoIdOwnership` 호출.

## 3. 백엔드 DTO ↔ 프론트 Zod 전수 비교 (핵심만)
| 필드 | 백엔드 | 프론트 Zod | 일치 | 비고 |
|------|--------|------------|------|------|
| securitiesAccountId | Long | z.number().nullish() | ✅ | `nullish()`는 `nullable()` 상위집합(undefined 포함) → 계약 불일치 아님 |
| securitiesAccountName | String | z.string().nullish() | ✅ | 동일 |
| allocation.allocatedAmount | Long(nullable) | z.number().nullable() | ✅ | |
| sale.amount/fee/tax/otherCost | Long(nullable) | z.number().nullable() | ✅ | |
| 나머지 20개 필드 | 타입 일치 | 타입 일치 | ✅ | |

**정정**: 이전 문서의 "nullish/nullable 차이 = 계약 불일치"는 오류. `nullish()` = `nullable().or(undefined)`로 더 관대함.

## 4. Jackson 직렬화 현황
`application.yml`: `sort-properties-alphabetically: false`만 설정, **전역 `@JsonInclude(NON_NULL)` 없음** → Spring 기본값으로 **null 필드 포함 전송**. `SecuritiesAccountResponse`만 `ALWAYS` 예외.
**정정**: NON_NULL 설정 변경 불필요(현재 null 포함 전송이 프론트 Zod와 호환). 해당 SF 삭제.

## 5. 데이터 시나리오별 계약 (수정 후 기대 동작)
- **0건(신규/미청약)**: `GET /investment-history?...&size=1` → `content: [], totalElements: 0` → `historyItem=null`, 섹션 `create` 모드 (**200 OK**)
- **청약만**: `allocation: null, sales: [], remainingQuantity: null`
- **배정만**: `allocation` 있음, `sales: []`, `remainingQuantity = allocatedQuantity`
- **분할 매도 N건**: `sales[N]`, `remainingQuantity = allocatedQty - ΣactiveQty`, 잔여 0시 추가 잠금

## 6. 계좌/투자자 문맥 및 복구
- 계좌: 주관사만 NFKC 정확일치 필터, 1개 자동선택, 복수 선택, 번호 없는 계좌 허용, 외부 링크 fallback 없음
- 복구: 저장/삭제 성공 → `onSaved()` → `invalidateAll()`(목록→상세→원본 순서) → 실패 시 "다시 불러오기" 버튼, 입력값 보존

## 7. 모의 API vs 실제 서버 차이 (테스트 누락 지점)
| 항목 | 테스트 목 데이터 | 실제 서버 | 위험도 |
|------|------------------|-----------|--------|
| securitiesAccountId/Name | **완전 누락** | null 포함 전송 | 🔴 높음 |
| allocation.allocatedAmount | null | null 포함 전송 | 🟡 중간 |
| sale 금액 필드들 | null | null 포함 전송 | 🟡 중간 |
| 비ACTIVE subscriptionStatus | 테스트 없음 | CANCELLED 등 가능 | 🟡 중간 |

**핵심**: 목 데이터에 `securitiesAccountId/Name` 누락 → Jackson 설정 변경 시 필드 완전 누락되어 Zod `nullish()`도 통과, 런타임 감지 불가.

## 8. 재현 가능성 분류 (수정 후)
| 현상 | 분류 | 근거 |
|------|------|------|
| verifyIpoIdOwnership 403 (본인 청약 0건) | **확정 결함** | `InvestmentHistoryOwnershipVerifier.java:50-57`, `TradeManagePage.tsx:154-166` |
| historyQuery.isError (네트워크/권한/400) | **재현 가능** | `TradeManagePage.tsx:587-598` 에러 UI |
| totalElements > 1 | **재현 가능** | 162-166, 599-604 검증 로직 |
| subscriptionStatus !== ACTIVE | **재현 가능** | 162-166, 605-610 검증 |
| 편집 원본 GET 404/403 | **재현 가능** | 174-202 원본 쿼리, 각 섹션 119-127 에러 처리 |
| 잔여 계산 불일치 | **미확인** | `SaleSection.tsx:82-88` vs `Assembler.java:84-90` |
| 자동 생성 청약 값 오류 | **미확인** | `SubscriptionSection.tsx:394-415`, AC-17 준수 여부 |

## 9. 공통 실패 경로 (확정)
1. **verifyIpoIdOwnership 403** → `historyQuery.isError` → 상단 에러 배너 → 전 섹션 `status: 'error'` → 원본 쿼리 미실행 → 3영역 동시 "확인 필요"
2. 네트워크/권한/400 → 동일 연쇄
3. `totalElements > 1` 또는 비ACTIVE → 데이터 무결성 에러 → 동일 연쇄

## 10. 비존재 IPO 정책 조사 및 권장
- **현행**: `verifyIpoIdOwnership`이 청약 존재 여부로 403 판단 (잘못됨)
- **권장**: IPO는 공용 카탈로그이므로 `ipoId` 필터에 대한 소유권 검증 **제거**. Repository 쿼리(`findByInvestorAccountIdWithFilters`)가 `currentAccountId`로 스코프 강제하므로 본인 데이터만 조회됨. 본인 청약 0건이면 `content: [], totalElements: 0`로 **200 OK** 반환.
- **단건 조회(`/investment-history/{subscriptionId}`)**: `verifySubscriptionOwnership` 유지(해당 청약 소유권 검증)

## 11. 결론 요약
- **확정 결함**: `verifyIpoIdOwnership` 잘못된 권한 검증으로 신규/미청약 사용자 403 → 전체 화면 잠금
- **DTO↔Zod**: 필드 레벨 완전 일치(`nullish()`가 `nullable` 포함하므로 계약 불일치 없음)
- **Jackson**: 전역 NON_NULL 미설정 → null 포함 전송(현재 호환), 변경 불필요
- **테스트 목 데이터**: `securitiesAccountId/Name` 누락으로 회귀 미탐지 위험
- **수정 우선순위**: ① verifyIpoIdOwnership 제거/무효화 ② 테스트 목 데이터 보강 ③ 에러 경계 UX 개선 ④ 동시성 검증 강화