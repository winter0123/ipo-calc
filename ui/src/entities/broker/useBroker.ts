import { useQuery } from '@tanstack/react-query'
import { fetchBrokerCredential, fetchSyncPreview } from './api-client.ts'
import { brokerKeys } from './queryKeys.ts'

export function useBrokerCredential(securitiesAccountId?: number) {
  return useQuery({
    queryKey: brokerKeys.credential(securitiesAccountId),
    queryFn: ({ signal }) => fetchBrokerCredential(securitiesAccountId!, signal),
    enabled: securitiesAccountId !== undefined,
  })
}

// 증권사를 실제로 호출하므로 사용자가 `조회`를 눌렀을 때(enabled)만 실행하고 포커스 복귀 등으로 다시 부르지 않는다.
export function useSyncPreview(securitiesAccountId: number | undefined, enabled: boolean) {
  return useQuery({
    queryKey: brokerKeys.preview(securitiesAccountId),
    queryFn: ({ signal }) => fetchSyncPreview(securitiesAccountId!, signal),
    enabled: enabled && securitiesAccountId !== undefined,
    refetchOnWindowFocus: false,
    retry: false,
  })
}
