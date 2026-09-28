# 웹 조회·대시보드 컴포넌트 존재 여부 점검

이 문서는 Epic 16(web-inquiry-dashboard)의 세부 기능(SF)별 산출물 파일 경로가 기존 ui/ 디렉터리에 존재하는지 확인하여, 실제 구현 시 재사용 대상인지 신규 생성 대상인지를 정리한다. 확인 방법은 실제 파일 시스템에서의 존재 여부만을 기준으로 하며, 내용은 검증하지 않는다.

## 1. SF별 산출물 파일 경로 존재 여부

### SF-1: 투자 내역 화면: 상단 필터 바 및 EmptyState 위젯
| 산출물 파일 경로 | 존재 여부 | 비고 |
|------------------|-----------|------|
| features/investment-history/FilterBar/FilterBar.tsx | does not exist | 신규 생성 대상 |
| features/investment-history/FilterBar/FilterBar.module.css | does not exist | 신규 생성 대상 |
| features/investment-history/FilterBar/useInvestmentHistoryFilters.ts | does not exist | 신규 생성 대상 |
| widgets/EmptyState/EmptyState.tsx | does not exist | 신규 생성 대상 (SF-3에서 재사용 예정) |
| widgets/EmptyState/EmptyState.module.css | does not exist | 신규 생성 대상 (SF-3에서 재사용 예정) |
| pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx | does not exist | 신규 생성 대상 |
| pages/InvestmentHistoryPage/InvestmentHistoryPage.module.css | does not exist | 신규 생성 대상 |
| App.tsx - `/investments` 라우트 | does not exist | 추가 필요 |

### SF-2: 투자 내역 화면: 목록 및 상세 표시
| 산출물 파일 경로 | 존재 여부 | 비고 |
|------------------|-----------|------|
| widgets/InvestmentHistoryTable/InvestmentHistoryTable.tsx | does not exist | 신규 생성 대상 |
| widgets/InvestmentHistoryTable/InvestmentHistoryTable.module.css | does not exist | 신규 생성 대상 |
| widgets/InvestmentHistoryTable/InvestmentHistoryRow.tsx | does not exist | 신규 생성 대상 |
| widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx | does not exist | 신규 생성 대상 |
| features/investment-history/useInvestmentHistory.ts | does not exist | 신규 생성 대상 |
| features/investment-history/useInvestmentHistoryDetail.ts | does not exist | 신규 생성 대상 |
| pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx (업데이트) | does not exist | 신규 생성 대상 (파일이 없으므로 생성 후 업데이트 개념) |
| shared/components/ConfirmDialog/ConfirmDialog.tsx | exists | **재사용 대상** (Epic 14에서 생성됨) |
| shared/components/ConfirmDialog/ConfirmDialog.module.css | exists | **재사용 대상** |

### SF-3: 대시보드 화면: 탭 전환 및 빈 상태
| 산출물 파일 경로 | 존재 여부 | 비고 |
|------------------|-----------|------|
| features/dashboard/DashboardTabs/DashboardTabs.tsx | does not exist | 신규 생성 대상 |
| features/dashboard/DashboardTabs/DashboardTabs.module.css | does not exist | 신규 생성 대상 |
| features/dashboard/useDashboardTabs.ts | does not exist | 신규 생성 대상 |
| widgets/EmptyState/EmptyState.tsx (재사용) | does not exist | **재사용 대상이나 현재 부재** (SF-1에서 생성 예정이나 아직 생성되지 않음) |
| widgets/EmptyState/EmptyState.module.css (재사용) | does not exist | **재사용 대상이나 현재 부재** (SF-1에서 생성 예정이나 아직 생성되지 않음) |
| pages/DashboardPage/DashboardPage.tsx (업데이트) | exists | 존재하지만 내용이 웹 조회·대시보드 대시보드와 다름 (투자자 목록 페이지) |
| pages/DashboardPage/DashboardPage.module.css (업데이트) | exists | 존재하지만 내용이 웹 조회·대시보드 대시보드와 다름 |
| App.tsx - `/dashboard` 라우트 | exists | 이미 존재 (Epic 11 또는 autre에서 추가됨) |

