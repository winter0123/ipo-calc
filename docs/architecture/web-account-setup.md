# 아키텍처 설계: 웹 계정 설정 (Web Account Setup)

> **프로젝트**: React + TypeScript, FSD 아키텍처(app→pages→widgets→features→entities→shared), TanStack Query
> **범위**: 프론트엔드 아키텍처 설계 (4개 화면: 로그인/회원가입/투자자등록/증권계좌등록)
> **의존 문서**: `docs/spec/web-account-setup.md`, `docs/spec/investor-management.md`, `docs/spec/securities-account-management.md`, `docs/spec/securities-firm-catalog.md`
> **API 계약**: D-W13-001

---

## FSD 레이어 구조 및 폴더 배치

### 레이어 구조 개요

```
app/
├── pages/
├── widgets/
├── features/
├── entities/
└── shared/
```

FSD 원칙에 따라 하위 레이어가 상위 레이어를 참조하지 않으며, `shared`는 특정 도메인에 의존하지 않는다. 순환 참조를 금지한다.

### pages 레이어 — 화면 배치

4개 화면을 `pages` 레이어에 경로별로 배치한다:

```
app/pages/
├── LoginPage/
│   └── LoginPage.tsx          # /login
├── SignupPage/
│   └── SignupPage.tsx         # /signup
├── InvestorRegisterPage/
│   └── InvestorRegisterPage.tsx  # /investors/register
└── SecuritiesAccountRegisterPage/
    └── SecuritiesAccountRegisterPage.tsx  # /securities-accounts/register
```

- 각 페이지 컴포넌트는 선언적 UI 표현에만 집중하며, 비즈니스 로직을 누적하지 않는다.
- 페이지 간 네비게이션은 TanStack Query의 mutation 성공 콜백 또는 라우터 네비게이션으로 처리한다.
- `InvestorRegisterPage`와 `SecuritiesAccountRegisterPage`는 인증 상태가 필요하므로, 인증 미확인 시 `LoginPage`로 리다이렉트하는 로직을 페이지 레벨에서 처리하지 않고, 라우터 가드(또는 401 인터셉터)가 담당한다.

### features 레이어 — 기능 단위 컴포넌트

각 화면의 폼과 상호작용 로직을 `features` 레이어에 기능 단위로 분리한다:

```
app/features/
├── login/
│   ├── LoginForm/
│   │   ├── LoginForm.tsx
│   │   └── LoginForm.module.css  # 또는 스타일링 방식에 맞는 파일
│   └── useLogin.ts              # 로그인 mutation 훅
├── signup/
│   ├── SignupForm/
│   │   ├── SignupForm.tsx
│   │   └── SignupForm.module.css
│   └── useSignup.ts             # 회원가입 mutation 훅
├── investor-register/
│   ├── InvestorRegisterForm/
│   │   ├── InvestorRegisterForm.tsx
│   │   └── InvestorRegisterForm.module.css
│   └── useInvestorRegister.ts   # 투자자 등록 mutation 훅
└── securities-account-register/
    ├── SecuritiesAccountRegisterForm/
    │   ├── SecuritiesAccountRegisterForm.tsx
    │   ├── SecuritiesAccountRegisterForm.module.css
    │   └── AccountNumberInput.tsx  # 하이픈 자동 삽입 컴포넌트
    ├── useSecuritiesAccountRegister.ts  # 증권계좌 등록 mutation 훅
    └── useSecuritiesFirms.ts     # 증권사 목록 조회 query 훅
```

- `LoginForm`, `SignupForm`, `InvestorRegisterForm`, `SecuritiesAccountRegisterForm`은 각각 해당 화면의 폼 UI와 제출 로직을 캡슐화한다.
- `AccountNumberInput`은 증권계좌 등록 전용으로, 입력 시 하이픈 자동 삽입을 담당한다.
- `useLogin`, `useSignup`, `useInvestorRegister`, `useSecuritiesAccountRegister`는 TanStack Query의 `useMutation`을 래핑한 커스텀 훅이다.
- `useSecuritiesFirms`는 `useQuery`로 증권사 목록을 조회한다.
- `widgets` 레이어는 이 프로젝트의 4개 화면이 단일 폼 중심이기 때문에 별도 분리가 필요하지 않다면 `features`에 직접 배치한다. 복잡한 복합 UI 위젯이 생기는 시점에 `widgets`로 분리 여부를 재검토한다.

