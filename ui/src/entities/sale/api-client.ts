import { apiFetch } from '../../shared/api/client.ts'
import type { Sale, SaleRegisterRequest } from './types.ts'

export async function createSale(data: SaleRegisterRequest): Promise<Sale> {
  return apiFetch<Sale>('/sales', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
