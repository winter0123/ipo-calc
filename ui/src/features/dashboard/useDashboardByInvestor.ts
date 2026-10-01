import { useQuery } from '@tanstack/react-query'
import { getDashboardByInvestor } from './api-client.ts'
import { dashboardKeys } from '@/entities/dashboard/index.ts'
import type { DashboardByInvestor } from './types.ts'

export function useDashboardByInvestor({ enabled }: { enabled?: boolean } = {}) {
  return useQuery<DashboardByInvestor[], Error>({
    queryKey: dashboardKeys.byInvestor(),
    queryFn: ({ signal }) => getDashboardByInvestor(signal),
    enabled,
  })
}
