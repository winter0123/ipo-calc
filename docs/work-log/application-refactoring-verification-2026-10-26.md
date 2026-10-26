# 애플리케이션 누적 리팩토링 검증

## 2026-10-26 — 최종 검증

### UI

- `pnpm run test`: PASS — 96 test files, 730 tests.
- `pnpm run lint`: PASS.
- `pnpm run typecheck`: PASS.
- `pnpm run format:check`: PASS.
- `pnpm run build`: PASS.

### Backend

- `.envrc`의 로컬 개발 설정과 격리 DB 슬롯 `ipo_slot_4` 사용.
- `./gradlew test --rerun`: PASS — 124 test suites, 1,099 tests, failures/errors/skipped 0.
- `./gradlew checkstyleMain checkstyleTest --rerun-tasks -q`: PASS.
- `./gradlew spotlessCheck`: PASS.
- `./gradlew build`: PASS.

### Diff 확인

- `git diff --check`: PASS.

### 검증 중 수정

- 첫 UI 전체 테스트에서 발견한 새 API 테스트의 Response 본문 재사용, IPO query key mock 누락, allocation response fixture의 `deletedAt` 누락을 수정했다.
- lint에서 발견한 `useSaleForm`의 unused `won` import를 제거했다.
- Prettier/Spotless가 각각 지적한 Sale API 테스트와 Announcement service helper 포맷을 수정했다.
- 수정 뒤 UI 전체 품질 명령과 backend 전체 build를 다시 완료했다.

### 실행하지 않은 검증

- GitHub Actions hosted runner: `NOT_RUN`.
- 브라우저 수동/E2E: `NOT_RUN`.
- 투자내역 100/500/1,000건 성능 기준 측정: `NOT_RUN`.
