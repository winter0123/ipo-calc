import { useQuery } from '@tanstack/react-query'
import { getDashboardByInvestor } from '../../entities/dashboard/api-client.ts'
import type { DashboardByInvestor } from '../../entities/dashboard/types.ts'

interface UseDashboardByInvestorOptions {
  enabled?: boolean
}

export function useDashboardByInvestor(options?: UseDashboardByInvestorOptions) {
  return useQuery<DashboardByInvestor[], Error>({
    queryKey: ['dashboardByInvestor'],
    queryFn: getDashboardByInvestor,
    enabled: options?.enabled ?? true,
    staleTime: 0,
    select: (data) => data,
  })
}
