import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchInvestors } from '../../../entities/investor/api-client.ts'
import { fetchSecuritiesAccounts } from '../../../entities/securities-account/api-client.ts'
import { fetchIpos } from '../../../entities/ipo/api-client.ts'
import { useSubscriptionRegister } from '../useSubscriptionRegister.ts'
import type { SubscriptionRegisterRequest } from '../../../entities/subscription/types.ts'
import styles from './SubscriptionRegisterForm.module.css'

export default function SubscriptionRegisterForm() {
  const navigate = useNavigate()

  const {
    data: investors,
    isLoading: investorsLoading,
    error: investorsError,
  } = useQuery({ queryKey: ['investors'], queryFn: fetchInvestors })
  const {
    data: securitiesAccounts,
    isLoading: securitiesAccountsLoading,
    error: securitiesAccountsError,
  } = useQuery({ queryKey: ['securities-accounts'], queryFn: () => fetchSecuritiesAccounts() })
  const {
    data: ipos,
    isLoading: iposLoading,
    error: iposError,
  } = useQuery({ queryKey: ['ipos'], queryFn: fetchIpos })

  const { mutate, isPending, error } = useSubscriptionRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SubscriptionRegisterRequest>({ mode: 'onSubmit' })

  const onSubmit = (data: SubscriptionRegisterRequest) => {
    mutate(data, {
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
      if (msg.includes('투자자') || msg.includes('investor')) {
        return '투자자를 찾을 수 없습니다.'
      }
      if (msg.includes('증권계좌') || msg.includes('securities-account')) {
        return '증권계좌를 찾을 수 없습니다.'
      }
      if (msg.includes('공모주') || msg.includes('ipo')) {
        return '공모주를 찾을 수 없습니다.'
      }
      return '투자자를 찾을 수 없습니다.'
    }
    if (msg.includes('Request failed: 409') || msg.includes('Conflict: 409')) {
      return '이미 동일한 조건의 청약이 존재합니다.'
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

  const dropdownsLoading = investorsLoading || securitiesAccountsLoading || iposLoading
  const dropdownsError = investorsError || securitiesAccountsError || iposError

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
          aria-invalid={!!errors.investorId || !!error}
          aria-describedby="investorId-error"
          {...register('investorId', {
            required: '투자자를 선택해 주세요.',
            valueAsNumber: true,
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
          aria-invalid={!!errors.securitiesAccountId || !!error}
          aria-describedby="securitiesAccountId-error"
          {...register('securitiesAccountId', {
            required: '증권계좌를 선택해 주세요.',
            valueAsNumber: true,
          })}
          disabled={isPending}
        >
          <option value="">증권계좌 선택</option>
          {securitiesAccounts?.map((account) => (
            <option key={account.id} value={account.id}>
              {account.securitiesFirmName ?? account.accountNumber}
            </option>
          ))}
        </select>
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
          aria-invalid={!!errors.ipoId || !!error}
          aria-describedby="ipoId-error"
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
          aria-invalid={!!errors.quantity || !!error}
          aria-describedby="quantity-error"
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
          aria-invalid={!!errors.depositAmount || !!error}
          aria-describedby="depositAmount-error"
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
          aria-invalid={!!errors.subscribedAt || !!error}
          aria-describedby="subscribedAt-error"
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
          aria-describedby="memo-error"
          {...register('memo')}
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
