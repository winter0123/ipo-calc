import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createAllocation } from '../../entities/allocation/api-client.ts'
import { useNavigate } from 'react-router-dom'

export function useAllocationRegister() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: createAllocation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })
      navigate('/subscriptions')
    },
  })
}