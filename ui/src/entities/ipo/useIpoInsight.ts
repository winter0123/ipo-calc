import { useQuery } from '@tanstack/react-query'
import { fetchIpoInsight } from './api-client.ts'
import { ipoKeys } from './queryKeys.ts'

// 서버가 외부 출처를 1시간 캐시하므로 화면도 그동안 다시 부르지 않는다.
export function useIpoInsight(id: number | undefined) {
  return useQuery({
    queryKey: ipoKeys.insight(id ?? -1),
    queryFn: ({ signal }) => fetchIpoInsight(id!, signal),
    enabled: id !== undefined,
    staleTime: 60 * 60 * 1000,
    retry: false,
  })
}
