import type { SaleProfitRow } from '../useAllInvestorProfits.ts'
import styles from './ProfitRow.module.css'

interface ProfitRowProps {
  profit: SaleProfitRow
}

function formatNumber(value: number | null): string {
  return value === null ? '-' : value.toLocaleString()
}

function getProfitClass(value: number): string {
  return value < 0 ? styles.negative : ''
}

export default function ProfitRow({ profit }: ProfitRowProps) {
  return (
    <tr className={styles.row}>
      <td>{profit.saleDate.slice(0, 10)}</td>
      <td>{profit.investorName}</td>
      <td className={styles.number}>{formatNumber(profit.quantity)}</td>
      <td className={styles.number}>{formatNumber(profit.saleAmount)}</td>
      <td className={styles.number}>{formatNumber(profit.matchedAcquisitionAmount)}</td>
      <td className={styles.number}>{formatNumber(profit.saleFee)}</td>
      <td className={styles.number}>{formatNumber(profit.saleTax)}</td>
      <td className={styles.number}>{formatNumber(profit.saleOtherCost)}</td>
      <td className={`${styles.number} ${getProfitClass(profit.realizedProfit)}`}>
        {formatNumber(profit.realizedProfit)}
      </td>
      <td className={`${styles.number} ${getProfitClass(profit.afterTaxProfit)}`}>
        {formatNumber(profit.afterTaxProfit)}
      </td>
    </tr>
  )
}
