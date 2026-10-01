import { apiFetch } from '../../shared/api/client.ts'
import type { SaleProfit, InvestorProfit } from './types.ts'

export async function getSaleProfit(saleId: number, signal?: AbortSignal): Promise<SaleProfit> {
  return apiFetch<SaleProfit>(`/sales/${saleId}/profit`, {
    method: 'GET',
    signal,
  })
}

export async function getInvestorProfits(
  investorId: number,
  signal?: AbortSignal,
): Promise<InvestorProfit> {
  return apiFetch<InvestorProfit>(`/investors/${investorId}/profits`, {
    method: 'GET',
    signal,
  })
}
