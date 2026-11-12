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
        // 번호가 없으면 단순 텍스트로 표시한다(번호 입력은 펼친 계좌관리의 계좌추가로 연다).
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

// 펼친 액션 패널. 계좌번호가 있는 계좌별로 수정 버튼(대상을 별칭/번호로 표시)을 보여 주고,
// 회사 단위 "계좌추가"는 정확히 1개만 둔다. 번호 미입력 활성 계좌가 있으면 계좌추가는 그 계좌(id 명시)를 채우는
// 폼으로 연결되고, 없으면 새 계좌 생성 폼으로 연결된다. 삭제는 메뉴가 아니라 수정 폼의 삭제 버튼에 있다.
// 같은 라벨이 겹치면 뒤에 순번을 붙여 이름과 대상이 항상 1:1로 대응하게 한다.
export function AccountActionGroup({
  accounts,
  displayName,
  groupId,
  busy,
  onEdit,
  onAddAccount,
  onClose,
}: {
  accounts: SecuritiesAccount[]
  displayName: string
  groupId: string
  busy: boolean
  onEdit: (account: SecuritiesAccount) => void
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
  const numbered = accounts.filter((a) => a.accountNumber != null && a.accountNumber !== '')
  const baseLabel = (a: SecuritiesAccount) =>
    a.accountName ??
    (a.accountNumber != null && a.accountNumber !== '' ? a.accountNumber : '미입력')
  const counts = new Map<string, number>()
  for (const a of numbered) {
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
  // 번호 미입력 활성 계좌(가장 오래된 것)를 계좌추가의 채움 대상으로 쓴다.
  const fillTarget =
    accounts
      .filter((a) => a.accountNumber == null || a.accountNumber === '')
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id)[0] ?? null

  return (
    <div
      id={groupId}
      role="group"
      aria-label={`${displayName} 계좌 작업`}
      className={styles.actionGroup}
      onKeyDown={onKeyDown}
    >
      {numbered.map((a) => {
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
          </div>
        )
      })}
      <div className={styles.actionRow}>
        {fillTarget && (
          <span className={styles.actionTarget} title={`미입력 계좌 #${fillTarget.id}`}>
            미입력 #{fillTarget.id}
          </span>
        )}
        <button
          type="button"
          onClick={onAddAccount}
          disabled={busy}
          aria-label={`${displayName} 계좌추가`}
          title={
            fillTarget ? `미입력 계좌 #${fillTarget.id}에 번호 입력` : `${displayName} 계좌추가`
          }
        >
          계좌추가
        </button>
      </div>
    </div>
  )
}
