import { useQuery } from '@tanstack/react-query'
import { fetchCurrentAccount } from './api-client.ts'
import { currentAccountKeys } from './queryKeys.ts'

// 로그인 전에는 401이 정상이다(공개 경로에서는 로그인 화면으로 보내지 않는다). 재시도하지 않는다.
export function useCurrentAccount() {
  return useQuery({
    queryKey: currentAccountKeys.all,
    queryFn: ({ signal }) => fetchCurrentAccount(signal),
    retry: false,
  })
}
