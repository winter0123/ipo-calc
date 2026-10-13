import { apiFetch } from '@/shared/api/client.ts'
import type { Allocation, AllocationRegisterRequest, AllocationUpdateRequest } from './types.ts'

export async function createAllocation(data: AllocationRegisterRequest): Promise<Allocation> {
  return apiFetch<Allocation>('/allocations', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getAllocation(id: number): Promise<Allocation> {
  return apiFetch<Allocation>(`/allocations/${id}`)
}

export async function updateAllocation(
  id: number,
  data: AllocationUpdateRequest,
): Promise<Allocation> {
  return apiFetch<Allocation>(`/allocations/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function deleteAllocation(id: number): Promise<void> {
  return apiFetch<void>(`/allocations/${id}`, { method: 'DELETE' })
}
