import { useQuery } from '@tanstack/react-query'
import { getDashboardBySecuritiesFirm } from './api-client.ts'
import { dashboardKeys } from '../../entities/dashboard/index.ts'
import type { DashboardBySecuritiesFirm } from './types.ts'

export function useDashboardBySecuritiesFirm({ enabled }: { enabled?: boolean } = {}) {
  return useQuery<DashboardBySecuritiesFirm[], Error>({
    queryKey: dashboardKeys.bySecuritiesFirm(),
    queryFn: ({ signal }) => getDashboardBySecuritiesFirm(signal),
    enabled,
  })
}
