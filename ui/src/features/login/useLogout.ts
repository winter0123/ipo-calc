import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    meta: { skipUnauthorizedRedirect: true },
    mutationFn: async () => {
      return apiFetch('/auth/logout', { method: 'POST' })
    },
    onSettled: () => queryClient.clear(),
  })
}
