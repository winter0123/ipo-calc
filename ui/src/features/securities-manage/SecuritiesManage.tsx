import { useState } from 'react'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import { useSecuritiesFirms } from '@/entities/securities-firm/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import SecuritiesLogo from '@/shared/components/SecuritiesLogo/SecuritiesLogo.tsx'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'
import { useCreateAccount, useDeleteAccount } from './api.ts'
import AccountForm from './AccountForm.tsx'
import AccountRow from './AccountRow.tsx'
import styles, { StyleScope } from './SecuritiesManage.styles.ts'

type Panel = { kind: 'create'; firmId: number } | { kind: 'edit'; accountId: number } | null

type FirmRegisterState = { pending: boolean; error: string | null }

// 현재 투자자의 증권사별 계좌 목록. 등록된 증권사를 위로 올리고 계좌 등록·수정·삭제를 한다.
// 체크박스 ON 시 계좌번호 없이 등록(존재하면 가장 오래된 활성 계좌 재사용), OFF 시
// 단일 계좌는 확인 후 비활성화, 복수 계좌는 개별 삭제로 유도한다.
export default function SecuritiesManage({ investorId }: { investorId: number }) {
  const firms = useSecuritiesFirms()
  const accounts = useSecuritiesAccounts(investorId)
  const create = useCreateAccount()
  const del = useDeleteAccount()
  const [panel, setPanel] = useState<Panel>(null)
  const [deleting, setDeleting] = useState<SecuritiesAccount | null>(null)
  const [registering, setRegistering] = useState<Record<number, FirmRegisterState>>({})
  const [actionError, setActionError] = useState<string | null>(null)

  if (firms.isLoading || accounts.isLoading) {
    return <p role="status">증권사 목록을 불러오는 중...</p>
  }
  if (firms.isError || accounts.isError) {
    return (
      <StyleScope>
        {
          <p role="alert" className={styles.formError}>
            증권사 목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.
          </p>
        }
      </StyleScope>
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
  const busy = del.isPending

  const run = (m: typeof del, id: number, after?: () => void) => {
    setActionError(null)
    m.mutate(id, {
      onSuccess: after,
      onError: (e) => setActionError(toErrorMessage(e)),
    })
  }

  // 체크박스 ON: 계좌번호 없이 POST(미제공). 이미 활성 계좌가 있으면 재사용.
  // OFF: 단일 계좌만 확인 후 비활성화, 복수는 개별 삭제로 유도(체크 유지).
  // mutateAsync로 각 firm의 등록 상태를 독립적으로 정산해,
  // 여러 firm을 연속/병렬 등록해도 앞선 firm의 pending이 영구히 남지 않는다.
  const handleFirmCheck = async (firm: (typeof allFirms)[number], checked: boolean) => {
    const mine = byFirm.get(firm.id) ?? []
    const reg = registering[firm.id]
    if (reg?.pending) return

    if (checked) {
      if (mine.length > 0) return // 이미 활성 계좌가 있으면 재사용(체크만 유지)
      setRegistering((prev) => ({ ...prev, [firm.id]: { pending: true, error: null } }))
      setActionError(null)
      try {
        await create.mutateAsync({ investorId, securitiesFirmId: firm.id })
        setRegistering((prev) => {
          const next = { ...prev }
          delete next[firm.id]
          return next
        })
      } catch (e) {
        setRegistering((prev) => ({
          ...prev,
          [firm.id]: { pending: false, error: toErrorMessage(e) },
        }))
      }
    } else {
      if (mine.length === 0) return
      if (mine.length > 1) {
        setActionError(
          '같은 증권사에 등록된 계좌가 여러 개 있어 한 번에 해제할 수 없습니다. 계좌 목록에서 개별 삭제해 주세요.',
        )
        return // 체크 유지(일괄 삭제 금지)
      }
      setDeleting(mine[0])
    }
  }

  const renderFirm = (firm: (typeof allFirms)[number]) => {
    const displayName = formatSecuritiesFirmName(firm.name)
    const mine = byFirm.get(firm.id) ?? []
    const formOpen =
      (panel?.kind === 'create' && panel.firmId === firm.id) ||
      (panel?.kind === 'edit' && mine.some((a) => a.id === panel.accountId))
    const reg = registering[firm.id]
    const checking = reg?.pending ?? false
    const checked = mine.length > 0 || checking
    const firmBusy = busy || checking

    return (
      <StyleScope key={firm.id}>
        <li className={formOpen ? `${styles.firm} ${styles.wide}` : styles.firm}>
          <div className={styles.firmHead}>
            <SecuritiesLogo firmName={firm.name} size={20} className={styles.firmLogo} />
            <h3 className={styles.firmName}>{displayName}</h3>
            <label className={styles.firmCheckbox}>
              <input
                type="checkbox"
                checked={checked}
                disabled={firmBusy}
                onChange={(e) => handleFirmCheck(firm, e.target.checked)}
                aria-label={`${displayName} 계좌 사용 ${checked ? '해제' : '등록'}`}
              />
            </label>
            {!formOpen && (
              <Button
                variant="secondary"
                onClick={() => setPanel({ kind: 'create', firmId: firm.id })}
                disabled={firmBusy}
                aria-label={`${displayName} ${mine.length > 0 ? '계좌 추가' : '계좌 등록'}`}
              >
                {mine.length > 0 ? '계좌 추가' : '계좌 등록'}
              </Button>
            )}
          </div>
          {reg?.error && (
            <div className={styles.formError} role="alert">
              {reg.error}
            </div>
          )}
          {mine.length > 0 && (
            <ul className={styles.accounts}>
              {mine.map((a) => (
                <AccountRow
                  key={a.id}
                  account={a}
                  busy={firmBusy}
                  onEdit={() => setPanel({ kind: 'edit', accountId: a.id })}
                  onDelete={() => setDeleting(a)}
                />
              ))}
            </ul>
          )}
          {panel?.kind === 'create' && panel.firmId === firm.id && (
            <AccountForm
              target={{ kind: 'create', investorId, firmId: firm.id, firmName: displayName }}
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
                    firmName: displayName,
                    accountName: a.accountName ?? '',
                    accountNumber: a.accountNumber,
                  }}
                  onDone={() => setPanel(null)}
                  onCancel={() => setPanel(null)}
                />
              ))}
        </li>
      </StyleScope>
    )
  }

  return (
    <StyleScope>
      {
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
            message="청약 이력이 없는 계좌만 해제할 수 있습니다. 연결된 청약 이력이 있으면 개별 삭제할 수 없습니다."
            confirmLabel="삭제"
            onCancel={() => setDeleting(null)}
            onConfirm={() => {
              if (deleting) run(del, deleting.id)
              setDeleting(null)
            }}
          />
        </>
      }
    </StyleScope>
  )
}
