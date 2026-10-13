import { Link } from 'react-router-dom'
import { useIpos } from '@/entities/ipo/index.ts'
import { buildUpcomingEvents } from './upcomingEvents.ts'
import styles from './DashboardHome.module.css'

export default function DashboardUpcoming() {
  const { data, isLoading, error } = useIpos()
  const events = buildUpcomingEvents(data ?? [], new Date())

  const renderBody = () => {
    if (isLoading) return <p className={styles.muted}>불러오는 중...</p>
    if (error) {
      return (
        <p className={styles.error} role="alert">
          공모주 일정을 불러오는 데 실패했습니다.
        </p>
      )
    }
    if (events.length === 0) return <p className={styles.muted}>7일 안에 예정된 일정이 없습니다.</p>
    return (
      <ul className={styles.list}>
        {events.map((e) => (
          <li key={e.key} className={styles.row}>
            <span>
              {new Date(`${e.date}T00:00:00`).toLocaleDateString('ko-KR')} · {e.kind}
              {e.estimated && ' (예상)'} · <Link to={`/ipos/${e.ipoId}`}>{e.ipoName}</Link>
            </span>
            {e.canSubscribe && (
              <Link to={`/subscriptions/register?ipoId=${e.ipoId}`}>청약 등록</Link>
            )}
          </li>
        ))}
      </ul>
    )
  }

  return (
    <section aria-labelledby="dash-upcoming" className={styles.card}>
      <h2 id="dash-upcoming" className={styles.title}>
        다가오는 일정
      </h2>
      {renderBody()}
      <Link to="/ipo-calendar" className={styles.more}>
        공모주 일정 전체 보기
      </Link>
    </section>
  )
}
