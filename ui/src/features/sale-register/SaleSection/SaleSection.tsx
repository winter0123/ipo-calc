import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useInvestmentHistory } from '@/entities/subscription/index.ts'
import { deleteSale, type Sale } from '@/entities/sale/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import type { InvestorResponse } from '@/entities/investor/index.ts'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { formatShares, formatWon } from '@/shared/lib/format.ts'
import { dateOnly } from '@/shared/lib/date.ts'
import formStyles, { StyleScope as FormScope } from '../SaleRegisterForm/SaleRegisterForm.styles.ts'
import AddSaleForm from './AddSaleForm.tsx'
import EditSaleForm from './EditSaleForm.tsx'
import styles, { StyleScope } from './SaleSection.styles.ts'

/** TradeManagePage가 features의 섹션에 주입하는 조회 상태. pages에는 의존하지 않는다. */
export type SectionStatus = 'loading' | 'error' | 'ready'

export interface SaleSectionProps {
  /** URL의 :ipoId로 확정한 공모주. 섹션 안에서 다른 공모주를 선택하지 않는다. */
  ipo: Ipo
  /** 상단 전환기의 현재 투자자. */
  investor: InvestorResponse
  /** 페이지가 고정한 증권계좌(1개 자동선택/복수 선택). */
  account: SecuritiesAccount
  /** 저장/삭제/갱신 완료 대기 — 페이지의 목록·상세·원본 재조회가 끝나면 resolve. */
  onSaved: () => Promise<void>
  status: SectionStatus
  /** 투자 내역 상세 — 매도 목록·잔여·배정 요약의 출처. 없으면 매도부터 사후 등록한다. */
  detail: InvestmentHistoryItem | null
  /** 편집 원본(GET /sales/{id}) — status가 ready이면 detail.sales의 id별로 모두 불러온 상태다. */
  records: Sale[]
  onDirtyChange: (dirty: boolean) => void
  /** 이 섹션의 저장/삭제 mutation이 다른 섹션과 겹치지 않도록 페이지가 켜는 잠금. */
  saveDisabled?: boolean
  /** 이 섹션의 mutation 진행 여부를 페이지에 알린다(다른 섹션 저장·계좌 전환 차단용). */
  onPendingChange?: (pending: boolean) => void
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '진행 중',
  INACTIVE: '비활성',
  CANCELLED: '취소됨',
}
// 백엔드 RecordStatus enum을 한글로 바꾼다. 모르는 값은 그대로 보여 준다.
const statusLabel = (status: string): string => STATUS_LABELS[status] ?? status

const won = (value: number | null | undefined): string => (value == null ? '-' : formatWon(value))

/** 매도 화면의 배정·매도 진행 단계. allocationStatus(lifecycle)가 아니라 배정 수량·ACTIVE 매도 합계로만 derive한다. */
type SalePhase = '배정 미기록' | '미배정(0주)' | '매도 전' | '부분 매도' | '매도 완료'

/** 이 화면에서 동시에 열 수 있는 입력은 하나뿐 — idle | 매도 추가 폼 | 특정 매도 수정 폼 */
type SaleView = { kind: 'idle' } | { kind: 'add' } | { kind: 'edit'; saleId: number }

