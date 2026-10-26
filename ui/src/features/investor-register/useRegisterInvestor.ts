import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  investorKeys,
  registerInvestor,
  type InvestorRegisterRequest,
  type InvestorResponse,
} from '@/entities/investor/index.ts'

export type RegisterInvestorRequest = InvestorRegisterRequest
export type RegisterInvestorResponse = InvestorResponse

export function useRegisterInvestor() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: registerInvestor,
    // 첫 투자자 등록 직후 '먼저 투자자를 등록해 주세요'가 남지 않도록 목록을 갱신한다
    onSuccess: () => queryClient.invalidateQueries({ queryKey: investorKeys.all }),
  })
}
