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

## 2026-09-21 — SF 분해 착수 전 Master 결정 (구현 착수, ui/ 프로젝트 최초 생성)

프로젝트에 `ui/` 폴더가 아직 없어 `package.json`이 존재하지 않는다. architecture.md가 "확인 후 결정"으로 미뤄둔 항목들을 Master가 신규 프로젝트 기준으로 확정한다.

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-W13-007 | 빌드 도구 | **Vite** (React+TS 템플릿) | CRA는 공식적으로 유지보수 종료됨. Vite가 현재 React 공식 문서가 권장하는 표준 스캐폴딩 도구 |
| D-W13-008 | 라우팅 | **react-router-dom 신규 설치** | 4개 화면 간 이동(`/dashboard`, `/investors/register`, `/securities-accounts/register`)이 spec에 명시돼 있어 라우팅 라이브러리 없이는 구현 불가 |
| D-W13-009 | 폼 상태 관리 | **react-hook-form 신규 설치** | 폼이 4개이고 각각 유효성 검사 규칙이 있어, `useState` 기반 직접 구현 시 boilerplate가 4배로 반복된다. architecture.md도 이 방향을 명시 |
| D-W13-010 | 폼 검증 | **zod 없이 `shared/lib/validators.ts`에 직접 구현** | spec의 검증 규칙(이메일 정규식, 길이, 필수)이 단순해 zod 스키마 도입은 과설계. 프로젝트 전체가 "필요할 때만 의존성 추가" 원칙을 따름(D-IPO-003과 동일 판단 기준) |
| D-W13-011 | HTTP 클라이언트 | **네이티브 `fetch` 래퍼** (axios 미도입) | `credentials: 'include'` 옵션만으로 세션 쿠키 요구사항(D-W13-003)을 충족하므로 axios가 제공하는 추가 기능이 불필요 |
| D-W13-012 | 스타일링 | **CSS Modules** (architecture.md의 "기본 확정 옵션" 채택) | Tailwind/CSS-in-JS 신규 도입 없이 Vite가 기본 지원하는 방식으로 충분 |
| D-W13-013 | 접근성 검증 | **axe-core를 devDependency로 설치, 개발 중 콘솔 경고로 활용** | Lighthouse 등 추가 도구 없이 D-W13-005(axe-core 단일 채택)를 그대로 구현 |
| D-W13-014 | 투자자 ID 전파 방식 | **react-router-dom의 `navigate(path, { state })`** (router state) | AC-016이 회원가입 성공 시 "자동 이동"만 요구하고 새로고침 유지까지는 요구하지 않으므로, sessionStorage보다 단순한 router state로 충분. `shared`가 아닌 `pages`/`features` 레벨에서만 다뤄 FSD 규칙 위반 없음 |
| D-W13-015 | 컴포넌트/훅 단위 테스트 도구 | **Vitest + React Testing Library** (Vite 프로젝트 표준 조합) | 이 프로젝트의 Test-First(RED→GREEN) 원칙을 프론트에도 동일 적용하려면 백엔드의 JUnit에 대응하는 프론트 테스트 러너가 필요. Playwright E2E(spec 7.7)는 별도 QA 단계 관심사로 SF 범위에서 제외(D-W13-006과 일관) |

### 401 인터셉터 FSD 순환 참조 방지 (architecture.md 리스크 대응)
`shared/api/client.ts`는 401 발생 시 라우터를 직접 참조하지 않고 에러를 그대로 던진다. 로그인 리다이렉트 처리는 각 `pages`/`features`의 TanStack Query `onError`에서 `useNavigate()`로 수행한다.

### SF 분해 (5.4, 총 5개, 순차 의존)

```
SF-1 (프로젝트 스캐폴딩) → SF-2 (로그인 화면) → SF-3 (회원가입 화면)
  → SF-4 (투자자 등록 화면) → SF-5 (증권계좌 등록 화면)
```

1. **SF-1**: 저장소 루트에 `ui/` 폴더 생성, Vite(React+TS) 스캐폴딩, ESLint+Prettier+`tsc --strict` 설정(2칸 들여쓰기), FSD 폴더 뼈대(`app/{pages,widgets,features,entities,shared}`), TanStack Query client 설정(`QueryClientProvider`), react-router-dom 라우터 설정(빈 라우트만), `shared/api/client.ts`(fetch 래퍼, credentials:'include'), Vitest+RTL 설정. API 엔드포인트 화면은 아직 없음
2. **SF-2**: LoginPage/LoginForm/useLogin — `POST /auth/login` 연동, AC-001~003 커버
3. **SF-3**: SignupPage/SignupForm/useSignup — `POST /accounts` 연동, 성공 시 투자자 등록 화면 자동 이동(router state로 이후 필요한 값 전달), AC-004~006, AC-016, AC-019
4. **SF-4**: InvestorRegisterPage/InvestorRegisterForm/useRegisterInvestor — `POST /investors` 연동, AC-007~009, AC-017
5. **SF-5**: SecuritiesAccountRegisterPage — `GET /securities-firms` 드롭다운 + `POST /securities-accounts` 등록, 계좌번호 하이픈 자동 삽입, 마스킹된 응답 그대로 표시(D-W13-002), AC-010~012, AC-018, AC-020

각 SF는 컴포넌트/훅 단위 테스트(Vitest+RTL)를 Test-First로 작성한다. `npm run lint`, `npm run typecheck`, `npx prettier --check .` 통과가 각 SF의 완료 조건이다(AGENTS.md 14장).
