import { type SaleProfit } from '@/entities/profit/index.ts'
import styles from './ProfitRow.module.css'

interface ProfitRowProps {
  profit: SaleProfit
}

function formatNumber(value: number): string {
  return value.toLocaleString()
}

function getProfitClass(value: number): string {
  return value < 0 ? styles.negative : ''
}

export default function ProfitRow({ profit }: ProfitRowProps) {
  return (
    <tr className={styles.row}>
      <td>{profit.saleId}</td>
      <td>{profit.saleDate}</td>
      <td>{profit.allocationId}</td>
      <td className={styles.number}>{formatNumber(profit.quantity)}</td>
      <td className={styles.number}>{formatNumber(profit.saleAmount)}</td>
      <td className={styles.number}>{formatNumber(profit.matchedAcquisitionAmount)}</td>
      <td className={styles.number}>{formatNumber(profit.matchedCostAmount)}</td>
      <td className={styles.number}>{formatNumber(profit.saleFee)}</td>
      <td className={styles.number}>{formatNumber(profit.saleTax)}</td>
      <td className={styles.number}>{formatNumber(profit.saleOtherCost)}</td>
      <td className={`${styles.number} ${getProfitClass(profit.preTaxProfit)}`}>
        {formatNumber(profit.preTaxProfit)}
      </td>
      <td className={`${styles.number} ${getProfitClass(profit.afterTaxProfit)}`}>
        {formatNumber(profit.afterTaxProfit)}
      </td>
    </tr>
  )
}
