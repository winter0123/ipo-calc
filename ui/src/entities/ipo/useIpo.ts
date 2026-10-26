import { useQuery } from '@tanstack/react-query'
import { fetchIpo } from './api-client.ts'
import { ipoKeys } from './queryKeys.ts'
import type { Ipo } from './types.ts'

export function useIpo(id: number | undefined) {
  return useQuery<Ipo>({
    queryKey: id !== undefined ? ipoKeys.detail(id) : ipoKeys.invalidDetail(),
    queryFn: ({ signal }) => fetchIpo(id!, signal),
    enabled: id !== undefined && !Number.isNaN(id),
  })
}
