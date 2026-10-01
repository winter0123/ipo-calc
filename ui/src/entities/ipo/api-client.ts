import { apiFetch } from '../../shared/api/client.ts'
import type { Ipo } from './types.ts'

export async function fetchIpos(signal?: AbortSignal): Promise<Ipo[]> {
  return apiFetch<Ipo[]>('/ipos', {
    method: 'GET',
    signal,
  })
}