### entities 레이어 — 도메인 타입 및 API 클라이언트

```
app/entities/
├── account/
│   ├── types.ts                 # Account 타입 정의
│   └── api-client.ts            # Account 관련 API 호출 래퍼
├── investor/
│   ├── types.ts                 # Investor 타입 정의 (display_name, relationship 등)
│   └── api-client.ts            # Investor 관련 API 호출 래퍼
├── securities-account/
│   ├── types.ts                 # SecuritiesAccount 타입 (accountNumber 마스킹 포함)
│   └── api-client.ts            # SecuritiesAccount 관련 API 호출 래퍼
└── securities-firm/
    ├── types.ts                 # SecuritiesFirm 타입 (드롭다운 옵션용)
    └── api-client.ts            # SecuritiesFirm 조회 API 래퍼
```

- 각 엔티티 폴더는 해당 도메인의 타입 정의와 API 클라이언트를 포함한다.
- `types.ts`는 API 응답 구조와 일치하는 TypeScript 인터페이스/타입을 정의한다.
- `api-client.ts`는 `fetch`/`axios` 래퍼를 사용한 API 호출 함수를 정의하며, `features` 레이어에서 호출한다.
- entities는 `features`에서만 참조하고, `features`는 `entities`만 참조한다. `pages`는 `features`와 `entities`를 참조할 수 있다.

### shared 레이어 — 공통 컴포넌트 및 유틸

```
app/shared/
├── components/
│   ├── Button/
│   │   ├── Button.tsx
│   │   └── Button.module.css
│   ├── Input/
│   │   ├── Input.tsx
│   │   └── Input.module.css
│   ├── FormField/
│   │   ├── FormField.tsx        # label-input-error 연결 캡슐화
│   │   └── FormField.module.css
│   ├── ErrorMessage/
│   │   ├── ErrorMessage.tsx     # aria-live="polite" 적용
│   │   └── ErrorMessage.module.css
│   └── Spinner/
│       ├── Spinner.tsx
│       └── Spinner.module.css
├── lib/
│   ├── format-hyphen.ts         # 계좌번호 하이픈 자동 삽입 유틸 함수
│   └── validators.ts            # 공통 유효성 검증 함수(이메일 regex 등)
└── api/
    ├── client.ts                # fetch/axios 래퍼 (credentials: 'include' 설정)
    └── interceptors.ts          # 401 인터셉터 등 HTTP 인터셉터 설정
```

- `FormField`는 `<label>`, `<input>`, 에러 메시지를 하나의 컴포넌트로 캡슐화하며, `aria-invalid`, `aria-describedby` 속성을 자동으로 연결한다.
- `ErrorMessage`은 `aria-live="polite"`를 적용하여 스크린리더가 에러를 공지하도록 한다.
- `format-hyphen.ts`는 `accountNumber` 입력 시 4자리마다 하이픈을 삽입하는 순수 함수이다.
- `validators.ts`는 이메일 형식 검증, 비밀번호 길이 검증 등 공통 유효성 검증 함수를 포함한다.
- `shared/api/client.ts`에는 `credentials: 'include'`가 설정되어 있어, 모든 API 요청에 자동으로 JSESSIONID 쿠키가 포함된다.
- `shared/api/interceptors.ts`에는 401 응답 시 로그인 페이지로 리다이렉트하는 전역 인터셉터가 설정된다.
- `shared/components/Button`은 비활성화 상태(loading)와 에러 상태를 지원한다.

---

## TanStack Query 사용 설계

### API 호출 구성

각 API를 TanStack Query의 `useQuery` 또는 `useMutation`으로 구성한다:

