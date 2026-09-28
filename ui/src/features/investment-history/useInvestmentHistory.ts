import { useQuery } from '@tanstack/react-query'
import { getInvestmentHistory } from '../../entities/subscription/api-client.ts'
import type {
  InvestmentHistoryFilters,
  InvestmentHistoryResponse,
} from '../../entities/subscription/types.ts'

export function useInvestmentHistory(filters: InvestmentHistoryFilters) {
  return useQuery<InvestmentHistoryResponse>({
    queryKey: ['investmentHistory', filters],
    queryFn: () => getInvestmentHistory(filters),
    enabled: true,
    staleTime: 0,
    select: (data) => data,
  })
}
