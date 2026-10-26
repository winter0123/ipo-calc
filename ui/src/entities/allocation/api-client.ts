import { apiFetch } from '@/shared/api/client.ts'
import { apiFetchParsed, apiGetParsed } from '@/shared/api/parsed.ts'
import { allocationSchema } from './types.ts'
import type { Allocation, AllocationRegisterRequest, AllocationUpdateRequest } from './types.ts'

export async function createAllocation(data: AllocationRegisterRequest): Promise<Allocation> {
  return apiFetchParsed('/allocations', allocationSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getAllocation(id: number, signal?: AbortSignal): Promise<Allocation> {
  return apiGetParsed(`/allocations/${id}`, allocationSchema, signal)
}

export async function updateAllocation(
  id: number,
  data: AllocationUpdateRequest,
): Promise<Allocation> {
  return apiFetchParsed(`/allocations/${id}`, allocationSchema, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function deleteAllocation(id: number): Promise<void> {
  return apiFetch<void>(`/allocations/${id}`, { method: 'DELETE' })
}
