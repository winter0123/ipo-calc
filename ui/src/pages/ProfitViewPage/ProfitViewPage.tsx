import { useState } from 'react'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { ProfitTable, useAllInvestorProfits } from '@/features/profit-view/index.ts'
import styles from './ProfitViewPage.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

const pad = (n: number) => String(n).padStart(2, '0')
const toDay = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

const PRESETS = [
  {
    label: '이번 달',
    period: () => {
      const t = new Date()
      return { from: toDay(new Date(t.getFullYear(), t.getMonth(), 1)), to: toDay(t) }
    },
  },
  {
    label: '지난 달',
    period: () => {
      const t = new Date()
      return {
        from: toDay(new Date(t.getFullYear(), t.getMonth() - 1, 1)),
        to: toDay(new Date(t.getFullYear(), t.getMonth(), 0)),
      }
    },
  },
  {
    label: '올해',
    period: () => {
      const t = new Date()
      return { from: toDay(new Date(t.getFullYear(), 0, 1)), to: toDay(t) }
    },
  },
  { label: '전체', period: () => ({ from: '', to: '' }) },
]

// 접속 시점 기준 이번 달 1일 ~ 오늘
const defaultPeriod = PRESETS[0].period

export default function ProfitViewPage() {
  const [period, setPeriod] = useState(defaultPeriod)
  const periodInvalid = period.from !== '' && period.to !== '' && period.from > period.to

  // 현재 투자자(상단 전환기) 기준으로만 보여 준다. 기간 필터는 서버에 맡긴다.
  const { current, isLoading: investorsLoading } = useCurrentInvestor()
  const { rows: sales, isLoading, error } = useAllInvestorProfits(current ? [current] : [], period)

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
          {PRESETS.map((preset) => {
            const target = preset.period()
            const selected = target.from === period.from && target.to === period.to
            return (
              <button
                key={preset.label}
                type="button"
                className={selected ? styles.searchButton : styles.presetButton}
                aria-pressed={selected}
                onClick={() => setPeriod(target)}
              >
                {preset.label}
              </button>
            )
          })}
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
