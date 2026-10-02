export type DashboardTab = 'all' | 'securities-firm' | 'month' | 'year'

export const DASHBOARD_TABS: ReadonlyArray<{ key: DashboardTab; label: string }> = [
  { key: 'all', label: '종합' },
  { key: 'securities-firm', label: '증권사별' },
  { key: 'month', label: '월별' },
  { key: 'year', label: '연도별' },
]
