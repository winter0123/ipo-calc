import type { SaleProfitRow } from '../useAllInvestorProfits.ts'
import ProfitRow from './ProfitRow.tsx'
import ProfitSummaryRow from './ProfitSummaryRow.tsx'
import styles from './ProfitTable.module.css'

interface ProfitTableProps {
  sales: SaleProfitRow[]
}

export default function ProfitTable({ sales }: ProfitTableProps) {
  const sum = (pick: (s: SaleProfitRow) => number) => sales.reduce((acc, s) => acc + pick(s), 0)

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>매도일</th>
            <th>투자자</th>
            <th>수량</th>
            <th>매도금액</th>
            <th>취득금액</th>
            <th>공모주수수료</th>
            <th>세금 및 기타</th>
            <th>기타비용</th>
            <th>실현손익</th>
            <th>최종수익금</th>
          </tr>
        </thead>
        <tbody>
          {sales.length === 0 ? (
            <tr>
              <td colSpan={10} className={styles.empty}>
                매도 내역이 없습니다.
              </td>
            </tr>
          ) : (
            sales.map((profit) => <ProfitRow key={profit.saleId} profit={profit} />)
          )}
          {sales.length > 0 && (
            <ProfitSummaryRow
              totalSaleAmount={sum((s) => s.saleAmount ?? 0)}
              totalRealizedProfit={sum((s) => s.realizedProfit)}
              totalFinalProfit={sum((s) => s.afterTaxProfit)}
            />
          )}
        </tbody>
      </table>
    </div>
  )
}
