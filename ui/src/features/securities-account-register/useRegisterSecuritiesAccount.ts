import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  registerSecuritiesAccount,
  securitiesAccountKeys,
  type SecuritiesAccount,
  type SecuritiesAccountRegisterRequest,
} from '@/entities/securities-account/index.ts'

export type RegisterSecuritiesAccountRequest = SecuritiesAccountRegisterRequest
export type RegisterSecuritiesAccountResponse = SecuritiesAccount

export function useRegisterSecuritiesAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: registerSecuritiesAccount,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: securitiesAccountKeys.all }),
  })
}
