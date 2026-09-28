import { useQuery } from '@tanstack/react-query'
import { getSubscriptionDetail } from '../../entities/subscription/api-client.ts'
import type { InvestmentHistoryItem } from '../../entities/subscription/types.ts'

export function useInvestmentHistoryDetail(subscriptionId: number | undefined) {
  return useQuery<InvestmentHistoryItem>({
    queryKey: ['investmentHistoryDetail', subscriptionId],
    queryFn: () => getSubscriptionDetail(subscriptionId!),
    enabled: !!subscriptionId,
    staleTime: 0,
    select: (data) => data,
  })
}
