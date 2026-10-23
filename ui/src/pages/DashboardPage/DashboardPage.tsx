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
import { EmptyState } from '@/widgets/EmptyState/index.ts'
import { DashboardCardGroup } from '@/widgets/DashboardCardGroup/index.ts'
import styles from './DashboardPage.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { AnnouncementPopup } from '@/features/announcements/index.ts'

const EMPTY_SUMMARY = {
  totalInvestmentAmount: 0,
  totalPreTaxProfit: 0,
  totalAfterTaxProfit: 0,
  totalProfitRate: 0,
  participationCount: 0,
}

interface AmountListItem {
  key: string
  label: string
  amount: number
}

function AmountList({ items }: { items: AmountListItem[] }) {
  return (
    <ul className={styles.list}>
      {items.map((item) => (
        <li key={item.key}>
          {item.label}: {formatWon(item.amount)}
        </li>
      ))}
    </ul>
  )
}

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

  const renderTabData = () => {
    switch (currentTab) {
      case 'all': {
        const summary = summaryQuery.data
        if (!summary) return null
        if (summary.totalInvestmentAmount === 0 && summary.participationCount === 0) {
          return <EmptyState onResetFilters={handleReset} />
        }
        // 매도가 없는 투자자는 집계에 없으므로 0으로 채워 모든 투자자를 보여 준다.
        const byId = new Map((investorQuery.data ?? []).map((item) => [item.investorId, item]))
        const sections = (investors ?? []).map((investor) => ({
          id: investor.id,
          name: investor.displayName,
          hasSales: byId.has(investor.id),
          summary: byId.get(investor.id) ?? EMPTY_SUMMARY,
        }))
        return (
          <div className={styles.dataArea}>
            <section aria-labelledby="dash-total" className={styles.section}>
              <h2 id="dash-total" className={styles.sectionTitle}>
                종합
              </h2>
              <DashboardCardGroup summary={summary} />
            </section>
            {sections.length > 0 && (
              // 투자자가 많으면 길어지므로 4명 이상일 때는 접힌 채로 시작한다.
              <details className={styles.investors} open={sections.length <= 3}>
                <summary className={styles.investorsSummary}>
                  투자자별 ({sections.length}명)
                </summary>
                {sections.map((section) => (
                  <section
                    key={section.id}
                    aria-labelledby={`dash-investor-${section.id}`}
                    className={styles.section}
                  >
                    <h2 id={`dash-investor-${section.id}`} className={styles.sectionTitle}>
                      {section.name}
                    </h2>
                    {section.hasSales ? (
                      <DashboardCardGroup summary={section.summary} />
                    ) : (
                      <p className={styles.noSales}>아직 매도 기록이 없습니다.</p>
                    )}
                  </section>
                ))}
              </details>
            )}
          </div>
        )
      }
      case 'securities-firm': {
        const data = securitiesFirmQuery.data
        if (!data) return null
        if (data.length === 0) return <EmptyState onResetFilters={handleReset} />
        return (
          <div className={styles.dataArea}>
            <AmountList
              items={data.map((item) => ({
                key: item.securitiesFirmName,
                label: item.securitiesFirmName,
                amount: item.totalInvestmentAmount,
              }))}
            />
          </div>
        )
      }
      case 'month':
      case 'year': {
        const data = periodQuery.data
        if (!data) return null
        if (data.length === 0) return <EmptyState onResetFilters={handleReset} />
        return (
          <div className={styles.dataArea}>
            <AmountList
              items={data.map((item) => ({
                key: item.period,
                label: item.period,
                amount: item.totalInvestmentAmount,
              }))}
            />
          </div>
        )
      }
    }
  }

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
      return <p className={styles.loading}>대시보드 데이터를 불러오는 중...</p>
    }
    if (currentQuery.error) {
      return (
        <p className={styles.error} role="alert">
          {toErrorMessage(currentQuery.error, {
            fallback: '대시보드 데이터를 불러오는 데 실패했습니다.',
          })}
        </p>
      )
    }
    return renderTabData()
  }

  return (
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
  )
}
