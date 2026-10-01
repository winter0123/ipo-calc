import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import type { LoginRequest } from './types.ts'

export function useLogin() {
  return useMutation({
    // 잘못된 자격 증명(401)은 세션 만료가 아니므로 전역 로그인 redirect에서 제외한다.
    meta: { skipUnauthorizedRedirect: true },
    mutationFn: async (data: LoginRequest) => {
      return apiFetch('/auth/login', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },
  })
}
