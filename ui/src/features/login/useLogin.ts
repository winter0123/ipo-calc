import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '../../shared/api/client.ts'
import type { LoginRequest } from '../../entities/auth/types.ts'

export function useLogin() {
  return useMutation({
    mutationFn: async (data: LoginRequest) => {
      return apiFetch('/auth/login', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },
  })
}
