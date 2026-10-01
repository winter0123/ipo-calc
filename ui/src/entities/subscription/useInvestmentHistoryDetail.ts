import { useQuery } from '@tanstack/react-query'
import { getSubscriptionDetail } from './api-client.ts'
import { investmentHistoryKeys } from './queryKeys.ts'
import type { InvestmentHistoryItem } from './types.ts'

export function useInvestmentHistoryDetail(subscriptionId: number | null | undefined) {
  return useQuery<InvestmentHistoryItem>({
    queryKey: investmentHistoryKeys.detail(subscriptionId),
    queryFn: ({ signal }) => getSubscriptionDetail(subscriptionId!, signal),
    enabled: !!subscriptionId,
  })
}
