import { profitToneClass } from '@/shared/lib/profitTone.ts'
import { Link } from 'react-router-dom'
import type { SaleProfitRow } from '../useAllInvestorProfits.ts'
import ProfitRow from './ProfitRow.tsx'
import ProfitSummaryRow from './ProfitSummaryRow.tsx'
import styles, { StyleScope } from './ProfitTable.styles.ts'
import { formatWon } from '@/shared/lib/format.ts'

interface ProfitTableProps {
  sales: SaleProfitRow[]
}

export default function ProfitTable({ sales }: ProfitTableProps) {
  if (sales.length === 0) {
    return (
      <StyleScope>
        {
          <div className={styles.empty}>
            <p>이 기간에 매도 기록이 없습니다.</p>
            <p className={styles.emptyHint}>
              위의 &apos;전체&apos; 버튼으로 모든 기간을 볼 수 있어요.
            </p>
            <Link to="/sales/register" className={styles.emptyLink}>
              매도 등록
            </Link>
          </div>
        }
      </StyleScope>
    )
  }

  const sum = (pick: (s: SaleProfitRow) => number) => sales.reduce((acc, s) => acc + pick(s), 0)
  const totals = {
    saleAmount: sum((s) => s.saleAmount ?? 0),
    acquisitionAmount: sum((s) => s.matchedAcquisitionAmount ?? 0),
    fee: sum((s) => s.saleFee),
    tax: sum((s) => s.saleTax),
    realizedProfit: sum((s) => s.realizedProfit),
    finalProfit: sum((s) => s.afterTaxProfit),
  }
  const rate =
    totals.acquisitionAmount > 0
      ? `${((totals.finalProfit / totals.acquisitionAmount) * 100).toFixed(2)}%`
      : '-'

  return (
    <StyleScope>
      {
        <>
          <div className={styles.cards}>
            <div className={styles.card}>
              <div className={styles.cardLabel}>합계 매도금액</div>
              <div className={styles.cardValue}>{formatWon(totals.saleAmount)}</div>
            </div>
            <div className={styles.card}>
              <div className={styles.cardLabel}>최종수익금</div>
              <div className={`${styles.cardValue} ${profitToneClass(totals.finalProfit)}`}>
                {formatWon(totals.finalProfit)}
              </div>
            </div>
            <div className={styles.card}>
              <div className={styles.cardLabel}>수익률</div>
              <div className={`${styles.cardValue} ${profitToneClass(totals.finalProfit)}`}>
                {rate}
              </div>
            </div>
          </div>
          <div
            className={styles.tableWrapper}
            role="region"
            aria-label="매도별 수익 표"
            tabIndex={0}
          >
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>매도일</th>
                  <th>투자자</th>
                  <th className={styles.numeric}>수량</th>
                  <th className={styles.numeric}>매도금액</th>
                  <th className={styles.numeric}>취득금액</th>
                  <th className={styles.numeric}>공모주수수료</th>
                  <th className={styles.numeric}>세금 및 기타</th>
                  <th className={styles.numeric}>실현손익</th>
                  <th className={styles.numeric}>최종수익금</th>
                </tr>
              </thead>
              <tbody>
                {sales.map((profit) => (
                  <ProfitRow key={profit.saleId} profit={profit} />
                ))}
                <ProfitSummaryRow totals={totals} />
              </tbody>
            </table>
          </div>
        </>
      }
    </StyleScope>
  )
}
