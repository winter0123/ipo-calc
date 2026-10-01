import { useQuery } from '@tanstack/react-query'
import { getDashboardSummary } from './api-client.ts'
import { dashboardKeys } from '../../entities/dashboard/index.ts'
import type { DashboardSummary } from './types.ts'

export function useDashboardSummary({ enabled }: { enabled?: boolean } = {}) {
  return useQuery<DashboardSummary, Error>({
    queryKey: dashboardKeys.summary(),
    queryFn: ({ signal }) => getDashboardSummary(signal),
    enabled,
  })
}
