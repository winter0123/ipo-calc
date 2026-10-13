import { apiFetch } from '@/shared/api/client.ts'
import type { Sale, SaleRegisterRequest, SaleUpdateRequest } from './types.ts'

export async function createSale(data: SaleRegisterRequest): Promise<Sale> {
  return apiFetch<Sale>('/sales', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getSale(id: number): Promise<Sale> {
  return apiFetch<Sale>(`/sales/${id}`)
}

export async function updateSale(id: number, data: SaleUpdateRequest): Promise<Sale> {
  return apiFetch<Sale>(`/sales/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function deleteSale(id: number): Promise<void> {
  return apiFetch<void>(`/sales/${id}`, { method: 'DELETE' })
}
