import { apiFetch } from '../../shared/api/client.ts'
import type { SecuritiesFirmResponse } from './types.ts'

export async function fetchSecuritiesFirms(
  signal?: AbortSignal,
): Promise<SecuritiesFirmResponse[]> {
  return apiFetch<SecuritiesFirmResponse[]>('/securities-firms', {
    method: 'GET',
    signal,
  })
}
