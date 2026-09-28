import { useQuery } from '@tanstack/react-query'
import { getInvestorProfits } from '../../entities/profit/api-client.ts'
import type { InvestorProfit } from '../../entities/profit/types.ts'

interface UseInvestorProfitsOptions {
  investorId: number | undefined
  enabled: boolean
}

export function useInvestorProfits({ investorId, enabled }: UseInvestorProfitsOptions) {
  return useQuery<InvestorProfit, Error>({
    queryKey: ['investorProfits', investorId],
    queryFn: () => getInvestorProfits(investorId!),
    enabled: enabled && investorId !== undefined && investorId !== null,
  })
}