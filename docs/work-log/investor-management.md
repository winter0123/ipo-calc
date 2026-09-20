# Work Log: investor-management (Epic 2)

## 2026-09-20 — Phase 0 (조사/기획/설계)

### 완료 작업
- [x] `docs/research/investor-management.md` — Research 워커, Master 승인 (이전 세션에서 완료)
- [x] `docs/spec/investor-management.md` — Planning 워커(Nemotron 3 Super) 작성, Master 검증
- [x] `docs/architecture/investor-management.md` — Architecture 워커(Nemotron 3 Super) 작성, Master 검증

### Master 결정 사항 (architecture.md의 "결정 필요 사항" D-I-001~D-I-005에 대한 판단)

기술적 구현 세부사항이며 되돌리기 쉬운 초기 설계 수준이라 Master가 MVP 기준으로 결정. 사용자가 다른 의견이 있으면 구현 전에 언제든 변경 가능.

| ID | 항목 | 결정 | 근거 |
|---|---|---|---|
| D-I-001 | 투자자 식별자 타입 | **Long(IDENTITY), UUID 아님** | architecture.md 초안은 UUID를 제안했으나, 기존 Account 엔티티가 `@GeneratedValue(strategy = GenerationType.IDENTITY)`의 `Long id`를 사용 중(Epic 1 실제 구현 확인). 코드베이스 일관성을 위해 Investor도 동일 패턴 사용. 외부 노출 시 순차 ID 추측 위험은 계정 소유권 검증(모든 요청에서 account_id 일치 확인)으로 완화됨 |
| D-I-002 | 관계(relationship) 필드 필수 여부 | **선택(nullable)** | research.md 결론 그대로 — MVP에서는 표시 이름만으로 충분, 관계는 선택 |
| D-I-003 | 소프트 삭제 구현 방식 | **status ENUM + deleted_at 타임스탬프 둘 다 사용** | research.md 결론 그대로 — 상태 조회는 status로, 삭제 시점 추적은 deleted_at으로 분리 |
| D-I-004 | 이름 중복 방지 전략 | **중복 완전 허용, 유니크 제약 없음** | research.md 결론 그대로 — 동명이인 가족(예: 아버지·아들 동명) 현실 반영, UUID(→Long id) 기반으로 구분 |
| D-I-005 | 비활성화 투자자 이름 재사용 정책 | **재사용 허용** | research.md 결론 그대로 — `deleted_at IS NOT NULL`인 레코드는 중복 검사에서 제외 |

### 다음 단계
Git Manager에게 Epic 2 Phase 0 완료 이슈 생성 요청 → PR 생성(spec+architecture) → 사람 승인 후 병합 → Epic 2 세부기능(SF) 분해(5.4) 진행.