| 기능 | 훅 | 타입 | 비고 |
|------|-----|------|------|
| 로그인 | `useLogin` | `useMutation` | `POST /auth/login` |
| 회원가입 | `useSignup` | `useMutation` | `POST /accounts` |
| 투자자 등록 | `useInvestorRegister` | `useMutation` | `POST /investors` |
| 증권계좌 등록 | `useSecuritiesAccountRegister` | `useMutation` | `POST /securities-accounts` |
| 증권사 목록 조회 | `useSecuritiesFirms` | `useQuery` | `GET /securities-firms` |

### useMutation 설계 방향

- **로그인/회원가입/투자자등록/증권계좌등록**: 모두 `useMutation`으로 구성한다.
- `mutationFn`: `app/entities/[도메인]/api-client.ts`의 API 호출 함수를 직접 전달한다.
- `onSuccess`: 성공 시 해당 화면의 리다이렉션 처리와 캐시 무효화를 수행한다.
- `onError`: 에러 응답(400, 401, 404, 409)을 필드별 에러 메시지로 매핑하여 폼 상태에 설정한다.
- `onSettled`: 로딩 상태 초기화(스피너 숨김, 버튼 활성화 복원).

### 캐시 무효화 및 페이지 이동 처리

- **회원가입 성공 후**: 백엔드가 가입 트랜잭션에서 계정 소유의 본인 투자자를 함께 생성한다. 승인된 계정은 자동 로그인 후 `/dashboard`로 이동하고, 승인이 필요한 계정은 승인 안내를 표시한다. 추가 가족 투자자만 `/investors/register`에서 등록한다.
- **증권계좌 등록 성공 후**: `useSecuritiesAccountRegister`의 `onSuccess`에서 대시보드(`/dashboard`)로 이동한다. 증권계좌 목록이 표시되는 다른 화면이 있다면 해당 쿼리를 무효화한다.
- **투자자 등록 성공 후**: `useInvestorRegister`의 `onSuccess`에서 대시보드(`/dashboard`)로 이동한다. 투자자 목록 쿼리 무효화는 별도 화면이 존재할 경우에만 수행.
- **증권사 목록 조회**: `useSecuritiesFirms`는 `GET /securities-firms`를 `useQuery`로 실행하며, `staleTime`을 적절히 설정하여 증권계좌 등록 화면 진입 시 한 번만 조회되도록 한다.

### 인증 상태 관리 방향

- **세션 쿠키 기반**: 서버가 `JSESSIONID` 쿠키를 설정하므로, 프론트엔드는 별도 토큰 스토어를 유지하지 않는다.
- **세션 확인**: `/accounts/me` 같은 확인 API가 존재하는 경우 `useQuery`로 세션 상태를 확인한다. 존재하지 않는 경우, `shared/api/interceptors.ts`의 401 인터셉터가 전역으로 처리한다.
- **401 인터셉터**: `shared/api/client.ts`에서 설정된 HTTP 인터셉터가 401 응답을 감지하면, `router.push('/login')`로 리다이렉트한다. 이때 현재 URL을 리다이렉트 파라미터로 보존하여 로그인 성공 후 원래 페이지로 돌아갈 수 있도록 한다.
- **로딩 상태**: 증권계좌 등록 화면 진입 시 `useSecuritiesFirms`의 `isLoading` 상태로 드롭다운 로딩을 표시한다.

### mutation 성공/실패 시 처리

- **성공**: `onSuccess` 콜백에서 라우터 이동을 수행하고, 해당 mutation에 의해 영향을 받는 쿼리 캐시를 `invalidateQueries`로 무효화한다.
- **실패**: `onError` 콜백에서 API 응답의 에러 필드를 폼 상태에 매핑한다. 네트워크 오류는 전체 폼 에러로 표시한다.
- **취소**: `useMutation`의 `onSettled`에서 항상 로딩 상태를 해제한다.

---

## 폼 상태 관리

### 폼 라이브러리 사용 판단

