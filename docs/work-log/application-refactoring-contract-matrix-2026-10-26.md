# 애플리케이션 리팩토링 계약 gap matrix (2026-10-26)

> 근거는 현재 production 소스와 API DTO/schema, 기존 API client 및 mutation 테스트의 정적 대조다. 이 문서 작성 시 테스트·lint·build는 실행하지 않았다. 해당 상태는 모두 `NOT_RUN`으로 취급한다.

## UI query / API 경계

| 경계 | 현재 상태 | 근거와 보존 계약 |
|---|---|---|
| Query key | production query는 entity key factory 사용 | 남아 있던 IPO invalid detail, announcement, IPO sync status, all investment history key를 factory에 포함. 이전 배열 key/prefix를 유지 |
| GET response | parse helper와 선택 AbortSignal 사용 | entity GET 전체를 `apiGetParsed` 사용처와 queryFn signal 전달 흐름으로 대조 |
| Body response mutation | Zod schema 적용 | IPO, subscription/allocation/sale, broker credential, admin announcement, investor/account/signup, account reveal, notification success, broker sync confirm |
| No-body mutation | `void` 유지 | DELETE, 204 action 및 login/logout처럼 body 없는 응답. 파싱 대상 response body가 없음 |
| Secret response cache | mutation 결과에만 노출 | 계좌 원문 공개는 DTO parse 후 번호 문자열만 반환, `gcTime: 0` 유지 |
| Query invalidation | 영향 도메인 prefix만 무효화 | 투자 기록 변경 → investment history/profit/dashboard; broker confirm → 해당 계좌 preview와 세 view; account/investor/broker/IPO/admin/notification은 각 entity key |
| Announcement dismiss | 기존 방문 상태 동작 유지 | 닫기 동작은 방문별 local state로 제어하며 서버 dismissal mutation 오류 표시 보존 |

## Backend 경계

| 경계 | 현재 상태 | 근거와 보존 계약 |
|---|---|---|
| Investment history 목록 | 페이지 단위 배치 loader | 빈 페이지면 참조 repository 조회를 생략. 페이지 레코드 단위 allocation/sale/IPO/investor/account/firm 호출을 하지 않도록 batch API 사용 |
| Investment history 상세 | 단건 read model loader | 소유권 검사 후 assembler에 필요한 allocation/sale/IPO/investor/account/firm 데이터를 전달 |
| IPO synchronization | coordinator와 persistence 분리 | 회사별 fetch/실패 분류/lock/transaction/schedule merge는 coordinator, 기존/수동 보호/생성/자동 갱신은 `IpoSyncPersistence` |
| Securities account ownership | verifier 경계 사용 | investor 존재/소유 및 account investor owner 검사; 기존 404/403 변환 유지 |
| Domain validation | 명백한 중복만 제거 | Sale 음수 금액 공통화, Allocation update 중복 음수 사전검사 제거, Subscription update의 이미 검증한 account 재사용 |
| Announcement exception | 보류 | 직접 Spring 예외를 공통 업무 예외로 바꾸면 JSON 계약이 달라질 수 있어 기존 status/detail 특성화 테스트만 추가 |

## 테스트·실행 상태

- 관련 단위/API/UI 회귀 테스트는 구현 변경과 함께 추가했다. 현재 전체 미커밋 변경 기준 실행 상태: **`NOT_RUN`**.
- GitHub Actions workflow: 추가 완료. hosted runner 결과는 **`NOT_RUN`**.
- 투자내역 대표 데이터 규모별 query count/응답 시간 기준: **`NOT_RUN`**. 기준 데이터 세트와 수치 임계값은 측정 전 설정하지 않는다.
- Browser 수동/E2E: **`NOT_RUN`**.
- 계좌 식별 정보 추가 DTO: 이번 리팩토링에는 API 응답 계약 확장이 필요해 제외했다. 해당 제품 요구가 승인된 별도 범위에서 처리한다.
