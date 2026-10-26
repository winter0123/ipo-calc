import { useMutation } from '@tanstack/react-query'
import { registerAccount } from './api-client.ts'
import type { SignupRequest } from './types.ts'

export type { SignupRequest } from './types.ts'

export function useSignup() {
  return useMutation({
    mutationFn: (data: SignupRequest) => registerAccount(data),
  })
}
