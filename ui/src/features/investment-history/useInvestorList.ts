import { useQuery } from '@tanstack/react-query'
import { fetchInvestors } from '../../entities/investor/api-client.ts'
import type { InvestorResponse } from '../../entities/investor/types.ts'

export function useInvestorList() {
  return useQuery<InvestorResponse[]>({
    queryKey: ['investors'],
    queryFn: fetchInvestors,
    staleTime: 0,
  })
}
