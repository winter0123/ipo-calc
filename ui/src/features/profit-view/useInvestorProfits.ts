import { useQuery } from '@tanstack/react-query'
import { getInvestorProfits, profitKeys, type InvestorProfit } from '@/entities/profit/index.ts'

interface UseInvestorProfitsOptions {
  investorId: number | undefined
  enabled: boolean
}

export function useInvestorProfits({ investorId, enabled }: UseInvestorProfitsOptions) {
  return useQuery<InvestorProfit, Error>({
    queryKey: profitKeys.investor(investorId),
    queryFn: ({ signal }) => getInvestorProfits(investorId!, signal),
    enabled: enabled && investorId !== undefined && investorId !== null,
  })
}
