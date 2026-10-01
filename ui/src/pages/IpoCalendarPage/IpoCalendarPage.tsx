import { useIpos } from '../../entities/ipo/index.ts'
import { IpoCalendar } from '../../widgets/IpoCalendar/index.ts'
import styles from './IpoCalendarPage.module.css'
import { toErrorMessage } from '../../shared/api/apiError.ts'

export default function IpoCalendarPage() {
  const { data: ipos, isLoading, error } = useIpos()

  const getErrorMessage = () =>
    toErrorMessage(error, { fallback: '공모주 일정을 불러오는 데 실패했습니다.' })

  if (isLoading) {
    return (
      <div className={styles.container}>
        <h1 className={styles.title}>공모주 일정</h1>
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
        <p className={styles.loading} role="status">
          표시할 공모주 일정이 없습니다.
        </p>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>공모주 일정</h1>
      <IpoCalendar ipos={ipos} />
    </div>
  )
}
