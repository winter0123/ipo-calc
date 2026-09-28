import { useQuery } from '@tanstack/react-query'
import { getDashboardByPeriod } from '../../entities/dashboard/api-client.ts'
import type { DashboardByPeriod } from '../../entities/dashboard/types.ts'

interface UseDashboardByPeriodOptions {
  unit: 'month' | 'year'
  year?: number
  month?: number
  enabled?: boolean
}

export function useDashboardByPeriod({ unit, year, month, enabled }: UseDashboardByPeriodOptions) {
  const queryKey: (string | number)[] = ['dashboardByPeriod', unit]
  if (year !== undefined) queryKey.push(year)
  if (month !== undefined) queryKey.push(month)

  return useQuery<DashboardByPeriod[], Error>({
    queryKey,
    queryFn: () => getDashboardByPeriod(unit),
    enabled: enabled ?? true,
    staleTime: 0,
    select: (data) => data,
  })
}
