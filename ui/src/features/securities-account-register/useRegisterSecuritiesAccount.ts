import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'

export interface RegisterSecuritiesAccountRequest {
  investorId: number
  securitiesFirmId: number
  accountNumber: string
  accountName?: string
  isDefault?: boolean
}

export interface RegisterSecuritiesAccountResponse {
  id: number
  investorId: number
  securitiesFirmId: number
  securitiesFirmName: string
  accountNumber: string
  accountName?: string
  isDefault: boolean
  status: string
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}

export function useRegisterSecuritiesAccount() {
  return useMutation({
    mutationFn: async (data: RegisterSecuritiesAccountRequest) => {
      return apiFetch<RegisterSecuritiesAccountResponse>('/securities-accounts', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },
  })
}
