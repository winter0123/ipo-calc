import { useQuery } from '@tanstack/react-query'
import { getDashboardByPeriod } from './api-client.ts'
import { dashboardKeys } from '@/entities/dashboard/index.ts'
import type { DashboardByPeriod, PeriodUnit } from './types.ts'

interface UseDashboardByPeriodOptions {
  unit: PeriodUnit
  year?: number
  month?: number
  enabled?: boolean
}

export function useDashboardByPeriod({ unit, year, month, enabled }: UseDashboardByPeriodOptions) {
  const effectiveMonth = unit === 'month' ? (month ?? new Date().getMonth() + 1) : undefined

  return useQuery<DashboardByPeriod[], Error>({
    queryKey: dashboardKeys.byPeriod(unit, year, effectiveMonth),
    queryFn: ({ signal }) => getDashboardByPeriod(unit, year, effectiveMonth, signal),
    enabled,
  })
}
