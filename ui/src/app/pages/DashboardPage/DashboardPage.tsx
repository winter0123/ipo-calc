import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchInvestors } from '../../entities/investor/api-client.ts'
import styles from './DashboardPage.module.css'

export default function DashboardPage() {
  const {
    data: investors,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['investors'],
    queryFn: fetchInvestors,
  })

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>대시보드</h1>

      {isLoading && <p className={styles.status}>투자자 목록을 불러오는 중...</p>}
      {error && <p className={styles.error}>투자자 목록을 불러올 수 없습니다.</p>}

      {investors && investors.length === 0 && (
        <p className={styles.status}>등록된 투자자가 없습니다.</p>
      )}

      {investors && investors.length > 0 && (
        <ul className={styles.investorList}>
          {investors.map((investor) => (
            <li key={investor.id} className={styles.investorItem}>
              <span className={styles.investorName}>{investor.displayName}</span>
              <Link
                to={`/securities-accounts/register/${investor.id}`}
                className={styles.registerButton}
              >
                증권계좌 등록
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
