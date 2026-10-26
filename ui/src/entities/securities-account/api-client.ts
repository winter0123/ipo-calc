import { z } from 'zod'
import { apiFetchParsed, apiGetParsed } from '@/shared/api/parsed.ts'
import { revealedSecuritiesAccountNumberSchema, securitiesAccountSchema } from './types.ts'
import type {
  RevealedSecuritiesAccountNumber,
  SecuritiesAccount,
  SecuritiesAccountRegisterRequest,
} from './types.ts'

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

export function revealSecuritiesAccountNumber(
  id: number,
): Promise<RevealedSecuritiesAccountNumber> {
  return apiFetchParsed(
    `/securities-accounts/${id}/account-number`,
    revealedSecuritiesAccountNumberSchema,
    { method: 'GET' },
  )
}

export function registerSecuritiesAccount(
  data: SecuritiesAccountRegisterRequest,
): Promise<SecuritiesAccount> {
  return apiFetchParsed('/securities-accounts', securitiesAccountSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateSecuritiesAccount(
  id: number,
  data: { accountName: string },
): Promise<SecuritiesAccount> {
  return apiFetchParsed(`/securities-accounts/${id}`, securitiesAccountSchema, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export function setDefaultSecuritiesAccount(id: number): Promise<SecuritiesAccount> {
  return apiFetchParsed(`/securities-accounts/${id}/set-default`, securitiesAccountSchema, {
    method: 'PATCH',
  })
}
