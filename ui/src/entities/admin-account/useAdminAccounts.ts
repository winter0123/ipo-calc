import { useQuery } from '@tanstack/react-query'
import { fetchAdminAccounts } from './api-client.ts'
import { adminAccountKeys } from './queryKeys.ts'

export function useAdminAccounts(pendingOnly: boolean) {
  return useQuery({
    queryKey: adminAccountKeys.list(pendingOnly),
    queryFn: ({ signal }) => fetchAdminAccounts(pendingOnly, signal),
    retry: false,
  })
}
