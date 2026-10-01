import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createSale } from '../../entities/sale/index.ts'
import { useNavigate } from 'react-router-dom'
import { investmentHistoryKeys } from '../../entities/subscription/index.ts'
import { profitKeys } from '../../entities/profit/index.ts'
import { dashboardKeys } from '../../entities/dashboard/index.ts'

export function useSaleRegister() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: createSale,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: investmentHistoryKeys.all })
      queryClient.invalidateQueries({ queryKey: profitKeys.all })
      queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
      navigate('/investments')
    },
  })
}
