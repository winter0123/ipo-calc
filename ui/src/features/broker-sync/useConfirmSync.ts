import { useMutation, useQueryClient } from '@tanstack/react-query'
import { brokerKeys, confirmSync, type SyncConfirmSale } from '@/entities/broker/index.ts'
import { dashboardKeys } from '@/entities/dashboard/index.ts'
import { profitKeys } from '@/entities/profit/index.ts'
import { investmentHistoryKeys } from '@/entities/subscription/index.ts'

export function useConfirmSync(securitiesAccountId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sales: SyncConfirmSale[]) => confirmSync(securitiesAccountId, sales),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: brokerKeys.preview(securitiesAccountId) })
      queryClient.invalidateQueries({ queryKey: investmentHistoryKeys.all })
      queryClient.invalidateQueries({ queryKey: profitKeys.all })
      queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
    },
  })
}
