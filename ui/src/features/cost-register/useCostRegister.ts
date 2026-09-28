import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createCost } from '../../entities/cost/api-client.ts'

export function useCostRegister() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: createCost,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['costs'] })
    },
  })
}