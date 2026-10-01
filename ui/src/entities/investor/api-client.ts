import { apiFetch } from '@/shared/api/client.ts'
import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { investorResponseSchema } from './types.ts'
import type { InvestorRegisterRequest, InvestorResponse } from './types.ts'

export async function registerInvestor(data: InvestorRegisterRequest): Promise<InvestorResponse> {
  return apiFetch<InvestorResponse>('/investors', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function fetchInvestors(signal?: AbortSignal): Promise<InvestorResponse[]> {
  return apiGetParsed('/investors', z.array(investorResponseSchema), signal)
}