- **실제 설치 여부 확인 후 결정**: `react-hook-form`, `formik` 등 폼 라이브러리가 프로젝트에 설치되어 있는지 `package.json`을 확인한 뒤 결정한다.
- **설치된 경우**: `react-hook-form`을 우선 사용한다. 타입 안전성이 높고, Zod와의 연동으로 서버 검증 규칙과 프론트 검증을 일치시킬 수 있다.
- **미설치 경우**: `controlled input` + `useState`로 간단한 폼을 구현한다. 4개 화면 모두 필드 수가 2~4개로 적으므로, 별도 폼 라이브러리 없이도 충분하다. 이 경우 각 `features` 폴더에서 `useState`로 폼 상태를 관리하고, 제출 시 `entities/[도메인]/api-client.ts`를 호출하여 서버 유효성 검증을 수행한다.
- **결정 근거**: 프로젝트의 복잡성과 기존 의존성을 먼저 평가한 뒤, 과도한 추가는 최소화한다.

### 하이픈 자동 삽입 로직 위치

- **위치**: `app/shared/lib/format-hyphen.ts`에 순수 함수로 분리한다.
- **함수 시그니처**: `(value: string): string` — 입력된 숫자 문자열을 받아 4자리마다 하이픈을 삽입한 문자열을 반환한다.
- **동작**: `accountNumber` 입력 필드에서 `onChange` 이벤트가 발생할 때, 입력값에서 숫자만 추출한 뒤 `formatHyphen` 함수를 호출하여 포맷된 값을 설정한다.
- **테스트**: `shared/lib/format-hyphen.ts`는 순수 함수이므로 별도 유닛 테스트를 작성하여 `0000000000000000` → `0000-0000-0000-0000` 변환을 검증한다.
- **마스킹 제외**: 서버가 이미 마스킹된 계좌번호를 응답으로 보내면 프론트는 그대로 표시하며, 추가 마스킹 로직을 구현하지 않는다.

---

## 접근성/반응형 구현 방향

### WCAG 2.1 AA 준수

- **공통 컴포넌트 설계**: `shared/components/FormField`가 `<label>`-`<input>`-에러 메시지를 자동 연결한다. `htmlFor`/`id` 속성을 일치시키고, `aria-invalid`, `aria-describedby`를 자동 설정한다.
- **에러 메시지**: `shared/components/ErrorMessage`은 `aria-live="polite"`를 적용하여 스크린리더가 에러를 공지하도록 한다.
- **포커스 관리**: 에러 발생 시 첫 번째 에러 필드로 자동 포커스를 이동한다. 구현 시점에 확인하되, `shared/lib`에 포커스 유틸 함수를 분리할지 또는 각 폼 컴포넌트에서 직접 처리할지 결정한다.
- **색상만으로 전달하지 않음**: 에러 필드에 아이콘(⚠)과 텍스트 에러 메시지를 함께 표시한다.
- **키보드 네비게이션**: Tab, Enter, Escape 키 지원. `Button` 컴포넌트는 `onClick`과 `onKeyDown` 모두를 처리한다.
- **검증 도구**: `axe-core`를 사용하여 WCAG 2.1 AA 준수를 검증한다.

### 반응형 브레이크포인트

- **CSS 미디어 쿼리 또는 CSS-in-JS/Tailwind 등**: 프로젝트에 설치된 스타일링 방식을 `package.json`에서 확인한 뒤 결정한다.
  - 설치된 경우 해당 방식의 반응형 패턴을 사용한다 (예: Tailwind의 `sm:`, `md:`, `lg:` 접두사, 또는 CSS-in-JS의 미디어 쿼리).
  - 미설치 경우 CSS 미디어 쿼리를 `*.module.css` 파일에 작성한다.
- **브레이크포인트**: `@media (min-width: 640px)`, `@media (min-width: 768px)`, `@media (min-width: 1024px)` — 모바일 우선(mobile-first) 접근.
- **레이아웃**:
  - 모바일(640px): 폼 전체 폭 100%, 단일 컬럼
  - 태블릿(768px): 2열 레이아웃 가능(필드 그룹화)
  - 데스크톱(1024px): 전체 폭 제한, 양쪽 여백 확보

---

## 세션 인증 처리

### fetch/axios wrapper에 credentials 설정 위치

