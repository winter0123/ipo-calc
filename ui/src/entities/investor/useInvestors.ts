import { useQuery } from '@tanstack/react-query'
import { fetchInvestors } from './api-client.ts'
import { investorKeys } from './queryKeys.ts'
import type { InvestorResponse } from './types.ts'

export function useInvestors() {
  return useQuery<InvestorResponse[]>({
    queryKey: investorKeys.list(),
    queryFn: ({ signal }) => fetchInvestors(signal),
  })
}