/** 매도 섹션 — N건 리스트·건별 수정/삭제·인라인 추가·요약·잔여 잠금을 담당한다. */
export default function SaleSection(props: SaleSectionProps) {
  const { detail, status, records, account, investor } = props
  // 폼을 한 번이라도 열었으면, 이후 조회가 늦어지거나 실패해도 입력을 지우고 화면을 언마운트하지 않는다.
  const [hydrated, setHydrated] = useState(status === 'ready')
  if (!hydrated && status === 'ready') {
    setHydrated(true)
  }

  // 캡처 종목 매칭 후보 — 현재 투자자의 ACTIVE 청약 전체(등록 화면과 같은 계약).
  const subscriptionHistoryQuery = useInvestmentHistory(
    { investorId: investor.id },
    { enabled: !!investor.id },
  )
  const subscriptions = (subscriptionHistoryQuery.data?.content ?? []).filter(
    (item) => item.subscriptionStatus === 'ACTIVE',
  )

  const sales = detail?.sales ?? []
  const activeSales = sales.filter((sale) => sale.status === 'ACTIVE')
  const soldQuantity = activeSales.reduce((total, sale) => total + sale.quantity, 0)
  const allocation = detail?.allocation ?? null
  const remaining = detail?.remainingQuantity ?? null
  // 잔여가 없으면 배정 수량 − ACTIVE 매도 합계로 방어적으로 계산한다(서버 값 우선).
  const effectiveRemaining =
    remaining ??
    (allocation != null ? Math.max(0, allocation.allocatedQuantity - soldQuantity) : null)
  const soldOut =
    allocation != null &&
    (allocation.allocatedQuantity === 0 || (effectiveRemaining != null && effectiveRemaining <= 0))
  const hasPriorActiveSales = activeSales.length > 0
  const recordById = new Map(records.map((record) => [record.id, record]))
  // 배정 수량과 ACTIVE 매도 합계만으로 진행 단계를 판정한다 — allocationStatus(lifecycle)는 거래 단계와 혼동하지 않는다.
  const phaseLabel: SalePhase = (() => {
    if (allocation == null) return '배정 미기록'
    if (allocation.allocatedQuantity === 0) return '미배정(0주)'
    if (soldQuantity === 0) return '매도 전'
    if ((effectiveRemaining ?? 0) > 0) return '부분 매도'
    return '매도 완료'
  })()

  const [view, setView] = useState<SaleView>({ kind: 'idle' })
  // 입력 중인 폼을 닫거나 다른 매도로 바꾸려는 시도 — 확인을 거쳐야 진행된다.
  const [pendingView, setPendingView] = useState<SaleView | null>(null)
  const [addPending, setAddPending] = useState(false)
  const [addDirty, setAddDirty] = useState(false)
  const [editPending, setEditPending] = useState(false)
  const [editDirty, setEditDirty] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [syncError, setSyncError] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<{ id: number; label: string } | null>(null)

  const deleteSaleMutation = useMutation({
    mutationFn: (id: number) => deleteSale(id),
  })

  const deletePending = deleteSaleMutation.isPending
  const sectionPending = syncing || addPending || editPending || deletePending
  const locked = sectionPending || props.saveDisabled === true || status !== 'ready'

  const currentDirty = view.kind === 'add' ? addDirty : view.kind === 'edit' ? editDirty : false

  /** 화면 전환 요청 — 진행 중 변경·삭제가 있으면 무시하고, 입력 중이면 확인을 먼저 받는다. */
  const requestView = (next: SaleView) => {
    if (sectionPending) return // 저장·삭제·갱신 중에는 다른 매도로 바꾸지 않는다.
    if (view.kind === 'idle') {
      setView(next)
      return
    }
    if (view.kind === next.kind) {
      // 같은 종류의 전환 — 추가→추가는 불가(버튼 미노출), 같은 매도 수정 재클릭은 no-op.
      if (view.kind === 'edit' && next.kind === 'edit' && view.saleId === next.saleId) return
      if (view.kind === 'add' && next.kind === 'add') return
    }
    if (currentDirty) {
      setPendingView(next)
      return
    }
    setView(next)
  }
  const confirmPendingView = () => {
    if (sectionPending) return
    if (pendingView) setView(pendingView)
    setPendingView(null)
  }
  const cancelPendingView = () => setPendingView(null)

  const onDirtyChangeRef = useRef(props.onDirtyChange)
  useEffect(() => {
    onDirtyChangeRef.current = props.onDirtyChange
  })
  const anyDirty = addDirty || editDirty
  useEffect(() => {
    onDirtyChangeRef.current(anyDirty)
    return () => onDirtyChangeRef.current(false)
  }, [anyDirty])

  const onPendingChangeRef = useRef(props.onPendingChange)
  useEffect(() => {
    onPendingChangeRef.current = props.onPendingChange
  })
  useEffect(() => {
    onPendingChangeRef.current?.(sectionPending)
    return () => onPendingChangeRef.current?.(false)
  }, [sectionPending])

  const finishOperation = () => setSyncing(false)
  const retrySync = async () => {
    if (syncing) return
    setSyncing(true)
    try {
      await props.onSaved()
      setSyncError(false)
    } catch {
      setSyncError(true)
    }
    finishOperation()
  }
  const confirmDeleteYes = async () => {
    if (locked || syncing || !deleteTarget || deletePending) return
    setSyncing(true)
    setSyncError(false)
    try {
      await deleteSaleMutation.mutateAsync(deleteTarget.id)
    } catch {
      // 입력은 보존되고, 실패한 요청은 자동으로 다시 보내지 않는다.
      finishOperation()
      setDeleteTarget(null)
      return
    }
    try {
      await props.onSaved()
    } catch {
      setSyncError(true)
    }
    finishOperation()
    setDeleteTarget(null)
  }

  if (!hydrated) {
    if (status === 'loading') {
      return (
        <FormScope>
          {
            <p className={formStyles.hint} role="status">
              매도 기록을 불러오는 중...
            </p>
          }
        </FormScope>
      )
    }
    return (
      <FormScope>
        {
          <div className={formStyles.formError} role="alert">
            매도 기록을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.
            <button
              type="button"
              className={formStyles.retryButton}
              onClick={retrySync}
              disabled={syncing}
            >
              다시 불러오기
            </button>
          </div>
        }
      </FormScope>
    )
  }

  const deleteError = toErrorMessage(deleteSaleMutation.error)
  const rows = sales.map((sale) => ({ sale, record: recordById.get(sale.id) ?? null }))

  return (
    <StyleScope>
      {
        <>
          {status === 'loading' && (
            <p className={formStyles.hint} role="status">
              매도 기록을 최신으로 갱신하는 중...
            </p>
          )}
          {status === 'error' && (
            <div className={formStyles.formError} role="alert">
              매도 기록을 갱신하지 못했습니다. 표시된 내용은 마지막으로 불러온 값입니다.
              <button
                type="button"
                className={formStyles.retryButton}
                onClick={retrySync}
                disabled={syncing}
              >
                다시 불러오기
              </button>
            </div>
          )}
          {syncError && (
            <div className={formStyles.formError} role="alert">
              저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.
              <button
                type="button"
                className={formStyles.submitButton}
                onClick={retrySync}
                disabled={locked}
              >
                다시 불러오기
              </button>
            </div>
          )}
          {deleteError && (
            <div className={formStyles.formError} role="alert">
              {deleteError}
            </div>
          )}

          <div className={styles.summaryBar} role="status" aria-label="매도 요약">
            <span>
              배정{' '}
              <strong>
                {allocation != null ? formatShares(allocation.allocatedQuantity) : '미기록'}
              </strong>
            </span>
            <span>
              매도 합계 <strong>{formatShares(soldQuantity)}</strong>
            </span>
            <span>
              잔여{' '}
              <strong>{effectiveRemaining != null ? formatShares(effectiveRemaining) : '-'}</strong>
            </span>
            <span>
              상태 <strong data-testid="sale-phase">{phaseLabel}</strong>
            </span>
          </div>

          {detail == null && (
            <p className={formStyles.hint} role="status">
              아직 배정이 없습니다. 매도를 먼저 등록하면 청약·배정이 함께 만들어집니다. 배정수량을
              입력해야 저장할 수 있습니다.
            </p>
          )}

          {activeSales.length === 0 && sales.length === 0 ? (
            <p className={formStyles.hint}>등록된 매도가 없습니다.</p>
          ) : (
            <table className={styles.list}>
              <thead>
                <tr>
                  <th scope="col">매도일</th>
                  <th scope="col">수량</th>
                  <th scope="col">단가</th>
                  <th scope="col">금액</th>
                  <th scope="col">비용</th>
                  <th scope="col">관리</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ sale, record }) => {
                  const isEditRow = view.kind === 'edit' && view.saleId === sale.id
                  if (isEditRow && record) {
                    return (
                      <tr key={sale.id}>
                        <td colSpan={6}>
                          <EditSaleForm
                            record={record}
                            locked={locked}
                            onSaved={props.onSaved}
                            onDirtyChange={setEditDirty}
                            onPendingChange={setEditPending}
                            onCancel={() => requestView({ kind: 'idle' })}
                            onClosed={() => setView({ kind: 'idle' })}
                          />
                        </td>
                      </tr>
                    )
                  }
                  const isActive = sale.status === 'ACTIVE'
                  const priceText = record
                    ? record.price != null
                      ? formatWon(record.price)
                      : '미입력'
                    : '불러오는 중...'
                  return (
                    <tr key={sale.id}>
                      <td data-label="매도일">{dateOnly(sale.soldAt) ?? '-'}</td>
                      <td data-label="수량">{formatShares(sale.quantity)}</td>
                      <td data-label="단가">{priceText}</td>
                      <td data-label="금액">{won(sale.amount)}</td>
                      <td data-label="비용">
                        공모주수수료 {won(sale.fee)} · 세금기타 {won(sale.tax)}
                        {sale.otherCost != null && sale.otherCost > 0
                          ? ` · 기타비용 ${won(sale.otherCost)}`
                          : ''}
                        {!isActive && (
                          <span className={styles.statusCancelled}>
                            {' '}
                            · {statusLabel(sale.status)}
                          </span>
                        )}
                      </td>
                      <td data-label="관리">
                        <span className={styles.rowActions}>
                          <button
                            type="button"
                            className={styles.smallButton}
                            onClick={() => requestView({ kind: 'edit', saleId: sale.id })}
                            disabled={locked || !isActive || record == null}
                            aria-label={`${dateOnly(sale.soldAt) ?? sale.id} 매도 수정`}
                          >
                            수정
                          </button>
                          <button
                            type="button"
                            className={`${styles.smallButton} ${styles.danger}`}
                            onClick={() =>
                              setDeleteTarget({
                                id: sale.id,
                                label: `매도 ${formatShares(sale.quantity)}(${dateOnly(sale.soldAt) ?? '날짜 미상'})`,
                              })
                            }
                            disabled={locked || !isActive}
                            aria-label={`${dateOnly(sale.soldAt) ?? sale.id} 매도 삭제`}
                          >
                            삭제
                          </button>
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}

          {view.kind === 'idle' && (
            <div className={styles.rowActions}>
              <Button
                type="button"
                onClick={() => requestView({ kind: 'add' })}
                disabled={locked || soldOut}
              >
                + 매도 추가
              </Button>
              {soldOut && (
                <p className={formStyles.hint} role="status">
                  배정 잔여가 0주라 매도를 추가할 수 없습니다. 기존 건은 수정·삭제할 수 있습니다.
                </p>
              )}
            </div>
          )}
          {view.kind === 'add' && (
            <AddSaleForm
              ipo={props.ipo}
              investor={investor}
              account={account}
              detail={detail}
              remainingQuantity={effectiveRemaining}
              hasPriorActiveSales={hasPriorActiveSales}
              subscriptions={subscriptions}
              subscriptionsLoading={subscriptionHistoryQuery.isLoading}
              onSaved={props.onSaved}
              onDirtyChange={setAddDirty}
              onPendingChange={setAddPending}
              locked={locked}
              onCancel={() => requestView({ kind: 'idle' })}
              onClosed={() => setView({ kind: 'idle' })}
            />
          )}

          <ConfirmDialog
            isOpen={pendingView != null}
            title="저장하지 않은 입력이 있어요"
            message="지금 닫거나 다른 매도로 바꾸면 입력한 내용은 저장되지 않고 사라집니다. 그래도 진행할까요?"
            confirmLabel="닫고 진행"
            cancelLabel="계속 작성"
            pending={sectionPending}
            onConfirm={confirmPendingView}
            onCancel={cancelPendingView}
          />
          <ConfirmDialog
            isOpen={deleteTarget != null}
            title="매도 삭제"
            message={`${deleteTarget?.label ?? '매도'}을(를) 삭제할까요? 목록에서 보이지 않고 투자 내역에는 취소로 남습니다.`}
            confirmLabel="삭제"
            cancelLabel="취소"
            pending={deletePending || syncing}
            onConfirm={confirmDeleteYes}
            onCancel={() => setDeleteTarget(null)}
          />
        </>
      }
    </StyleScope>
  )
}
