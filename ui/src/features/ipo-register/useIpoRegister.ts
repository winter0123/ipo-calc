import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { registerIpo } from '@/entities/ipo/index.ts'
import { ipoKeys } from '@/entities/ipo/index.ts'

export function useIpoRegister() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: registerIpo,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ipoKeys.all })
      navigate('/ipo-calendar')
    },
  })
}
