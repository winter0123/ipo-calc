import { type InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import { statusLabel } from './statusLabel.ts'
import styles, { StyleScope } from './InvestmentHistoryTable.styles.ts'

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
    <StyleScope>
      {
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
          <td data-label="청약일">{formattedDate}</td>
          <td data-label="공모주명" className={styles.name}>
            {item.ipoName}
          </td>
          <td data-label="투자자">{item.investorName ?? '-'}</td>
          <td data-label="청약수량" className={styles.numeric}>
            {item.subscriptionQuantity}
          </td>
          <td data-label="상태" className={styles.status}>
            {statusLabel(item.subscriptionStatus)}
          </td>
          <td data-label="잔여수량" className={styles.numeric}>
            {item.remainingQuantity ?? '배정 전'}
          </td>
        </tr>
      }
    </StyleScope>
  )
}
