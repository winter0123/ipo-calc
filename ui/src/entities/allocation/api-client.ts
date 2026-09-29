import { apiFetch } from '../../shared/api/client.ts'
import type { Allocation, AllocationRegisterRequest } from './types.ts'

export async function createAllocation(data: AllocationRegisterRequest): Promise<Allocation> {
  return apiFetch<Allocation>('/allocations', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
