# 작업 로그: 투자 내역 조회 API (investment-query-api, Epic 11)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 스코프 정정)

`docs/research/investment-query-api.md`는 조회 기준을 **Sale(매도) 중심**으로
설계하려 했으나, 이는 실제 설계 결함이다: Sale을 기준(anchor)으로 조회하면
**아직 배정되지 않았거나 배정됐지만 매도하지 않은 청약(Subscription/
Allocation)은 결과에 전혀 나타나지 않는다.** 사용자가 "내가 청약한 것 중
아직 안 판 것"을 확인하려는 요구(조사 문서 질문 4 "미매도 기록 표시")와
정면으로 모순된다. 이를 Master가 아래와 같이 정정한다.

### D-Q-001: 조회는 Subscription을 기준(anchor)으로 한다 (Sale 아님)
모든 투자 내역 조회는 **Subscription 1건당 응답 1개 row**를 기본으로 하고,
그 안에 연결된 Allocation(nullable — 아직 배정 전이면 null)과 Sale
목록(0건 이상 배열)을 중첩(nested)으로 포함한다. 이렇게 하면 청약만 하고
아직 배정/매도되지 않은 기록도 자연스럽게 조회 결과에 포함된다.

### D-Q-002: 페이지네이션은 offset/limit(page, size)로 충분 (cursor 불필요)
이 서비스는 개인용 투자 기록 도구로 데이터량이 크지 않다. cursor 기반
페이지네이션은 MVP에 과한 복잡도이며, 표준 `page`/`size` 쿼리 파라미터
(기본 size=20, 최대 size=100)로 충분하다.

### D-Q-003: 기본 정렬은 Subscription.subscribedAt DESC 단일 기준
조사 문서가 제안한 다수의 정렬 옵션(청약일/배정일/수익률/투자금 오름·
내림차순)은 MVP 범위를 넘어선다. 기본 정렬은 **청약일시(subscribedAt)
내림차순**(최근 청약이 먼저) 하나만 제공한다. 추가 정렬 옵션은 필요성이
확인되면 이후 반영한다.

### D-Q-004: 에러 응답 형식은 기존 Epic들과 동일한 단순 포맷 사용
조사 문서가 제안한 `{errorCode, message, details}` 형식 대신, Epic 5~10
전체에서 일관되게 써온 `{error, message}` 형식을 그대로 사용한다(전체
서비스 일관성 우선). 상태 코드는 400(검증 실패)/401(미인증)/403(소유권
없음)/404(존재하지 않음)만 사용하며, 422는 다른 Epic에서 쓰지 않으므로
도입하지 않고 400으로 통일한다.

### D-Q-005: 미매도 잔여수량은 파생값으로 계산 (저장하지 않음)
`잔여수량 = Allocation.allocatedQuantity - Σ(ACTIVE Sale.quantity)`.
Allocation이 없으면(아직 미배정) 잔여수량 필드는 null로 응답한다. 이는
[[profit-calculation]] D-P-001과 동일한 원칙(파생값은 저장하지 않고
on-demand 계산)을 따른다.

### D-Q-006: 소유권 검증은 로그인 계정 → Investor 경유로 항상 수행
요청자의 인증된 Account가 소유한 Investor의 Subscription만 조회 가능하다.
명시적으로 investorId/securitiesAccountId 필터를 지정해도 해당 필터
대상이 요청자 소유인지 재검증하며, 아니면 403을 반환한다.

이 결정들은 `docs/spec/investment-query-api.md` /
`docs/architecture/investment-query-api.md` 작성 태스크 지시에 반영되었다.
