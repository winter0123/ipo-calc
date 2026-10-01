import { useQuery } from '@tanstack/react-query'
import {
  fetchSecuritiesFirms,
  securitiesFirmKeys,
  type SecuritiesFirmResponse,
} from '../../entities/securities-firm/index.ts'

export function useSecuritiesFirms() {
  return useQuery<SecuritiesFirmResponse[]>({
    queryKey: securitiesFirmKeys.list(),
    queryFn: ({ signal }) => fetchSecuritiesFirms(signal),
  })
}
