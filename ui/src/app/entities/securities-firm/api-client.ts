import { apiFetch } from '../../shared/api/client.ts'
import type { SecuritiesFirmResponse } from './types.ts'

export async function fetchSecuritiesFirms(): Promise<SecuritiesFirmResponse[]> {
  return apiFetch<SecuritiesFirmResponse[]>('/securities-firms', {
    method: 'GET',
  })
}
