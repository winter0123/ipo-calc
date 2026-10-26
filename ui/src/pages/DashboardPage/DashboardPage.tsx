import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useInvestors } from '@/entities/investor/index.ts'
import {
  DashboardTabs,
  DashboardHome,
  PeriodSelector,
  useDashboardTabs,
  useDashboardSummary,
  useDashboardByInvestor,
  useDashboardBySecuritiesFirm,
  useDashboardByPeriod,
} from '@/features/dashboard/index.ts'
import type { DashboardTab } from '@/features/dashboard/index.ts'
import DashboardTabContent from './DashboardTabContent.tsx'
import styles, { StyleScope } from './DashboardPage.styles.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { AnnouncementPopup } from '@/features/announcements/index.ts'

export default function DashboardPage() {
  const { currentTab, setCurrentTab, invalidateCurrentTab } = useDashboardTabs()
  const queryClient = useQueryClient()
  const [selectedYear, setSelectedYear] = useState<number>(new Date().getFullYear())
  const [selectedMonth, setSelectedMonth] = useState<number>(new Date().getMonth() + 1)

  const summaryQuery = useDashboardSummary({ enabled: currentTab === 'all' })
  // 종합 탭은 합계 아래에 계정에 등록된 투자자별 집계를 이어서 보여 준다.
  const investorQuery = useDashboardByInvestor({ enabled: currentTab === 'all' })
  const { data: investors } = useInvestors()
  const securitiesFirmQuery = useDashboardBySecuritiesFirm({
    enabled: currentTab === 'securities-firm',
  })
  const periodQuery = useDashboardByPeriod({
    unit: currentTab === 'month' ? 'month' : 'year',
    year: selectedYear,
    month: currentTab === 'month' ? selectedMonth : undefined,
    enabled: currentTab === 'month' || currentTab === 'year',
  })

  const handleTabChange = (tab: DashboardTab) => {
    setCurrentTab(tab)
  }

  const handlePeriodChange = (year: number, month?: number) => {
    setSelectedYear(year)
    if (month !== undefined) {
      setSelectedMonth(month)
    }
  }

  const periodSelectorKey = `${currentTab}-${selectedYear}-${selectedMonth}`

  const handleReset = () => {
    const now = new Date()
    setSelectedYear(now.getFullYear())
    if (currentTab === 'month') {
      setSelectedMonth(now.getMonth() + 1)
    }
    invalidateCurrentTab(queryClient)
  }

  const hasPeriodSelector = currentTab === 'month' || currentTab === 'year'

  const currentQuery = {
    all: {
      isLoading: summaryQuery.isLoading || investorQuery.isLoading,
      error: summaryQuery.error ?? investorQuery.error,
    },
    'securities-firm': securitiesFirmQuery,
    month: periodQuery,
    year: periodQuery,
  }[currentTab]

  const renderContent = () => {
    if (currentQuery.isLoading) {
      return (
        <StyleScope>
          {<p className={styles.loading}>대시보드 데이터를 불러오는 중...</p>}
        </StyleScope>
      )
    }
    if (currentQuery.error) {
      return (
        <StyleScope>
          {
            <p className={styles.error} role="alert">
              {toErrorMessage(currentQuery.error, {
                fallback: '대시보드 데이터를 불러오는 데 실패했습니다.',
              })}
            </p>
          }
        </StyleScope>
      )
    }
    return (
      <DashboardTabContent
        currentTab={currentTab}
        summary={summaryQuery.data}
        investorSummaries={investorQuery.data}
        investors={investors ?? []}
        securitiesFirmSummaries={securitiesFirmQuery.data}
        periodSummaries={periodQuery.data}
        onResetFilters={handleReset}
      />
    )
  }

  return (
    <StyleScope>
      {
        <>
          <AnnouncementPopup />
          <div className={styles.container}>
            <h1 className={styles.title}>대시보드</h1>
            <DashboardHome />
            <DashboardTabs currentTab={currentTab} onTabChange={handleTabChange} />
            <div className={styles.resultsArea}>
              {hasPeriodSelector && (
                <PeriodSelector
                  key={periodSelectorKey}
                  unit={currentTab}
                  initialYear={selectedYear}
                  initialMonth={currentTab === 'month' ? selectedMonth : undefined}
                  onSearch={handlePeriodChange}
                />
              )}
              {renderContent()}
              <nav className={styles.navLink}>
                <Link to="/investments">투자 내역 화면으로 이동</Link>
              </nav>
            </div>
          </div>
        </>
      }
    </StyleScope>
  )
}
