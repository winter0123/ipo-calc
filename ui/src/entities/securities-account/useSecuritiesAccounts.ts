import { useQuery } from '@tanstack/react-query'
import { fetchSecuritiesAccounts } from './api-client.ts'
import { securitiesAccountKeys } from './queryKeys.ts'
import type { SecuritiesAccount } from './types.ts'

export function useSecuritiesAccounts(investorId?: number) {
  return useQuery<SecuritiesAccount[]>({
    queryKey: securitiesAccountKeys.list(investorId),
    queryFn: ({ signal }) => fetchSecuritiesAccounts(investorId, signal),
    enabled: Number.isFinite(investorId),
  })
}
