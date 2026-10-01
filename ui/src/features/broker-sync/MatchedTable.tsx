import { useState } from 'react'
import type { SyncConfirmSale, SyncPreviewItem } from '@/entities/broker/index.ts'
import Button from '@/shared/components/Button/Button.tsx'
import styles from './BrokerSyncPanel.module.css'

type EditField = 'price' | 'amount' | 'fee' | 'tax'

interface Row {
  checked: boolean
  price: string
  amount: string
  fee: string
  tax: string
}

const FIELD_LABEL: Record<EditField, string> = {
  price: '단가',
  amount: '금액',
  fee: '수수료',
  tax: '세금',
}

const rowKey = (item: SyncPreviewItem) => `${item.tradeDate}|${item.stockCode}`
const isWholeNumber = (value: string) => /^\d+$/.test(value.trim())

interface Props {
  items: SyncPreviewItem[]
  pending: boolean
  onConfirm: (sales: SyncConfirmSale[]) => void
}

// 미리보기 값을 사용자가 고쳐서 확정할 수 있다. 새 미리보기가 오면 부모가 key로 다시 마운트한다.
export default function MatchedTable({ items, pending, onConfirm }: Props) {
  const [rows, setRows] = useState<Record<string, Row>>(() =>
    Object.fromEntries(
      items.map((item) => [
        rowKey(item),
        {
          checked: true,
          price: String(item.price),
          amount: String(item.amount),
          fee: String(item.fee),
          tax: String(item.tax),
        },
      ]),
    ),
  )

  const update = (key: string, patch: Partial<Row>) =>
    setRows((prev) => ({ ...prev, [key]: { ...prev[key], ...patch } }))

  const selected = items.filter((item) => rows[rowKey(item)].checked)
  const allValid = selected.every((item) =>
    (['price', 'amount', 'fee', 'tax'] as EditField[]).every((f) =>
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
          fee: Number(row.fee),
          tax: Number(row.tax),
          saleDate: `${item.tradeDate}T00:00:00`,
        }
      }),
    )

  return (
    <div>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <caption>매칭된 매도 ({items.length}건) — 값을 고친 뒤 확정할 수 있습니다</caption>
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
          단가·금액·수수료·세금은 0 이상의 정수로 입력해 주세요.
        </p>
      )}
      <Button onClick={handleConfirm} disabled={pending || selected.length === 0 || !allValid}>
        {pending ? '저장 중...' : `선택한 ${selected.length}건 매도로 등록`}
      </Button>
    </div>
  )
}
