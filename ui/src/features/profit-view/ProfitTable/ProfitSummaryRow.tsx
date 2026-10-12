import styles from './ProfitSummaryRow.module.css'

export interface ProfitTotals {
  saleAmount: number
  acquisitionAmount: number
  fee: number
  tax: number
  realizedProfit: number
  finalProfit: number
}

function getProfitClass(value: number): string {
  return value < 0 ? styles.negative : ''
}

export default function ProfitSummaryRow({ totals }: { totals: ProfitTotals }) {
  const cell = (value: number, signed = false) => (
    <td className={`${styles.number} ${signed ? getProfitClass(value) : ''}`}>
      {value.toLocaleString()}
    </td>
  )
  return (
    <tr className={styles.summaryRow}>
      <td colSpan={3} className={styles.label}>
        합계
      </td>
      {cell(totals.saleAmount)}
      {cell(totals.acquisitionAmount)}
      {cell(totals.fee)}
      {cell(totals.tax)}
      {cell(totals.realizedProfit, true)}
      {cell(totals.finalProfit, true)}
    </tr>
  )
}
