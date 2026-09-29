import { useQuery } from '@tanstack/react-query'
import { fetchSecuritiesAccounts } from '../../entities/securities-account/api-client.ts'
import type { SecuritiesAccount } from '../../entities/securities-account/types.ts'

export function useSecuritiesAccountList(investorId?: number) {
  return useQuery<SecuritiesAccount[]>({
    queryKey: ['securitiesAccounts', investorId],
    queryFn: () => fetchSecuritiesAccounts(investorId),
    enabled: investorId !== undefined,
    staleTime: 0,
  })
}
