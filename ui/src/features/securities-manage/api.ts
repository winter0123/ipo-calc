import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import {
  registerSecuritiesAccount,
  revealSecuritiesAccountNumber,
  securitiesAccountKeys,
  setDefaultSecuritiesAccount,
  updateSecuritiesAccount,
  type SecuritiesAccountRegisterRequest,
} from '@/entities/securities-account/index.ts'

const BASE = '/securities-accounts'

export type CreateAccountRequest = SecuritiesAccountRegisterRequest

// 목록이 바뀌는 mutation은 성공하면 계좌 목록을 다시 읽는다.
function useAccountMutation<V>(fn: (v: V) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: securitiesAccountKeys.all }),
  })
}

export const useCreateAccount = () => useAccountMutation(registerSecuritiesAccount)

export const useUpdateAccount = () =>
  useAccountMutation((v: { id: number; accountName: string }) =>
    updateSecuritiesAccount(v.id, { accountName: v.accountName }),
  )

export const useDeleteAccount = () =>
  useAccountMutation((id: number) => apiFetch(`${BASE}/${id}`, { method: 'DELETE' }))

export const useSetDefaultAccount = () => useAccountMutation(setDefaultSecuritiesAccount)

// 평문 계좌번호는 쿼리 캐시에 두지 않는다. 호출한 컴포넌트의 상태로만 들고 있다가 페이지를 떠나면 사라진다.
export function useRevealAccountNumber() {
  return useMutation({
    mutationFn: async (id: number) => (await revealSecuritiesAccountNumber(id)).accountNumber,
    // 평문 계좌번호가 화면을 떠난 뒤 mutation 캐시에 남지 않게 바로 버린다.
    gcTime: 0,
  })
}
