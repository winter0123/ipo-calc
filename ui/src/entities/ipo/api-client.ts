import { apiFetch } from '../../shared/api/client.ts'
import type { Ipo } from './types.ts'

export async function fetchIpos(): Promise<Ipo[]> {
  return apiFetch<Ipo[]>('/ipos', {
    method: 'GET',
  })
}
