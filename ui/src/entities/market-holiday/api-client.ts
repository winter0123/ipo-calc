import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { marketHolidaySchema } from './types.ts'
import type { MarketHoliday } from './types.ts'

export async function fetchMarketHolidays(
  year: number,
  signal?: AbortSignal,
): Promise<MarketHoliday[]> {
  return apiGetParsed(`/market-holidays?year=${year}`, z.array(marketHolidaySchema), signal)
}
