# planning 작업 가이드

> **누가 읽는가**: ① Master가 planning 워커(조사·기획·설계·breakdown)의 task spec을 쓸 때, ② planning 워커가 작업 전에.
>
> 공통 규칙은 `AGENTS.md`(6.0절 구조) → `docs/project-agent-guide.md` → `docs/work-plan.md` → `docs/roadmap.md` → `docs/current-status.md` 순서로 먼저 읽는다. spec 형식은 `docs/worker-spec-template.md`를 따른다.
>
> **문서 작업 워커에게 특히 주의**: "append하라"고 지시했는데 워커가 기존 본문을 삭제한 사고가 있었다. 병합 전 `git diff --stat`으로 삭제된 줄 수를 반드시 확인한다.

## 1. 조사·기획·설계 규칙

- Phase 0 문서는 `research` → `spec` → `architecture` 순서로 작성한다. 모두 실제 근거·링크·미결정 사항을 포함해야 한다.
- 이미 Phase 0가 끝난 Epic이라도 구현 직전에는 `breakdown-research`와 `breakdown`으로 SF 범위와 API 계약 드리프트를 재확인한다.
- Test는 AC별 정상·실패·권한·경계 조건을 테스트 케이스로 만든다. QA는 프로젝트에 존재하는 실제 명령과 브라우저 증적으로만 PASS를 기록한다.
- 근거 없는 API 계약이나 화면 구조를 지어내지 않는다 — spec/architecture 문서에 없는 내용은 "미정, 확인 필요"로 표시하고 사용자 결정 대기로 남긴다.

## 2. 선행 큐 운영

planning은 backend/frontend의 완료를 기다리지 않는다. backend 또는 frontend가 Epic N을 구현 중이면 planning은 의존성을 확인한 뒤 최소 Epic N+1, 가능하면 N+2의 `breakdown`·spec 보강·엣지 케이스 검토를 진행해 다음 구현 작업을 준비한다.

신규 계획 문서가 바닥나면 다음으로 큐를 채운다:
- 기존 breakdown의 드리프트 검토(선행 Epic이 실제로 병합된 뒤 가정이 여전히 유효한지 재확인)
- 프론트엔드 사용자 가이드(아래 4절)
- QA 증적처럼 충돌 없는 산출물

비워 둘 수밖에 없다면 사유와 재평가 조건을 work-log에 남긴다.

**문서 append 작업 주의**: 기존 breakdown 문서에 섹션을 추가할 때 "append"로 지시받으면 반드시 append만 한다. 병합 전 `git diff --stat`으로 삭제된 줄 수를 확인해 기존 본문이 실수로 삭제되지 않았는지 검증한다(과거 실제 사고 있었음).

## 3. Epic 재분해(SF) 작성 형식

각 SF에는 범위, 비범위, Acceptance Criteria, 선행 SF, 산출물 파일 경로, 특이사항을 명시한다. 직전 Epic의 breakdown 문서 형식(목차 구조, SF 개요 표, E2E 매핑, 구현 순서 가이드)을 그대로 따른다.

## 4. 프론트엔드 사용자 가이드

프론트엔드 SF가 병합되면 planning 작업공간에서 실제 화면을 띄워 데스크톱 1280px·모바일 390px 스크린샷을 만든다. `docs/user-guides/[epic-slug]/`에 스크린샷과 외부 CDN 없는 순수 HTML 안내서를 추가하고 일반 PR로 병합한다.

휴대폰에서 볼 공유 링크가 필요하면 Master가 별도 Artifact를 발행하고 링크를 Hermes로 보낸다. Artifact 발행은 외부 공개 행위이므로 사용자 또는 설정된 권한 범위 안에서만 수행한다.
