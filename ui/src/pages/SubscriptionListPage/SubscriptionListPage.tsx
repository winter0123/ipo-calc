import { useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useIpos } from '@/entities/ipo/index.ts'
import { useAllInvestmentHistory } from '@/entities/subscription/index.ts'
import {
  SubscriptionScheduleList,
  aggregateSubscriptionSchedule,
  currentMonthRange,
  filterIposBySubscriptionStart,
  parseDateRange,
  updateDateRangeParams,
} from '@/features/subscription-list/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import styles, { StyleScope } from './SubscriptionListPage.styles.ts'

export default function SubscriptionListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const fallback = currentMonthRange()
  const range = parseDateRange(searchParams.get('from'), searchParams.get('to'))
  useEffect(() => {
    if (searchParams.has('from') && searchParams.has('to')) return
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (!next.has('from')) next.set('from', fallback.from)
        if (!next.has('to')) next.set('to', fallback.to)
        return next
      },
      { replace: true },
    )
  }, [fallback.from, fallback.to, searchParams, setSearchParams])
  const { current, isLoading: investorLoading, needsSelection } = useCurrentInvestor()
  const iposQuery = useIpos()
  const historyQuery = useAllInvestmentHistory(current ? { investorId: current.id } : {}, {
    enabled: !!current,
  })
  const updateRange = (key: 'from' | 'to', value: string) => {
    setSearchParams((prev) => updateDateRangeParams(prev, key, value, fallback), { replace: true })
  }
  const schedule = aggregateSubscriptionSchedule(
    filterIposBySubscriptionStart(iposQuery.data ?? [], range),
    historyQuery.data ?? [],
  )

  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>청약 목록</h1>
            <Link to="/subscriptions/register" className={styles.registerButton}>
              새 청약 등록
            </Link>
          </div>
          <div className={styles.dateRange}>
            <label>
              시작일
              <input
                type="date"
                value={range.from}
                onChange={(event) => updateRange('from', event.target.value)}
              />
            </label>
            <label>
              종료일
              <input
                type="date"
                value={range.to}
                onChange={(event) => updateRange('to', event.target.value)}
              />
            </label>
          </div>

          {investorLoading && <p className={styles.status}>투자자 정보를 불러오는 중...</p>}
          {needsSelection && (
            <p className={styles.status} role="status">
              상단에서 투자자를 선택해 청약 상태를 확인해 주세요.
            </p>
          )}
          {!investorLoading && !current && !needsSelection && (
            <p className={styles.status} role="status">
              선택할 투자자가 없습니다.
            </p>
          )}
          {iposQuery.isLoading && <p className={styles.status}>공모주 일정을 불러오는 중...</p>}
          {current && historyQuery.isLoading && (
            <p className={styles.status}>투자내역을 불러오는 중...</p>
          )}
          {iposQuery.error && (
            <p className={styles.error} role="alert">
              {toErrorMessage(iposQuery.error, {
                fallback: '공모주 일정을 불러오는 데 실패했습니다.',
              })}
            </p>
          )}
          {current && historyQuery.error && (
            <p className={styles.error} role="alert">
              {toErrorMessage(historyQuery.error, {
                fallback: '투자내역을 불러오는 데 실패했습니다.',
              })}
            </p>
          )}
          {!iposQuery.isLoading &&
            !iposQuery.error &&
            !(current && historyQuery.isLoading) &&
            !(current && historyQuery.error) && (
              <SubscriptionScheduleList items={schedule} investorSelected={!!current} />
            )}
        </div>
      }
    </StyleScope>
  )
}
