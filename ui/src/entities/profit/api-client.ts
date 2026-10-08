import { apiGetParsed } from '@/shared/api/parsed.ts'
import { investorProfitSchema } from './types.ts'
import type { InvestorProfit } from './types.ts'

export interface InvestorProfitPeriod {
  from?: string
  to?: string
}

export async function getInvestorProfits(
  investorId: number,
  period: InvestorProfitPeriod | undefined = {},
  signal?: AbortSignal,
): Promise<InvestorProfit> {
  const params = new URLSearchParams()
  if (period?.from) params.set('from', period.from)
  if (period?.to) params.set('to', period.to)

  const query = params.toString()
  return apiGetParsed(
    `/investors/${investorId}/profits${query ? `?${query}` : ''}`,
    investorProfitSchema,
    signal,
  )
}
