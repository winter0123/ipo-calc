import { useState } from 'react'
import { useInvestmentHistoryDetail } from '../../features/investment-history/useInvestmentHistoryDetail.ts'
import InvestmentHistoryDetail from './InvestmentHistoryDetail.tsx'
import InvestmentHistoryRow from './InvestmentHistoryRow.tsx'
import type { InvestmentHistoryItem } from '../../entities/subscription/types.ts'
import styles from './InvestmentHistoryTable.module.css'

interface InvestmentHistoryTableProps {
  items: InvestmentHistoryItem[]
  onSelectSubscription?: (id: number | null) => void
}

export default function InvestmentHistoryTable({
  items,
  onSelectSubscription,
}: InvestmentHistoryTableProps) {
  const [selectedSubscriptionId, setSelectedSubscriptionId] = useState<number | null>(null)

  const {
    data: detail,
    isLoading: isDetailLoading,
    error: detailError,
  } = useInvestmentHistoryDetail(selectedSubscriptionId ?? undefined)

  const handleSelect = (subscriptionId: number) => {
    const nextId = selectedSubscriptionId === subscriptionId ? null : subscriptionId
    setSelectedSubscriptionId(nextId)
    onSelectSubscription?.(nextId)
  }

  const handleCloseDetail = () => {
    setSelectedSubscriptionId(null)
    onSelectSubscription?.(null)
  }

  return (
    <div className={styles.tableContainer}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">구독 ID</th>
            <th scope="col">구독일</th>
            <th scope="col">공모주명</th>
            <th scope="col">투자자</th>
            <th scope="col">청약수량</th>
            <th scope="col">상태</th>
            <th scope="col">잔여수량</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <InvestmentHistoryRow
              key={item.id}
              item={item}
              isSelected={selectedSubscriptionId === item.id}
              onSelect={handleSelect}
            />
          ))}
        </tbody>
      </table>

      {selectedSubscriptionId !== null && (
        <div className={styles.detailWrapper}>
          {isDetailLoading && <p>상세 정보를 불러오는 중...</p>}
          {detailError && (
            <p className={styles.error} role="alert" aria-live="polite">
              해당 구독을 찾을 수 없습니다.
            </p>
          )}
          {detail && <InvestmentHistoryDetail detail={detail} onClose={handleCloseDetail} />}
        </div>
      )}
    </div>
  )
}
