import { useState, useCallback } from 'react'

export type DashboardTab = 'all' | 'investor' | 'securities-firm' | 'month' | 'year'

interface TabQueryConfig {
  queryKey: (string | object)[]
  enabled: boolean
}

export const ALL_TABS: DashboardTab[] = ['all', 'investor', 'securities-firm', 'month', 'year']

const TAB_QUERY_KEYS_MAP: Record<string, (string | object)[]> = {
  'securities-firm': ['dashboardBySecuritiesFirm'],
}

function getTabKey(tab: DashboardTab): (string | object)[] {
  if (tab === 'securities-firm') return TAB_QUERY_KEYS_MAP['securities-firm']
  const keyMap: Record<string, (string | object)[]> = {
    all: ['dashboardSummary'],
    investor: ['dashboardByInvestor'],
    month: ['dashboardByPeriod', { unit: 'month' as const }],
    year: ['dashboardByPeriod', { unit: 'year' as const }],
  }
  return keyMap[tab]
}

interface QueryClientLike {
  invalidateQueries: (options: { queryKey: (string | object)[] }) => void
}

export function useDashboardTabs() {
  const [currentTab, setCurrentTab] = useState<DashboardTab>('all')

  const invalidateCurrentTab = useCallback(
    (queryClient: QueryClientLike) => {
      queryClient.invalidateQueries({ queryKey: getTabKey(currentTab) })
    },
    [currentTab],
  )

  const getTabQueryConfig = useCallback(
    (tab: DashboardTab): TabQueryConfig => ({
      queryKey: getTabKey(tab),
      enabled: tab === currentTab,
    }),
    [currentTab],
  )

  return {
    currentTab,
    setCurrentTab,
    invalidateCurrentTab,
    getTabQueryConfig,
  }
}
