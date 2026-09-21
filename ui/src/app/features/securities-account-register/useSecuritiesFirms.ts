import { useQuery } from '@tanstack/react-query'
import { fetchSecuritiesFirms } from '../../entities/securities-firm/api-client.ts'
import type { SecuritiesFirmResponse } from '../../entities/securities-firm/types.ts'

export function useSecuritiesFirms() {
  return useQuery<SecuritiesFirmResponse[]>({
    queryKey: ['securities-firms'],
    queryFn: fetchSecuritiesFirms,
  })
}
