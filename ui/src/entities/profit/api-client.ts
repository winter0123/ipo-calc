import { apiGetParsed } from '@/shared/api/parsed.ts'
import { investorProfitSchema, saleProfitSchema } from './types.ts'
import type { SaleProfit, InvestorProfit } from './types.ts'

export async function getSaleProfit(saleId: number, signal?: AbortSignal): Promise<SaleProfit> {
  return apiGetParsed(`/sales/${saleId}/profit`, saleProfitSchema, signal)
}

export async function getInvestorProfits(
  investorId: number,
  signal?: AbortSignal,
): Promise<InvestorProfit> {
  return apiGetParsed(`/investors/${investorId}/profits`, investorProfitSchema, signal)
}
