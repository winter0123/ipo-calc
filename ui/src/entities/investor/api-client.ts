import { z } from 'zod'
import { apiFetchParsed, apiGetParsed } from '@/shared/api/parsed.ts'
import { investorResponseSchema } from './types.ts'
import type { InvestorRegisterRequest, InvestorResponse } from './types.ts'

export async function fetchInvestors(signal?: AbortSignal): Promise<InvestorResponse[]> {
  return apiGetParsed('/investors', z.array(investorResponseSchema), signal)
}

export function registerInvestor(data: InvestorRegisterRequest): Promise<InvestorResponse> {
  return apiFetchParsed('/investors', investorResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
