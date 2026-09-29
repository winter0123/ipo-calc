import { useForm } from 'react-hook-form'
import { useSubscriptionOptions } from '../useSubscriptionOptions.ts'
import { useCostRegister } from '../useCostRegister.ts'
import type { CostRegisterRequest } from '../../../entities/cost/types.ts'
import styles from './CostRegisterForm.module.css'

export default function CostRegisterForm() {
  const {
    data: subscriptionHistory,
    isLoading: subscriptionsLoading,
    error: subscriptionsError,
  } = useSubscriptionOptions()

  const { mutate, isPending, error } = useCostRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CostRegisterRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: CostRegisterRequest) => {
    mutate(data, {
      onSuccess: () => {},
      onError: () => {},
    })
  }

  const getFormErrorMessage = () => {
    if (!error) return null
    const msg = error instanceof Error ? error.message : String(error)
    if (msg.includes('Request failed: 404') || msg.includes('Not Found: 404')) {
      return '청약을 찾을 수 없습니다.'
    }
    if (msg.includes('Request failed: 400') || msg.includes('Bad Request: 400')) {
      return msg
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

  const validatePositiveInteger = (v: unknown) =>
    v === undefined ||
    v === null ||
    v === '' ||
    Number.isNaN(Number(v)) ||
    (Number.isInteger(Number(v)) && Number(v) > 0) ||
    '0 초과 정수를 입력해 주세요.'

  if (subscriptionsLoading) {
    return <div className={styles.loading}>목록을 불러오는 중...</div>
  }

  if (subscriptionsError) {
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
      aria-label="비용 등록 폼"
      noValidate
    >
      <div className={styles.field}>
        <label htmlFor="subscriptionId">청약</label>
        <select
          id="subscriptionId"
          aria-invalid={!!errors.subscriptionId || !!error}
          aria-describedby="subscriptionId-error"
          {...register('subscriptionId', {
            required: '청약을 선택해 주세요.',
            valueAsNumber: true,
          })}
          disabled={isPending || subscriptionsLoading}
        >
          <option value="">청약 선택</option>
          {subscriptions.map((item) => (
            <option key={item.id} value={item.id}>
              {item.ipoName} - {item.subscribedAt}
            </option>
          ))}
        </select>
        {errors.subscriptionId && (
          <span id="subscriptionId-error" className={styles.error} aria-live="polite">
            {String(errors.subscriptionId.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="costType">비용종류</label>
        <input
          id="costType"
          type="text"
          aria-invalid={!!errors.costType || !!error}
          aria-describedby="costType-error"
          {...register('costType', {
            required: '비용종류를 입력해 주세요.',
            maxLength: { value: 255, message: '비용종류는 255자 이하로 입력해 주세요.' },
          })}
          disabled={isPending}
        />
        {errors.costType && (
          <span id="costType-error" className={styles.error} aria-live="polite">
            {String(errors.costType.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="amount">금액</label>
        <input
          id="amount"
          type="number"
          aria-invalid={!!errors.amount || !!error}
          aria-describedby="amount-error"
          {...register('amount', {
            required: '금액을 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => validatePositiveInteger(v),
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
        <label htmlFor="incurredAt">발생일시</label>
        <input
          id="incurredAt"
          type="datetime-local"
          aria-invalid={!!errors.incurredAt || !!error}
          aria-describedby="incurredAt-error"
          {...register('incurredAt', {
            required: '발생일시를 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.incurredAt && (
          <span id="incurredAt-error" className={styles.error} aria-live="polite">
            {String(errors.incurredAt.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="memo">메모 (선택)</label>
        <input
          id="memo"
          type="text"
          aria-invalid={!!errors.memo || !!error}
          aria-describedby="memo-error"
          {...register('memo', {
            maxLength: { value: 255, message: '메모는 255자 이하로 입력해 주세요.' },
          })}
          disabled={isPending}
        />
        {errors.memo && (
          <span id="memo-error" className={styles.error} aria-live="polite">
            {String(errors.memo.message)}
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
