import { useQuery } from '@tanstack/react-query'
import { getDashboardSummary } from '../../entities/dashboard/api-client.ts'
import type { DashboardSummary } from '../../entities/dashboard/types.ts'

interface UseDashboardSummaryOptions {
  enabled?: boolean
}

export function useDashboardSummary(options?: UseDashboardSummaryOptions) {
  return useQuery<DashboardSummary, Error>({
    queryKey: ['dashboardSummary'],
    queryFn: getDashboardSummary,
    enabled: options?.enabled ?? true,
    staleTime: 0,
    select: (data) => data,
  })
}
