# frontend 작업 가이드

> **누가 읽는가**: ① Master가 frontend 워커의 task spec을 쓸 때(검증 명령을 **여기서 그대로 복사해** spec에 넣는다 — `typecheck`만이 아니라 `build`까지 포함해야 한다), ② frontend 워커가 작업 전에.
>
> 공통 규칙은 `AGENTS.md`(6.0절 구조) → `docs/project-agent-guide.md` → `docs/roadmap.md` → `docs/current-status.md` 순서로 먼저 읽는다. spec 형식은 `docs/worker-spec-template.md`를 따른다.

## 1. 기술 기준

| 영역 | 기준 |
|---|---|
| Frontend | React 19, TypeScript, Vite, TanStack Query, React Hook Form, FSD 유사 구조 |
| 패키지 관리 | pnpm |

`frontend`는 소스 폴더가 아니라 Orca 작업공간 이름이다. 프론트엔드 앱 소스는 반드시 `ui/`에 둔다.

## 2. 검증 명령

실제 설정 파일(`ui/package.json` 등)을 먼저 읽고 다음 명령을 쓴다.

```bash
cd ui
pnpm run test
pnpm run lint
pnpm run typecheck
pnpm run format:check
pnpm run build
```

`typecheck`만으로 끝내지 않는다. `tsc -b`를 실행하는 `pnpm run build`도 필수다 — `typecheck`가 프로젝트 참조 하위의 타입 오류를 놓친 사례가 있었다.

## 3. 프론트엔드 구현 규칙

- `pnpm`만 사용한다. `pnpm-lock.yaml`을 커밋하며 `package-lock.json`을 만들지 않는다.
- ESLint, Prettier, TypeScript strict를 유지한다. 들여쓰기는 2칸, 탭 금지다.
- FSD 레이어는 반드시 `src/{app,pages,widgets,features,entities,shared}/`처럼 `src` 바로 아래 형제로 둔다. `entities`, `features`, `pages`, `shared`, `widgets`를 `src/app/` 아래에 중첩하지 않는다.
- 의존 방향은 `app → pages → widgets → features → entities → shared`다. 하위 레이어가 상위 레이어를 참조하거나 순환 참조하지 않는다.
- 서버 상태는 TanStack Query, UI 상태는 지역 상태 또는 꼭 필요한 전역 상태로 구분한다. `useEffect`를 파생 상태·단순 계산·이벤트 처리 대체 수단으로 남용하지 않는다.
- 모든 화면은 처음부터 모바일 웹을 지원한다. 터치 대상은 최소 44×44px, `<meta name="viewport" content="width=device-width, initial-scale=1">` 유지, 가로 스크롤 금지, 640px/768px/1024px에서 레이아웃을 확인한다.
- 화면 작업은 로딩·오류·빈 상태, 키보드 접근성, 의미 있는 컴포넌트·통합·E2E 테스트를 함께 다룬다.

## 4. 공용 인프라 재사용 (Epic 13/14에서 확보)

- `ui/src/shared/api/client.ts`의 `apiFetch` — 4xx/5xx 응답의 JSON body에서 message 필드를 읽어 그대로 전달한다(Issue #134 수정 완료). Register 폼의 `getFormErrorMessage()` 조건문이 이미 "서버 메시지가 있으면 그대로 표시" 구조라 대부분 추가 수정이 필요 없다 — 새 폼 작성 시 이 패턴을 그대로 따른다.
- `ui/src/shared/components/ConfirmDialog/` — 기존 재사용, 중복 생성 금지.
- Epic 14의 청약 목록 화면 패턴 — 백엔드 API가 아직 없을 때 계약(spec/architecture 문서)만으로 프론트를 먼저 구현하고 mock으로 검증한 뒤, 실제 백엔드가 나오면 통합하는 방식. 프론트/백엔드가 서로 다른 Epic 순서로 진행될 때 병목을 줄이는 데 재사용 가능.
