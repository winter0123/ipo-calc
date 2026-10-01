import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createSubscription, investmentHistoryKeys } from '../../entities/subscription/index.ts'
import { profitKeys } from '../../entities/profit/index.ts'
import { dashboardKeys } from '../../entities/dashboard/index.ts'

export function useSubscriptionRegister() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: createSubscription,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: investmentHistoryKeys.all })
      queryClient.invalidateQueries({ queryKey: profitKeys.all })
      queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
    },
  })
}
