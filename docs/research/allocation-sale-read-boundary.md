# Allocation·Sale 조회 경계 조사

`guide-frontend.md`는 GET API client가 AbortSignal을 받고 Zod schema로 응답을 parse하도록 규정한다. 현재 `getAllocation`과 `getSale`은 `apiFetch<T>`를 직접 사용하고 signal을 받지 않는다. 세 편집 dialog queryFn은 allocation, sale, subscription 조회에서 signal을 전달하지 않는다. Backend DTO의 nullable 금액·메모 필드를 반영해야 한다. Investment history GET은 이미 parsed schema와 signal을 지원한다.

이 SF는 세 투자 기록 상세 GET을 schema parse와 요청 취소 규칙에 맞춘다. 등록/수정/삭제 mutation, 다른 도메인, UI 기능은 범위 밖이다. 기존 query key와 편집 화면 동작은 유지한다.