- **설정 위치**: `app/shared/api/client.ts` — 이 파일에서 HTTP 클라이언트를 생성할 때 `credentials: 'include'`를 설정한다.
- **구성**:
  - `fetch` 기반이라면: `fetch(url, { credentials: 'include', headers: { 'Content-Type': 'application/json' } })` 래퍼 함수를 정의한다.
  - `axios` 기반이라면: `axios.create({ withCredentials: true })`로 인스턴스를 생성한다.
- **설치 확인**: `package.json`에서 `axios`가 설치되어 있는지 확인하고, 있으면 axios를 사용하고 없으면 네이티브 `fetch` 래퍼를 사용한다. 어느 쪽이든 `credentials: 'include'`/`withCredentials: true`는 반드시 설정한다.

### 401 응답 시 전역 인터셉터

- **인터셉터 위치**: `app/shared/api/interceptors.ts`
- **동작**: HTTP 클라이언트의 응답 인터셉터에서 401 상태 코드를 감지하면 `router.push('/login')`로 리다이렉트한다.
- **리다이렉트 URL 보존**: 현재 URL을 `sessionStorage` 또는 `router.state`에 저장하여, 로그인 성공 후 `redirect` 파라미터로 원래 페이지로 돌아간다.
- **쿠키 직접 조작 금지**: 프론트엔드는 `JSESSIONID` 쿠키를 직접 다루지 않는다. 브라우저의 기본 쿠키 처리(`credentials: 'include'`)에 위임한다.
- **로그아웃**: 서버가 쿠키를 무효화하도록 요청하고, 프론트는 라우터로 `/login`으로 이동한다.

---

## 모듈 소유권 (FSD 레이어별 폴더 경로)

| FSD 레이어 | 폴더 경로 | 소유 콘텐츠 | 의존 관계 |
|---|---|---|---|
| **app** | `app/` | 최상위 진입점, 레이아웃 | pages, widgets, features, entities, shared 모두 참조 가능 |
| **pages** | `app/pages/*Page/` | 화면 컴포넌트 (LoginPage, SignupPage, InvestorRegisterPage, SecuritiesAccountRegisterPage) | features, entities, shared 참조 |
| **widgets** | `app/widgets/` | 복합 UI 위젯 (해당 시) | features, entities, shared 참조 |
| **features** | `app/features/*/` | 폼 컴포넌트, 커스텀 훅 (LoginForm, useLogin 등) | entities, shared 참조 |
| **entities** | `app/entities/*/` | 도메인 타입, API 클라이언트 | shared 참조 |
| **shared** | `app/shared/` | 공통 컴포넌트, 유틸 함수, API 클라이언트, 인터셉터 | 없음 (독립) |

### 의존성 규칙

- `pages` → `features`, `entities`, `shared` 참조 가능
- `features` → `entities`, `shared` 참조 가능
- `entities` → `shared` 참조 가능
- `shared` → 다른 레이어 참조 **불가**
- 하위 레이어가 상위 레이어를 참조하지 않는다 (순환 참조 금지)

---

## 의존성 (실제 설치 여부 확인 필요 항목 명시)

다음 라이브러리들이 프로젝트에 설치되어 있는지 `package.json`을 확인한 뒤 사용 여부를 결정한다:

| 라이브러리 | 용도 | 확인 방법 | 미설치 시 대안 |
|---|---|---|---|
| **react-hook-form** | 폼 상태 관리 | `package.json` 확인 | `useState` + controlled input |
| **axios** 또는 **fetch** | HTTP 클라이언트 | `package.json` 확인 | 네이티브 `fetch` 래퍼 |
| **@tanstack/react-query** | 서버 상태 관리 | `package.json` 확인 — spec에서 TanStack Query 명시되어 있으므로 설치되어 있을 것으로 예상 | — |
| **react-router-dom** | 라우팅 | `package.json` 확인 | — |
| **Tailwind CSS** / **CSS Modules** / **CSS-in-JS** | 스타일링 | `package.json` 및 `postcss.config` 등 확인 | CSS Modules (`*.module.css`) |
| **axe-core** | 접근성 검증 | `package.json` 확인 | — |
| **zod** (선택) | 폼 검증 | `package.json` 확인 | `validators.ts`에 직접 구현 |

