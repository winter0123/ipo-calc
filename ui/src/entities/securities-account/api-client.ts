import { apiFetch } from '../../shared/api/client.ts'
import type { SecuritiesAccount } from './types.ts'

export async function fetchSecuritiesAccounts(
  investorId?: number,
  signal?: AbortSignal,
): Promise<SecuritiesAccount[]> {
  const params = new URLSearchParams()
  if (investorId !== undefined) params.set('investorId', String(investorId))

  const query = params.toString()
  return apiFetch<SecuritiesAccount[]>(`/securities-accounts${query ? `?${query}` : ''}`, {
    method: 'GET',
    signal,
  })
}
