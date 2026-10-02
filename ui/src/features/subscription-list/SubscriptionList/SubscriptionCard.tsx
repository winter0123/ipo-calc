import { Link } from 'react-router-dom'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import styles from './SubscriptionList.module.css'

interface SubscriptionCardProps {
  item: InvestmentHistoryItem
}

export default function SubscriptionCard({ item }: SubscriptionCardProps) {
  const isAllocated = item.allocation !== null

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
          <dd>{item.subscriptionQuantity.toLocaleString()}주</dd>
        </div>
        <div className={styles.cardRow}>
          <dt>청약일</dt>
          <dd>{new Date(item.subscribedAt).toLocaleDateString('ko-KR')}</dd>
        </div>
      </dl>
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
