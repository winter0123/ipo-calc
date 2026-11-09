# CI 실패·개발 의존성 정리 (#496)

## 범위와 실행 방식 (2026-11-10)

사용자의 "워커 돌리지말고 너가해" 지시에 따라 Codex가 직접 재현·수정·검증한다. 워커·추가 Codex 세션은 만들지 않는다. 제품 요구사항·운영 DB·배포는 변경하지 않는다.

## 원인과 수정

- backend CI 실패 2건: 생성 응답의 나노초 시각과 PostgreSQL의 마이크로초 반올림 저장값을 문자열로 비교했다. `Clock` spy로 `.123456789` 입력을 주고 다음 요청 전 시계를 1초 진행해 로컬에서도 두 실패를 재현했다(expected `.123456789`, actual `.123457`). 비교 기준을 실제 DB 저장값으로 바꿨으며 응답·DB 양쪽의 `updatedAt`/`createdAt` 정확한 불변 검증은 유지했다. 백엔드 제품 로직은 수정하지 않았다.
- UI 기존 실패 2건: 전환 버튼의 접근성 이름이 `현재 투자자:김철수`로 합쳐져 테스트와 달랐다. 공통 `InvestorSwitcher` 버튼에 `aria-label`을 명시해 이름을 안정화했다. 기존 두 테스트가 실패하는 것을 확인한 뒤 7건 전체 통과를 확인했다.
- 포맷: 기존 AdminAnnouncementsPage 2파일·SecuritiesLogo 3파일·securitiesLogo.ts를 지정해 Prettier로만 정리했다.
- 취약점: `jsdom → undici` 8.11.2를 기존 허용 범위 안의 8.12.4로 갱신했다. `typescript-estree → minimatch` 10.2.6은 부모가 아직 10.x만 허용해 해당 경로에만 `^11.0.1` override를 적용했고 lock에는 11.0.5가 설치됐다. 기존 minimatch 3.x 경로는 유지한다. UI 런타임 직접 의존성은 추가하지 않았다.

minimatch 패치 범위는 [공식 보안 공지](https://github.com/advisories/GHSA-g3hv-75wg-m249)를 확인했다. Node 22를 사용하는 프로젝트와 갱신된 패키지의 엔진 조건이 호환된다. 설치된 전이 의존성 경로로 glob의 양성·음성·숨김 파일 처리 및 loopback HTTP 요청도 확인했다. 공격 PoC 대신 취약 버전 제거와 audit 재검사를 검증 근거로 사용한다.

## 검증

| 항목 | 결과 | 근거 |
|---|---|---|
| backend 실패 재현 | PASS | 나노초 시계로 2건 모두 예상 정밀도 차이에서 FAIL 관찰 |
| UI 실패 재현·수정 | PASS | 수정 전 2 FAIL/5 PASS → 수정 후 7 PASS |
| UI 전체 테스트 | PASS | 107파일876건, 실패0 |
| UI lint/typecheck/format/build | PASS | 오류0, lint 기존 경고4 및 기존 dynamic import 경고 유지 |
| 의존성 audit | PASS | 수정 전 high4/moderate5 → 알려진 취약점0 |
| 전이 의존성 호환성 | PASS | 실제 설치 경로의 minimatch·undici 동작 확인 |
| backend 전체 품질 검사 | NOT_RUN | 실행 중, 결과 확인 후 갱신 |
| 원격 CI | NOT_RUN | PR 생성 후 확인 예정, 통과 전 병합하지 않음 |
| 운영 DB·배포 | NOT_RUN | 이번 범위 밖 |

## 최종 로컬 검증

- backend 전체1,138건 PASS(failures/errors/skipped0), Checkstyle main/test 위반0 PASS, Spotless/build PASS. 최초 Spotless 검사에서 새 assertion 줄바꿈1건이 검출되어 해당 줄만 직접 정리했다. 전체 spotlessApply는 실행하지 않았다.
- UI 전체876건, lint/typecheck/format/build, frozen install 및 audit 모두 PASS. 알려진 취약점0.
- backend 제품 코드·기존 migration은 변경하지 않았다. 원격 CI는 아직 NOT_RUN이며 성공 확인 전 병합하지 않는다.

## 원격 1차 결과와 이미지 경로 보완

- run42720547292: backend 전체 품질 검사 PASS. UI 테스트·lint·typecheck·format PASS 후 build FAIL. 테스트 실패가 가려 두었던 기존 정적 자산 경로 문제다.
- ui/public/images가 /Users/joy/IdeaProjects/ipo-calc/images 절대 심볼릭 링크여서 Linux에서 ENOENT가 발생했다. 저장소의 ../../images 상대 링크로 바꿨다. 추적 중인 심볼릭 링크는 이 한 개이며 기존 이미지 파일은 수정하지 않았다.
- 로컬 build/format PASS. Git index를 아카이브해 별도의 임시 경로에 풀고 offline frozen install→build를 실행했다. 경로가 바뀌어도 아이콘·증권사 로고가 dist/images에 포함됨을 확인했다. 이식성 검증 PASS.
- 원격 CI는 새 커밋에서 다시 확인한다. 성공 전 병합하지 않는다.
