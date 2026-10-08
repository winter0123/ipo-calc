import { type InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import styles from './InvestmentHistoryTable.module.css'

interface InvestmentHistoryRowProps {
  item: InvestmentHistoryItem
  isSelected: boolean
  onSelect: (subscriptionId: number) => void
}

export default function InvestmentHistoryRow({
  item,
  isSelected,
  onSelect,
}: InvestmentHistoryRowProps) {
  const formattedDate = new Date(item.subscribedAt).toLocaleDateString('ko-KR')

  return (
    <tr
      role="row"
      tabIndex={0}
      className={isSelected ? styles.selectedRow : undefined}
      aria-selected={isSelected}
      onClick={() => onSelect(item.id)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onSelect(item.id)
        }
      }}
    >
      <td>{formattedDate}</td>
      <td>{item.ipoName}</td>
      <td>{item.investorName ?? '-'}</td>
      <td className={styles.numeric}>{item.subscriptionQuantity}</td>
      <td>{item.subscriptionStatus}</td>
      <td className={styles.numeric} aria-readonly="true">
        {item.remainingQuantity ?? '-'}
      </td>
    </tr>
  )
}
