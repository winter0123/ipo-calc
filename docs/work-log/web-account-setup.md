# 작업 로그: 웹 계정 설정 (web-account-setup, Epic 13)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 최신화)

`docs/research/web-account-setup.md`(2026-09-20 작성)는 투자자 등록/증권계좌
등록 API 경로를 "미정"으로 남겨뒀으나, 이후 Epic 2([[investor-management]])와
Epic 4([[securities-account-management]])의 spec이 완성되어 실제 계약이
확정되었다. 프론트엔드 spec은 추측이 아니라 이 확정된 계약을 그대로
사용해야 한다.

### D-W13-001: 확정된 API 계약 (추측 금지, 실제 spec 인용)
- **로그인**: `POST /auth/login` — `{email, password}` → 200(세션 쿠키) / 401
- **회원가입**: `POST /accounts` — `{email, password, name}` → 201(`{accountId, email}`) / 409(중복) / 400
- **투자자 등록**: `POST /investors` — `{display_name, relationship?}` → 201(`{id, display_name, account_id, status, ...}`) / 400
- **증권계좌 등록**: `POST /securities-accounts` — `{investorId, securitiesFirmId, accountNumber, accountName?, isDefault?}` → 201(마스킹된 `accountNumber` 포함 응답) / 400 / 404
- **증권사 목록**(계좌 등록 폼의 드롭다운용): `GET /securities-firms` — Epic 3([[securities-firm-catalog]])에서 이미 확정됨

### D-W13-002: 계좌번호 마스킹은 백엔드가 이미 처리 — 프론트에서 재설계 금지
[[securities-account-management]] D-C-005/아키텍처에 따라 계좌번호는 **서버
응답 시점에 이미 뒷 4자리만 노출하고 나머지는 마스킹된 문자열**로
내려온다. 프론트엔드는 서버가 보내주는 마스킹된 문자열을 그대로 표시하면
되며, 은행별 마스킹 패턴이나 별도 마스킹 로직을 프론트에서 구현하지
않는다. 입력 시 하이픈 자동 삽입 같은 **입력 서식 지원**만 프론트 책임이다.

### D-W13-003: 세션 쿠키는 Spring 기본값(JSESSIONID) 그대로 사용
커스텀 쿠키 이름이나 별도 경로 설정을 하지 않는다. 프론트엔드는 쿠키를
직접 다루지 않고 브라우저의 기본 쿠키 처리에 위임한다(fetch/axios
`credentials: 'include'` 설정만 필요).

### D-W13-004: 반응형 브레이크포인트는 조사 결론 그대로 채택
640px(모바일)/768px(태블릿)/1024px(데스크톱) — 별도 조정 없이 그대로
사용한다.

### D-W13-005: 접근성 검증 도구는 axe-core 단일 채택
Lighthouse와의 조합 등 복잡한 다중 도구 구성은 MVP 범위 밖이다. 개발 중
axe-core 브라우저 확장/CI 통합만으로 충분하다.

### D-W13-006: E2E 테스트 클린업 전략은 이 Epic에서 설계하지 않음
테스트 인프라 구현 세부사항이며 spec/architecture 문서의 범위가 아니다.
"실제 엔드포인트 호출, DB 상태 변경 포함"이라는 조사 결론만 유지하고,
구체적 클린업 메커니즘은 구현 단계(QA/테스트 워커)에서 결정한다.

이 결정들은 `docs/spec/web-account-setup.md` /
`docs/architecture/web-account-setup.md` 작성 태스크 지시에 반영되었다.
