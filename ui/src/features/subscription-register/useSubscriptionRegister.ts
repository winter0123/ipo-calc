import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createSubscription } from '../../entities/subscription/api-client.ts'

export function useSubscriptionRegister() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: createSubscription,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })
    },
  })
}
