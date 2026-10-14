import { profitToneClass } from '@/shared/lib/profitTone.ts'
import { lazy, Suspense, useState } from 'react'
import type { SaleProfitRow } from '../useAllInvestorProfits.ts'
import { groupProfits, type PeriodUnit } from './groupProfits.ts'
import styles from './ProfitPeriodSection.module.css'

// 차트 라이브러리는 이 화면에서만 필요해 따로 불러온다.
const ProfitLineChart = lazy(() => import('./ProfitLineChart.tsx'))

const UNITS: { unit: PeriodUnit; label: string }[] = [
  { unit: 'year', label: '연' },
  { unit: 'month', label: '월' },
  { unit: 'day', label: '일' },
]

const num = (v: number) => v.toLocaleString()

export default function ProfitPeriodSection({
  sales,
  from,
  to,
}: {
  sales: SaleProfitRow[]
  from: string
  to: string
}) {
  const [unit, setUnit] = useState<PeriodUnit>('month')
  const groups = groupProfits(sales, unit, from, to)
  const total = groups.reduce(
    (a, g) => ({
      count: a.count + g.count,
      saleAmount: a.saleAmount + g.saleAmount,
      profit: a.profit + g.profit,
    }),
    { count: 0, saleAmount: 0, profit: 0 },
  )
  const neg = profitToneClass

  return (
    <section aria-labelledby="profitPeriodTitle" className={styles.section}>
      <div className={styles.head}>
        <h2 id="profitPeriodTitle" className={styles.title}>
          기간별 수익
        </h2>
        <div className={styles.units} role="group" aria-label="집계 단위">
          {UNITS.map((u) => (
            <button
              key={u.unit}
              type="button"
              className={u.unit === unit ? styles.active : styles.unit}
              aria-pressed={u.unit === unit}
              onClick={() => setUnit(u.unit)}
            >
              {u.label}
            </button>
          ))}
        </div>
      </div>
      <p className={styles.hint}>공모주 상장일 기준, 최종수익금(원)입니다.</p>

      <div className={styles.chart}>
        <Suspense fallback={<div className={styles.loading}>그래프를 불러오는 중...</div>}>
          <ProfitLineChart data={groups} />
        </Suspense>
      </div>

      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">기간</th>
            <th scope="col" className={styles.numeric}>
              매도 건수
            </th>
            <th scope="col" className={styles.numeric}>
              매도금액
            </th>
            <th scope="col" className={styles.numeric}>
              최종수익금
            </th>
          </tr>
        </thead>
        <tbody>
          {/* 그래프는 빈 기간을 0으로 이어 그리지만 표는 매도가 있는 기간만 보여 준다 */}
          {groups
            .filter((g) => g.count > 0)
            .map((g) => (
              <tr key={g.key}>
                <th scope="row">{g.title}</th>
                <td className={styles.numeric}>{num(g.count)}</td>
                <td className={styles.numeric}>{num(g.saleAmount)}</td>
                <td className={`${styles.numeric} ${neg(g.profit)}`}>{num(g.profit)}</td>
              </tr>
            ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">합계</th>
            <td className={styles.numeric}>{num(total.count)}</td>
            <td className={styles.numeric}>{num(total.saleAmount)}</td>
            <td className={`${styles.numeric} ${neg(total.profit)}`}>{num(total.profit)}</td>
          </tr>
        </tfoot>
      </table>
    </section>
  )
}
