import { type SaleProfit, type InvestorProfit } from '@/entities/profit/index.ts'
import ProfitRow from './ProfitRow.tsx'
import ProfitSummaryRow from './ProfitSummaryRow.tsx'
import styles from './ProfitTable.module.css'

interface ProfitTableProps {
  data: SaleProfit | InvestorProfit | null
  mode: 'sale' | 'investor'
}

export default function ProfitTable({ data, mode }: ProfitTableProps) {
  if (!data) return null

  const investorData = mode === 'investor' && 'sales' in data ? data : null
  const sales = investorData ? investorData.sales : 'sales' in data ? [] : [data]

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>매도ID</th>
            <th>매도일시</th>
            <th>배정ID</th>
            <th>수량</th>
            <th>매도금액</th>
            <th>취득금액</th>
            <th>비용금액</th>
            <th>수수료</th>
            <th>세금</th>
            <th>기타비용</th>
            <th>세전수익</th>
            <th>세후수익</th>
          </tr>
        </thead>
        <tbody>
          {sales.length === 0 ? (
            <tr>
              <td colSpan={12} className={styles.empty}>
                매도 내역이 없습니다.
              </td>
            </tr>
          ) : (
            sales.map((profit) => <ProfitRow key={profit.saleId} profit={profit} />)
          )}
          {investorData && (
            <ProfitSummaryRow
              totalSaleAmount={investorData.totalSaleAmount}
              totalPreTaxProfit={investorData.totalPreTaxProfit}
              totalAfterTaxProfit={investorData.totalAfterTaxProfit}
            />
          )}
        </tbody>
      </table>
    </div>
  )
}
