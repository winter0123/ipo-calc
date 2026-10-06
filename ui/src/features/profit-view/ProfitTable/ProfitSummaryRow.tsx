import styles from './ProfitSummaryRow.module.css'

interface ProfitSummaryRowProps {
  totalSaleAmount: number
  totalRealizedProfit: number
  totalFinalProfit: number
}

function formatNumber(value: number): string {
  return value.toLocaleString()
}

function getProfitClass(value: number): string {
  return value < 0 ? styles.negative : ''
}

export default function ProfitSummaryRow({
  totalSaleAmount,
  totalRealizedProfit,
  totalFinalProfit,
}: ProfitSummaryRowProps) {
  return (
    <tr className={styles.summaryRow}>
      <td colSpan={3} className={styles.label}>
        합계
      </td>
      <td className={`${styles.number} ${styles.totalSaleAmount}`}>
        {formatNumber(totalSaleAmount)}
      </td>
      <td colSpan={4} />
      <td className={`${styles.number} ${getProfitClass(totalRealizedProfit)}`}>
        {formatNumber(totalRealizedProfit)}
      </td>
      <td className={`${styles.number} ${getProfitClass(totalFinalProfit)}`}>
        {formatNumber(totalFinalProfit)}
      </td>
    </tr>
  )
}
