import { Fragment } from 'react'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { useRevealAccountNumber } from './api.ts'
import styles from './SecuritiesManage.styles.ts'

export interface AccountControlsProps {
  account: SecuritiesAccount
}

// 계좌번호(없으면 미입력 텍스트)만 렌더한다. "계좌관리" 토글은 증권사당 정확히 1개로 회사 헤더에 둔다.
// 별칭은 계좌번호 컨트롤의 title로만 안내해 한 줄 공간을 확보한다.
export function AccountControls({ account }: AccountControlsProps) {
  const reveal = useRevealAccountNumber()
  const label = account.accountName ?? '계좌'
  const revealError = toErrorMessage(reveal.error)
  const hasNumber = account.accountNumber != null && account.accountNumber !== ''
  // 별칭은 화면 공간을 차지하지 않고 번호 컨트롤의 title로만 안내한다.
  const aliasTitle = account.accountName ?? undefined
  const toggleLabel = `${label} 계좌번호 ${reveal.data ? '숨기기' : '보기'}`

  return (
    <Fragment>
      {hasNumber ? (
        <button
          type="button"
          className={styles.numberButton}
          onClick={() => (reveal.data ? reveal.reset() : reveal.mutate(account.id))}
          disabled={reveal.isPending}
          aria-label={toggleLabel}
          aria-pressed={reveal.data != null}
          title={aliasTitle ?? toggleLabel}
        >
          {reveal.data ?? account.accountNumber}
        </button>
      ) : (
        // 번호가 없으면 단순 텍스트로 표시한다(수정은 펼친 계좌관리의 수정 버튼으로).
        <span className={styles.numberNoneText} title={aliasTitle ?? undefined}>
          미입력
        </span>
      )}
      {revealError && (
        <div className={styles.formError} role="alert">
          {revealError}
        </div>
      )}
    </Fragment>
  )
}

// 펼친 액션 패널. 계좌별 행에 그 계좌의 별칭(없으면 마스킹 번호, 둘 다 없으면 미입력)을 붙여
// 수정·삭제 대상을 명확히 표시하고, 회사 단위 "계좌추가"는 정확히 1개만 둔다.
// 같은 라벨이 겹치면 뒤에 순번을 붙여 이름과 대상이 항상 1:1로 대응하게 한다.
export function AccountActionGroup({
  accounts,
  displayName,
  groupId,
  busy,
  onEdit,
  onDelete,
  onAddAccount,
  onClose,
}: {
  accounts: SecuritiesAccount[]
  displayName: string
  groupId: string
  busy: boolean
  onEdit: (account: SecuritiesAccount) => void
  onDelete: (account: SecuritiesAccount) => void
  onAddAccount: () => void
  onClose: () => void
}) {
  const toggleId = `${groupId}-toggle`
  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.stopPropagation()
      onClose()
      document.getElementById(toggleId)?.focus()
    }
  }
  const baseLabel = (a: SecuritiesAccount) =>
    a.accountName ??
    (a.accountNumber != null && a.accountNumber !== '' ? a.accountNumber : '미입력')
  const counts = new Map<string, number>()
  for (const a of accounts) {
    const l = baseLabel(a)
    counts.set(l, (counts.get(l) ?? 0) + 1)
  }
  const seen = new Map<string, number>()
  const targetLabel = (a: SecuritiesAccount) => {
    const l = baseLabel(a)
    if ((counts.get(l) ?? 0) <= 1) return l
    const n = (seen.get(l) ?? 0) + 1
    seen.set(l, n)
    return `${l} ${n}`
  }
  return (
    <div
      id={groupId}
      role="group"
      aria-label={`${displayName} 계좌 작업`}
      className={styles.actionGroup}
      onKeyDown={onKeyDown}
    >
      {accounts.map((a) => {
        const label = targetLabel(a)
        return (
          <div className={styles.actionRow} key={a.id}>
            <span className={styles.actionTarget} title={label}>
              {label}
            </span>
            <button
              type="button"
              onClick={() => onEdit(a)}
              disabled={busy}
              aria-label={`${label} 수정`}
              title={`${label} 수정`}
            >
              수정
            </button>
            <button
              type="button"
              onClick={() => onDelete(a)}
              disabled={busy}
              aria-label={`${label} 삭제`}
              title={`${label} 삭제`}
            >
              삭제
            </button>
          </div>
        )
      })}
      <div className={styles.actionRow}>
        <button
          type="button"
          onClick={onAddAccount}
          disabled={busy}
          aria-label={`${displayName} 계좌추가`}
          title={`${displayName} 계좌추가`}
        >
          계좌추가
        </button>
      </div>
    </div>
  )
}
