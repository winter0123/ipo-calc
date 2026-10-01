import { useForm } from 'react-hook-form'
import { useState, useEffect } from 'react'
import { useInvestors } from '@/entities/investor/index.ts'
import { useInvestmentHistory, useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import { useSaleRegister } from '../useSaleRegister.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import { type SaleRegisterRequest } from '@/entities/sale/index.ts'
import styles from './SaleRegisterForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

type OptionalNumberField = 'price' | 'amount' | 'fee' | 'tax' | 'otherCost'

export default function SaleRegisterForm() {
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | undefined>()
  const [selectedSubscriptionId, setSelectedSubscriptionId] = useState<number | undefined>()

  const { data: investors, isLoading: investorsLoading, error: investorsError } = useInvestors()

  const { data: subscriptionHistory, isLoading: subscriptionsLoading } = useInvestmentHistory(
    selectedInvestorId ? { investorId: selectedInvestorId } : {},
    { enabled: !!selectedInvestorId },
  )

  const { data: allocationData, isLoading: allocationLoading } = useInvestmentHistoryDetail(
    selectedSubscriptionId ?? null,
  )

  const { mutate, isPending, error } = useSaleRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    resetField,
  } = useForm<SaleRegisterRequest>({ mode: 'onSubmit' })

  useEffect(() => {
    if (allocationData?.allocation) {
      setValue('allocationId', allocationData.allocation.id)
    }
  }, [allocationData, setValue])

  const onSubmit = (data: SaleRegisterRequest) => {
    mutate(data)
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: { 404: '배정을 찾을 수 없습니다.', 409: (e) => e.message },
  })

  const handleInvestorChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value ? Number(e.target.value) : undefined
    setSelectedInvestorId(id)
    setSelectedSubscriptionId(undefined)
    resetField('allocationId')
  }

  const handleSubscriptionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value ? Number(e.target.value) : undefined
    setSelectedSubscriptionId(id)
    resetField('allocationId')
  }

  const validateNonNegativeInteger = (v: unknown) =>
    v === undefined ||
    v === null ||
    v === '' ||
    Number.isNaN(Number(v)) ||
    (Number.isInteger(Number(v)) && Number(v) >= 0) ||
    '0 이상 정수를 입력해 주세요.'

  const renderOptionalNumberField = (name: OptionalNumberField, label: string) => (
    <div className={styles.field}>
      <label htmlFor={name}>{label}</label>
      <input
        id={name}
        type="number"
        aria-invalid={!!errors[name]}
        aria-describedby={errors[name] ? `${name}-error` : undefined}
        {...register(name, {
          valueAsNumber: true,
          validate: (v) => validateNonNegativeInteger(v),
        })}
        disabled={isPending}
      />
      {errors[name] && (
        <span id={`${name}-error`} className={styles.error} aria-live="polite">
          {String(errors[name]?.message)}
        </span>
      )}
    </div>
  )

  if (investorsLoading) {
    return <div className={styles.loading}>목록을 불러오는 중...</div>
  }

  if (investorsError) {
    return (
      <div className={styles.blockError}>
        목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.
      </div>
    )
  }

  const subscriptions = subscriptionHistory?.content ?? []

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="매도 등록 폼"
      noValidate
    >
      <div className={styles.field}>
        <label htmlFor="investorId">투자자</label>
        <select
          id="investorId"
          value={selectedInvestorId ?? ''}
          onChange={handleInvestorChange}
          disabled={isPending}
        >
          <option value="">투자자 선택</option>
          {investors?.map((investor) => (
            <option key={investor.id} value={investor.id}>
              {investor.displayName}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.field}>
        <label htmlFor="subscriptionId">청약</label>
        <select
          id="subscriptionId"
          value={selectedSubscriptionId ?? ''}
          onChange={handleSubscriptionChange}
          disabled={isPending || !selectedInvestorId || subscriptionsLoading}
        >
          <option value="">청약 선택</option>
          {subscriptions.map((item) => (
            <option key={item.id} value={item.id}>
              {item.ipoName} - {item.subscribedAt}
            </option>
          ))}
        </select>
      </div>

      {selectedSubscriptionId && (
        <div className={styles.referenceSection}>
          {allocationLoading && <div className={styles.loading}>로딩 중...</div>}
          {allocationData && allocationData.remainingQuantity !== null && (
            <ReferenceQuantity quantity={allocationData.remainingQuantity} />
          )}
        </div>
      )}

      <div className={styles.field}>
        <label htmlFor="allocationId">배정</label>
        <select
          id="allocationId"
          aria-invalid={!!errors.allocationId}
          aria-describedby={errors.allocationId ? 'allocationId-error' : undefined}
          {...register('allocationId', {
            required: '배정을 선택해 주세요.',
            valueAsNumber: true,
          })}
          disabled={isPending || !selectedSubscriptionId}
        >
          <option value="">배정 선택</option>
          {allocationData?.allocation ? (
            <option key={allocationData.allocation.id} value={allocationData.allocation.id}>
              배정 #{allocationData.allocation.id} ({allocationData.allocation.allocatedQuantity}주)
            </option>
          ) : null}
        </select>
        {errors.allocationId && (
          <span id="allocationId-error" className={styles.error} aria-live="polite">
            {String(errors.allocationId.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="quantity">매도수량</label>
        <input
          id="quantity"
          type="number"
          aria-invalid={!!errors.quantity}
          aria-describedby={errors.quantity ? 'quantity-error' : undefined}
          {...register('quantity', {
            required: '매도수량을 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.quantity && (
          <span id="quantity-error" className={styles.error} aria-live="polite">
            {String(errors.quantity.message)}
          </span>
        )}
      </div>

      {renderOptionalNumberField('price', '단가 (선택)')}

      {renderOptionalNumberField('amount', '금액 (선택)')}

      <div className={styles.field}>
        <label htmlFor="saleDate">매도일시</label>
        <input
          id="saleDate"
          type="datetime-local"
          aria-invalid={!!errors.saleDate}
          aria-describedby={errors.saleDate ? 'saleDate-error' : undefined}
          {...register('saleDate', {
            required: '매도일시를 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.saleDate && (
          <span id="saleDate-error" className={styles.error} aria-live="polite">
            {String(errors.saleDate.message)}
          </span>
        )}
      </div>

      {renderOptionalNumberField('fee', '수수료 (선택)')}

      {renderOptionalNumberField('tax', '세금 (선택)')}

      {renderOptionalNumberField('otherCost', '기타비용 (선택)')}

      {formErrorMessage && (
        <div className={styles.formError} role="alert">
          {formErrorMessage}
        </div>
      )}

      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '등록 중...' : '등록'}
      </button>
    </form>
  )
}
