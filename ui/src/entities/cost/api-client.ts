import { apiFetch } from '../../shared/api/client.ts'
import type { Cost, CostRegisterRequest } from './types.ts'

export async function createCost(data: CostRegisterRequest): Promise<Cost> {
  return apiFetch<Cost>('/costs', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
