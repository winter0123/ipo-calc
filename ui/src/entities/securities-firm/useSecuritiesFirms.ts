import { useQuery } from '@tanstack/react-query'
import { fetchSecuritiesFirms } from './api-client.ts'
import { securitiesFirmKeys } from './queryKeys.ts'
import type { SecuritiesFirmResponse } from './types.ts'

export function useSecuritiesFirms() {
  return useQuery<SecuritiesFirmResponse[]>({
    queryKey: securitiesFirmKeys.list(),
    queryFn: ({ signal }) => fetchSecuritiesFirms(signal),
  })
}
