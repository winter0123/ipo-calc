# 투자내역 read model 로더 분리 Spec

## 목표

페이지 조회에서 관련 도메인 데이터를 페이지 단위로 배치 수집·인덱싱하는 책임을 전담 컴포넌트로 옮겨 query service를 단순화한다.

## 범위

- `getHistory(...)`의 Allocation/Sale/IPO/Investor/Account/Firm 배치 조회와 map 구성을 로더로 추출한다.
- `getHistoryDetail(...)`의 단건 Allocation/Sale/IPO/Investor/Account/Firm 조회도 같은 로더로 이동한다.
- 기존 `InvestmentHistoryAssembler`가 받는 매핑 구조와 DTO를 유지한다.
- 빈 페이지에서는 관련 배치 저장소 조회를 생략한다.
- 사용되지 않는 `EntityManager` 필드를 제거한다.
- 서비스 테스트에서 계정 스코프, 필터, 결과 DTO, allocation/sale 배치 조회 및 빈 결과를 고정한다.

## 비범위

- API/DTO/JSON, repository 쿼리·인덱스, 페이지네이션·정렬, 소유권 검사 순서 변경.
- 상세 API/DTO 변경, projection 도입, 추가 데이터베이스 스키마, 성능 프레임워크 도입.
- 다른 서비스/화면 리팩토링.

## Acceptance Criteria

1. 같은 요청에 대한 결과 DTO, 총 건수, pageable metadata가 변경 전과 같다.
2. 현재 계정 ID와 기존 필터가 동일하게 repository에 전달된다.
3. 관련 배치 데이터는 페이지 ID 집합 기준으로 조회되며 row별 repository 호출이 없다.
4. 페이지가 비었으면 관련 데이터 배치 조회가 발생하지 않고 빈 `Page` 메타데이터를 보존한다.
5. allocation별 sale 결과가 해당 subscription DTO에 정확히 매핑되고 잔여수량 결과가 동일하다.
6. 변경된 backend 테스트, checkstyle 및 build 결과를 기록한다.
7. Query service가 인증/소유권 검증, subscription page/detail 조회, 로더/assembler 조율만 맡는다.

## 구현 제한

- 새 컴포넌트의 입출력은 읽기 모델 맵으로 제한하고, 현재 조립 DTO 인자를 불필요하게 재설계하지 않는다.
- 목록 로더에는 SecurityUtils나 Controller를 넣지 않는다. 계정 스코프의 진입 조건은 query service가 소유한다.
- 조회 결과가 없어도 이전과 동일한 null/빈 목록을 유지한다.
