import styles from './ProfitSummaryRow.module.css'

interface ProfitSummaryRowProps {
  totalSaleAmount: number
  totalPreTaxProfit: number
  totalAfterTaxProfit: number
}

function formatNumber(value: number): string {
  return value.toLocaleString()
}

function getProfitClass(value: number): string {
  return value < 0 ? styles.negative : ''
}

export default function ProfitSummaryRow({
  totalSaleAmount,
  totalPreTaxProfit,
  totalAfterTaxProfit,
}: ProfitSummaryRowProps) {
  return (
    <tr className={styles.summaryRow}>
      <td colSpan={10} className={styles.label}>
        합계
      </td>
      <td className={`${styles.number} ${styles.totalSaleAmount}`}>
        {formatNumber(totalSaleAmount)}
      </td>
      <td className={`${styles.number} ${getProfitClass(totalPreTaxProfit)}`}>
        {formatNumber(totalPreTaxProfit)}
      </td>
      <td className={`${styles.number} ${getProfitClass(totalAfterTaxProfit)}`}>
        {formatNumber(totalAfterTaxProfit)}
      </td>
    </tr>
  )
}