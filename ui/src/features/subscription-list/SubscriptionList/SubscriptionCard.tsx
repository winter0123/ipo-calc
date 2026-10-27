import { Link } from 'react-router-dom'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import styles, { StyleScope } from './SubscriptionList.styles.ts'
import { formatShares } from '@/shared/lib/format.ts'

interface SubscriptionCardProps {
  item: InvestmentHistoryItem
}

export default function SubscriptionCard({ item }: SubscriptionCardProps) {
  const allocation = item.allocation
  const isAllocated = allocation !== null
  const remaining = item.remainingQuantity ?? 0

  return (
    <StyleScope>
      {
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
          <div className={styles.buttonGroup}>
            <Link
              to={`/subscriptions/register?ipoId=${item.ipoId}`}
              className={styles.actionButton}
            >
              청약등록
            </Link>
            <Link
              to={`/allocations/register?subscriptionId=${item.id}`}
              className={styles.actionButton}
            >
              배정등록
            </Link>
            <Link to={`/sales/register?subscriptionId=${item.id}`} className={styles.actionButton}>
              매도등록
            </Link>
          </div>
        </li>
      }
    </StyleScope>
  )
}
