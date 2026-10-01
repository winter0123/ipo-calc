import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { useInvestors } from '@/entities/investor/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import { useIpos } from '@/entities/ipo/index.ts'
import { useSubscriptionRegister } from '../useSubscriptionRegister.ts'
import { type SubscriptionRegisterRequest } from '@/entities/subscription/index.ts'
import styles from './SubscriptionRegisterForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

export default function SubscriptionRegisterForm() {
  const navigate = useNavigate()

  const { data: investors, isLoading: investorsLoading, error: investorsError } = useInvestors()
  const { data: ipos, isLoading: iposLoading, error: iposError } = useIpos()

  const { mutate, isPending, error } = useSubscriptionRegister()

  const {
    register,
    handleSubmit,
    control,
    resetField,
    formState: { errors },
  } = useForm<SubscriptionRegisterRequest>({ mode: 'onSubmit' })

  const selectedInvestorId = useWatch({ control, name: 'investorId' })
  const investorSelected = Number.isFinite(selectedInvestorId)
  const {
    data: securitiesAccounts,
    isLoading: securitiesAccountsLoading,
    error: securitiesAccountsError,
  } = useSecuritiesAccounts(selectedInvestorId)

  const onSubmit = (data: SubscriptionRegisterRequest) => {
    mutate(data, {
      onSuccess: () => {
        navigate('/subscriptions')
      },
    })
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: {
      404: (e) => {
        const msg = e.serverMessage ?? ''
        if (msg.includes('증권계좌')) return '증권계좌를 찾을 수 없습니다.'
        if (msg.includes('공모주')) return '공모주를 찾을 수 없습니다.'
        return '투자자를 찾을 수 없습니다.'
      },
      409: '이미 동일한 조건의 청약이 존재합니다.',
    },
  })

  const dropdownsLoading = investorsLoading || iposLoading
  const dropdownsError = investorsError || iposError

  if (dropdownsLoading) {
    return <div className={styles.loading}>목록을 불러오는 중...</div>
  }

  if (dropdownsError) {
    return (
      <div className={styles.blockError}>
        목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.
      </div>
    )
  }

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="청약 등록 폼"
      noValidate
    >
      <div className={styles.field}>
        <label htmlFor="investorId">투자자</label>
        <select
          id="investorId"
          aria-invalid={!!errors.investorId}
          aria-describedby={errors.investorId ? 'investorId-error' : undefined}
          {...register('investorId', {
            required: '투자자를 선택해 주세요.',
            valueAsNumber: true,
            onChange: () => resetField('securitiesAccountId'),
          })}
          disabled={isPending}
        >
          <option value="">투자자 선택</option>
          {investors?.map((investor) => (
            <option key={investor.id} value={investor.id}>
              {investor.displayName}
            </option>
          ))}
        </select>
        {errors.investorId && (
          <span id="investorId-error" className={styles.error} aria-live="polite">
            {String(errors.investorId.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="securitiesAccountId">증권계좌</label>
        <select
          id="securitiesAccountId"
          aria-invalid={!!errors.securitiesAccountId}
          aria-describedby={errors.securitiesAccountId ? 'securitiesAccountId-error' : undefined}
          {...register('securitiesAccountId', {
            required: '증권계좌를 선택해 주세요.',
            valueAsNumber: true,
          })}
          disabled={isPending || !investorSelected || securitiesAccountsLoading}
        >
          <option value="">
            {!investorSelected
              ? '투자자를 먼저 선택해 주세요'
              : securitiesAccountsLoading
                ? '불러오는 중...'
                : '증권계좌 선택'}
          </option>
          {securitiesAccounts?.map((account) => (
            <option key={account.id} value={account.id}>
              {account.securitiesFirmName ?? account.accountNumber}
            </option>
          ))}
        </select>
        {securitiesAccountsError && (
          <span className={styles.error} role="alert">
            증권계좌 목록을 불러올 수 없습니다. 투자자를 다시 선택해 주세요.
          </span>
        )}
        {errors.securitiesAccountId && (
          <span id="securitiesAccountId-error" className={styles.error} aria-live="polite">
            {String(errors.securitiesAccountId.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="ipoId">공모주</label>
        <select
          id="ipoId"
          aria-invalid={!!errors.ipoId}
          aria-describedby={errors.ipoId ? 'ipoId-error' : undefined}
          {...register('ipoId', {
            required: '공모주를 선택해 주세요.',
            valueAsNumber: true,
          })}
          disabled={isPending}
        >
          <option value="">공모주 선택</option>
          {ipos?.map((ipo) => (
            <option key={ipo.id} value={ipo.id}>
              {ipo.ipoName}
            </option>
          ))}
        </select>
        {errors.ipoId && (
          <span id="ipoId-error" className={styles.error} aria-live="polite">
            {String(errors.ipoId.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="quantity">청약수량</label>
        <input
          id="quantity"
          type="number"
          aria-invalid={!!errors.quantity}
          aria-describedby={errors.quantity ? 'quantity-error' : undefined}
          {...register('quantity', {
            required: '청약수량을 입력해 주세요.',
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
        <label htmlFor="depositAmount">증거금</label>
        <input
          id="depositAmount"
          type="number"
          aria-invalid={!!errors.depositAmount}
          aria-describedby={errors.depositAmount ? 'depositAmount-error' : undefined}
          {...register('depositAmount', {
            required: '증거금을 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.depositAmount && (
          <span id="depositAmount-error" className={styles.error} aria-live="polite">
            {String(errors.depositAmount.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="subscribedAt">청약일시</label>
        <input
          id="subscribedAt"
          type="datetime-local"
          aria-invalid={!!errors.subscribedAt}
          aria-describedby={errors.subscribedAt ? 'subscribedAt-error' : undefined}
          {...register('subscribedAt', {
            required: '청약일시를 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.subscribedAt && (
          <span id="subscribedAt-error" className={styles.error} aria-live="polite">
            {String(errors.subscribedAt.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="memo">메모 (선택)</label>
        <textarea
          id="memo"
          aria-invalid={!!errors.memo}
          aria-describedby={errors.memo ? 'memo-error' : undefined}
          {...register('memo')}
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
