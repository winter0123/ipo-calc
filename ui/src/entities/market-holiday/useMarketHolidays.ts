import { useQuery } from '@tanstack/react-query'
import { fetchMarketHolidays } from './api-client.ts'
import { marketHolidayKeys } from './queryKeys.ts'
import type { MarketHoliday } from './types.ts'

export function useMarketHolidays(year: number) {
  return useQuery<MarketHoliday[]>({
    queryKey: marketHolidayKeys.year(year),
    queryFn: ({ signal }) => fetchMarketHolidays(year, signal),
    staleTime: 60 * 60 * 1000,
  })
}
