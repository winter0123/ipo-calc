import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAllocationRegister } from '../useAllocationRegister.ts'
import { useSubscriptionDetail } from '../useSubscriptionDetail.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import type { AllocationRegisterRequest } from '../../../entities/allocation/types.ts'
import styles from './AllocationRegisterForm.module.css'

export default function AllocationRegisterForm() {
  const [searchParams] = useSearchParams()
  const subscriptionId = Number(searchParams.get('subscriptionId'))
  const navigate = useNavigate()

  const { data: subscriptionDetail, isLoading: detailLoading, error: detailError } = useSubscriptionDetail(
    subscriptionId || null,
  )

  const { mutate, isPending, error } = useAllocationRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AllocationRegisterRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: Omit<AllocationRegisterRequest, 'subscriptionId'>) => {
    mutate({ ...data, subscriptionId }, {
      onSuccess: () => {
        navigate('/subscriptions')
      },
      onError: () => {
        // 에러 메시지는 error 상태로 처리
      },
    })
  }

  const getFormErrorMessage = () => {
    if (!error) return null
    const msg = error instanceof Error ? error.message : String(error)
    if (msg.includes('Request failed: 404') || msg.includes('Not Found: 404')) {
      return '선택된 청약을 찾을 수 없습니다.'
    }
    if (msg.includes('Request failed: 409') || msg.includes('Conflict: 409')) {
      return '이미 동일한 청약에 대한 배정이 존재합니다.'
    }
    if (msg.includes('Request failed: 400') || msg.includes('Bad Request: 400')) {
      // 서버 400 메시지를 그대로 표시 (배정수량>청약수량 등)
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

  // subscriptionId가 유효하지 않으면 리다이렉트 (useEffect에서 처리하되 여기서는 UI만 표시)
  // 실제 리다이렉트는 Page 컴포넌트에서 처리

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