# 관리자 임퍼소네이션 breakdown — Boot4.1.1 / Security7.1.1

## 1. SF 구현 순서 및 의존성 (요청 계약 그대로)
| 순서 | SF | 기능 | 선행 | 후속 |
|---|---|---|---|---|
| 1 | SF-1 | 임퍼소네이션 시작 | - | SF-2, SF-5 |
| 2 | SF-2 | 상태 조회 | SF-1 | SF-3, SF-4 |
| 3 | SF-3 | 종료 | SF-1~SF-2 | - |
| 4 | SF-4 | 대상 격리 | SF-1~SF-3 | SF-6 |
| 5 | SF-5 | CSRF·UI transport | SF-1 | SF-6 |
| 6 | SF-6 | 쓰기 차단·admin API 차단 | SF-1~SF-5 | - |
| 7 | SF-7 | 회원 API·`/admin/users` 중복 금지 | 전체 | - |

## 2. 착수 전 확인 (요청 계약 그대로)
- ADMIN만 진입, 임퍼소네이션 중 admin API 차단(`status` GET·`end` POST 예외).
- unsafe method(POST·PUT·PATCH·DELETE 등) 차단, `end` POST 예외.
- 대상 외 데이터 403.
- `GET /auth/csrf` 익명 bootstrap, unsafe 전 토큰 취득/login 후 재발급/logout CSRF 필수.

## 3. 검증
- `git diff --check`, 금지 계약 모순 검색.
- 근거 미확인 항목은 NOT_RUN.

---
*작성일: 2026-10-30*
*기준: Boot4.1.1, Security7.1.1*
