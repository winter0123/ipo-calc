# Allocation·Sale 상세 GET 경계 Spec

## 범위

- Allocation/Sale/Subscription response schema 및 `z.infer` 응답 타입을 둔다.
- 세 상세 GET 함수가 `signal?: AbortSignal`을 받아 `apiGetParsed`에 전달한다.
- 세 edit dialog queryFn이 TanStack Query의 signal을 넘긴다.

## Acceptance Criteria

1. Allocation·Sale·Subscription GET 응답은 DTO nullable 필드를 포함한 schema로 검증된다.
2. 요청 취소 신호가 client부터 `apiFetch`까지 전달된다.
3. 현재 query key, edit form의 nullable→빈값 변환과 사용자 동작은 보존된다.
4. 관련 api-client 테스트에서 schema mismatch와 signal 전달을 검증한다.

## 비범위

mutation response schema, API/DTO 변경, 다른 feature/query key 변경.
