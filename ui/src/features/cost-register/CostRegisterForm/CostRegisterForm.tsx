import { useForm, useWatch } from 'react-hook-form'
import { useInvestmentHistory } from '@/entities/subscription/index.ts'
import { useCostRegister } from '../useCostRegister.ts'
import { type CostRegisterRequest } from '@/entities/cost/index.ts'
import styles from './CostRegisterForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

export default function CostRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const {
    data: subscriptionHistory,
    isLoading: subscriptionsLoading,
    error: subscriptionsError,
  } = useInvestmentHistory()

  const { mutate, isPending, error } = useCostRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
    control,
  } = useForm<CostRegisterRequest>({ mode: 'onSubmit' })
  const costTypeInput = useWatch({ control, name: 'costType' })
  // 공모주수수료(청약 수수료)는 매도 등록에서 입력한다. 여기에도 넣으면 이중으로 차감되므로 미리 알려 준다.
  const looksLikeIpoFee = /(청약|공모주)\s*수수료/.test(costTypeInput ?? '')

  const onSubmit = (data: CostRegisterRequest) => {
    mutate(data)
  }

  const formErrorMessage = toErrorMessage(error, { byStatus: { 404: '청약을 찾을 수 없습니다.' } })

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
      aria-label="기타 비용 등록 폼"
      noValidate
    >
      <div className={styles.notice} role="note">
        공모주수수료(청약 수수료)는 <strong>매도 등록</strong>에서 입력합니다. 여기에는 그 밖의
        비용(예: 증거금 이자)만 등록하세요. 등록한 비용은 매도한 수량 비율만큼 최종수익금에서
        차감됩니다.
      </div>

      <div className={styles.field}>
        <label htmlFor="subscriptionId">청약</label>
        <select
          id="subscriptionId"
          aria-invalid={!!errors.subscriptionId}
          aria-describedby={errors.subscriptionId ? 'subscriptionId-error' : undefined}
          {...register('subscriptionId', {
            required: '청약을 선택해 주세요.',
            valueAsNumber: true,
          })}
          disabled={isPending}
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
          placeholder="예: 증거금 이자"
          aria-invalid={!!errors.costType}
          aria-describedby={errors.costType ? 'costType-error' : undefined}
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
        {looksLikeIpoFee && (
          <span className={styles.warning} role="status">
            공모주수수료는 매도 등록에서 입력합니다. 여기에도 등록하면 이중으로 차감됩니다.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="amount">금액</label>
        <input
          id="amount"
          type="number"
          aria-invalid={!!errors.amount}
          aria-describedby={errors.amount ? 'amount-error' : undefined}
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
          aria-invalid={!!errors.incurredAt}
          aria-describedby={errors.incurredAt ? 'incurredAt-error' : undefined}
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
          aria-invalid={!!errors.memo}
          aria-describedby={errors.memo ? 'memo-error' : undefined}
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
