# Work Log: account-authentication (Epic 1)

## 2026-09-20

### 완료 작업
- [x] `docs/research/account-authentication.md` — Research 워커(Nemotron 3 Ultra) 작성, Master 승인
- [x] `docs/spec/account-authentication.md` — Planning 워커(Nemotron 3 Ultra) 작성, Master 승인
- [x] `docs/architecture/account-authentication.md` — Architecture 워커(Nemotron 3 Super) 작성, Master 승인
- [x] GitHub 이슈 #35 생성 (Git Manager 워커, Nemotron 3 Ultra)

### 확인 사항
- 세션 기반 인증(HttpSession) 채택, JWT는 MVP 이후 검토
- API 5개 엔드포인트: POST /accounts, POST /auth/login, POST /auth/logout, POST /auth/password-change, GET /auth/session-status
- Account 테이블: email(unique), password_hash, failed_login_attempts, locked_until, created_at, updated_at, last_login_at

### Master 결정 사항 (architecture.md의 "결정 필요 사항" D-001~D-008에 대한 판단)

기술적 구현 세부사항이며 되돌리기 쉬운 초기 설계 수준이라 Master가 MVP 기준으로 결정. 사용자가 다른 의견이 있으면 구현 전에 언제든 변경 가능.

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-001 | 비밀번호 정책 강도 | 최소 8자 + 영문/숫자/특수문자 중 2종류 이상 | Spring Security 일반 권장 수준, 과하지 않음 |
| D-003 | 다중 기기 동시 로그인 | 제한 없음(여러 세션 허용) | MVP 단순화, 나중에 `maximumSessions`로 제한 추가 가능 |
| D-004 | Remember Me 포함 여부 | **MVP 제외** | spec.md 제외 범위와 정합성(2단계 인증 등과 같은 이유로 후순위), 토큰 관리 복잡도 회피 |
| D-005 | 계정 잠금 해제 방식 | 일정 시간(30분) 경과 후 자동 해제 | 관리자 화면이 없는 MVP에 적합, `locked_until` 필드로 이미 지원 가능한 설계 |
| D-006 | 세션 타임아웃 | 30분 | 금융 데이터 서비스로서 과도하게 길지 않은 일반적인 값 |
| D-007 | 비밀번호 변경 후 세션 처리 | 변경한 계정의 다른 모든 세션 무효화 | 보안 모범 사례(비밀번호 유출 대응) |
| D-008 | 로그인 실패 카운터 저장소 | DB(`Account.failed_login_attempts`) | architecture.md의 DB 설계에 이미 반영되어 있어 별도 결정 불필요 |

### 남은 작업
- [ ] Epic worktree 생성 (`feature/account-authentication`)
- [ ] worktree 안에서 breakdown-research/breakdown 진행 (세부 기능 분해)
- [ ] B그룹(Test/Implementation/QA) 착수
- [ ] GitHub 이슈 #35 종료 (Phase 0 완료 시점)

### 다음 단계
Epic worktree 생성 후 5.4 미니 Phase 0(세부 기능 재분해)로 진행.
