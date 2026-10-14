import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ipoKeys, syncIposNow } from '@/entities/ipo/index.ts'

export function useIpoSyncNow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: syncIposNow,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ipoKeys.all }),
  })
}
