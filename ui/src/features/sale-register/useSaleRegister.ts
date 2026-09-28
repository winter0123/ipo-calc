import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createSale } from '../../entities/sale/api-client.ts'
import { useNavigate } from 'react-router-dom'

export function useSaleRegister() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: createSale,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['investmentHistory'] })
      navigate('/investments')
    },
  })
}
