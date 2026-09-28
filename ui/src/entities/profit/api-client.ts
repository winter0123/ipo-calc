import { apiFetch } from '../../shared/api/client.ts'
import type { SaleProfit, InvestorProfit } from './types.ts'

export async function getSaleProfit(saleId: number): Promise<SaleProfit> {
  return apiFetch<SaleProfit>(`/sales/${saleId}/profit`, {
    method: 'GET',
  })
}

export async function getInvestorProfits(investorId: number): Promise<InvestorProfit> {
  return apiFetch<InvestorProfit>(`/investors/${investorId}/profits`, {
    method: 'GET',
  })
}