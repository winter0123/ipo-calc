import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { createCost } from '../../entities/cost/index.ts'
import { investmentHistoryKeys } from '../../entities/subscription/index.ts'
import { profitKeys } from '../../entities/profit/index.ts'
import { dashboardKeys } from '../../entities/dashboard/index.ts'

export function useCostRegister() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: createCost,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: investmentHistoryKeys.all })
      queryClient.invalidateQueries({ queryKey: profitKeys.all })
      queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
      navigate('/investments')
    },
  })
}
