import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAllocationRegister } from '../useAllocationRegister.ts'
import { useSubscriptionDetail } from '../useSubscriptionDetail.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import type { AllocationRegisterRequest } from '../../../entities/allocation/types.ts'
import styles from './AllocationRegisterForm.module.css'
import { toErrorMessage } from '../../../shared/api/apiError.ts'

export default function AllocationRegisterForm() {
  const [searchParams] = useSearchParams()
  const subscriptionId = Number(searchParams.get('subscriptionId'))
  const navigate = useNavigate()

  const {
    data: subscriptionDetail,
    isLoading: detailLoading,
    error: detailError,
  } = useSubscriptionDetail(subscriptionId || null)

  const { mutate, isPending, error } = useAllocationRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AllocationRegisterRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: Omit<AllocationRegisterRequest, 'subscriptionId'>) => {
    mutate(
      { ...data, subscriptionId },
      {
        onSuccess: () => {
          navigate('/subscriptions')
        },
      },
    )
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: {
      404: '선택된 청약을 찾을 수 없습니다.',
      409: '이미 동일한 청약에 대한 배정이 존재합니다.',
    },
  })

  if (detailLoading) {
    return <div className={styles.loading}>청약 정보를 불러오는 중...</div>
  }

  if (detailError || !subscriptionDetail) {
    return (
      <div className={styles.blockError}>
        청약 정보를 불러올 수 없습니다. 청약 목록으로 이동합니다.
      </div>
    )
  }

  const referenceQuantity = subscriptionDetail.subscriptionQuantity

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="배정 등록 폼"
      noValidate
    >
      <div className={styles.referenceSection}>
        <ReferenceQuantity quantity={referenceQuantity} />
      </div>

      <div className={styles.field}>
        <label htmlFor="allocatedQuantity">배정수량</label>
        <input
          id="allocatedQuantity"
          type="number"
          aria-invalid={!!errors.allocatedQuantity || !!error}
          aria-describedby="allocatedQuantity-error"
          {...register('allocatedQuantity', {
            required: '배정수량을 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.allocatedQuantity && (
          <span id="allocatedQuantity-error" className={styles.error} aria-live="polite">
            {String(errors.allocatedQuantity.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="allocatedAt">배정일시</label>
        <input
          id="allocatedAt"
          type="datetime-local"
          aria-invalid={!!errors.allocatedAt || !!error}
          aria-describedby="allocatedAt-error"
          {...register('allocatedAt', {
            required: '배정일시를 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.allocatedAt && (
          <span id="allocatedAt-error" className={styles.error} aria-live="polite">
            {String(errors.allocatedAt.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="allocationMethod">배정방식 (선택)</label>
        <select
          id="allocationMethod"
          aria-invalid={!!errors.allocationMethod}
          aria-describedby="allocationMethod-error"
          {...register('allocationMethod')}
          disabled={isPending}
        >
          <option value="">배정방식 선택</option>
          <option value="PROPORTIONAL">비례배정</option>
          <option value="LOTTERY">추첨배정</option>
          <option value="FIRST_COME">선착순</option>
        </select>
        {errors.allocationMethod && (
          <span id="allocationMethod-error" className={styles.error} aria-live="polite">
            {String(errors.allocationMethod.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="allocationPrice">배정단가 (선택)</label>
        <input
          id="allocationPrice"
          type="number"
          aria-invalid={!!errors.allocationPrice || !!error}
          aria-describedby="allocationPrice-error"
          {...register('allocationPrice', {
            valueAsNumber: true,
            validate: (v) => !v || (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.allocationPrice && (
          <span id="allocationPrice-error" className={styles.error} aria-live="polite">
            {String(errors.allocationPrice.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="note">비고 (선택)</label>
        <textarea
          id="note"
          aria-invalid={!!errors.note}
          aria-describedby="note-error"
          {...register('note')}
          disabled={isPending}
        />
        {errors.note && (
          <span id="note-error" className={styles.error} aria-live="polite">
            {String(errors.note.message)}
          </span>
        )}
      </div>

      {formErrorMessage && (
        <div className={styles.formError} aria-live="polite">
          {formErrorMessage}
        </div>
      )}

      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '등록 중...' : '등록'}
      </button>
    </form>
  )
}
