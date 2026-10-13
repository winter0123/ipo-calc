import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { investorResponseSchema } from './types.ts'
import type { InvestorResponse } from './types.ts'

export async function fetchInvestors(signal?: AbortSignal): Promise<InvestorResponse[]> {
  return apiGetParsed('/investors', z.array(investorResponseSchema), signal)
}
