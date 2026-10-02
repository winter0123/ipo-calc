import { useState, useCallback } from 'react'
import type { QueryClient } from '@tanstack/react-query'
import { dashboardKeys } from '@/entities/dashboard/index.ts'
import type { DashboardTab } from './tabs.ts'

function getTabKey(tab: DashboardTab) {
  switch (tab) {
    case 'all':
      return dashboardKeys.all
    case 'securities-firm':
      return dashboardKeys.bySecuritiesFirm()
    case 'month':
    case 'year':
      return dashboardKeys.byPeriod(tab)
  }
}

export function useDashboardTabs() {
  const [currentTab, setCurrentTab] = useState<DashboardTab>('all')

  const invalidateCurrentTab = useCallback(
    (queryClient: Pick<QueryClient, 'invalidateQueries'>) => {
      queryClient.invalidateQueries({ queryKey: getTabKey(currentTab) })
    },
    [currentTab],
  )

  return { currentTab, setCurrentTab, invalidateCurrentTab }
}