### SF-4: 대시보드 화면: 지표 카드 표시
| 산출물 파일 경로 | 존재 여부 | 비고 |
|------------------|-----------|------|
| widgets/DashboardCardGroup/DashboardCardGroup.tsx | does not exist | 신규 생성 대상 |
| widgets/DashboardCardGroup/DashboardCardGroup.module.css | does not exist | 신규 생성 대상 |
| widgets/DashboardCard/DashboardCard.tsx | does not exist | 신규 생성 대상 |
| widgets/DashboardCard/DashboardCard.module.css | does not exist | 신규 생성 대상 |
| features/dashboard/useDashboardSummary.ts | does not exist | 신규 생성 대상 |
| features/dashboard/useDashboardByInvestor.ts | does not exist | 신규 생성 대상 |
| features/dashboard/useDashboardBySecuritiesFirm.ts | does not exist | 신규 생성 대상 |
| features/dashboard/useDashboardByPeriod.ts | does not exist | 신규 생성 대상 |
| pages/DashboardPage/DashboardPage.tsx (업데이트) | exists | 존재하지만 내용이 웹 조회·대시보드 대시보드와 다름 |
| pages/DashboardPage/DashboardPage.module.css (업데이트) | exists | 존재하지만 내용이 웹 조회·대시보드 대시보드와 다름 |
| entities/dashboard/types.ts | does not exist | 신규 생성 대상 |
| entities/dashboard/api-client.ts | does not exist | 신규 생성 대상 |

### SF-5: 대시보드 화면: 기간별 탭 연도 선택기 및 API 연동
| 산출물 파일 경로 | 존재 여부 | 비고 |
|------------------|-----------|------|
| features/dashboard/PeriodSelector/PeriodSelector.tsx | does not exist | 신규 생성 대상 |
| features/dashboard/PeriodSelector/PeriodSelector.module.css | does not exist | 신규 생성 대상 |
| features/dashboard/useDashboardByPeriod.ts (업데이트) | does not exist | 신규 생성 대상 (파일이 없으므로 생성 후 업데이트 개념) |
| pages/DashboardPage/DashboardPage.tsx (업데이트) | exists | 존재하지만 내용이 웹 조회·대시보드 대시보드와 다름 |
| entities/dashboard/api-client.ts (업데이트) | does not exist | 신규 생성 대상 (파일이 없으므로 생성 후 업데이트 개념) |

## 2. 라우팅 확인
- `/investments` 라우트: **존재하지 않음** → SF-1에서 `App.tsx`에 추가 필요
- `/dashboard` 라우트: **존재함** → 현재 `App.tsx`에 이미 정의되어 있음 (라인 19)

## 3. 이름 충돌 위험 및 드리프트 분석
 breakdown에서 "기존 재사용"이라고 명시한 컴포넌트의 실제 존재 여부를 확인한 결과:

- **shared/components/ConfirmDialog/ConfirmDialog.tsx**: 존재함 → 재사용 가능, 이름 충돌 위험 없음.
- **widgets/EmptyState/EmptyState.tsx**: **존재하지 않음** → breakdown의 SF-3에서는 SF-1에서 생성된 것을 재사용한다고 명시했으나, 현재 SF-1이 구현되지 않아 해당 파일이 부재.이는 브레이크다운과 현재 코드 상태 사이의 드리프트이며, SF-1을 구현한 후에 해결됩니다.

또한、`pages/DashboardPage/DashboardPage.tsx` 파일이 존재하지만, 그 내용은 웹 Inquiry 대시보드가 기대하는 대시보드 페이지와 다릅니다(투자자 목록 페이지). 따라서 이 파일을 재사용하려면 상당한 수정이 필요하며, 이는 브레이크다운과 구현 사이의 내용상의 드리프트라고 할 수 있습니다.

## 4. 실착수 시점 frontend 팀장 확인 항목 (우선순위)
실제로 구현을 시작하기 전에 frontend 팀장이 먼저 확인해야 할 항목을 우선순위로 정리한다.

1. **라우팅 확인**: `/investments` 라우트가 `App.tsx`에 추가되어 있는지 확인 (존재하지 않는 경우 추가)
2. **EmptyState 위젯의 존재 여부**: SF-3에서 재사용을 가정하는 `widgets/EmptyState/EmptyState.tsx`이 실제로 존재하는지 확인 (존재하지 않는 경우 SF-1에서 먼저 생성)
3. **DashboardPage 내용의 일치성**: `pages/DashboardPage/DashboardPage.tsx`의 현재 내용이 웹 조회·대시보드 대시보드용인지 확인 (다를 경우, 브레이크다운의 의도에 맞게 수정하거나 덮어쓰기해야 함)
4. **공통 컴포넌트 재사용 가능성**: `shared/components/ConfirmDialog/ConfirmDialog.tsx` 가 예상대로 작동하는지 확인하고, SF-2에서 사용에 문제가 없는지 검증
5. **디렉터리 구조의 선결 조건**: features/investment-history, features/dashboard, widgets/* 등 필요한 디렉터리가 존재하는지 확인하고, 존재하지 않는 경우 생성

> **참고**: 이 점검 결과는 2026-09-28 현재의 파일 시스템 상태를 기반으로 한다. 실제 구현 전에는 반드시 최신 상태를 다시 확인할 것.