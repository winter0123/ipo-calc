import { z } from 'zod'

export const marketHolidaySchema = z.object({
  date: z.string(),
  name: z.string(),
})

export type MarketHoliday = z.infer<typeof marketHolidaySchema>
