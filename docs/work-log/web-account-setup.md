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
- **투자자 등록**: `POST /investors` — `{displayName, relationship?}` → 201(`{id, displayName, accountId, status, ...}`) / 400 (**2026-09-21 정정**: 초안은 snake_case였으나 Epic 2 SF-3 구현 중 camelCase로 확정됨 — investor-management.md 실제 API 기준)
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

---

## 2026-09-21 — SF-1: 프로젝트 스캐폴딩 완료

### 완료 내용
- 저장소 루트에 `ui/` 폴더 생성 (Vite React+TS 스캐폴딩)
- ESLint + Prettier + `tsc --strict` 설정
- FSD 폴더 뼈대 생성 (`app/{pages,widgets,features,entities,shared}`)
- `shared/api/client.ts` fetch 래퍼 작성 (`credentials: 'include'`)
- `main.tsx`에 QueryClientProvider + BrowserRouter 설정
- Vitest + React Testing Library 설정, 샘플 테스트 1개 작성

### 설치된 패키지 버전
| 패키지 | 버전 | 용도 |
|---|---|---|
| react-router-dom | ^7.18.4 | 라우팅 (D-W13-008) |
| react-hook-form | ^7.88.0 | 폼 상태 관리 (D-W13-009) |
| @tanstack/react-query | ^5.103.1 | 서버 상태 관리 |
| axe-core | ^4.13.0 | 접근성 검증 (D-W13-013) |
| vitest | ^5.0.1 | 테스트 러너 (D-W13-015) |
| @testing-library/react | ^16.3.3 | 컴포넌트 테스트 |
| @testing-library/jest-dom | ^7.0.1 | DOM 매처 |
| jsdom | ^30.1.0 | 테스트 환경 |
| eslint | ^9.39.5 | 린팅 |
| prettier | ^3.9.8 | 코드 포맷팅 |

### 실행한 명령어 및 결과
| 명령어 | 결과 |
|---|---|
| `npm run lint` (eslint .) | PASS (0 errors) |
| `npm run typecheck` (tsc --noEmit) | PASS (0 errors) |
| `npm run format:check` (prettier --check .) | PASS (All matched files use Prettier code style!) |
| `npm run test` (vitest run) | PASS (1 file, 3 tests passed) |
| `npm run build` (tsc -b && vite build) | PASS (dist/index.html, dist/assets/) |


---

## 2026-09-21 — SF-2: 로그인 화면 완료

### 완료 내용
- `features/login/LoginForm.tsx` — `react-hook-form` 기반 폼, 이메일/비밀번호 유효성 검사, 로딩 상태(버튼 비활성화), 성공 시 `/dashboard` 리디렉션 (`useNavigate`), 401/네트워크 오류 인라인 메시지 표시(`aria-live="polite"`)
- `features/login/useLogin.ts` — TanStack Query `useMutation` 래핑, `POST /auth/login` 호출, `shared/api/client.ts` 재사용 (`credentials: 'include'`)
- `pages/LoginPage/LoginPage.tsx` — `/login` 페이지, `LoginForm` 포함
- `entities/auth/types.ts` — `LoginRequest` 타입
- `shared/lib/validators.ts` — `isValidEmail`, `isMinLength`
- `App.tsx`에 `/login` 라우트 추가
- CSS Modules (`LoginForm.module.css`, `LoginPage.module.css`) 사용
- RED→GREEN 테스트: `LoginForm.test.tsx`

