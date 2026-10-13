import { useState } from 'react'
import {
  useBrokerCredential,
  useSyncPreview,
  type SyncRange,
  type UnmatchedReason,
} from '@/entities/broker/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import MatchedTable from './MatchedTable.tsx'
import { useConfirmSync } from './useConfirmSync.ts'
import styles from './BrokerSyncPanel.module.css'
import { toLocalYmd } from '@/shared/lib/date.ts'

const REASON_TEXT: Record<UnmatchedReason, string> = {
  NO_ALLOCATION_FOR_STOCK: '이 종목의 배정 기록이 없습니다 — 수동으로 등록해 주세요.',
  AMBIGUOUS_ALLOCATIONS:
    '같은 종목의 배정이 여러 건이라 자동으로 고를 수 없습니다 — 수동으로 등록해 주세요.',
  ALREADY_REGISTERED: '이미 같은 내용으로 등록돼 있습니다.',
  SPLIT_SALE_MISMATCH: '그날 등록된 매도 합계가 증권사 집계와 다릅니다 — 직접 확인해 주세요.',
  QUANTITY_EXCEEDS_ALLOCATION: '매도 수량이 배정 수량을 넘습니다 — 직접 확인해 주세요.',
}

const DAY_MS = 24 * 60 * 60 * 1000

// LS증권은 한 번에 31일까지만 조회할 수 있다(시작일~종료일 차이 30일). 그 밖의 증권사는 기본 1년, 최대 10년이다.
const LS_RANGE = { lookbackDays: 30, maxDays: 30, label: '기본 최근 30일, 최대 31일' }
const DEFAULT_RANGE = { lookbackDays: 365, maxDays: 3650, label: '기본 최근 1년, 최대 10년' }
type RangeRule = typeof DEFAULT_RANGE

function rangeError(range: SyncRange, today: string, rule: RangeRule): string | null {
  if (!range.from || !range.to) return '조회 기간을 입력해 주세요.'
  if (range.to > today) return '조회 종료일은 오늘 이후일 수 없습니다.'
  if (range.from > range.to) return '조회 시작일은 종료일보다 늦을 수 없습니다.'
  if ((Date.parse(range.to) - Date.parse(range.from)) / DAY_MS > rule.maxDays) {
    return rule === LS_RANGE
      ? 'LS증권은 한 번에 31일까지 조회할 수 있습니다.'
      : '조회 기간은 최대 10년까지입니다.'
  }
  return null
}

export default function BrokerSyncPanel({ securitiesAccountId }: { securitiesAccountId: number }) {
  const today = toLocalYmd(new Date())
  const credential = useBrokerCredential(securitiesAccountId)
  const rule =
    (credential.data?.apiProvider ?? credential.data?.supportedProvider) === 'LS'
      ? LS_RANGE
      : DEFAULT_RANGE
  // 입력 중 값(draft)과 `조회`로 제출한 값(submitted)을 분리한다. 입력만으로는 증권사를 호출하지 않는다.
  const [edited, setDraft] = useState<SyncRange | null>(null)
  // 직접 고르기 전에는 증권사 규칙의 기본 기간을 보여 준다(연결 정보가 늦게 와도 따라간다).
  const defaultFrom = new Date()
  defaultFrom.setDate(defaultFrom.getDate() - rule.lookbackDays)
  const draft = edited ?? { from: toLocalYmd(defaultFrom), to: today }
  const [submitted, setSubmitted] = useState<SyncRange | null>(null)
  const [savedCount, setSavedCount] = useState<number | null>(null)
  const preview = useSyncPreview(securitiesAccountId, submitted)
  const confirm = useConfirmSync(securitiesAccountId)
  const draftError = rangeError(draft, today, rule)

  const handleQuery = () => {
    setSavedCount(null)
    confirm.reset()
    if (submitted && submitted.from === draft.from && submitted.to === draft.to) {
      void preview.refetch()
    } else {
      setSubmitted(draft)
    }
  }

  const queryError = toErrorMessage(preview.error, {
    byStatus: {
      404: '연결된 증권사 API가 없습니다. 먼저 연결해 주세요.',
      424: (e) => e.serverMessage,
      429: (e) => e.serverMessage,
      400: (e) => e.serverMessage,
      502: '증권사 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.',
      503: '서버에 증권사 연동 설정이 없어 지금은 조회할 수 없습니다.',
    },
  })
  const confirmError = toErrorMessage(confirm.error, {
    byStatus: { 400: (e) => e.serverMessage, 409: (e) => e.serverMessage },
    fallback: '매도 내역을 저장하지 못했습니다. 다시 조회한 뒤 시도해 주세요.',
  })
  const data = preview.data

  return (
    <section className={styles.panel} aria-labelledby="broker-sync-title">
      <h2 id="broker-sync-title" className={styles.title}>
        매도 내역 동기화
      </h2>
      <p className={styles.notice}>
        선택한 기간({rule.label})의 증권사 매도 내역을 조회해 채워 줍니다. 확정하기 전에는 아무것도
        저장되지 않습니다.
      </p>
      <div className={styles.range}>
        <div className={styles.rangeField}>
          <label htmlFor="broker-sync-from">조회 시작일</label>
          <input
            id="broker-sync-from"
            type="date"
            max={today}
            value={draft.from}
            onChange={(e) => setDraft({ ...draft, from: e.target.value })}
          />
        </div>
        <div className={styles.rangeField}>
          <label htmlFor="broker-sync-to">조회 종료일</label>
          <input
            id="broker-sync-to"
            type="date"
            max={today}
            value={draft.to}
            onChange={(e) => setDraft({ ...draft, to: e.target.value })}
          />
        </div>
      </div>
      {draftError && (
        <p role="alert" className={styles.error}>
          {draftError}
        </p>
      )}
      <Button onClick={handleQuery} disabled={preview.isFetching || draftError !== null}>
        {preview.isFetching ? '조회 중...' : '증권사에서 매도 내역 조회'}
      </Button>

      {queryError && (
        <p role="alert" className={styles.error}>
          {queryError}
        </p>
      )}
      {savedCount !== null && <p role="status">{savedCount}건을 매도로 등록했습니다.</p>}

      {data && submitted && !preview.isFetching && (
        <>
          <p>
            조회 기간: {submitted.from} ~ {submitted.to}
          </p>
          {data.matched.length === 0 && data.unmatched.length === 0 && (
            <p>조회된 매도 내역이 없습니다.</p>
          )}
          {data.matched.length > 0 && (
            <MatchedTable
              key={preview.dataUpdatedAt}
              items={data.matched}
              pending={confirm.isPending}
              onConfirm={(sales) =>
                confirm.mutate(sales, { onSuccess: () => setSavedCount(sales.length) })
              }
            />
          )}
          {confirmError && (
            <p role="alert" className={styles.error}>
              {confirmError}
            </p>
          )}
          {data.unmatched.length > 0 && (
            <div>
              <h3 className={styles.subtitle}>
                자동으로 채우지 못한 내역 ({data.unmatched.length}건)
              </h3>
              <ul className={styles.list}>
                {data.unmatched.map((item) => (
                  <li key={`${item.tradeDate}|${item.stockCode}`}>
                    {item.tradeDate} {item.stockCode} · 수량 {item.quantity} —{' '}
                    {item.reason ? REASON_TEXT[item.reason] : ''}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {data.allocationsWithoutStockCode.length > 0 && (
            <p>
              종목코드가 없는 IPO의 배정 {data.allocationsWithoutStockCode.length}건은 자동으로 맞출
              수 없으니 수동으로 등록해 주세요.
            </p>
          )}
        </>
      )}
    </section>
  )
}
