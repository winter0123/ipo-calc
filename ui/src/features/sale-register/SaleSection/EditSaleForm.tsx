import { useEffect, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import { updateSale, type Sale, type SaleUpdateRequest } from '@/entities/sale/index.ts'
import Button from '@/shared/components/Button/Button.tsx'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { isNum } from '../SaleRegisterForm/saleFormUtils.ts'
import formStyles, { StyleScope as FormScope } from '../SaleRegisterForm/SaleRegisterForm.styles.ts'
import styles, { StyleScope } from './SaleSection.styles.ts'

const ymd = (value: string) => value.slice(0, 10)
/** 등록과 같이 날짜만 받아 T09:00을 붙여 보낸다. */
const at9 = (date: string) => `${date}T09:00`

/** 숫자 칸의 "변경" 판정: 비우면(NaN) 원래 값 유지로 본다(무변경). 원래 null이면 값이 생기면 변경. */
const numberChanged = (original: number | null, current: number): boolean => {
  if (Number.isNaN(current)) return false
  if (original == null) return true
  return current !== original
}

export interface EditSaleFormProps {
  record: Sale
  /** 다른 섹션 저장 중이거나 조회가 최신이 아니면 true — 입력·제출을 잠근다. */
  locked: boolean
  /** 저장 성공 후 상세 재조회가 끝나면 resolve. 실패하면 reject. */
  onSaved: () => Promise<void>
  onDirtyChange: (dirty: boolean) => void
  onPendingChange: (pending: boolean) => void
  onCancel: () => void
  /** 저장 성공 후 상세 재조회까지 끝났을 때 부모가 수정 모드를 끝낸다. */
  onClosed: () => void
}

/** 매도 1건의 인라인 수정 폼. 변경한 필드만 PATCH하고, 금액은 단가·수량 변경 시에만 다시 계산한다. */
export default function EditSaleForm({
  record,
  locked,
  onSaved,
  onDirtyChange,
  onPendingChange,
  onCancel,
  onClosed,
}: EditSaleFormProps) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<{
    quantity: number
    price: number
    saleDate: string
    otherFees: number
    ipoFee: number
  }>({
    mode: 'onSubmit',
    defaultValues: {
      quantity: record.quantity,
      price: record.price ?? Number.NaN,
      saleDate: ymd(record.saleDate),
      otherFees: record.tax ?? Number.NaN,
      ipoFee: record.fee ?? Number.NaN,
    },
  })

  const save = useMutation({
    mutationFn: (body: SaleUpdateRequest) => updateSale(record.id, body),
  })

  const [syncing, setSyncing] = useState(false)
  const [syncError, setSyncError] = useState(false)
  const [noChange, setNoChange] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const onDirtyChangeRef = useRef(onDirtyChange)
  useEffect(() => {
    onDirtyChangeRef.current = onDirtyChange
  })
  const onPendingChangeRef = useRef(onPendingChange)
  useEffect(() => {
    onPendingChangeRef.current = onPendingChange
  })
  useEffect(() => {
    onDirtyChangeRef.current(isDirty)
    return () => onDirtyChangeRef.current(false)
  }, [isDirty])
  const pending = save.isPending || syncing
  useEffect(() => {
    onPendingChangeRef.current(pending)
    return () => onPendingChangeRef.current(false)
  }, [pending])

  const priceValue = useWatch({ control, name: 'price' })
  const priceMissing = record.price == null && !isNum(priceValue)
  const feeTaxHint = `원래 공모주수수료 ${record.fee != null ? formatWon(record.fee) : '미입력'} · 세금 및 기타 ${record.tax != null ? formatWon(record.tax) : '미입력'}`

  const onSubmit = async (values: {
    quantity: number
    price: number
    saleDate: string
    otherFees: number
    ipoFee: number
  }) => {
    if (locked || syncing || isSubmitting) return
    setNoChange(false)
    setEditError(null)

    // 단가가 없던 기록은 단가 없이 수량을 바꿀 수 없다 — 금액을 임의로 추정해 저장하지 않는다.
    if (!isNum(values.quantity) || !Number.isInteger(values.quantity) || values.quantity < 1) {
      setEditError('매도수량은 1 이상 정수를 입력해 주세요.')
      return
    }
    if (record.price != null && !isNum(values.price)) {
      setEditError('단가를 비울 수 없습니다. 원래 단가를 입력하거나 되돌려 주세요.')
      return
    }
    const quantityChanged = Number(values.quantity) !== record.quantity
    const priceChanged = numberChanged(record.price, Number(values.price))
    if (quantityChanged && !isNum(values.price) && record.price == null) {
      setEditError('이 매도는 단가가 기록에 없습니다. 수량을 바꾸려면 단가도 함께 입력해 주세요.')
      return
    }

    const body: SaleUpdateRequest = {}
    if (quantityChanged) body.quantity = Number(values.quantity)
    if (priceChanged) body.price = Number(values.price)
    if (quantityChanged || priceChanged) {
      const newPrice = isNum(values.price) ? Number(values.price) : record.price
      const newQuantity = quantityChanged ? Number(values.quantity) : record.quantity
      if (newPrice != null) body.amount = newPrice * newQuantity
    }
    if (values.saleDate !== ymd(record.saleDate)) body.saleDate = at9(values.saleDate)
    if (numberChanged(record.tax, Number(values.otherFees))) body.tax = Number(values.otherFees)
    if (numberChanged(record.fee, Number(values.ipoFee))) body.fee = Number(values.ipoFee)
    if (Object.keys(body).length === 0) {
      setNoChange(true)
      return
    }

    setSyncing(true)
    setSyncError(false)
    try {
      await save.mutateAsync(body)
    } catch {
      // 입력은 보존되고, 실패한 요청은 자동으로 다시 보내지 않는다.
      setSyncing(false)
      return
    }
    try {
      await onSaved()
      onClosed()
    } catch {
      // 저장 자체는 성공했다. 다시 불러오기만 하면 된다.
      setSyncError(true)
    }
    setSyncing(false)
  }

  const retrySync = async () => {
    if (syncing) return
    setSyncing(true)
    try {
      await onSaved()
      setSyncError(false)
      onClosed()
    } catch {
      setSyncError(true)
    }
    setSyncing(false)
  }

  const formLocked = locked || pending
  const submitError = editError ?? toErrorMessage(save.error)

  return (
    <FormScope>
      {
        <StyleScope>
          <form
            className={styles.editRow}
            onSubmit={handleSubmit(onSubmit)}
            aria-label={`매도 수정 폼 ${record.id}`}
            noValidate
          >
            <div className={styles.editGrid}>
              <div className={styles.editField}>
                <label htmlFor={`manage-edit-sale-quantity-${record.id}`}>매도수량</label>
                <input
                  id={`manage-edit-sale-quantity-${record.id}`}
                  type="number"
                  inputMode="numeric"
                  min="1"
                  aria-invalid={!!errors.quantity}
                  aria-describedby={
                    errors.quantity ? `manage-edit-sale-quantity-error-${record.id}` : undefined
                  }
                  {...register('quantity', {
                    valueAsNumber: true,
                    validate: (v) =>
                      Number.isInteger(v) && v > 0 ? true : '1 이상 정수를 입력해 주세요.',
                  })}
                  disabled={formLocked}
                />
                {errors.quantity && (
                  <span
                    id={`manage-edit-sale-quantity-error-${record.id}`}
                    className={styles.fieldError}
                    aria-live="polite"
                  >
                    {String(errors.quantity.message)}
                  </span>
                )}
              </div>

              <div className={styles.editField}>
                <label htmlFor={`manage-edit-sale-price-${record.id}`}>매도단가</label>
                <MoneyInput
                  id={`manage-edit-sale-price-${record.id}`}
                  control={control}
                  name="price"
                  aria-invalid={!!errors.price}
                  aria-describedby={`manage-edit-sale-price-hint-${record.id}`}
                  validate={(v) =>
                    Number.isNaN(v) ||
                    (Number.isInteger(v) && v > 0) ||
                    '0 초과 정수를 입력해 주세요.'
                  }
                  allowNegative
                  disabled={formLocked}
                />
                <span id={`manage-edit-sale-price-hint-${record.id}`} className={styles.fieldHint}>
                  {record.price == null
                    ? '원래 단가가 기록에 없습니다. 비워 두면 원본 금액을 그대로 보존합니다.'
                    : `원래 단가 ${formatWon(record.price)}`}
                </span>
              </div>

              <div className={styles.editField}>
                <label htmlFor={`manage-edit-sale-date-${record.id}`}>매도일</label>
                <input
                  id={`manage-edit-sale-date-${record.id}`}
                  type="date"
                  aria-invalid={!!errors.saleDate}
                  aria-describedby={
                    errors.saleDate ? `manage-edit-sale-date-error-${record.id}` : undefined
                  }
                  {...register('saleDate', { required: '매도일을 입력해 주세요.' })}
                  disabled={formLocked}
                />
                {errors.saleDate && (
                  <span
                    id={`manage-edit-sale-date-error-${record.id}`}
                    className={styles.fieldError}
                    aria-live="polite"
                  >
                    {String(errors.saleDate.message)}
                  </span>
                )}
              </div>

              <div className={styles.editField}>
                <label htmlFor={`manage-edit-sale-otherFees-${record.id}`}>세금 및 기타</label>
                <MoneyInput
                  id={`manage-edit-sale-otherFees-${record.id}`}
                  control={control}
                  name="otherFees"
                  disabled={formLocked}
                />
                <span className={styles.fieldHint}>{feeTaxHint} · 비우면 원래 값 유지</span>
              </div>

              <div className={styles.editField}>
                <label htmlFor={`manage-edit-sale-ipoFee-${record.id}`}>공모주수수료</label>
                <MoneyInput
                  id={`manage-edit-sale-ipoFee-${record.id}`}
                  control={control}
                  name="ipoFee"
                  disabled={formLocked}
                />
                <span className={styles.fieldHint}>비우면 원래 값 유지</span>
              </div>
            </div>
            {priceMissing && (
              <p className={styles.fieldHint} role="status">
                단가가 기록에 없어 수량만 바꾸면 금액을 계산할 수 없습니다. 단가를 입력해 주세요.
              </p>
            )}
            {submitError && (
              <div className={formStyles.formError} role="alert">
                {submitError}
              </div>
            )}
            {syncError && (
              <div className={formStyles.formError} role="alert">
                저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.
                <button
                  type="button"
                  className={formStyles.submitButton}
                  onClick={retrySync}
                  disabled={formLocked}
                >
                  다시 불러오기
                </button>
              </div>
            )}
            {noChange && !isDirty && (
              <p className={formStyles.hint} role="status">
                변경된 내용이 없습니다.
              </p>
            )}

            <div className={styles.rowActions}>
              <Button type="submit" disabled={formLocked}>
                {pending ? '저장 중...' : '수정 저장'}
              </Button>
              <Button type="button" variant="secondary" onClick={onCancel} disabled={formLocked}>
                취소
              </Button>
            </div>
          </form>
        </StyleScope>
      }
    </FormScope>
  )
}
