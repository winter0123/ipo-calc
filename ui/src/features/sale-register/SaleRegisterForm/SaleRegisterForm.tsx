import { useForm } from 'react-hook-form'
import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchInvestors } from '../../../entities/investor/api-client.ts'
import { getInvestmentHistory } from '../../../entities/subscription/api-client.ts'
import { useSaleRegister } from '../useSaleRegister.ts'
import { useAllocationOptions } from '../useAllocationOptions.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import type { SaleRegisterRequest } from '../../../entities/sale/types.ts'
import styles from './SaleRegisterForm.module.css'

export default function SaleRegisterForm() {
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | undefined>()
  const [selectedSubscriptionId, setSelectedSubscriptionId] = useState<number | undefined>()

  const {
    data: investors,
    isLoading: investorsLoading,
    error: investorsError,
  } = useQuery({ queryKey: ['investors'], queryFn: fetchInvestors })

  const { data: subscriptionHistory, isLoading: subscriptionsLoading } = useQuery({
    queryKey: ['investmentHistory', selectedInvestorId],
    queryFn: () =>
      getInvestmentHistory(selectedInvestorId ? { investorId: selectedInvestorId } : {}),
    enabled: !!selectedInvestorId,
  })

  const { data: allocationData, isLoading: allocationLoading } = useAllocationOptions(
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
    mutate(data, {
      onSuccess: () => {},
      onError: () => {},
    })
  }

  const getFormErrorMessage = () => {
    if (!error) return null
    const msg = error instanceof Error ? error.message : String(error)
    if (msg.includes('Request failed: 404') || msg.includes('Not Found: 404')) {
      return '배정을 찾을 수 없습니다.'
    }
    if (msg.includes('Request failed: 409') || msg.includes('Conflict: 409')) {
      return msg
    }
    if (msg.includes('Request failed: 400') || msg.includes('Bad Request: 400')) {
      return msg.includes('Request failed') ? '입력값을 확인해 주세요.' : msg
    }
    if (
      msg.includes('Failed to fetch') ||
      msg.includes('Network error') ||
      msg.includes('Network request failed')
    ) {
      return '네트워크 연결을 확인해 주세요.'
    }
    if (msg) return msg
    return '네트워크 연결을 확인해 주세요.'
  }

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
          aria-invalid={!!error}
          aria-describedby="investorId-error"
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
          aria-invalid={!!error}
          aria-describedby="subscriptionId-error"
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
          aria-invalid={!!errors.allocationId || !!error}
          aria-describedby="allocationId-error"
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
          aria-invalid={!!errors.quantity || !!error}
          aria-describedby="quantity-error"
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

      <div className={styles.field}>
        <label htmlFor="price">단가 (선택)</label>
        <input
          id="price"
          type="number"
          aria-invalid={!!errors.price || !!error}
          aria-describedby="price-error"
          {...register('price', {
            valueAsNumber: true,
            validate: (v) => validateNonNegativeInteger(v),
          })}
          disabled={isPending}
        />
        {errors.price && (
          <span id="price-error" className={styles.error} aria-live="polite">
            {String(errors.price.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="amount">금액 (선택)</label>
        <input
          id="amount"
          type="number"
          aria-invalid={!!errors.amount || !!error}
          aria-describedby="amount-error"
          {...register('amount', {
            valueAsNumber: true,
            validate: (v) => validateNonNegativeInteger(v),
          })}
          disabled={isPending}
        />
        {errors.amount && (
          <span id="amount-error" className={styles.error} aria-live="polite">
            {String(errors.amount.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="saleDate">매도일시</label>
        <input
          id="saleDate"
          type="datetime-local"
          aria-invalid={!!errors.saleDate || !!error}
          aria-describedby="saleDate-error"
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

      <div className={styles.field}>
        <label htmlFor="fee">수수료 (선택)</label>
        <input
          id="fee"
          type="number"
          aria-invalid={!!errors.fee || !!error}
          aria-describedby="fee-error"
          {...register('fee', {
            valueAsNumber: true,
            validate: (v) => validateNonNegativeInteger(v),
          })}
          disabled={isPending}
        />
        {errors.fee && (
          <span id="fee-error" className={styles.error} aria-live="polite">
            {String(errors.fee.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="tax">세금 (선택)</label>
        <input
          id="tax"
          type="number"
          aria-invalid={!!errors.tax || !!error}
          aria-describedby="tax-error"
          {...register('tax', {
            valueAsNumber: true,
            validate: (v) => validateNonNegativeInteger(v),
          })}
          disabled={isPending}
        />
        {errors.tax && (
          <span id="tax-error" className={styles.error} aria-live="polite">
            {String(errors.tax.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="otherCost">기타비용 (선택)</label>
        <input
          id="otherCost"
          type="number"
          aria-invalid={!!errors.otherCost || !!error}
          aria-describedby="otherCost-error"
          {...register('otherCost', {
            valueAsNumber: true,
            validate: (v) => validateNonNegativeInteger(v),
          })}
          disabled={isPending}
        />
        {errors.otherCost && (
          <span id="otherCost-error" className={styles.error} aria-live="polite">
            {String(errors.otherCost.message)}
          </span>
        )}
      </div>

      {getFormErrorMessage() && (
        <div className={styles.formError} aria-live="polite">
          {getFormErrorMessage()}
        </div>
      )}

      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '등록 중...' : '등록'}
      </button>
    </form>
  )
}
