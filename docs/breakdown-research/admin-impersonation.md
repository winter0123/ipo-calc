# 관리자 임퍼소네이션 breakdown-research — Boot4.1.1 / Security7.1.1

## 1. 연구 목적
SF-1~SF-7의 범위·의존성·AC를 요청 계약 그대로 매핑한다.

## 2. SF 매핑 (요청 계약 그대로)
- SF-1: 임퍼소네이션 시작 — `POST /admin/impersonation/start/{id}`, `{"sessionId":"uuid","expiresAt":"...","readOnly":true}`, 본인 불가·30분.
- SF-2: 상태 조회 — `GET /admin/impersonation/status`, `{"impersonating":true,"targetAccountId":123,"expiresAt":"..."}`.
- SF-3: 종료 — `POST /admin/impersonation/end`, 204.
- SF-4: 대상 격리 — 대상 외 데이터 403, 기존 조회 API allowlist.
- SF-5: CSRF·UI transport — `GET /auth/csrf`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, `Origin`/`Referer`, `APP_CSRF_TRUSTED_ORIGIN`, `null Origin` 403.
- SF-6: 쓰기 차단·admin API 차단 — unsafe method 차단, `end` POST 예외.
- SF-7: 회원 관리 API·`/admin/users` 중복 금지 (서버 관리용 `/admin/users`와 `/admin/users/{id}`는 허용).

## 3. 선행·후속
- 선행: SF-1 → SF-2 → SF-3, SF-5 → SF-6. 후속: SF-4 → SF-6, SF-7(통합).

## 4. 완료 조건
- SF-1~SF-7 각각 AC·검증 기준을 요청 계약 그대로 충족한다.

---
*작성일: 2026-10-30*
*기준: Boot4.1.1, Security7.1.1*
