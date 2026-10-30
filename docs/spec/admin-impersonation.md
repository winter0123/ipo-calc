# 관리자 임퍼소네이션 명세 — Boot4.1.1 / Security7.1.1

## 1. 목적
관리자 임퍼소네이션 API·CSRF·인가 계약을 확정한다. 서버 관리용 신규 회원 관리 API `/admin/users`와 `/admin/users/{id}`, 필요한 하위 내역을 정의한다. 외부 `/api` 접두사는 Caddy가 제거한다. 신규 `/api/users` 컨트롤러는 만들지 않는다.

## 2. API 계약 (요청 계약 그대로)
- 서버: `POST /admin/impersonation/start/{id}`, `GET /admin/impersonation/status`, `POST /admin/impersonation/end`.
- 외부 노출은 Caddy `/api` prefix로 붙인다.
- `status` = `{impersonating:true, targetAccountId:123, expiresAt:...>}`.
- `start` = `{"sessionId":"uuid","expiresAt":"...","readOnly":true}`.
- `end` = HTTP 204.

## 3. 인가 계약 (요청 계약 그대로)
- ADMIN만 진입한다.
- 임퍼소네이션 중 모든 admin API를 차단하되 `status` GET·`end` POST만 예외 허용한다.

## 4. CSRF 계약 (요청 계약 그대로)
- `GET /auth/csrf` 익명 bootstrap. Spring 기본 `CsrfFilter` + SPA cookie `XSRF-TOKEN` / header `X-XSRF-TOKEN`.
- unsafe 전 토큰 취득, login 후 재발급, logout CSRF 필수.
- `Origin` 존재 시 scheme·host·effective port 신뢰설정(`APP_CSRF_TRUSTED_ORIGIN`) 대조. 없으면 `Referer` 동일 대조. 둘 다 없어도 유효 CSRF 필수. `null Origin` 403.

## 5. Acceptance Criteria
- AC-01: start → 200, `{"sessionId":"uuid","expiresAt":"...","readOnly":true}`.
- AC-02: status → `{"impersonating":true,"targetAccountId":123,"expiresAt":"..."}`.
- AC-03: end → 204.
- AC-09: 신규 `/api/users` 중복 API 금지.

---
*작성일: 2026-10-30*
*기준: Boot4.1.1, Security7.1.1*
