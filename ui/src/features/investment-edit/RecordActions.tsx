import { useRef, useState } from 'react'
import { deleteSubscription } from '@/entities/subscription/index.ts'
import { deleteAllocation } from '@/entities/allocation/index.ts'
import { deleteSale } from '@/entities/sale/index.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { useRecordMutation } from './useRecordMutation.ts'
import { AllocationEditDialog } from './AllocationEditDialog.tsx'
import { SaleEditDialog } from './SaleEditDialog.tsx'
import { SubscriptionEditDialog } from './SubscriptionEditDialog.tsx'
import styles, { StyleScope } from './RecordActions.styles.ts'

export type RecordKind = 'subscription' | 'allocation' | 'sale'

const DELETE_BY_KIND = {
  subscription: deleteSubscription,
  allocation: deleteAllocation,
  sale: deleteSale,
} as const

const KIND_LABEL: Record<RecordKind, string> = {
  subscription: '청약',
  allocation: '배정',
  sale: '매도',
}

interface RecordActionsProps {
  kind: RecordKind
  id: number
  /** 삭제 확인 문구에 쓰는 대상 설명(예: "배정 100주") */
  label?: string
  /** 삭제가 끝난 뒤(예: 청약 삭제면 상세 패널을 닫는다) */
  onDeleted?: () => void
}

/** 투자 내역 상세의 청약·배정·매도 항목 옆에 놓는 수정·삭제 버튼과 그 대화상자. */
export default function RecordActions({ kind, id, label, onDeleted }: RecordActionsProps) {
  const [mode, setMode] = useState<'edit' | 'delete' | null>(null)
  const name = KIND_LABEL[kind]
  const deleting = useRef(false)
  const remove = useRecordMutation(DELETE_BY_KIND[kind])
  const close = () => setMode(null)

  return (
    <StyleScope>
      {
        <>
          <span className={styles.actions}>
            <button
              type="button"
              className={styles.smallButton}
              onClick={() => setMode('edit')}
              aria-label={`${name} 수정`}
            >
              수정
            </button>
            <button
              type="button"
              className={`${styles.smallButton} ${styles.danger}`}
              onClick={() => {
                remove.reset()
                setMode('delete')
              }}
              aria-label={`${name} 삭제`}
            >
              삭제
            </button>
          </span>

          {remove.isError && mode === null && (
            <p role="alert" className={styles.error}>
              {toErrorMessage(remove.error)}
            </p>
          )}

          {mode === 'edit' && kind === 'subscription' && (
            <SubscriptionEditDialog id={id} onClose={close} />
          )}
          {mode === 'edit' && kind === 'allocation' && (
            <AllocationEditDialog id={id} onClose={close} />
          )}
          {mode === 'edit' && kind === 'sale' && <SaleEditDialog id={id} onClose={close} />}

          <ConfirmDialog
            isOpen={mode === 'delete'}
            title={`${name} 삭제`}
            message={`${label ?? name}을(를) 삭제할까요?`}
            confirmLabel="삭제"
            onCancel={close}
            pending={remove.isPending}
            onConfirm={() => {
              // 같은 틱의 연속 클릭은 isPending이 아직 false라 ref로 막는다.
              if (deleting.current) return
              deleting.current = true
              remove.mutate(id, {
                onSuccess: () => {
                  close()
                  onDeleted?.()
                },
                onError: close,
                onSettled: () => {
                  deleting.current = false
                },
              })
            }}
          />
        </>
      }
    </StyleScope>
  )
}
