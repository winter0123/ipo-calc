import { type InvestmentHistoryItem } from '../../../entities/subscription/index.ts'
import type { SubscriptionFilterValue } from '../SubscriptionFilter.tsx'
import SubscriptionCard from './SubscriptionCard.tsx'
import styles from './SubscriptionList.module.css'

interface SubscriptionListProps {
  items: InvestmentHistoryItem[]
  filter: SubscriptionFilterValue
}

function applyFilter(items: InvestmentHistoryItem[], filter: SubscriptionFilterValue) {
  if (filter === 'unallocated') return items.filter((item) => item.allocation === null)
  if (filter === 'allocated') return items.filter((item) => item.allocation !== null)
  return items
}

export default function SubscriptionList({ items, filter }: SubscriptionListProps) {
  const filtered = applyFilter(items, filter)

  if (filtered.length === 0) {
    return <p className={styles.empty}>표시할 청약이 없습니다.</p>
  }

  return (
    <ul className={styles.list}>
      {filtered.map((item) => (
        <SubscriptionCard key={item.id} item={item} />
      ))}
    </ul>
  )
}
