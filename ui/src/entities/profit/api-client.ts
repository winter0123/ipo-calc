import { apiGetParsed } from '@/shared/api/parsed.ts'
import { investorProfitSchema } from './types.ts'
import type { InvestorProfit } from './types.ts'

export async function getInvestorProfits(
  investorId: number,
  signal?: AbortSignal,
): Promise<InvestorProfit> {
  return apiGetParsed(`/investors/${investorId}/profits`, investorProfitSchema, signal)
}
