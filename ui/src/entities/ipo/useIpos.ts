import { useQuery } from '@tanstack/react-query'
import { fetchIpos } from './api-client.ts'
import { ipoKeys } from './queryKeys.ts'
import type { Ipo } from './types.ts'

export function useIpos() {
  return useQuery<Ipo[]>({
    queryKey: ipoKeys.list(),
    queryFn: ({ signal }) => fetchIpos(signal),
  })
}
