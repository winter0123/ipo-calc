import { useQuery } from '@tanstack/react-query'
import { getInvestmentHistory } from '../../entities/subscription/api-client.ts'
import type { InvestmentHistoryFilters } from '../../entities/subscription/types.ts'

export function useInvestmentHistory(filters: InvestmentHistoryFilters = {}) {
  return useQuery({
    queryKey: ['investmentHistory', filters],
    queryFn: () => getInvestmentHistory(filters),
  })
}
