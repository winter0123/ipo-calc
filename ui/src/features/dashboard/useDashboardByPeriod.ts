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
  const currentMonth = new Date().getMonth() + 1
  const effectiveMonth = unit === 'month' ? (month ?? currentMonth) : undefined
  const effectiveYear = year

  const queryKey: (string | number)[] = ['dashboardByPeriod', unit]
  if (effectiveYear !== undefined) queryKey.push(effectiveYear)
  if (effectiveMonth !== undefined) queryKey.push(effectiveMonth)

  return useQuery<DashboardByPeriod[], Error>({
    queryKey,
    queryFn: () => getDashboardByPeriod(unit, effectiveYear, effectiveMonth),
    enabled: enabled ?? true,
    staleTime: 0,
    select: (data) => data,
  })
}
