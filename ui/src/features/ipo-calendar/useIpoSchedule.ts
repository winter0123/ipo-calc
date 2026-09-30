import { useQuery } from '@tanstack/react-query'
import { fetchIpos } from '../../entities/ipo/api-client.ts'
import type { Ipo } from '../../entities/ipo/types.ts'

export function useIpoSchedule() {
  return useQuery<Ipo[]>({
    queryKey: ['ipoSchedule'],
    queryFn: fetchIpos,
  })
}