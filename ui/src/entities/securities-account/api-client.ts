import { apiFetch } from '../../shared/api/client.ts'
import type { SecuritiesAccount } from './types.ts'

export async function fetchSecuritiesAccounts(): Promise<SecuritiesAccount[]> {
  return apiFetch<SecuritiesAccount[]>('/securities-accounts', {
    method: 'GET',
  })
}
