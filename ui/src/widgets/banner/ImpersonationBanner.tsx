import { useCallback, useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { useImpersonation, impersonationKeys } from '@/entities/impersonation/index.ts'
import { dateOnly } from '@/shared/lib/date.ts'
import styles, { StyleScope } from './ImpersonationBanner.styles.ts'

const STORAGE_CHANGE_KEY = 'impersonation-changed'

/**
 * 임퍼소네이션 배너.
 * 조회 대상·읽기전용·만료 시각을 보여 주고 종료 버튼을 제공한다.
 * 다른 탭에서 시작/종료가 일어나면 캐시를 비우고 상태를 재조회한다.
 */
export default function ImpersonationBanner() {
  const queryClient = useQueryClient()
  const { data: account } = useCurrentAccount()
  const isAdmin = account?.role === 'ADMIN'
  const { isImpersonating, targetAccountId, expiresAt, end, isPending, endError } =
    useImpersonation({ isAdmin })

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_CHANGE_KEY) {
        void queryClient.cancelQueries()
        queryClient.clear()
        void queryClient.invalidateQueries({ queryKey: impersonationKeys.status() })
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [queryClient])

  const handleEnd = useCallback(async () => {
    try {
      await end()
    } catch {
      // 종료 오류는 endError로 노출
    }
  }, [end])

  if (!isAdmin || !isImpersonating) return null

  return (
    <StyleScope>
      <section className={styles.banner} aria-label="임퍼소네이션 상태">
        <span className={styles.label}>임퍼소네이션 중</span>
        <span className={styles.target}>대상 계정: {String(targetAccountId ?? '-')}</span>
        <span className={styles.readonly}>읽기 전용</span>
        {expiresAt && <span className={styles.expires}>만료: {dateOnly(expiresAt)}</span>}
        <button type="button" className={styles.endButton} disabled={isPending} onClick={handleEnd}>
          {isPending ? '종료 중...' : '임퍼소네이션 종료'}
        </button>
        {endError && <span className={styles.error}>종료하지 못했습니다.</span>}
      </section>
    </StyleScope>
  )
}
