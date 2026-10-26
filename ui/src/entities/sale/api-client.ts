import { apiFetch } from '@/shared/api/client.ts'
import { apiFetchParsed, apiGetParsed } from '@/shared/api/parsed.ts'
import { saleSchema } from './types.ts'
import type { Sale, SaleRegisterRequest, SaleUpdateRequest } from './types.ts'

export async function createSale(data: SaleRegisterRequest): Promise<Sale> {
  return apiFetchParsed('/sales', saleSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getSale(id: number, signal?: AbortSignal): Promise<Sale> {
  return apiGetParsed(`/sales/${id}`, saleSchema, signal)
}

export async function updateSale(id: number, data: SaleUpdateRequest): Promise<Sale> {
  return apiFetchParsed(`/sales/${id}`, saleSchema, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function deleteSale(id: number): Promise<void> {
  return apiFetch<void>(`/sales/${id}`, { method: 'DELETE' })
}
