# API 응답 파싱 경계 통합

## 2026-10-26 — 구현

- 공통 `apiFetchParsed(path, schema, options)`를 추가했다. `apiGetParsed`도 이 구현을 호출해 GET과 본문 응답 요청이 fetch 이후 같은 Zod parse 경계를 사용한다.
- IPO 동기화 POST, 알림 설정 PUT, 테스트 알림 POST의 개별 응답 파싱을 공통 helper로 이동했다. 요청 method/body, 반환값, 검증 schema는 유지했다.
- 공통 helper의 요청 옵션 전달과 schema 오류를 고정하는 테스트를 추가했다. 누적 리팩토링 지시에 따라 UI 검사 실행은 보류한다: `NOT_RUN`.

## 후속 구현 — 브로커·공지 mutation 응답 검증

- `POST/PUT /securities-accounts/{id}/api-credential` 응답은 backend `ApiCredentialResponse`이며 GET의 `brokerCredentialSchema`와 필드가 일치한다. 두 mutation을 `apiFetchParsed`로 전환하고 method/body 및 유효·불완전 응답 테스트를 추가했다.
- 관리자 공지 POST/PATCH도 `AnnouncementResponse`를 반환한다. 기존 `announcementSchema`로 응답 검증을 적용하고 정상·불완전 응답 테스트를 추가했다.
- DELETE·dismiss·broker sync confirm 및 admin-account action은 본문이 없는 `204` 경로로 확인되어 `void` 요청을 유지했다.
- 테스트·lint·typecheck·format:check·build는 실행하지 않았다: `NOT_RUN`.

## 후속 구현 — 계좌·투자자·회원가입 및 민감 계좌번호 응답 경계

- 투자자/증권계좌 등록 mutation을 feature에서 직접 `apiFetch<T>`하던 구조에서 entity api-client의 `apiFetchParsed` 경계로 이동했다. 기존 GET 응답 schema를 재사용해 중복 response interface를 entity `z.infer` 타입으로 통일했다.
- 계좌명 수정 및 기본 계좌 지정도 서버가 `SecuritiesAccountResponse` 본문을 반환하므로 `securitiesAccountSchema` 검증을 적용했다. 계좌 비활성화 등 204 mutation은 기존 `void` 요청을 유지했다.
- 평문 계좌번호 공개 응답은 backend `id`, `accountNumber`, `exposedAt` DTO를 별도 schema로 검증한다. hook은 검증된 응답에서 문자열만 돌려주며 `gcTime: 0` 보호를 유지해 평문이 mutation cache에 남지 않게 했다.
- 회원가입은 backend `SignupResponse`의 accountId/email/name/nickname/approved 전체를 검증하는 feature api-client 경계를 추가했다. UI fixture를 실제 DTO와 맞췄다.
- API client와 UI fixture 테스트를 추가했으며, UI 전체 검증은 여전히 누적 마지막 단계까지 실행하지 않았다: `NOT_RUN`.
- `AnnouncementService`의 직접 `ResponseStatusException` 사용은 공통 `ApiException`으로 치환하면 응답 계약이 바뀔 수 있어 보류했다. 종합 검증에서 실제 MockMvc 응답 body가 비어 있음을 확인해 controller test를 400/404 status와 빈 body 기준으로 고쳤다. API 오류 형식 변경을 별도 설계할 때 이 테스트를 기준으로 한다.
- 최종 transport 검색에서 notification subscribe/unsubscribe가 backend `SuccessResponse(message)`를 반환하지만 `void`로 처리했고, broker-sync confirm이 `SaleResponse[]`를 반환하지만 `void`로 처리함을 추가 확인했다. 알림은 기존 `Promise<void>`를 유지하면서 `successResponseSchema`로 검증한다. Broker sync confirm transport는 broker entity에서 multi-entity orchestration feature로 옮겨 `saleSchema[]`를 검증하고 기존 `Promise<void>` 계약을 유지한다.

## 후속 감사 — mutation 캐시 무효화 범위

- 투자 기록 생성·수정·삭제 및 브로커 동기화 확정은 투자 내역·수익·대시보드 캐시를 무효화한다. 브로커 확정은 해당 계좌 미리보기 키도 함께 무효화한다.
- 이 감사에서 위 핵심 mutation 경계 테스트에 broker preview 키와 투자 기록 수정/삭제의 세 prefix 검증을 추가했다. 투자자/계좌 등록, 로그인, IPO 등록, 알림 설정, 증권사 연동 및 IPO sync는 각 도메인 key에만 연결되며 화면·API 흐름에서 확인했다.
- 남은 `apiFetch` production 호출은 DELETE/204, 로그인·로그아웃의 본문 없는 응답이다. 응답 body를 버리는 현재 계약을 유지한다.
- 이번 테스트는 추가만 했고 실행하지 않았다. 사용자 지시에 따라 UI 전체 검증 때 함께 실행한다: `NOT_RUN`.
