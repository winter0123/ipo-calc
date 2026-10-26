import { profitToneClass } from '@/shared/lib/profitTone.ts'
import type { SaleProfitRow } from '../useAllInvestorProfits.ts'
import { formatDay } from './formatDay.ts'
import styles, { StyleScope } from './ProfitRow.styles.ts'

interface ProfitRowProps {
  profit: SaleProfitRow
}

function formatNumber(value: number | null): string {
  return value === null ? '-' : value.toLocaleString()
}

export default function ProfitRow({ profit }: ProfitRowProps) {
  return (
    <StyleScope>
      {
        <tr className={styles.row}>
          <td data-label="매도일">{formatDay(profit.saleDate)}</td>
          <td data-label="투자자">{profit.investorName}</td>
          <td data-label="수량" className={styles.number}>
            {formatNumber(profit.quantity)}
          </td>
          <td data-label="매도금액" className={styles.number}>
            {formatNumber(profit.saleAmount)}
          </td>
          <td data-label="취득금액" className={styles.number}>
            {formatNumber(profit.matchedAcquisitionAmount)}
          </td>
          <td data-label="공모주수수료" className={styles.number}>
            {formatNumber(profit.saleFee)}
          </td>
          <td data-label="세금 및 기타" className={styles.number}>
            {formatNumber(profit.saleTax)}
          </td>
          <td
            data-label="실현손익"
            className={`${styles.number} ${profitToneClass(profit.realizedProfit)}`}
          >
            {formatNumber(profit.realizedProfit)}
          </td>
          <td
            data-label="최종수익금"
            className={`${styles.number} ${profitToneClass(profit.afterTaxProfit)}`}
          >
            {formatNumber(profit.afterTaxProfit)}
          </td>
        </tr>
      }
    </StyleScope>
  )
}
