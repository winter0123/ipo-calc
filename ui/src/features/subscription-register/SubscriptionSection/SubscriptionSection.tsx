import { useEffect, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import {
  createSubscription,
  deleteSubscription,
  updateSubscription,
  type InvestmentHistoryItem,
  type Subscription,
  type SubscriptionRegisterRequest,
  type SubscriptionUpdateRequest,
} from '@/entities/subscription/index.ts'
import type { InvestorResponse } from '@/entities/investor/index.ts'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { formatShares } from '@/shared/lib/format.ts'
import styles, { StyleScope } from '../SubscriptionRegisterForm/SubscriptionRegisterForm.styles.ts'
import {
  defaultSubscribedAt,
  depositFor,
  formatDate,
  koreanToday,
  subscriptionPeriodDates,
} from '../SubscriptionRegisterForm/subscriptionForm.ts'

/** TradeManagePage가 features의 섹션에 주입하는 조회 상태. pages에는 의존하지 않는다. */
export type SectionStatus = 'loading' | 'error' | 'ready'

export interface SubscriptionSectionProps {
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
  /** 투자 내역 상세(잔여·상태 표시용, 없으면 null — 없어도 저장은 가능해야 한다). */
  detail: InvestmentHistoryItem | null
  /** 편집 원본(GET /subscriptions/{id}) — depositAmount/memo/subscribedAt의 유일한 출처. */
  record: Subscription | null
  onDirtyChange: (dirty: boolean) => void
  /** 이 섹션의 저장/삭제 mutation이 다른 섹션과 겹치지 않도록 페이지가 켜는 잠금. */
  saveDisabled?: boolean
  /** 이 섹션의 mutation 진행 여부를 페이지에 알린다(다른 섹션 저장 차단용). */
  onPendingChange?: (pending: boolean) => void
}

// 매도·배정을 먼저 등록하면 서버가 자동으로 만들어 주는 청약을 구분하는 memo 마커.
const AUTO_GENERATED_MARKER = '매도/배정 등록에서 자동 생성된 청약 연결 기록'

const ymd = (value: string) => value.slice(0, 10)

function sectionErrorMessage(error: unknown): string | null {
  return toErrorMessage(error, {
    byStatus: {
      403: '이 청약을 수정할 권한이 없습니다.',
      404: '청약 기록을 찾을 수 없습니다. 새로고침 후 다시 시도해 주세요.',
      409: '이미 동일한 조건의 청약이 존재합니다.',
    },
  })
}

export default function SubscriptionSection(props: SubscriptionSectionProps) {
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
            청약 기록을 불러오는 중...
          </p>
        </StyleScope>
      )
    }
    if (status === 'error') {
      return (
        <StyleScope>
          <div className={styles.formError} role="alert">
            청약 기록을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.
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
          청약 기록을 불러오는 중...
        </p>
      </StyleScope>
    )
  }

  return (
    <StyleScope>
      {status === 'loading' && hydrated && (
        <p className={styles.hint} role="status">
          청약 기록을 최신으로 갱신하는 중...
        </p>
      )}
      {status === 'error' && hydrated && (
        <div className={styles.formError} role="alert">
          청약 기록을 갱신하지 못했습니다. 표시된 내용은 마지막으로 불러온 값입니다.
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
      <SubscriptionSectionForm
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
  quantity: number
  depositAmount: number
  subscribedAt: string
  memo: string
}

function SubscriptionSectionForm({
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
}: SubscriptionSectionProps) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const periodDates = subscriptionPeriodDates(ipo)
  // 기존 기록의 청약일(기간 밖 포함)은 원본 그대로 유지한다.
  const originalDate = record ? ymd(record.subscribedAt) : null
  const defaultDate =
    mode === 'create' ? defaultSubscribedAt(periodDates ?? [], koreanToday()) : (originalDate ?? '')
  const createBlocked = mode === 'create' && periodDates === null

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty, isSubmitting },
    getValues,
    getFieldState,
    setValue,
    reset,
  } = useForm<FormValues>({
    mode: 'onSubmit',
    defaultValues: {
      quantity: record?.quantity ?? Number.NaN,
      depositAmount: record?.depositAmount ?? Number.NaN,
      subscribedAt: defaultDate,
      memo: record?.memo ?? '',
    },
  })

  const watchedSubscribedAt = useWatch({ control, name: 'subscribedAt' })

  // 다른 섹션 저장 등으로 청약 기록이 새로 생기면(create→edit), 아직 손대지 않은 칸만 기록 값으로
  // 맞춘다. 사용자가 입력 중인 값(dirty)은 그대로 유지한다.
  useEffect(() => {
    if (mode !== 'edit' || !record) return
    reset(
      {
        quantity: record.quantity,
        depositAmount: record.depositAmount,
        subscribedAt: ymd(record.subscribedAt),
        memo: record.memo ?? '',
      },
      { keepDirty: true, keepDirtyValues: true },
    )
  }, [mode, record, reset])

  /** 기존(기간 밖 포함) 날짜는 보존하고, 그 외에는 실제 청약 기간 안의 날짜만 허용한다. */
  register('subscribedAt', {
    required: '청약일을 선택해 주세요.',
    validate: (v) => {
      if (originalDate && v === originalDate) return true
      if (!periodDates) return '청약 일정을 확인할 수 없어 날짜를 변경할 수 없습니다.'
      return periodDates.includes(v) || '청약 기간 안의 날짜만 선택할 수 있습니다.'
    },
  })

  /** 새 청약의 증거금 기본값(확정 공모가×수량×50%). 사용자가 고친 칸은 덮어쓰지 않는다. */
  const syncDeposit = () => {
    if (mode !== 'create') return
    if (getFieldState('depositAmount').isDirty) return
    const { quantity } = getValues()
    const deposit = depositFor(ipo, quantity)
    setValue('depositAmount', deposit ?? Number.NaN)
  }

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
        const body: SubscriptionRegisterRequest = {
          investorId: investor.id,
          securitiesAccountId: account.id,
          ipoId: ipo.id,
          quantity: Number(values.quantity),
          depositAmount: values.depositAmount,
          subscribedAt: `${values.subscribedAt}T09:00`,
          ...(values.memo.trim() ? { memo: values.memo } : {}),
        }
        return createSubscription(body)
      }
      // 변경된 필드만 보낸다(서버는 null=무변경).
      const body: SubscriptionUpdateRequest = {}
      if (Number(values.quantity) !== record!.quantity) body.quantity = Number(values.quantity)
      if (values.depositAmount !== record!.depositAmount) body.depositAmount = values.depositAmount
      if (values.subscribedAt !== originalDate) body.subscribedAt = `${values.subscribedAt}T09:00`
      if (values.memo !== (record!.memo ?? '')) body.memo = values.memo
      if (Object.keys(body).length === 0) return null
      return updateSubscription(record!.id, body)
    },
  })

  const [confirmDelete, setConfirmDelete] = useState(false)
  const [noChange, setNoChange] = useState(false)
  const [savedTick, setSavedTick] = useState(0)
  const del = useMutation({
    mutationFn: () => deleteSubscription(record!.id),
  })

  // 저장/삭제 요청부터 조회 갱신이 끝날 때까지 이 섹션의 작업을 잠근다.
  // mutation만 끝난 시점(isPending=false)에는 잠그지 않는다 — onSaved(조회)까지 완료돼야 풀린다.
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
    if (createBlocked || saveDisabled || isSubmitting || syncing) return
    if (mode === 'edit' && !record) return
    if (mode === 'edit') {
      const changed =
        Number(values.quantity) !== record!.quantity ||
        values.depositAmount !== record!.depositAmount ||
        values.subscribedAt !== originalDate ||
        values.memo !== (record!.memo ?? '')
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

  const autoGenerated = record?.memo?.includes(AUTO_GENERATED_MARKER) ?? false

  return (
    <StyleScope>
      {mode === 'edit' && detail && (
        <p className={styles.sectionMeta} role="status">
          청약 기록이 있습니다. 수량 {formatShares(detail.subscriptionQuantity)}
        </p>
      )}
      {mode === 'create' && (
        <p className={styles.sectionBody}>
          아직 등록된 청약이 없습니다. 청약부터 입력할 수 있고, 배정·매도를 먼저 입력할 수도
          있습니다. 배정·매도를 먼저 저장하면 청약도 함께 만들어집니다.
        </p>
      )}
      {autoGenerated && (
        <p className={styles.hint} role="status">
          매도·배정을 먼저 등록하면 청약이 함께 만들어질 수 있습니다. 실제 청약 내용으로 고쳐
          주세요. 자동으로 만들어진 청약일·증거금은 실제 값과 다를 수 있고, 기간이 지난 청약일도
          그대로 보존됩니다.
        </p>
      )}

      <form
        className={styles.form}
        onSubmit={handleSubmit(onSubmit)}
        aria-label={mode === 'create' ? '청약 등록 폼' : '청약 수정 폼'}
        noValidate
      >
        <div className={styles.field}>
          <label htmlFor="manage-quantity">청약수량</label>
          <input
            id="manage-quantity"
            type="number"
            inputMode="numeric"
            min="1"
            aria-invalid={!!errors.quantity}
            aria-describedby={errors.quantity ? 'manage-quantity-error' : undefined}
            {...register('quantity', {
              required: '청약수량을 입력해 주세요.',
              valueAsNumber: true,
              validate: (v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
              onChange: syncDeposit,
            })}
            disabled={locked}
          />
          {errors.quantity && (
            <span id="manage-quantity-error" className={styles.error} aria-live="polite">
              {String(errors.quantity.message)}
            </span>
          )}
        </div>

        <div className={styles.field}>
          <label htmlFor="manage-depositAmount">증거금</label>
          <MoneyInput
            id="manage-depositAmount"
            control={control}
            name="depositAmount"
            allowNegative
            aria-invalid={!!errors.depositAmount}
            aria-describedby={
              errors.depositAmount ? 'manage-depositAmount-error' : 'manage-depositAmount-hint'
            }
            requiredMessage="증거금을 입력해 주세요."
            validate={(v) => (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.'}
            disabled={locked}
          />
          {errors.depositAmount ? (
            <span id="manage-depositAmount-error" className={styles.error} aria-live="polite">
              {String(errors.depositAmount.message)}
            </span>
          ) : (
            <span id="manage-depositAmount-hint" className={styles.hint}>
              {ipo.fixedPrice == null
                ? '확정 공모가가 없어 증거금을 자동 계산하지 않습니다. 직접 입력하세요.'
                : '확정 공모가 × 청약수량 × 50%로 채워 드려요. 증권사 기준에 맞게 고치세요.'}
            </span>
          )}
        </div>

        <div className={styles.field}>
          <span id="manage-subscribedAtLabel" className={styles.fieldLabel}>
            청약일
          </span>
          {periodDates ? (
            <div role="group" aria-labelledby="manage-subscribedAtLabel" className={styles.dateRow}>
              {periodDates.map((date) => (
                <button
                  key={date}
                  type="button"
                  className={styles.dateButton}
                  aria-pressed={watchedSubscribedAt === date}
                  disabled={locked}
                  onClick={() =>
                    setValue('subscribedAt', date, {
                      shouldDirty: true,
                      shouldValidate: true,
                    })
                  }
                >
                  {date}
                </button>
              ))}
            </div>
          ) : mode === 'create' ? (
            <div className={styles.formError} role="alert">
              청약 일정을 확인할 수 없어 신규 등록을 할 수 없습니다. 공모주 상세에서 청약 기간을
              확인해 주세요.
            </div>
          ) : (
            <p className={styles.hint} role="status">
              청약 일정을 확인할 수 없어 날짜를 바꿀 수 없습니다. 기존 청약일(
              {formatDate(originalDate ?? '')})은 그대로 보존하며, 다른 항목만 저장할 수 있습니다.
            </p>
          )}
          {mode === 'edit' &&
            periodDates &&
            originalDate &&
            !periodDates.includes(originalDate) && (
              <p className={styles.hint} role="status">
                기존 청약일({formatDate(originalDate)})은 청약 기간 밖이라 그대로 보존됩니다. 날짜를
                다시 선택하지 않으면 같은 날짜로 저장됩니다.
              </p>
            )}
          {errors.subscribedAt && (
            <span id="manage-subscribedAt-error" className={styles.error} aria-live="polite">
              {String(errors.subscribedAt.message)}
            </span>
          )}
        </div>

        <div className={styles.field}>
          <label htmlFor="manage-memo">메모 (선택)</label>
          <textarea
            id="manage-memo"
            aria-invalid={!!errors.memo}
            aria-describedby={errors.memo ? 'manage-memo-error' : undefined}
            {...register('memo')}
            disabled={locked}
          />
          {errors.memo && (
            <span id="manage-memo-error" className={styles.error} aria-live="polite">
              {String(errors.memo.message)}
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
              className={styles.dateButton}
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
          <Button type="submit" disabled={locked || createBlocked}>
            {ownBusy ? '저장 중...' : '청약 저장'}
          </Button>
          {mode === 'edit' && record && (
            <Button
              type="button"
              variant="secondary"
              onClick={() => setConfirmDelete(true)}
              disabled={locked}
            >
              청약 삭제
            </Button>
          )}
        </div>
      </form>

      <ConfirmDialog
        isOpen={confirmDelete}
        title="청약을 삭제할까요?"
        message="청약을 삭제하면 목록에서 보이지 않고 투자 내역에는 취소로 남습니다. 연결된 배정이 있으면 삭제할 수 없습니다. 배정을, 배정에 매도가 있으면 매도를 먼저 삭제해 주세요."
        confirmLabel="삭제"
        cancelLabel="취소"
        pending={del.isPending || syncing}
        onConfirm={confirmDeleteYes}
        onCancel={() => setConfirmDelete(false)}
      />
    </StyleScope>
  )
}