### Master 독립 검증에서 발견·수정한 결함 (워커가 "완료"로 보고했으나 실제로는 4/4 테스트 실패 상태였음)
- **테스트가 `QueryClientProvider` 없이 작성돼 전부 실패**: `useLogin`이 TanStack Query `useMutation`을 쓰는데 테스트가 provider로 감싸지 않아 렌더링 자체가 예외로 죽었음. 또한 개별 테스트 바디가 실제 상호작용(입력/제출) 없이 초기 렌더링 상태에서 나타날 이유가 없는 텍스트를 찾는 식으로 작성돼 있어, 통과하더라도 각 시나리오(401/이메일오류/필수값) 를 실제로 검증하지 못하는 상태였음. `QueryClientProvider`로 감싸고 `apiFetch`를 모킹해 각 시나리오를 실제 입력→제출→결과 확인 흐름으로 재작성.
- **실제 컴포넌트 버그 발견**: `<form>`에 `noValidate`가 없어, `type="email"` 필드에 형식이 잘못된 값을 넣고 제출하면 브라우저 네이티브 검증이 submit 이벤트 자체를 막아버려 react-hook-form의 커스텀 한글 에러 메시지("올바른 이메일 형식을 입력해 주세요.")가 절대 뜨지 않는 결함이 있었음(재작성한 테스트가 이를 실제로 잡아냄). `noValidate` 추가로 수정.
- **SF-1부터 있던 tsconfig 설정 결함 발견**: `tsconfig.app.json`의 `types`에 `vitest/globals`/jest-dom 타입이 없어서, `npm run typecheck`(`tsc --noEmit`, 프로젝트 참조 없이 동작해 사실상 아무것도 검사하지 않음)는 통과하지만 `npm run build`(`tsc -b`, 프로젝트 참조를 실제로 빌드)는 `toBeInTheDocument` 등 jest-dom 매처를 인식 못 해 실패하는 상태였음. `@testing-library/jest-dom/vitest`(Vitest용 export, Jest용과 다름) 사용으로 수정하고 `types`에 추가. **주의**: 이후 SF부터는 `npm run typecheck`만 믿지 말고 `npm run build`까지 반드시 함께 실행해 검증한다 — 현재 `typecheck` 스크립트는 프로젝트 참조를 안 타서 이런 타입 오류를 못 잡는다.

---

## 2026-09-21 — SF-3: 회원가입 화면 완료

### 완료 내용
- `features/signup/SignupForm.tsx` + `useSignup.ts` — `POST /accounts`, react-hook-form (`noValidate` 포함), TanStack Query 사용, 201 성공 시 `navigate('/investors/register', { state: { accountId } })` (D-W13-014 router state)
- `features/signup/SignupForm.test.tsx` — RED→GREEN: 정상 가입, 409 중복, 400 오류, 이메일 형식 오류, 비밀번호 조건 미충족, 필수값 누락 시나리오 실제 검증 (QueryClientProvider 감싸기 + fireEvent 입력/제출)
- `pages/SignupPage/SignupPage.tsx` + `.module.css`
- `App.tsx`에 `/signup` 라우트 추가
- CSS Modules 사용, 2칸 들여쓰기, ESLint/Prettier/tsc strict 준수

### Master 독립 검증에서 확인한 결함(이전 사고 방지)
- `<form>`에 `noValidate` 누락 시 브라우저 네이티브 검증이 커스텀 한글 에러 메시지를 막는 버그 → 추가 완료
- 테스트가 초기 렌더링 상태 텍스트 검색만으로 작성되어 실제 시나리오 미검증 → fireEvent 실제 입력/제출로 재작성

### 실행 결과
| 명령어 | 결과 |
|---|---|
| `pnpm run lint` | PASS |
| `pnpm run typecheck` | PASS |
| `pnpm run format:check` | PASS (my files; only pre-existing pnpm-lock.yaml warning) |
| `pnpm run test` | PASS (6 tests) |
| `pnpm run build` | PASS (dist/ 생성) |

## 2026-09-21 — 사용자 가이드 HTML 및 스크린샷 산출물 완료

### 완료 내용
- `ui/`에서 `pnpm install`을 실행하고 Vite 개발 서버를 실제 브라우저에서 사용할 수 있도록 기동했다.
- 기본 포트 5173이 이미 사용 중이어서 Vite가 선정한 실제 포트 5174를 먼저 확인했고, 이후 재현 가능한 캡처용으로 4173 포트를 사용해 `/login`과 `/signup`을 확인했다.
- `tools/screenshot-guide/`에 Playwright만 devDependency로 가진 독립 도구를 추가하고 `capture.mjs`로 데스크톱 1280×720, 모바일 390×844 전체 페이지 스크린샷 4장을 생성했다.
- `docs/user-guides/web-account-setup/index.html`을 순수 HTML+인라인 CSS로 작성했다. 로그인과 회원가입의 목적, 입력 필드, 검증 규칙, 성공 후 이동 경로를 설명하고 스크린샷을 상대 경로로 연결했다.
- 작업 완료 후 기동한 Vite 프로세스를 모두 종료했다.

