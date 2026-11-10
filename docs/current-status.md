# 프로젝트 현재 상황 (Master 소유)

갱신: 2026-11-10.

## CI 실패·의존성 보완 완료 (#496)

- 사용자 지시 "워커 돌리지말고 너가해"에 따라 이번 수정은 Codex가 직접 수행했다. 새 워커·Codex 하위 세션·watchdog은 만들지 않았다.
- PR #497을 원격 CI 성공 확인 후 main에 병합했다(e6b89a7d). #496 CLOSED. 수정 커밋81cb42a1/f63c7d1b 및 병합 내용을 push했고 로컬 main을 동기화했다. 기존 .playwright-cli/ 산출물은 보존한다.
- backend는 Clock spy에 나노초 시각을 넣어 기존 CI 실패2건을 재현했다. 생성 응답 .123456789와 DB 왕복 .123457을 잘못 비교하던 테스트를 DB 저장값 기준으로 고쳤다. 시계를1초 진행하고 응답·DB 양쪽의 createdAt/updatedAt 불변을 정확히 확인한다. 제품 backend 로직은 수정하지 않았다.
- UI 투자자 전환 버튼에 aria-label을 명시해 이름의 공백 불일치를 해결했다. 기존 포맷 미준수6파일은 Prettier로만 정리했다.
- jsdom의 undici8.12.4와 typescript-estree 경로에 한정한 minimatch11.0.5로 갱신했다. 부모가 minimatch10.x만 허용해 좁은 override가 필요하며, 기존 minimatch3.x 경로와 직접 런타임 의존성은 유지한다.

| 검증 | 결과 | 근거 |
|---|---|---|
| backend 실패 재현 | PASS | 나노초 Clock으로 기존 두 실패를 로컬에서 재현 |
| backend 전체 테스트 | PASS | 슬롯4, 1,138건, failures/errors/skipped0 |
| backend Checkstyle/Spotless/build | PASS | main/test 위반0, 최종 명령 실행 |
| UI 전체 테스트 | PASS | 107파일876건, 기존2실패 해소 |
| UI lint/typecheck/format/build | PASS | 기존 lint 경고4·NotFoundPage import 경고는 유지, 오류0 |
| 의존성 audit/frozen install | PASS | 알려진 취약점9→0, lockfile 일치 |
| 전이 의존성 호환성 | PASS | 실제 설치 경로의 glob 양성·음성·숨김 파일 및 loopback HTTP 확인 |
| 원격 CI | PASS | run42721712150에서 UI quality·Backend quality 모두 SUCCESS, 검증된 f63c7d1b를 병합 |
| 운영 배포 | PASS | 2026-11-10 backend/UI 배포, 홈페이지·API HTTP 200 |
| Flyway 적용 버전 직접 조회 | NOT_RUN | 배포 후 DB 확인에 추가 sudo 인증이 필요해 수행하지 않음 |

근거: docs/work-log/ci-quality-repair.md, /tmp/ci-repair-* 로그.

## 이미 병합된 계좌 보유 등록

- #493 CLOSED, PR #495(backend)·#494(UI) MERGED. main의 이전 정산 커밋 d5725289.
- 증권사 체크로 계좌 보유를 등록하고 번호는 NULL로 저장한다. 나중에 같은 ID에 최초1회 번호를 입력하며 기존 번호 교체는 제공하지 않는다.
- 동시 등록·후입력 보호, 청약 이력 계좌 삭제 거부, 번호 없는 브로커 연결·동기화 차단을 유지한다. 기존 이미지 자동 입력은 유지했다.
- V42는 계좌번호 NOT NULL 제약만 제거한다. 기존 암호문·ID·참조를 보존한다. 2026-11-10 backend/UI 배포 후 backend 서비스 active 및 API HTTP 200을 확인했으며, Flyway 적용 버전은 DB에서 직접 조회하지 않았다.
- 이전 Run run_374c2a862662 워커·임시 작업공간·watchdog은 정리했다. 이번 보완에는 워커를 사용하지 않는다.

## 다음 단계

1. #496·PR #497 보완 완료. 활성 워커0이며 추가 검증 대기는 없다.
2. 로컬 보류 브랜치 `winter0123/listing-open-price-backend`와 `winter0123/sync-range-ui`는 이번 릴리스에 포함하지 않았다. Epic 21 백엔드 및 선행 백엔드가 필요한 UI 변경으로 별도 검토가 필요하다.
3. SimwonAmericaCorp. 철회·일정 정정은 연구 문서만 fc61626b로 병합했다. 공식 공모 건 식별·데이터 처리 결정 후 설계·구현 범위를 확정한다.

## 원격 빌드 경로 보완

- PR #497의 첫 CI는 backend PASS, UI build FAIL이었다. Mac 전용 ui/public/images 절대 링크를 ../../images 상대 링크로 수정한 뒤 두 번째 CI의 UI/backend 모두 PASS를 확인했다. 이미지 내용은 유지했다.
- 별도 임시 경로에 Git index를 아카이브한 새 소스로 offline frozen install/build를 실행하고 dist 아이콘·증권사 로고 포함까지 PASS 확인했다. 직접 수정·검증 후 CI 성공을 확인해 병합했으며, main의 backend/ui가 검증된 커밋과 동일함도 확인했다.
