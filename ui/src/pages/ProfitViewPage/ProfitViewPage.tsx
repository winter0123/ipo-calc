import { useState } from 'react'
import { useInvestors } from '@/entities/investor/index.ts'
import { ProfitTable, useAllInvestorProfits } from '@/features/profit-view/index.ts'
import styles from './ProfitViewPage.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

const pad = (n: number) => String(n).padStart(2, '0')
const toDay = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

// 접속 시점 기준 이번 달 1일 ~ 오늘
function defaultPeriod() {
  const today = new Date()
  return { from: toDay(new Date(today.getFullYear(), today.getMonth(), 1)), to: toDay(today) }
}

export default function ProfitViewPage() {
  const [period, setPeriod] = useState(defaultPeriod)
  const [investorId, setInvestorId] = useState('')
  const periodInvalid = period.from !== '' && period.to !== '' && period.from > period.to

  const { data: investors, isLoading: investorsLoading } = useInvestors()
  const shown = (investors ?? []).filter((i) => !investorId || String(i.id) === investorId)
  const { rows, isLoading, error } = useAllInvestorProfits(shown)

  const sales = periodInvalid
    ? []
    : rows.filter((s) => {
        const day = s.saleDate.slice(0, 10)
        return (!period.from || day >= period.from) && (!period.to || day <= period.to)
      })

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>수익 확인</h1>

      <div className={styles.querySection}>
        <div className={styles.inputGroup}>
          <div>
            <label htmlFor="periodFrom" className={styles.label}>
              시작일
            </label>
            <input
              id="periodFrom"
              type="date"
              className={styles.input}
              value={period.from}
              max={period.to || undefined}
              onChange={(e) => setPeriod((p) => ({ ...p, from: e.target.value }))}
            />
          </div>
          <div>
            <label htmlFor="periodTo" className={styles.label}>
              종료일
            </label>
            <input
              id="periodTo"
              type="date"
              className={styles.input}
              value={period.to}
              min={period.from || undefined}
              onChange={(e) => setPeriod((p) => ({ ...p, to: e.target.value }))}
            />
          </div>
          <div>
            <label htmlFor="investorId" className={styles.label}>
              투자자
            </label>
            <select
              id="investorId"
              className={styles.select}
              value={investorId}
              onChange={(e) => setInvestorId(e.target.value)}
              disabled={investorsLoading}
            >
              <option value="">전체 투자자</option>
              {investors?.map((investor) => (
                <option key={investor.id} value={investor.id}>
                  {investor.displayName}
                </option>
              ))}
            </select>
          </div>
          <button
            type="button"
            className={styles.searchButton}
            onClick={() => setPeriod(defaultPeriod())}
          >
            이번 달
          </button>
        </div>

        {periodInvalid && (
          <div className={styles.error} role="alert">
            시작일이 종료일보다 늦습니다.
          </div>
        )}
        {error && (
          <div className={styles.error} role="alert">
            {toErrorMessage(error, { fallback: '수익 조회에 실패했습니다.' })}
          </div>
        )}
      </div>

      {investorsLoading || isLoading ? (
        <div className={styles.loading}>수익 정보를 불러오는 중...</div>
      ) : (
        !error && <ProfitTable sales={sales} />
      )}
    </div>
  )
}
