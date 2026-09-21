import { useState } from 'react'
import SubscriptionFilter, {
  type SubscriptionFilterValue,
} from '../../features/subscription-list/SubscriptionFilter.tsx'
import SubscriptionList from '../../features/subscription-list/SubscriptionList/SubscriptionList.tsx'
import { useInvestmentHistory } from '../../features/subscription-list/useInvestmentHistory.ts'
import styles from './SubscriptionListPage.module.css'

export default function SubscriptionListPage() {
  const [filter, setFilter] = useState<SubscriptionFilterValue>('all')
  const { data, isLoading, error } = useInvestmentHistory()

  const getErrorMessage = () => {
    if (!error) return null
    const msg = error instanceof Error ? error.message : String(error)
    if (msg.includes('Request failed: 404') || msg.includes('Not Found: 404')) {
      return '투자자 정보를 찾을 수 없습니다.'
    }
    if (
      msg.includes('Failed to fetch') ||
      msg.includes('Network error') ||
      msg.includes('Network request failed')
    ) {
      return '네트워크 연결을 확인해 주세요.'
    }
    return '청약 목록을 불러오는 데 실패했습니다.'
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>청약 목록</h1>

      <SubscriptionFilter onChange={setFilter} />

      {isLoading && <p className={styles.status}>목록을 불러오는 중...</p>}

      {error && (
        <p className={styles.error} role="alert">
          {getErrorMessage()}
        </p>
      )}

      {data && <SubscriptionList items={data.content} filter={filter} />}
    </div>
  )
}