- 위 항목 중 설치가 확인되지 않는 라이브러리는 해당 섹션에서 "확인 후 결정" 방향으로 설계를 조정한다.
- 프로젝트에 이미 설치된 버전의 공식 권장 API를 우선 사용한다 (AGENTS.md 14장 참고).

---

## 리스크

| 리스크 | 설명 | 완화 방안 |
|---|---|---|
| **폼 라이브러리 미설치** | react-hook-form이 설치되어 있지 않으면 각 폼별로 `useState` 기반 폼을 직접 구현해야 하며, 이 경우 boilerplate가 증가한다 | `package.json` 확인 후 즉시 결정. 미설치 시 4개 폼 모두 동일한 패턴으로 통일하여 관리 |
| **스타일링 방식 불확정** | Tailwind/CSS-in-JS/CSS Modules 중 어떤 방식을 사용할지 모르면 컴포넌트 스타일링이 지연될 수 있음 | `package.json`과 설정 파일 확인 후 즉시 결정. CSS Modules가 기본 확정 옵션 |
| **라우팅 라이브러리 부재** | react-router-dom이 없으면 네비게이션 구현이 달라진다 | `package.json` 확인. 설치되지 않면 네이티브 History API 또는 다른 라우팅 솔루션 사용 |
| **401 인터셉터와 라우터 의존성** | `shared/api/interceptors.ts`가 `router`(react-router-dom)를 참조하면 `shared` 레이어가 `pages`에 간접 의존하게 되어 FSD 규칙 위반 | 인터셉터에서 리다이렉트 로직을 `shared`에 두지 않고, 에러 상태를 반환한 뒤 `pages`/`features` 레벨에서 라우터 이동을 처리하거나, 라우터를 독립적 유틸로 분리 |
| **세션 상태 동기화** | `credentials: 'include'`로 쿠키가 자동 전송되지만, 브라우저 간 탭 간 세션 동기화가 필요할 수 있음 | `storage` 이벤트 리스너나 `navigator.heartbeat` 등으로 탭 간 세션 상태를 동기화하는지 여부를 추후 결정 |
| **하이픈 포맷터 Edge Case** | 사용자가 백스페이스로 중간 문자를 삭제하거나 붙여넣기를 했을 때 하이픈 위치가 어긋날 수 있음 | `format-hyphen` 함수에 모든 경우를 처리하고, 유닛 테스트로 커버. 입력값에서 숫자만 추출 후 재포맷하는 방식으로 일관성 유지 |
| **최초 투자자 ID 전파** | 회원가입 시 생성되는 본인 투자자의 ID를 어떻게 확보할지 | 별도 전달 불필요. 로그인 후 `/investors` 목록을 조회해 계정 소유 투자자를 사용한다. |
| **증권사 목록 조회 실패** | `GET /securities-firms` 실패 시 증권계좌 등록 화면이 로딩 상태에 머물 수 있음 | `useSecuritiesFirms`의 `onError`에서 에러 메시지 표시 및 재시도 옵션 제공 |
| **FSD 레이어 간 순환 참조** | `features`가 `shared`의 `router` 유틸을 참조하거나, `entities`가 `features`를 참조하는 순환 발생 가능 | AGENTS.md FSD 규칙 엄격 적용. 의존성 방향을 수시로 점검 |

---

## 참고

- **API 계약**: D-W13-001에 명시된 확정된 계약만 사용하며, 추가 경로를 추측하지 않는다.
- **프로젝트 아키텍처**: React + TypeScript, FSD(Feature-Sliced Design), TanStack Query (AGENTS.md 14장 참고)
- **코드 스타일**: ESLint + Prettier + `tsc --strict`, 2칸 들여쓰기, 탭 금지
- **백엔드**: Java 21, Spring Boot, PostgreSQL (프론트엔드 아키텍처 문서이므로 백엔드 레이어 설계는 포함하지 않음)
