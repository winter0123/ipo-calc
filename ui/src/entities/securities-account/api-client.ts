import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { securitiesAccountSchema } from './types.ts'
import type { SecuritiesAccount } from './types.ts'

export async function fetchSecuritiesAccounts(
  investorId?: number,
  signal?: AbortSignal,
): Promise<SecuritiesAccount[]> {
  const params = new URLSearchParams()
  if (investorId !== undefined) params.set('investorId', String(investorId))

  const query = params.toString()
  return apiGetParsed(
    `/securities-accounts${query ? `?${query}` : ''}`,
    z.array(securitiesAccountSchema),
    signal,
  )
}
