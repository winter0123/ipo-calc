import { useState } from 'react'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import { useSecuritiesFirms } from '@/entities/securities-firm/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { useDeleteAccount, useSetDefaultAccount } from './api.ts'
import AccountForm from './AccountForm.tsx'
import AccountRow from './AccountRow.tsx'
import styles from './SecuritiesManage.module.css'

type Panel = { kind: 'create'; firmId: number } | { kind: 'edit'; accountId: number } | null

// 현재 투자자의 증권사별 계좌 목록. 등록된 증권사를 위로 올리고 계좌 등록·수정·삭제·기본 지정을 한다.
export default function SecuritiesManage({ investorId }: { investorId: number }) {
  const firms = useSecuritiesFirms()
  const accounts = useSecuritiesAccounts(investorId)
  const del = useDeleteAccount()
  const setDefault = useSetDefaultAccount()
  const [panel, setPanel] = useState<Panel>(null)
  const [deleting, setDeleting] = useState<SecuritiesAccount | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  if (firms.isLoading || accounts.isLoading) {
    return <p role="status">증권사 목록을 불러오는 중...</p>
  }
  if (firms.isError || accounts.isError) {
    return (
      <p role="alert" className={styles.formError}>
        증권사 목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.
      </p>
    )
  }

  const byFirm = new Map<number, SecuritiesAccount[]>()
  for (const a of accounts.data ?? []) {
    byFirm.set(a.securitiesFirmId, [...(byFirm.get(a.securitiesFirmId) ?? []), a])
  }
  // 등록한 증권사와 나머지를 나눠 보여 준다. 나머지는 서버 순서를 유지한다.
  const allFirms = firms.data ?? []
  const registered = allFirms.filter((f) => byFirm.has(f.id))
  const others = allFirms.filter((f) => !byFirm.has(f.id))
  const busy = del.isPending || setDefault.isPending

  const run = (m: typeof del | typeof setDefault, id: number, after?: () => void) => {
    setActionError(null)
    m.mutate(id, {
      onSuccess: after,
      onError: (e) => setActionError(toErrorMessage(e)),
    })
  }

  const renderFirm = (firm: (typeof allFirms)[number]) => {
    const mine = byFirm.get(firm.id) ?? []
    const formOpen =
      (panel?.kind === 'create' && panel.firmId === firm.id) ||
      (panel?.kind === 'edit' && mine.some((a) => a.id === panel.accountId))
    return (
      <li key={firm.id} className={formOpen ? `${styles.firm} ${styles.wide}` : styles.firm}>
        <div className={styles.firmHead}>
          <h3 className={styles.firmName}>{firm.name}</h3>
          {mine.length > 0 && <span className={styles.badge}>등록됨</span>}
          {!formOpen && (
            <Button
              variant="secondary"
              onClick={() => setPanel({ kind: 'create', firmId: firm.id })}
              aria-label={`${firm.name} ${mine.length > 0 ? '계좌 추가' : '계좌 등록'}`}
            >
              {mine.length > 0 ? '계좌 추가' : '계좌 등록'}
            </Button>
          )}
        </div>
        {mine.length > 0 && (
          <ul className={styles.accounts}>
            {mine.map((a) => (
              <AccountRow
                key={a.id}
                account={a}
                busy={busy}
                onEdit={() => setPanel({ kind: 'edit', accountId: a.id })}
                onDelete={() => setDeleting(a)}
                onSetDefault={() => run(setDefault, a.id)}
              />
            ))}
          </ul>
        )}
        {panel?.kind === 'create' && panel.firmId === firm.id && (
          <AccountForm
            target={{ kind: 'create', investorId, firmId: firm.id, firmName: firm.name }}
            onDone={() => setPanel(null)}
            onCancel={() => setPanel(null)}
          />
        )}
        {panel?.kind === 'edit' &&
          mine
            .filter((a) => a.id === panel.accountId)
            .map((a) => (
              <AccountForm
                key={a.id}
                target={{
                  kind: 'edit',
                  accountId: a.id,
                  firmName: firm.name,
                  accountName: a.accountName ?? '',
                }}
                onDone={() => setPanel(null)}
                onCancel={() => setPanel(null)}
              />
            ))}
      </li>
    )
  }

  return (
    <>
      {actionError && (
        <div className={styles.formError} role="alert">
          {actionError}
        </div>
      )}
      {registered.length > 0 && (
        <section aria-labelledby="sec-mine">
          <h2 id="sec-mine" className={styles.sectionTitle}>
            등록한 증권사
          </h2>
          <ul className={styles.firms}>{registered.map(renderFirm)}</ul>
        </section>
      )}
      <section aria-labelledby="sec-others">
        <h2 id="sec-others" className={styles.sectionTitle}>
          {registered.length > 0 ? '다른 증권사' : '증권사'}
        </h2>
        <ul className={`${styles.firms} ${styles.grid}`}>{others.map(renderFirm)}</ul>
      </section>
      <ConfirmDialog
        isOpen={deleting !== null}
        title="계좌를 삭제할까요?"
        message="이 계좌로 기록한 청약은 그대로 남습니다."
        confirmLabel="삭제"
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) run(del, deleting.id)
          setDeleting(null)
        }}
      />
    </>
  )
}
