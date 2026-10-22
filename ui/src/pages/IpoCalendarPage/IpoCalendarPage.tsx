import { useIpos } from '@/entities/ipo/index.ts'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { IpoCalendar } from '@/widgets/IpoCalendar/index.ts'
import { IpoSyncNowPanel } from '@/features/ipo-sync-now/index.ts'
import {
  parseVisibleTypes,
  toTypesParam,
  type VisibleTypes,
} from '@/features/ipo-calendar/index.ts'
import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import styles from './IpoCalendarPage.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

export default function IpoCalendarPage() {
  const { data: ipos, isLoading, error } = useIpos()
  const { data: account } = useCurrentAccount()
  const [searchParams, setSearchParams] = useSearchParams()

  // searchParams가 바뀔 때만 새 Set을 만든다(렌더마다 새 참조면 하위 useMemo가 무력화된다)
  const visibleTypes = useMemo(() => parseVisibleTypes(searchParams), [searchParams])

  const handleVisibleTypesChange = (next: VisibleTypes) => {
    const nextParam = toTypesParam(next)
    setSearchParams(
      (prev) => {
        const nextParams = new URLSearchParams(prev)
        if (nextParam === null) {
          nextParams.delete('types')
        } else {
          nextParams.set('types', nextParam)
        }
        return nextParams
      },
      { replace: true },
    )
  }

  const getErrorMessage = () =>
    toErrorMessage(error, { fallback: '공모주 일정을 불러오는 데 실패했습니다.' })

  if (isLoading) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>공모주 일정</h1>
        {account?.role === 'ADMIN' && <IpoSyncNowPanel />}
        <p className={styles.loading} role="status" aria-live="polite">
          일정을 불러오는 중...
        </p>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>공모주 일정</h1>
        {account?.role === 'ADMIN' && <IpoSyncNowPanel />}
        <p className={styles.error} role="alert">
          {getErrorMessage()}
        </p>
      </div>
    )
  }

  if (!ipos || ipos.length === 0) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>공모주 일정</h1>
        {account?.role === 'ADMIN' && <IpoSyncNowPanel />}
        <p className={styles.loading} role="status">
          표시할 공모주 일정이 없습니다.
        </p>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>공모주 일정</h1>
      {account?.role === 'ADMIN' && <IpoSyncNowPanel />}
      <IpoCalendar
        ipos={ipos}
        visibleTypes={visibleTypes}
        onVisibleTypesChange={handleVisibleTypesChange}
      />
    </div>
  )
}
