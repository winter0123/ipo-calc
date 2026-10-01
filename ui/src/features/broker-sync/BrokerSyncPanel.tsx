import { useState } from 'react'
import { useSyncPreview, type UnmatchedReason } from '@/entities/broker/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import MatchedTable from './MatchedTable.tsx'
import { useConfirmSync } from './useConfirmSync.ts'
import styles from './BrokerSyncPanel.module.css'

const REASON_TEXT: Record<UnmatchedReason, string> = {
  NO_ALLOCATION_FOR_STOCK: '이 종목의 배정 기록이 없습니다 — 수동으로 등록해 주세요.',
  AMBIGUOUS_ALLOCATIONS:
    '같은 종목의 배정이 여러 건이라 자동으로 고를 수 없습니다 — 수동으로 등록해 주세요.',
  ALREADY_REGISTERED: '이미 같은 내용으로 등록돼 있습니다.',
  SPLIT_SALE_MISMATCH: '그날 등록된 매도 합계가 증권사 집계와 다릅니다 — 직접 확인해 주세요.',
  QUANTITY_EXCEEDS_ALLOCATION: '매도 수량이 배정 수량을 넘습니다 — 직접 확인해 주세요.',
}

export default function BrokerSyncPanel({ securitiesAccountId }: { securitiesAccountId: number }) {
  const [requested, setRequested] = useState(false)
  const [savedCount, setSavedCount] = useState<number | null>(null)
  const preview = useSyncPreview(securitiesAccountId, requested)
  const confirm = useConfirmSync(securitiesAccountId)

  const handleQuery = () => {
    setSavedCount(null)
    if (requested) void preview.refetch()
    else setRequested(true)
  }

  const queryError = toErrorMessage(preview.error, {
    byStatus: {
      404: '연결된 증권사 API가 없습니다. 먼저 연결해 주세요.',
      502: '증권사 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.',
      503: '서버에 증권사 연동 설정이 없어 지금은 조회할 수 없습니다.',
    },
  })
  const confirmError = toErrorMessage(confirm.error, {
    byStatus: { 400: (e) => e.serverMessage, 409: (e) => e.message },
  })
  const data = preview.data

  return (
    <section className={styles.panel} aria-labelledby="broker-sync-title">
      <h2 id="broker-sync-title" className={styles.title}>
        매도 내역 동기화
      </h2>
      <p className={styles.notice}>
        최근 89일의 증권사 매도 내역을 조회해 채워 줍니다. 확정하기 전에는 아무것도 저장되지
        않습니다.
      </p>
      <Button onClick={handleQuery} disabled={preview.isFetching}>
        {preview.isFetching ? '조회 중...' : '증권사에서 매도 내역 조회'}
      </Button>

      {queryError && (
        <p role="alert" className={styles.error}>
          {queryError}
        </p>
      )}
      {savedCount !== null && <p role="status">{savedCount}건을 매도로 등록했습니다.</p>}

      {data && !preview.isFetching && (
        <>
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
