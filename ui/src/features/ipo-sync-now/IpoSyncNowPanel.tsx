import { useState } from 'react'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import type { IpoSyncStatus } from '@/entities/ipo/index.ts'
import { useIpoSyncNow } from './useIpoSyncNow.ts'
import styles, { StyleScope } from './IpoSyncNowPanel.styles.ts'

const ERROR_FALLBACK = '가져오기에 실패했습니다. 잠시 후 다시 시도해 주세요.'
const MIN_MONTH = '2015-01'
const MAX_SPAN = 24

const toIndex = (ym: string) => Number(ym.slice(0, 4)) * 12 + Number(ym.slice(5, 7))

function validateRange(from: string, to: string, max: string): string | null {
  if (!from || !to) return '시작 월과 끝 월을 모두 고르세요.'
  if (from > to) return '시작 월이 끝 월보다 늦습니다.'
  if (from < MIN_MONTH) return '시작 월은 2015-01 이후여야 합니다.'
  if (to > max) return '끝 월은 이번 달 이후일 수 없습니다.'
  if (toIndex(to) - toIndex(from) + 1 > MAX_SPAN) return '기간은 24개월을 넘을 수 없습니다.'
  return null
}

function statusText(s: IpoSyncStatus | undefined): string {
  const r = s?.result
  if (!s || !r || s.state === 'IDLE') return ''
  if (s.state === 'FAILED') return s.error ?? '가져오기에 실패했습니다.'
  if (s.state === 'RUNNING') {
    return `진행 중: ${s.monthsDone ?? 0}/${s.monthsTotal ?? 1}개월 · 새로 등록 ${r.created}건 · 상세 ${r.detailSaved}건`
  }
  return `${r.aborted ? '중간에 중단됐습니다. ' : '완료: '}새로 등록 ${r.created}건 · 갱신 ${r.updated}건 · 상세 ${r.detailSaved}건 · 실패 ${r.failed}건`
}

export default function IpoSyncNowPanel() {
  const now = new Date()
  const thisMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const [fromMonth, setFromMonth] = useState(`${now.getFullYear()}-01`)
  const [toMonth, setToMonth] = useState(thisMonth)
  const { status, start, isRunning } = useIpoSyncNow()

  const rangeError = validateRange(fromMonth, toMonth, thisMonth)
  const errorMessage = toErrorMessage(start.error, {
    byStatus: {
      400: (e) => e.serverMessage ?? ERROR_FALLBACK,
      409: '이미 가져오기가 진행 중입니다. 잠시 후 다시 시도해 주세요.',
      503: 'OpenDART 키가 설정되지 않았습니다.',
    },
    fallback: ERROR_FALLBACK,
  })
  const text = statusText(status)

  return (
    <StyleScope>
      {
        <section className={styles.panel} aria-label="DART 공모주 가져오기">
          <div className={styles.range}>
            <label className={styles.field}>
              시작 월
              <input
                type="month"
                value={fromMonth}
                min={MIN_MONTH}
                max={thisMonth}
                disabled={isRunning}
                onChange={(e) => setFromMonth(e.target.value)}
              />
            </label>
            <label className={styles.field}>
              끝 월
              <input
                type="month"
                value={toMonth}
                min={MIN_MONTH}
                max={thisMonth}
                disabled={isRunning}
                onChange={(e) => setToMonth(e.target.value)}
              />
            </label>
          </div>
          <p className={styles.description}>
            고른 기간의 공모주와 상세 정보를 DART에서 가져옵니다. 1년이면 몇 분 걸릴 수 있습니다.
            매일 새벽에는 최근 공모주를 자동으로 가져옵니다.
          </p>
          <button
            type="button"
            className={styles.button}
            disabled={isRunning || rangeError !== null}
            aria-describedby={rangeError ? 'sync-range-error' : undefined}
            onClick={() => start.mutate({ fromMonth, toMonth })}
          >
            {isRunning ? '가져오는 중…' : 'DART에서 가져오기'}
          </button>
          {rangeError && (
            <p id="sync-range-error" className={styles.error}>
              {rangeError}
            </p>
          )}
          <div role="status" aria-live="polite" className={styles.status}>
            {text}
          </div>
          {errorMessage && (
            <div role="alert" className={styles.error}>
              {errorMessage}
            </div>
          )}
        </section>
      }
    </StyleScope>
  )
}
