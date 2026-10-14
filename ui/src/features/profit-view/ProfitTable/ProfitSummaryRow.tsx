import { profitToneClass } from '@/shared/lib/profitTone.ts'
import styles from './ProfitSummaryRow.module.css'

export interface ProfitTotals {
  saleAmount: number
  acquisitionAmount: number
  fee: number
  tax: number
  realizedProfit: number
  finalProfit: number
}

export default function ProfitSummaryRow({ totals }: { totals: ProfitTotals }) {
  const cell = (label: string, value: number, signed = false) => (
    <td data-label={label} className={`${styles.number} ${signed ? profitToneClass(value) : ''}`}>
      {value.toLocaleString()}
    </td>
  )
  return (
    <tr className={styles.summaryRow}>
      <td colSpan={3} className={styles.label}>
        합계
      </td>
      {cell('매도금액', totals.saleAmount)}
      {cell('취득금액', totals.acquisitionAmount)}
      {cell('공모주수수료', totals.fee)}
      {cell('세금 및 기타', totals.tax)}
      {cell('실현손익', totals.realizedProfit, true)}
      {cell('최종수익금', totals.finalProfit, true)}
    </tr>
  )
}
