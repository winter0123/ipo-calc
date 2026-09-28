import { useQueryClient } from '@tanstack/react-query'
import { useDashboardTabs } from '../../features/dashboard/useDashboardTabs.ts'
import { useDashboardSummary } from '../../features/dashboard/useDashboardSummary.ts'
import { useDashboardByInvestor } from '../../features/dashboard/useDashboardByInvestor.ts'
import { useDashboardBySecuritiesFirm } from '../../features/dashboard/useDashboardBySecuritiesFirm.ts'
import { useDashboardByPeriod } from '../../features/dashboard/useDashboardByPeriod.ts'
import EmptyState from '../../widgets/EmptyState/EmptyState.tsx'
import DashboardTabs from '../../features/dashboard/DashboardTabs/DashboardTabs.tsx'
import DashboardCardGroup from '../../widgets/DashboardCardGroup/DashboardCardGroup.tsx'
import styles from './DashboardPage.module.css'

export default function DashboardPage() {
  const { currentTab, setCurrentTab, invalidateCurrentTab } = useDashboardTabs()
  const queryClient = useQueryClient()

  const summaryQuery = useDashboardSummary({ enabled: currentTab === 'all' })
  const investorQuery = useDashboardByInvestor({ enabled: currentTab === 'investor' })
  const securitiesFirmQuery = useDashboardBySecuritiesFirm({
    enabled: currentTab === 'securities-firm',
  })
  const monthQuery = useDashboardByPeriod({ unit: 'month', enabled: currentTab === 'month' })
  const yearQuery = useDashboardByPeriod({ unit: 'year', enabled: currentTab === 'year' })

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

  const renderContent = () => {
    if (currentQuery.isLoading) {
      return <p className={styles.loading}>대시보드 데이터를 불러오는 중...</p>
    }
    if (currentQuery.error) {
      return (
        <p className={styles.error} role="alert">
          {getErrorMessage()}
        </p>
      )
    }
    if (isEmpty) {
      return <EmptyState onResetFilters={handleReset} />
    }
    if (!currentQuery.isLoading && !currentQuery.error && !isEmpty) {
      return (
        <div className={styles.dataArea}>
          {currentTab === 'all' && (
            <DashboardCardGroup
              summary={
                currentQuery.data as {
                  totalInvestmentAmount: number
                  totalPreTaxProfit: number
                  totalAfterTaxProfit: number
                  totalProfitRate: number
                  participationCount: number
                }
              }
            />
          )}
          {currentTab === 'investor' && (
            <ul className={styles.list}>
              {(currentQuery.data as { investorName: string; totalInvestmentAmount: number }[]).map(
                (item) => (
                  <li key={item.investorName}>
                    {item.investorName}: {item.totalInvestmentAmount.toLocaleString()}원
                  </li>
                ),
              )}
            </ul>
          )}
          {currentTab === 'securities-firm' && (
            <ul className={styles.list}>
              {(
                currentQuery.data as { securitiesFirmName: string; totalInvestmentAmount: number }[]
              ).map((item) => (
                <li key={item.securitiesFirmName}>
                  {item.securitiesFirmName}: {item.totalInvestmentAmount.toLocaleString()}원
                </li>
              ))}
            </ul>
          )}
          {(currentTab === 'month' || currentTab === 'year') && (
            <ul className={styles.list}>
              {(
                currentQuery.data as unknown as { period: string; totalInvestmentAmount: number }[]
              ).map((item) => (
                <li key={item.period}>
                  {item.period}: {item.totalInvestmentAmount.toLocaleString()}원
                </li>
              ))}
            </ul>
          )}
        </div>
      )
    }
    return null
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>대시보드</h1>
      <DashboardTabs currentTab={currentTab} onTabChange={handleTabChange} />
      <div className={styles.resultsArea}>
        {renderContent()}
        <nav className={styles.navLink}>
          <a href="/investments">투자 내역 화면으로 이동</a>
        </nav>
      </div>
    </div>
  )
}
