import { useEffect, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import {
  createAllocation,
  deleteAllocation,
  updateAllocation,
  type Allocation,
  type AllocationRegisterRequest,
  type AllocationUpdateRequest,
} from '@/entities/allocation/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import type { InvestorResponse } from '@/entities/investor/index.ts'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { formatShares, formatWon } from '@/shared/lib/format.ts'
import styles, { StyleScope } from '../AllocationRegisterForm/AllocationRegisterForm.styles.ts'
import ReferenceQuantity from '../AllocationRegisterForm/ReferenceQuantity.tsx'

/** TradeManagePage가 features의 섹션에 주입하는 조회 상태. pages에는 의존하지 않는다. */
export type SectionStatus = 'loading' | 'error' | 'ready'

export interface AllocationSectionProps {
  /** URL의 :ipoId로 확정한 공모주. 섹션 안에서 다른 공모주를 선택하지 않는다. */
  ipo: Ipo
  /** 상단 전환기의 현재 투자자. */
  investor: InvestorResponse
  /** 페이지가 고정한 증권계좌(1개 자동선택/복수 선택). */
  account: SecuritiesAccount
  /** 저장/삭제 성공 후 페이지의 목록·상세 query를 무효화하고 갱신 완료까지 기다린다. */
  onSaved: () => Promise<void>
  mode: 'create' | 'edit'
  status: SectionStatus
  /** 투자 내역 상세 — 연결된 ACTIVE 청약(id·수량)의 출처. 없으면 청약도 함께 만들어진다. */
  detail: InvestmentHistoryItem | null
  /** 편집 원본(GET /allocations/{id}) — allocatedQuantity/note의 유일한 출처. */
  record: Allocation | null
  onDirtyChange: (dirty: boolean) => void
  /** 이 섹션의 저장/삭제 mutation이 다른 섹션과 겹치지 않도록 페이지가 켜는 잠금. */
  saveDisabled?: boolean
  /** 이 섹션의 mutation 진행 여부를 페이지에 알린다(다른 섹션 저장 차단용). */
  onPendingChange?: (pending: boolean) => void
}

function sectionErrorMessage(error: unknown): string | null {
  return toErrorMessage(error, {
    byStatus: {
      403: '이 배정을 수정할 권한이 없습니다.',
      404: '배정 기록을 찾을 수 없습니다. 새로고침 후 다시 시도해 주세요.',
      409: '이미 동일한 청약에 배정이 존재합니다.',
    },
  })
}

export default function AllocationSection(props: AllocationSectionProps) {
  const { status, mode, record, account } = props
  // 폼을 한 번이라도 열었으면, 이후 조회가 늦어지거나 실패해도 입력을 지우고 폼을 언마운트하지 않는다.
  const [hydrated, setHydrated] = useState(status === 'ready')
  // 조회 실패 배너의 "다시 불러오기" — 페이지의 목록·상세·원본을 재조회한다.
  const [retryPending, setRetryPending] = useState(false)
  if (!hydrated && status === 'ready') {
    // 이전 상태를 렌더 중에 갱신하는 공식 패턴 — effect로 삼키지 않는다.
    setHydrated(true)
  }
  const retryFetch = async () => {
    if (retryPending) return
    setRetryPending(true)
    try {
      await props.onSaved()
    } catch {
      // 갱신에 실패하면 배너가 그대로 남는다.
    }
    setRetryPending(false)
  }

  if (!hydrated) {
    if (status === 'loading') {
      return (
        <StyleScope>
          <p className={styles.hint} role="status">
            배정 기록을 불러오는 중...
          </p>
        </StyleScope>
      )
    }
    if (status === 'error') {
      return (
        <StyleScope>
          <div className={styles.formError} role="alert">
            배정 기록을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.
            <button
              type="button"
              className={styles.submitButton}
              onClick={retryFetch}
              disabled={retryPending}
            >
              다시 불러오기
            </button>
          </div>
        </StyleScope>
      )
    }
  }
  if (mode === 'edit' && !record && !hydrated) {
    return (
      <StyleScope>
        <p className={styles.hint} role="status">
          배정 기록을 불러오는 중...
        </p>
      </StyleScope>
    )
  }

  return (
    <StyleScope>
      {status === 'loading' && hydrated && (
        <p className={styles.hint} role="status">
          배정 기록을 최신으로 갱신하는 중...
        </p>
      )}
      {status === 'error' && hydrated && (
        <div className={styles.formError} role="alert">
          배정 기록을 갱신하지 못했습니다. 표시된 내용은 마지막으로 불러온 값입니다.
          <button
            type="button"
            className={styles.submitButton}
            onClick={retryFetch}
            disabled={retryPending}
          >
            다시 불러오기
          </button>
        </div>
      )}
      <AllocationSectionForm
        // 계좌가 바뀌면 새 폼으로 시작한다(전환 확인을 거친 경우에만 비움). record.id로는 키를 두지 않는다 —
        // 배경 조회 결과로 키가 바뀌어 입력이 날아가는 일을 막기 위해서다.
        key={account.id}
        ipo={props.ipo}
        investor={props.investor}
        account={props.account}
        mode={mode}
        status={status}
        record={record}
        detail={props.detail}
        onSaved={props.onSaved}
        onDirtyChange={props.onDirtyChange}
        saveDisabled={props.saveDisabled}
        onPendingChange={props.onPendingChange}
      />
    </StyleScope>
  )
}

interface FormValues {
  allocatedQuantity: number
  note: string
}

function AllocationSectionForm({
  ipo,
  investor,
  account,
  mode,
  status,
  record,
  detail,
  onSaved,
  onDirtyChange,
  saveDisabled,
  onPendingChange,
}: AllocationSectionProps) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty, isSubmitting },
    reset,
  } = useForm<FormValues>({
    mode: 'onSubmit',
    defaultValues: {
      allocatedQuantity: record?.allocatedQuantity ?? Number.NaN,
      note: record?.note ?? '',
    },
  })

  // 화면은 확정 공모가 × 배정수량만 미리 보여 준다(서버가 계산한 금액을 대체하지 않는다).
  const watchedQuantity = useWatch({ control, name: 'allocatedQuantity' })

  // 다른 섹션 저장 등으로 배정 기록이 새로 생기면(create→edit), 아직 손대지 않은 칸만 기록 값으로
  // 맞춘다. 사용자가 입력 중인 값(dirty)은 그대로 유지한다.
  useEffect(() => {
    if (mode !== 'edit' || !record) return
    reset(
      { allocatedQuantity: record.allocatedQuantity, note: record.note ?? '' },
      { keepDirty: true, keepDirtyValues: true },
    )
  }, [mode, record, reset])
  const quantityForTotal =
    Number.isFinite(watchedQuantity) && watchedQuantity > 0 ? watchedQuantity : 0
  const allocationTotal = ipo.fixedPrice != null ? ipo.fixedPrice * quantityForTotal : undefined

  // 미저장 입력을 페이지에 알린다(계좌 전환 경고에 쓴다). 언마운트 시에는 해제한다.
  const onDirtyChangeRef = useRef(onDirtyChange)
  useEffect(() => {
    onDirtyChangeRef.current = onDirtyChange
  })
  useEffect(() => {
    onDirtyChangeRef.current(isDirty)
  }, [isDirty])
  useEffect(
    () => () => {
      onDirtyChangeRef.current(false)
    },
    [],
  )

  const save = useMutation({
    mutationFn: async (values: FormValues) => {
      if (mode === 'create') {
        const body: AllocationRegisterRequest = {
          allocatedQuantity: Number(values.allocatedQuantity),
          ...(values.note.trim() ? { note: values.note } : {}),
          ipoId: ipo.id,
          investorId: investor.id,
          securitiesAccountId: account.id,
          // ACTIVE 청약이 이미 있으면 그 청약에 연결한다. 없으면 청약도 함께 만들어진다.
          ...(detail ? { subscriptionId: detail.id } : {}),
        }
        return createAllocation(body)
      }
      const body: AllocationUpdateRequest = {}
      if (Number(values.allocatedQuantity) !== record!.allocatedQuantity) {
        body.allocatedQuantity = Number(values.allocatedQuantity)
      }
      if (values.note !== (record!.note ?? '')) body.note = values.note
      if (Object.keys(body).length === 0) return null
      return updateAllocation(record!.id, body)
    },
  })

  const [confirmDelete, setConfirmDelete] = useState(false)
  const [noChange, setNoChange] = useState(false)
  const [savedTick, setSavedTick] = useState(0)
  const del = useMutation({
    mutationFn: () => deleteAllocation(record!.id),
  })

  // 저장/삭제 요청부터 조회 갱신이 끝날 때까지 이 섹션의 작업을 잠근다.
  const [syncing, setSyncing] = useState(false)
  const [syncError, setSyncError] = useState(false)

  // 이 섹션의 진행 여부를 페이지에 알린다(다른 섹션 저장·계좌 전환 차단용).
  const onPendingChangeRef = useRef(onPendingChange)
  useEffect(() => {
    onPendingChangeRef.current = onPendingChange
  })
  const sectionPending = save.isPending || del.isPending || syncing
  useEffect(() => {
    onPendingChangeRef.current?.(sectionPending)
    return () => onPendingChangeRef.current?.(false)
  }, [sectionPending])

  const locked =
    sectionPending ||
    saveDisabled === true ||
    // 조회가 최신이 아니거나 편집 원본이 없으면 저장·삭제를 잠근다(입력값은 유지한다).
    status !== 'ready' ||
    (mode === 'edit' && record == null)
  const ownBusy = save.isPending || del.isPending || syncing
  const submitError = sectionErrorMessage(save.error)
  const deleteError = sectionErrorMessage(del.error)
  const finishOperation = () => setSyncing(false)

  const onSubmit = async (values: FormValues) => {
    setNoChange(false)
    if (saveDisabled || syncing || isSubmitting) return
    if (mode === 'edit' && !record) return
    if (mode === 'edit') {
      const changed =
        Number(values.allocatedQuantity) !== record!.allocatedQuantity ||
        values.note !== (record!.note ?? '')
      if (!changed) {
        setNoChange(true)
        return
      }
    }
    setSyncing(true)
    setSyncError(false)
    try {
      await save.mutateAsync(values)
    } catch {
      // 입력은 보존되고, 실패한 요청은 자동으로 다시 보내지 않는다.
      finishOperation()
      return
    }
    try {
      await onSaved()
      reset(values)
      setSavedTick((tick) => tick + 1)
    } catch {
      // 저장 자체는 성공했다. 다시 불러오기만 하면 된다.
      setSyncError(true)
    }
    finishOperation()
  }

  const retrySync = async () => {
    if (syncing) return
    setSyncing(true)
    try {
      await onSaved()
      setSyncError(false)
    } catch {
      setSyncError(true)
    }
    finishOperation()
  }

  const confirmDeleteYes = async () => {
    if (saveDisabled || syncing || !record) return
    setSyncing(true)
    setSyncError(false)
    try {
      await del.mutateAsync()
    } catch {
      // 입력은 보존되고, 실패한 요청은 자동으로 다시 보내지 않는다.
      finishOperation()
      setConfirmDelete(false)
      return
    }
    try {
      await onSaved()
    } catch {
      setSyncError(true)
    }
    finishOperation()
    setConfirmDelete(false)
  }

  return (
    <StyleScope>
      {mode === 'edit' && detail?.allocation && (
        <p className={styles.sectionMeta} role="status">
          배정 {formatShares(detail.allocation.allocatedQuantity)} · 기록 있음
        </p>
      )}
      {mode === 'create' && (
        <p className={styles.sectionBody}>
          등록된 배정이 없습니다. 청약 뒤에 배정을 입력하거나, 배정부터 입력할 수도 있습니다. 아직
          청약이 없다면 배정을 저장할 때 청약도 함께 만들어집니다.
        </p>
      )}
      {detail && (
        <div className={styles.referenceSection}>
          <ReferenceQuantity
            ipoName={detail.ipoName}
            subscribedAt={detail.subscribedAt}
            quantity={detail.subscriptionQuantity}
          />
        </div>
      )}

      <form
        className={styles.form}
        onSubmit={handleSubmit(onSubmit)}
        aria-label={mode === 'create' ? '배정 등록 폼' : '배정 수정 폼'}
        noValidate
      >
        <div className={styles.field}>
          <label htmlFor="manage-allocatedQuantity">배정수량</label>
          <input
            id="manage-allocatedQuantity"
            type="number"
            inputMode="numeric"
            min="0"
            aria-invalid={!!errors.allocatedQuantity}
            aria-describedby={
              errors.allocatedQuantity
                ? 'manage-allocatedQuantity-error'
                : 'manage-allocatedQuantity-hint'
            }
            {...register('allocatedQuantity', {
              required: '배정수량을 입력해 주세요.',
              valueAsNumber: true,
              validate: (v) => (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.',
            })}
            disabled={locked}
          />
          <span id="manage-allocatedQuantity-hint" className={styles.hint}>
            배정 결과를 확인했지만 배정받지 못한 경우 0주를 입력하세요. 이미 매도한 수량보다 작게
            줄일 수는 없습니다(같은 수량은 허용됩니다).
          </span>
          {errors.allocatedQuantity && (
            <span id="manage-allocatedQuantity-error" className={styles.error} aria-live="polite">
              {String(errors.allocatedQuantity.message)}
            </span>
          )}
        </div>

        <div className={styles.field}>
          <span className={styles.fieldLabel}>배정 공모주 총 가격</span>
          <output
            className={styles.allocationTotal}
            aria-label="배정 공모주 총 가격"
            data-testid="manage-allocation-total"
          >
            {allocationTotal === undefined
              ? '확정 공모가가 없어 자동 계산할 수 없습니다.'
              : formatWon(allocationTotal)}
          </output>
          <span className={styles.hint}>확정 공모가 × 배정수량으로 자동 계산됩니다.</span>
        </div>

        <div className={styles.field}>
          <label htmlFor="manage-note">비고 (선택)</label>
          <textarea
            id="manage-note"
            aria-invalid={!!errors.note}
            aria-describedby={errors.note ? 'manage-note-error' : undefined}
            {...register('note')}
            disabled={locked}
          />
          {errors.note && (
            <span id="manage-note-error" className={styles.error} aria-live="polite">
              {String(errors.note.message)}
            </span>
          )}
        </div>

        {(submitError || deleteError) && (
          <div className={styles.formError} role="alert">
            {submitError ?? deleteError}
          </div>
        )}
        {syncError && (
          <div className={styles.formError} role="alert">
            저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.
            <button
              type="button"
              className={styles.submitButton}
              onClick={retrySync}
              disabled={locked}
            >
              다시 불러오기
            </button>
          </div>
        )}
        {noChange && !isDirty && (
          <p className={styles.hint} role="status">
            변경된 내용이 없습니다.
          </p>
        )}
        {savedTick > 0 && !isDirty && (
          <p className={styles.hint} role="status">
            저장되었습니다.
          </p>
        )}

        <div className={styles.actions}>
          <Button type="submit" disabled={locked}>
            {ownBusy ? '저장 중...' : '배정 저장'}
          </Button>
          {mode === 'edit' && record && (
            <Button
              type="button"
              variant="secondary"
              onClick={() => setConfirmDelete(true)}
              disabled={locked}
            >
              배정 삭제
            </Button>
          )}
        </div>
      </form>

      <ConfirmDialog
        isOpen={confirmDelete}
        title="배정을 삭제할까요?"
        message="배정을 삭제하면 목록에서 보이지 않고 투자 내역에는 취소로 남습니다. 연결된 매도가 있으면 삭제할 수 없습니다. 매도를 먼저 삭제해 주세요."
        confirmLabel="삭제"
        cancelLabel="취소"
        pending={del.isPending || syncing}
        onConfirm={confirmDeleteYes}
        onCancel={() => setConfirmDelete(false)}
      />
    </StyleScope>
  )
}
