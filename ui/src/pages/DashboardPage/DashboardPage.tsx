import { useQueryClient } from '@tanstack/react-query'
import { useDashboardTabs } from '../../features/dashboard/useDashboardTabs.ts'
import {
  useDashboardSummary,
  useDashboardByInvestor,
  useDashboardBySecuritiesFirm,
  useDashboardByPeriod,
} from '../../features/dashboard/mock-hooks.ts'
import EmptyState from '../../widgets/EmptyState/EmptyState.tsx'
import DashboardTabs from '../../features/dashboard/DashboardTabs/DashboardTabs.tsx'
import styles from './DashboardPage.module.css'

export default function DashboardPage() {
  const { currentTab, setCurrentTab, invalidateCurrentTab } = useDashboardTabs()
  const queryClient = useQueryClient()

  const summaryQuery = useDashboardSummary()
  const investorQuery = useDashboardByInvestor()
  const securitiesFirmQuery = useDashboardBySecuritiesFirm()
  const monthQuery = useDashboardByPeriod('month')
  const yearQuery = useDashboardByPeriod('year')

  const currentQuery = (() => {
    switch (currentTab) {
      case 'all':
        return summaryQuery
      case 'investor':
        return investorQuery
      case 'securities-firm':
        return securitiesFirmQuery
      case 'month':
        return monthQuery
      case 'year':
        return yearQuery
    }
  })()

  const handleTabChange = (tab: typeof currentTab) => {
    setCurrentTab(tab)
  }

  const handleReset = () => {
    invalidateCurrentTab(queryClient)
  }

  const isEmpty = (() => {
    const data = currentQuery.data
    if (!data) return false
    if (Array.isArray(data)) return data.length === 0
    const summary = data as { totalInvestmentAmount: number; participationCount: number }
    return summary.totalInvestmentAmount === 0 && summary.participationCount === 0
  })()

  const getErrorMessage = () => {
    if (!currentQuery.error) return null
    const msg =
      currentQuery.error instanceof Error ? currentQuery.error.message : String(currentQuery.error)
    if (msg.includes('Failed to fetch') || msg.includes('Network error')) {
      return '네트워크 연결을 확인해 주세요.'
    }
    return '대시보드 데이터를 불러오는 데 실패했습니다.'
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>대시보드</h1>

      <DashboardTabs currentTab={currentTab} onTabChange={handleTabChange} />

      <div className={styles.resultsArea}>
        {currentQuery.isLoading && (
          <p className={styles.loading}>대시보드 데이터를 불러오는 중...</p>
        )}

        {currentQuery.error && (
          <p className={styles.error} role="alert">
            {getErrorMessage()}
          </p>
        )}

        {!currentQuery.isLoading && !currentQuery.error && isEmpty && (
          <EmptyState onResetFilters={handleReset} />
        )}

        {!currentQuery.isLoading && !currentQuery.error && !isEmpty && (
          <div className={styles.dataArea}>
            {currentTab === 'all' && (
              <div className={styles.cardGrid}>
                <div className={styles.card}>총 투자금</div>
                <div className={styles.card}>세전 수익</div>
                <div className={styles.card}>세후 수익</div>
                <div className={styles.card}>수익률</div>
                <div className={styles.card}>참여 건수</div>
              </div>
            )}
            {currentTab === 'investor' && (
              <ul className={styles.list}>
                {(currentQuery.data as { investorName: string; totalAmount: number }[]).map(
                  (item) => (
                    <li key={item.investorName}>
                      {item.investorName}: {item.totalAmount.toLocaleString()}원
                    </li>
                  ),
                )}
              </ul>
            )}
            {currentTab === 'securities-firm' && (
              <ul className={styles.list}>
                {(currentQuery.data as { securitiesFirmName: string; totalAmount: number }[]).map(
                  (item) => (
                    <li key={item.securitiesFirmName}>
                      {item.securitiesFirmName}: {item.totalAmount.toLocaleString()}원
                    </li>
                  ),
                )}
              </ul>
            )}
            {currentTab === 'month' && (
              <ul className={styles.list}>
                {(currentQuery.data as { period: string; totalAmount: number }[]).map((item) => (
                  <li key={item.period}>
                    {item.period}: {item.totalAmount.toLocaleString()}원
                  </li>
                ))}
              </ul>
            )}
            {currentTab === 'year' && (
              <ul className={styles.list}>
                {(currentQuery.data as { period: string; totalAmount: number }[]).map((item) => (
                  <li key={item.period}>
                    {item.period}: {item.totalAmount.toLocaleString()}원
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <nav className={styles.navLink}>
          <a href="/investments">투자 내역 화면으로 이동</a>
        </nav>
      </div>
    </div>
  )
}