### 확인한 산출물
- `docs/user-guides/web-account-setup/screenshots/login-desktop.png` — 10,170 bytes, 1280×720
- `docs/user-guides/web-account-setup/screenshots/login-mobile.png` — 7,945 bytes, 390×844
- `docs/user-guides/web-account-setup/screenshots/signup-desktop.png` — 11,910 bytes, 1280×720
- `docs/user-guides/web-account-setup/screenshots/signup-mobile.png` — 10,050 bytes, 390×844
- `docs/user-guides/web-account-setup/index.html` — 생성 완료
- `tools/screenshot-guide/package.json`, `pnpm-lock.yaml`, `capture.mjs` — 생성 완료

### 실행한 검증
- `pnpm --dir ui run build` — PASS
- Playwright Chromium 설치 및 `pnpm --dir tools/screenshot-guide run capture` — PASS
- PNG 4개 파일 크기 및 dimensions 확인 — PASS(모두 0 bytes 아님)
- HTML의 `screenshots/*.png` 상대 경로 존재 여부 확인 — PASS
- 개발 서버 종료 확인 — PASS

### 변경 범위
- `ui/` 앱 소스 코드는 수정하지 않았다.
- `docs/current-status.md`는 수정하지 않았다.
- 커밋, 푸시, PR은 생성하지 않았다.

---

## 2026-09-21 — SF-4: 투자자 등록 화면 완료

### 완료 내용
- `features/investor-register/InvestorRegisterForm.tsx` — `react-hook-form` 기반 폼, displayName(필수, 1-50자)/relationship(선택, select) 유효성 검사, 로딩 상태(버튼 비활성화+스피너 텍스트), 성공 시 `/dashboard` 리디렉션 (`useNavigate`), 400/네트워크 오류 인라인 메시지 표시(`aria-live="polite"`), `<form noValidate>` 적용
- `features/investor-register/useRegisterInvestor.ts` — TanStack Query `useMutation` 래핑, `POST /investors` 호출(camelCase: `displayName`, `relationship`), `shared/api/client.ts` 재사용 (`credentials: 'include'`)
- `features/investor-register/InvestorRegisterForm.test.tsx` — RED→GREEN: 정상 등록, 빈 displayName 클라이언트 검증, 50자 초과 클라이언트 검증, 유효하지 않은 relationship API 400 응답, 네트워크 오류, relationship 미선택(빈 값 전송), 로딩 상태 버튼 비활성화 — 7개 시나리오 실제 검증 (QueryClientProvider 감싸기 + fireEvent 입력/제출)
- `pages/InvestorRegisterPage/InvestorRegisterPage.tsx` + `.module.css` — 기존 플레이스홀더 페이지에 폼 연동
- `App.tsx`에 `/investors/register` 라우트 이미 존재 확인
- `shared/lib/validators.ts`에 `isValidDisplayName` 추가 (1-50자 검증)
- CSS Modules 사용, 2칸 들여쓰기, ESLint/Prettier/tsc strict 준수
- 반응형 브레이크포인트(640px/768px/1024px) CSS 미디어 쿼리 적용

### Master 독립 검증에서 확인한 사항(이전 사고 방지)
- `<form>`에 `noValidate` 필수 — 브라우저 네이티브 검증이 커스텀 한글 에러 메시지를 막는 버그 재발 방지
- 테스트는 초기 렌더링 텍스트만 확인하지 않고 `fireEvent`로 실제 입력/제출 시뮬레이션하여 각 시나리오 검증
- `npm run typecheck`만 통과해도 `npm run build`(`tsc -b`)에서 타입 오류가 잡힐 수 있으므로 둘 다 필수 실행
- select 필드의 유효하지 않은 값 클라이언트 검증은 UI상 불가능(옵션만 선택 가능)하므로 API 400 응답 매핑 테스트로 대체

### 실행한 명령어 및 결과
| 명령어 | 결과 |
|---|---|
| `pnpm run lint` | PASS (0 errors) |
| `pnpm run typecheck` | PASS (0 errors) |
| `pnpm run format:check` | PASS (All matched files use Prettier code style!) |
| `pnpm run test` | PASS (7 tests, 총 20 tests) |
| `pnpm run build` | PASS (dist/ 생성, 88 modules transformed) |
