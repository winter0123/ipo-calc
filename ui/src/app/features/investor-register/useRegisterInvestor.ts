import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '../../shared/api/client.ts'

export interface RegisterInvestorRequest {
  displayName: string
  relationship?: string
}

export interface RegisterInvestorResponse {
  id: number
  displayName: string
  accountId: number
  status: string
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}

export function useRegisterInvestor() {
  return useMutation({
    mutationFn: async (data: RegisterInvestorRequest) => {
      return apiFetch<RegisterInvestorResponse>('/investors', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },
  })
}
