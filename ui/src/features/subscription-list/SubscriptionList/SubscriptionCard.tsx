import { Link } from 'react-router-dom'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import styles from './SubscriptionList.module.css'
import { formatShares } from '@/shared/lib/format.ts'

interface SubscriptionCardProps {
  item: InvestmentHistoryItem
}

export default function SubscriptionCard({ item }: SubscriptionCardProps) {
  const allocation = item.allocation
  const isAllocated = allocation !== null
  const remaining = item.remainingQuantity ?? 0

  return (
    <li className={styles.card}>
      <div className={styles.cardHeader}>
        <span className={styles.ipoName}>{item.ipoName}</span>
        <span
          className={isAllocated ? styles.badgeAllocated : styles.badgeUnallocated}
          aria-label={isAllocated ? '배정완료' : '미배정'}
        >
          {isAllocated ? '배정완료' : '미배정'}
        </span>
      </div>
      <dl className={styles.cardBody}>
        <div className={styles.cardRow}>
          <dt>청약수량</dt>
          <dd>{formatShares(item.subscriptionQuantity)}</dd>
        </div>
        <div className={styles.cardRow}>
          <dt>청약일</dt>
          <dd>{new Date(item.subscribedAt).toLocaleDateString('ko-KR')}</dd>
        </div>
        {allocation && (
          <>
            <div className={styles.cardRow}>
              <dt>배정 수량</dt>
              <dd>{formatShares(allocation.allocatedQuantity)}</dd>
            </div>
            <div className={styles.cardRow}>
              <dt>남은 수량</dt>
              <dd>{formatShares(remaining)}</dd>
            </div>
          </>
        )}
      </dl>
      {isAllocated && remaining > 0 && (
        <Link to={`/sales/register?subscriptionId=${item.id}`} className={styles.allocateButton}>
          매도 등록
        </Link>
      )}
      {isAllocated && remaining <= 0 && <span className={styles.badgeAllocated}>모두 매도</span>}
      {!isAllocated && (
        <Link
          to={`/allocations/register?subscriptionId=${item.id}`}
          className={styles.allocateButton}
        >
          배정 입력
        </Link>
      )}
    </li>
  )
}
