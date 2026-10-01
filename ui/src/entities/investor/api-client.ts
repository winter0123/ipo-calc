import { apiFetch } from '../../shared/api/client.ts'
import type { InvestorRegisterRequest, InvestorResponse } from './types.ts'

export async function registerInvestor(data: InvestorRegisterRequest): Promise<InvestorResponse> {
  return apiFetch<InvestorResponse>('/investors', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function fetchInvestors(signal?: AbortSignal): Promise<InvestorResponse[]> {
  return apiFetch<InvestorResponse[]>('/investors', {
    method: 'GET',
    signal,
  })
}
