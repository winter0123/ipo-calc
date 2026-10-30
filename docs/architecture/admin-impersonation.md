# 관리자 임퍼소네이션 아키텍처 설계 — Boot4.1.1 / Security7.1.1

## 1. 아키텍처 원칙
관리자(actor) Authentication은 기존 HTTP 세션 30일 보존을 따른다.

## 2. 세션·스레드 설계 (요청 계약 그대로)
- impersonation은 별도 DB UUID + HTTP 세션 marker로 관리한다.
- impersonation 유지 30분, scheduler expiry 1분.
- ThreadLocal `finally.remove()`로 종료하며 `SecurityContext.clear`·`session.invalidate`·`deleteById`는 금지한다.

## 3. 요청 검증·스케줄러 (요청 계약 그대로)
- 매요청 actor ADMIN·actorUUID 귀속·target approved·expiry 검증. 종료 시 현재 marker 제거.
- scheduler는 DB 종료 + 감사 일체 tx를 수행한다. 다른 HTTP 세션 marker는 다음 요청에 lazy 제거한다.

## 4. DB 설계 (요청 계약 그대로)
- start: actor 행 lock + partial unique(`actor_admin_id where ended_at is null`), 만료 행 종료 후 생성.
- audit start/end/replaced/expired 영속 저장.
- 기존 V36·V37 변경 금지, 신규 migration만.

---
*작성일: 2026-10-30*
*기준: Boot4.1.1, Security7.1.1*
