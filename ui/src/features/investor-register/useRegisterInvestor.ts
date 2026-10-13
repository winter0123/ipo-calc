import { useMutation, useQueryClient } from '@tanstack/react-query'
import { investorKeys } from '@/entities/investor/index.ts'
import { apiFetch } from '@/shared/api/client.ts'

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
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (data: RegisterInvestorRequest) => {
      return apiFetch<RegisterInvestorResponse>('/investors', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },
    // 첫 투자자 등록 직후 '먼저 투자자를 등록해 주세요'가 남지 않도록 목록을 갱신한다
    onSuccess: () => queryClient.invalidateQueries({ queryKey: investorKeys.all }),
  })
}
