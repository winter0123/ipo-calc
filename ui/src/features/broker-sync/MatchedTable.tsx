import { useState } from 'react'
import type { SyncConfirmSale, SyncPreviewItem } from '@/entities/broker/index.ts'
import Button from '@/shared/components/Button/Button.tsx'
import styles from './BrokerSyncPanel.module.css'

type EditField = 'price' | 'amount' | 'otherFees' | 'ipoFee'

interface Row {
  checked: boolean
  price: string
  amount: string
  otherFees: string
  ipoFee: string
}

const FIELD_LABEL: Record<EditField, string> = {
  price: '단가',
  amount: '금액',
  otherFees: '세금 및 기타',
  ipoFee: '공모주수수료',
}

// 공모주수수료는 배정 1건당 한 번이라, 같은 배정의 매도가 여러 건이면 첫 건에만 기본값을 넣는다.
const DEFAULT_IPO_FEE = 2000

const rowKey = (item: SyncPreviewItem) => `${item.tradeDate}|${item.stockCode}`
const isWholeNumber = (value: string) => /^\d+$/.test(value.trim())

interface Props {
  items: SyncPreviewItem[]
  pending: boolean
  onConfirm: (sales: SyncConfirmSale[]) => void
}

// 미리보기 값을 사용자가 고쳐서 확정할 수 있다. 새 미리보기가 오면 부모가 key로 다시 마운트한다.
export default function MatchedTable({ items, pending, onConfirm }: Props) {
  const [rows, setRows] = useState<Record<string, Row>>(() => {
    const seenAllocations = new Set<number | null>()
    return Object.fromEntries(
      items.map((item) => {
        const first = !seenAllocations.has(item.allocationId)
        seenAllocations.add(item.allocationId)
        return [
          rowKey(item),
          {
            checked: true,
            price: String(item.price),
            amount: String(item.amount),
            // 증권사가 알려 준 수수료와 세금의 합이 "세금 및 기타 수수료"다.
            otherFees: String(item.fee + item.tax),
            ipoFee: String(first ? DEFAULT_IPO_FEE : 0),
          },
        ]
      }),
    )
  })

  const update = (key: string, patch: Partial<Row>) =>
    setRows((prev) => ({ ...prev, [key]: { ...prev[key], ...patch } }))

  const selected = items.filter((item) => rows[rowKey(item)].checked)
  const allValid = selected.every((item) =>
    (['price', 'amount', 'otherFees', 'ipoFee'] as EditField[]).every((f) =>
      isWholeNumber(rows[rowKey(item)][f]),
    ),
  )

  const handleConfirm = () =>
    onConfirm(
      selected.map((item) => {
        const row = rows[rowKey(item)]
        return {
          allocationId: item.allocationId!,
          quantity: item.quantity,
          price: Number(row.price),
          amount: Number(row.amount),
          fee: Number(row.ipoFee),
          tax: Number(row.otherFees),
          saleDate: `${item.tradeDate}T00:00:00`,
        }
      }),
    )

  return (
    <div>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <caption>
            매칭된 매도 ({items.length}건) — 값을 고친 뒤 확정할 수 있습니다. 세금 및 기타는
            증권사의 수수료와 세금의 합계이고, 공모주수수료는 기본 2,000원(같은 배정의 둘째 매도부터
            0원)입니다.
          </caption>
          <thead>
            <tr>
              <th scope="col">선택</th>
              <th scope="col">일자</th>
              <th scope="col">종목코드</th>
              <th scope="col">수량</th>
              {(Object.keys(FIELD_LABEL) as EditField[]).map((f) => (
                <th key={f} scope="col">
                  {FIELD_LABEL[f]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {items.map((item) => {
              const key = rowKey(item)
              const row = rows[key]
              const name = `${item.tradeDate} ${item.stockCode}`
              return (
                <tr key={key}>
                  <td>
                    <input
                      type="checkbox"
                      aria-label={`${name} 선택`}
                      checked={row.checked}
                      onChange={(e) => update(key, { checked: e.target.checked })}
                    />
                  </td>
                  <td>{item.tradeDate}</td>
                  <td>{item.stockCode}</td>
                  <td>{item.quantity}</td>
                  {(Object.keys(FIELD_LABEL) as EditField[]).map((f) => (
                    <td key={f}>
                      <input
                        type="text"
                        inputMode="numeric"
                        aria-label={`${name} ${FIELD_LABEL[f]}`}
                        aria-invalid={isWholeNumber(row[f]) ? undefined : 'true'}
                        value={row[f]}
                        onChange={(e) => update(key, { [f]: e.target.value })}
                      />
                    </td>
                  ))}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {!allValid && (
        <p role="alert" className={styles.error}>
          단가·금액·세금 및 기타·공모주수수료는 0 이상의 정수로 입력해 주세요.
        </p>
      )}
      <Button onClick={handleConfirm} disabled={pending || selected.length === 0 || !allValid}>
        {pending ? '저장 중...' : `선택한 ${selected.length}건 매도로 등록`}
      </Button>
    </div>
  )
}
