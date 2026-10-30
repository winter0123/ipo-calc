import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { endImpersonation, impersonationStatus, startImpersonation } from './api-client.ts'
import { impersonationKeys } from './queryKeys.ts'
import { csrfBootstrap } from '@/shared/api/client.ts'

export const IMPERSONATION_CHANGED_KEY = 'impersonation-changed'

export function broadcastImpersonationChange() {
  try {
    localStorage.setItem(IMPERSONATION_CHANGED_KEY, String(Date.now()))
    localStorage.removeItem(IMPERSONATION_CHANGED_KEY)
  } catch {
    // 저장소 접근 불가 환경은 무시한다.
  }
}

/**
 * 임퍼소네이션 상태와 시작/종료를 다루는 훅.
 * actor(로그인 관리자) 인증 정보와 subject(조회 대상 회원) 컨텍스트를 분리한다.
 * 호출 측이 isAdmin을 넘겨 주며, 상태 API는 ADMIN 역할일 때만 호출된다.
 */
export function useImpersonation({ isAdmin }: { isAdmin: boolean }) {
  const queryClient = useQueryClient()

  const status = useQuery({
    queryKey: impersonationKeys.status(),
    queryFn: ({ signal }) => impersonationStatus(signal),
    enabled: isAdmin,
    retry: false,
  })

  const startMutation = useMutation({
    mutationFn: startImpersonation,
    onSuccess: async () => {
      await queryClient.cancelQueries()
      queryClient.clear()
      try {
        localStorage.removeItem('currentInvestorId')
      } catch {
        // 저장소 접근 불가 환경은 무시한다.
      }
      broadcastImpersonationChange()
    },
  })

  const endMutation = useMutation({
    mutationFn: endImpersonation,
    onSuccess: async () => {
      await queryClient.cancelQueries()
      queryClient.clear()
      try {
        localStorage.removeItem('currentInvestorId')
      } catch {
        // 저장소 접근 불가 환경은 무시한다.
      }
      broadcastImpersonationChange()
    },
  })

  const start = async (accountId: number) => {
    if (!isAdmin) return
    const token = document.cookie.split(';').some((part) => part.trim().startsWith('XSRF-TOKEN='))
    if (!token) await csrfBootstrap()
    return startMutation.mutateAsync(accountId)
  }

  const end = () => endMutation.mutateAsync(undefined)

  return {
    isPending: status.isPending || startMutation.isPending || endMutation.isPending,
    data: status.data,
    isImpersonating: Boolean(status.data?.impersonating),
    targetAccountId: status.data?.targetAccountId ?? null,
    expiresAt: status.data?.expiresAt ?? null,
    start,
    end,
    startError: startMutation.error,
    endError: endMutation.error,
  }
}
