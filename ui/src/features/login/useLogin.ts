import { useMutation, useQueryClient } from '@tanstack/react-query'
import { currentAccountKeys } from '@/entities/current-account/index.ts'
import { apiFetch } from '@/shared/api/client.ts'
import type { LoginRequest } from './types.ts'

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    // 잘못된 자격 증명(401)은 세션 만료가 아니므로 전역 로그인 redirect에서 제외한다.
    meta: { skipUnauthorizedRedirect: true },
    mutationFn: async (data: LoginRequest) => {
      return apiFetch('/auth/login', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },
    // 로그인 전에 401로 캐시된 현재 계정 정보를 새로 받아 역할별 메뉴가 바로 보이게 한다.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: currentAccountKeys.all }),
  })
}
