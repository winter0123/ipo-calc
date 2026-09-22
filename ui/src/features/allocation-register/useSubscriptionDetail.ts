import { useQuery } from '@tanstack/react-query'
import { getSubscriptionDetail } from '../../entities/subscription/api-client.ts'
import type { InvestmentHistoryItem } from '../../entities/subscription/types.ts'

export function useSubscriptionDetail(subscriptionId: number | null) {
  return useQuery<InvestmentHistoryItem>({
    queryKey: ['subscriptionDetail', subscriptionId],
    queryFn: () => getSubscriptionDetail(subscriptionId!),
    enabled: !!subscriptionId,
  })
}