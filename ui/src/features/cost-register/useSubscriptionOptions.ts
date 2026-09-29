import { useQuery } from '@tanstack/react-query'
import { getInvestmentHistory } from '../../entities/subscription/api-client.ts'
import type {
  InvestmentHistoryResponse,
  InvestmentHistoryFilters,
} from '../../entities/subscription/types.ts'

export function useSubscriptionOptions(filters: InvestmentHistoryFilters = {}) {
  return useQuery<InvestmentHistoryResponse>({
    queryKey: ['subscriptionOptions', filters],
    queryFn: () => getInvestmentHistory(filters),
  })
}
