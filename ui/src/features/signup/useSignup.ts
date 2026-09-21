import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '../../shared/api/client.ts'

export interface SignupRequest {
  email: string
  password: string
  name: string
}

export function useSignup() {
  return useMutation({
    mutationFn: async (data: SignupRequest) => {
      return apiFetch('/accounts', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },
  })
}
