import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAllocationRegister } from '../useAllocationRegister.ts'
import { useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import { type AllocationRegisterRequest } from '@/entities/allocation/index.ts'
import styles from './AllocationRegisterForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

export default function AllocationRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const [searchParams] = useSearchParams()
  const subscriptionId = Number(searchParams.get('subscriptionId'))
  const navigate = useNavigate()

  const {
    data: subscriptionDetail,
    isLoading: detailLoading,
    error: detailError,
  } = useInvestmentHistoryDetail(subscriptionId || null)

  const { mutate, isPending, error } = useAllocationRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AllocationRegisterRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: Omit<AllocationRegisterRequest, 'subscriptionId'>) => {
    mutate(
      // 날짜만 입력받고 서버 형식에 맞춰 시각은 09:00으로 채운다.
      { ...data, subscriptionId, allocatedAt: `${data.allocatedAt}T09:00` },
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
          aria-invalid={!!errors.allocatedQuantity}
          aria-describedby={errors.allocatedQuantity ? 'allocatedQuantity-error' : undefined}
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
        <label htmlFor="allocatedAt">배정일</label>
        <input
          id="allocatedAt"
          type="date"
          aria-invalid={!!errors.allocatedAt}
          aria-describedby={errors.allocatedAt ? 'allocatedAt-error' : undefined}
          {...register('allocatedAt', {
            required: '배정일을 선택해 주세요.',
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
          aria-describedby={errors.allocationMethod ? 'allocationMethod-error' : undefined}
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
          aria-invalid={!!errors.allocationPrice}
          aria-describedby={errors.allocationPrice ? 'allocationPrice-error' : undefined}
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
          aria-describedby={errors.note ? 'note-error' : undefined}
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
