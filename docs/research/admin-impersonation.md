# 관리자 임퍼소네이션 조사 문서 — Boot4.1.1 / Security7.1.1

## 1. 조사 목적
관리자 임퍼소네이션의 actor·세션·CSRF·만료 정책을 Boot4.1.1·Security7.1.1 기준으로 조사·확정한다. Epic 1 Phase 0 정본(v7)의 account-authentication 범위와 연계한다.

## 2. 조사 범위
- actor Authentication과 기존 HTTP 세션 30일 보존.
- impersonation 별도 DB UUID + HTTP 세션 marker.
- impersonation 유지 30분, scheduler expiry 1분.

## 3. 확정 결정 (요청 계약 그대로)
1. 관리자(actor) Authentication은 기존 HTTP 세션 30일 보존을 따른다.
2. impersonation은 별도 DB UUID + HTTP 세션 marker로 관리한다.
3. impersonation 유지시간 30분, scheduler expiry 1분.
4. ThreadLocal `finally.remove()`로 종료하며 `SecurityContext.clear`·`session.invalidate`·`deleteById`는 절대 금지한다.

## 4. 미확인 사항
- 조사로 확인되지 않은 정책은 신규 추론하지 않고 NOT_RUN으로 기록한다.

---
*작성일: 2026-10-30*
*기준: Boot4.1.1, Security7.1.1 (Epic 1 Phase 0 정본 v7)*
