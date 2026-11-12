import { useState } from 'react'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import { useSecuritiesFirms } from '@/entities/securities-firm/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import SecuritiesLogo from '@/shared/components/SecuritiesLogo/SecuritiesLogo.tsx'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'
import { useCreateAccount, useDeleteAccount } from './api.ts'
import AccountForm from './AccountForm.tsx'
import { AccountActionGroup, AccountControls } from './AccountRow.tsx'
import styles, { StyleScope } from './SecuritiesManage.styles.ts'

type View =
  | { kind: 'idle' }
  | { kind: 'menu'; firmId: number }
  | { kind: 'create'; firmId: number }
  | { kind: 'edit'; accountId: number }

type FirmRegisterState = { pending: boolean; error: string | null }

const NO_NUMBER = (a: SecuritiesAccount) => a.accountNumber == null || a.accountNumber === ''

// 현재 투자자의 증권사별 계좌 목록. 등록된 증권사를 위로 올리고 계좌 등록·수정·삭제를 한다.
// 체크박스 ON 시 계좌번호 없이 등록(존재하면 가장 오래된 활성 계좌 재사용), OFF 시
// 단일 계좌는 확인 후 비활성화, 복수 계좌는 개별 삭제로 유도한다.
// 증권사 카드 헤더는 체크박스 → 로고 → 회사명 → 번호들 → "계좌관리" 정확히 1개 순서.
// 복수 계좌의 번호는 필요한 만큼 작은 줄로 접히고, 계좌별 관리 토글은 없다.
// 화면의 "패널"은 하나의 명확한 state(View)로 idle/menu/create/edit 상호배타를 보장한다.
// 폼이 열린 상태에서 계좌관리를 다시 누르면 폼이 닫히고 메뉴로 전환된다(폼+메뉴 동시 노출 금지).
// 펼친 메뉴에는 번호 입력된 계좌별 수정과 회사 단위 계좌추가 1개만 둔다(삭제는 수정 폼에 있다).
// 계좌추가는 번호 미입력 활성 계좌가 있으면 그 계좌(가장 오래된 것)를 채우는 수정 폼으로, 없으면 새 계좌 생성 폼으로 연결된다.
export default function SecuritiesManage({ investorId }: { investorId: number }) {
  const firms = useSecuritiesFirms()
  const accounts = useSecuritiesAccounts(investorId)
  const create = useCreateAccount()
  const del = useDeleteAccount()
  const [view, setView] = useState<View>({ kind: 'idle' })
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

  // 계좌관리 토글: 메뉴만 열려 있으면 닫고, 그 외(폼 열림 포함)에는 메뉴로 전환한다.
  // 폼과 메뉴가 동시에 보이지 않도록 항상 View 하나만 유지한다.
  const toggleMenu = (firmId: number) =>
    setView((prev) =>
      prev.kind === 'menu' && prev.firmId === firmId ? { kind: 'idle' } : { kind: 'menu', firmId },
    )
  const closeView = () => setView({ kind: 'idle' })

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
    const reg = registering[firm.id]
    const checking = reg?.pending ?? false
    const checked = mine.length > 0 || checking
    const firmBusy = busy || checking
    const firmGroupId = `firm-${firm.id}`
    const firmExpanded = view.kind === 'menu' && view.firmId === firm.id

    const openEdit = (a: SecuritiesAccount) => setView({ kind: 'edit', accountId: a.id })
    // 계좌추가: 번호 미입력 활성 계좌(가장 오래된 것)가 있으면 그 계좌를 채우는 수정 폼으로,
    // 없으면 새 계좌 생성 폼으로 연결한다.
    const openAdd = () => {
      const fillTarget =
        mine
          .filter(NO_NUMBER)
          .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id)[0] ?? null
      setView(
        fillTarget
          ? { kind: 'edit', accountId: fillTarget.id }
          : { kind: 'create', firmId: firm.id },
      )
    }

    return (
      <StyleScope key={firm.id}>
        <li className={styles.firm}>
          <div className={styles.firmHead}>
            <label className={styles.firmCheckbox}>
              <input
                type="checkbox"
                checked={checked}
                disabled={firmBusy}
                onChange={(e) => handleFirmCheck(firm, e.target.checked)}
                aria-label={`${displayName} 계좌 사용 ${checked ? '해제' : '등록'}`}
              />
            </label>
            <SecuritiesLogo firmName={firm.name} size={20} className={styles.firmLogo} />
            <h3 className={styles.firmName} title={firm.name}>
              {displayName}
            </h3>
            {mine.length > 0 ? (
              <div className={styles.numbers}>
                {mine.map((a) => (
                  <AccountControls key={a.id} account={a} />
                ))}
              </div>
            ) : (
              <span className={styles.numberNoneText}>미입력</span>
            )}
            <button
              type="button"
              id={`${firmGroupId}-toggle`}
              className={styles.actionToggle}
              onClick={() => toggleMenu(firm.id)}
              onKeyDown={(e) => {
                if (e.key === 'Escape' && firmExpanded) {
                  e.stopPropagation()
                  closeView()
                }
              }}
              disabled={firmBusy}
              aria-expanded={firmExpanded}
              aria-controls={firmGroupId}
              aria-label={`${displayName} 계좌관리`}
              title={`${displayName} 계좌관리`}
            >
              계좌관리
            </button>
          </div>
          {firmExpanded && (
            <AccountActionGroup
              accounts={mine}
              displayName={displayName}
              groupId={firmGroupId}
              busy={firmBusy}
              onEdit={openEdit}
              onAddAccount={openAdd}
              onClose={closeView}
            />
          )}
          {reg?.error && (
            <div className={styles.formError} role="alert">
              {reg.error}
            </div>
          )}
          {view.kind === 'create' && view.firmId === firm.id && (
            <AccountForm
              target={{ kind: 'create', investorId, firmId: firm.id, firmName: displayName }}
              onDone={closeView}
              onCancel={closeView}
            />
          )}
          {view.kind === 'edit' &&
            mine
              .filter((a) => a.id === view.accountId)
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
                  onDone={closeView}
                  onCancel={closeView}
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
            <ul className={styles.firms}>{others.map(renderFirm)}</ul>
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
