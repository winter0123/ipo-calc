import { useMutation, useQueryClient } from '@tanstack/react-query'
import { investmentHistoryKeys } from '@/entities/subscription/index.ts'
import { profitKeys } from '@/entities/profit/index.ts'
import { dashboardKeys } from '@/entities/dashboard/index.ts'

/** 수정·삭제 성공 시 투자 내역·수익·대시보드 캐시를 무효화하는 mutation. */
export function useRecordMutation<V>(mutationFn: (variables: V) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: investmentHistoryKeys.all }),
        queryClient.invalidateQueries({ queryKey: profitKeys.all }),
        queryClient.invalidateQueries({ queryKey: dashboardKeys.all }),
      ]),
  })
}
