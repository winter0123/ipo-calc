import { useSearchParams } from 'react-router-dom'
import {
  SubscriptionFilter,
  SubscriptionList,
  parseSubscriptionFilter,
  type SubscriptionFilterValue,
} from '@/features/subscription-list/index.ts'
import { useInvestmentHistory } from '@/entities/subscription/index.ts'
import styles from './SubscriptionListPage.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

export default function SubscriptionListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = parseSubscriptionFilter(searchParams.get('status'))
  const setFilter = (value: SubscriptionFilterValue) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value === 'all') next.delete('status')
        else next.set('status', value)
        return next
      },
      { replace: true },
    )
  }
  const { data, isLoading, error } = useInvestmentHistory()

  const getErrorMessage = () =>
    toErrorMessage(error, {
      byStatus: { 404: '투자자 정보를 찾을 수 없습니다.' },
      fallback: '청약 목록을 불러오는 데 실패했습니다.',
    })

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>청약 목록</h1>

      <SubscriptionFilter value={filter} onChange={setFilter} />

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
