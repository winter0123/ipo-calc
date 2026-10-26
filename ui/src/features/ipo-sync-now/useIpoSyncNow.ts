import { useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getIpoSyncStatus, ipoKeys, syncIposNow } from '@/entities/ipo/index.ts'

const POLL_MS = 3000

export function useIpoSyncNow() {
  const queryClient = useQueryClient()

  // 마운트 시 한 번 읽고, 실행 중인 동안만 3초마다 다시 읽는다.
  const status = useQuery({
    queryKey: ipoKeys.syncStatus(),
    queryFn: ({ signal }) => getIpoSyncStatus(signal),
    refetchInterval: (query) => (query.state.data?.state === 'RUNNING' ? POLL_MS : false),
  })

  const start = useMutation({
    mutationFn: (range: { fromMonth: string; toMonth: string }) => syncIposNow(range),
    onSuccess: (next) => queryClient.setQueryData(ipoKeys.syncStatus(), next),
  })

  // 실행이 끝나는 순간(완료·실패 모두 일부는 저장됨) 공모주 캐시를 새로 읽게 한다.
  const state = status.data?.state
  const wasRunning = useRef(false)
  useEffect(() => {
    if (wasRunning.current && state !== 'RUNNING') {
      void queryClient.invalidateQueries({ queryKey: ipoKeys.all })
    }
    wasRunning.current = state === 'RUNNING'
  }, [state, queryClient])

  return { status: status.data, start, isRunning: state === 'RUNNING' || start.isPending }
}
