import { useQuery } from '@tanstack/react-query'
import { getDashboardBySecuritiesFirm } from '../../entities/dashboard/api-client.ts'
import type { DashboardBySecuritiesFirm } from '../../entities/dashboard/types.ts'

interface UseDashboardBySecuritiesFirmOptions {
  enabled?: boolean
}

export function useDashboardBySecuritiesFirm(options?: UseDashboardBySecuritiesFirmOptions) {
  return useQuery<DashboardBySecuritiesFirm[], Error>({
    queryKey: ['dashboardBySecuritiesFirm'],
    queryFn: getDashboardBySecuritiesFirm,
    enabled: options?.enabled ?? true,
    staleTime: 0,
    select: (data) => data,
  })
}
