import { useForm, useWatch } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import { useIpos, type Ipo } from '@/entities/ipo/index.ts'
import { useSubscriptionRegister } from '../useSubscriptionRegister.ts'
import { type SubscriptionRegisterRequest } from '@/entities/subscription/index.ts'
import styles, { StyleScope } from './SubscriptionRegisterForm.styles.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import {
  depositFor,
  formatDate,
  ipoPriceInfo,
  sortIposForSubscription,
} from './subscriptionForm.ts'
import { toLocalYmd } from '@/shared/lib/date.ts'

type FormValues = Omit<SubscriptionRegisterRequest, 'investorId'>

export default function SubscriptionRegisterForm() {
  // 투자자는 상단 전환기의 현재 투자자다. 전환하면 페이지가 이 폼을 새로 만든다.
  const { isLoading: investorsLoading } = useCurrentInvestor()
  const { data: ipos, isLoading: iposLoading, error: iposError } = useIpos()

  if (investorsLoading || iposLoading) {
    return <StyleScope>{<div className={styles.loading}>목록을 불러오는 중...</div>}</StyleScope>
  }

  if (iposError) {
    return (
      <StyleScope>
        {
          <div className={styles.blockError}>
            목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.
          </div>
        }
      </StyleScope>
    )
  }

  return <SubscriptionForm ipos={sortIposForSubscription(ipos ?? [], toLocalYmd())} />
}

function SubscriptionForm({ ipos }: { ipos: Ipo[] }) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { current } = useCurrentInvestor()

  // ?ipoId=<id>로 들어오면 그 공모주를 미리 고른다(목록에 없으면 무시).
  const presetIpoId = Number(searchParams.get('ipoId'))
  const presetIpo = ipos.find((ipo) => ipo.id === presetIpoId)

  const { mutate, isPending, error } = useSubscriptionRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
    control,
    getValues,
    setValue,
    getFieldState,
  } = useForm<FormValues>({
    mode: 'onSubmit',
    defaultValues: { subscribedAt: toLocalYmd(), ipoId: presetIpo?.id, depositAmount: Number.NaN },
  })

  const watchedIpoId = useWatch({ control, name: 'ipoId' })
  const selectedIpo = ipos.find((ipo) => ipo.id === Number(watchedIpoId))

  /** 증거금 기본값(공모가×수량×50%). 사용자가 고친 칸은 덮어쓰지 않는다. */
  const syncDeposit = () => {
    if (getFieldState('depositAmount').isDirty) return
    const { ipoId, quantity } = getValues()
    const deposit = depositFor(
      ipos.find((ipo) => ipo.id === Number(ipoId)),
      quantity,
    )
    if (deposit !== undefined) setValue('depositAmount', deposit)
  }

  const selectedInvestorId = current?.id
  const investorSelected = selectedInvestorId !== undefined
  const {
    data: securitiesAccounts,
    isLoading: securitiesAccountsLoading,
    error: securitiesAccountsError,
  } = useSecuritiesAccounts(selectedInvestorId)

  const onSubmit = (data: FormValues) => {
    if (selectedInvestorId === undefined) return
    // 날짜만 입력받고 서버 형식에 맞춰 시각은 09:00으로 채운다.
    mutate(
      { ...data, investorId: selectedInvestorId, subscribedAt: `${data.subscribedAt}T09:00` },
      {
        onSuccess: () => {
          navigate('/subscriptions')
        },
      },
    )
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

  return (
    <StyleScope>
      {
        <form
          className={styles.form}
          onSubmit={handleSubmit(onSubmit)}
          aria-label="청약 등록 폼"
          noValidate
        >
          <p className={styles.hint}>
            투자자: <strong>{current?.displayName ?? '선택 안 됨'}</strong>
          </p>

          <div className={styles.field}>
            <label htmlFor="securitiesAccountId">증권계좌</label>
            <select
              id="securitiesAccountId"
              aria-invalid={!!errors.securitiesAccountId}
              aria-describedby={
                errors.securitiesAccountId ? 'securitiesAccountId-error' : undefined
              }
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
                onChange: syncDeposit,
              })}
              disabled={isPending}
            >
              <option value="">공모주 선택</option>
              {ipos.map((ipo) => (
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
            {selectedIpo && (
              <p className={styles.ipoInfo} data-testid="ipo-info">
                {ipoPriceInfo(selectedIpo)} · 청약 {formatDate(selectedIpo.subscriptionStartDate)} ~{' '}
                {formatDate(selectedIpo.subscriptionEndDate)}
              </p>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="quantity">청약수량</label>
            <input
              id="quantity"
              type="number"
              inputMode="numeric"
              aria-invalid={!!errors.quantity}
              aria-describedby={errors.quantity ? 'quantity-error' : undefined}
              {...register('quantity', {
                required: '청약수량을 입력해 주세요.',
                valueAsNumber: true,
                validate: (v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
                onChange: syncDeposit,
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
            <MoneyInput
              id="depositAmount"
              control={control}
              name="depositAmount"
              allowNegative // 음수를 입력하면 지우지 않고 검증 메시지로 알린다
              aria-invalid={!!errors.depositAmount}
              aria-describedby={errors.depositAmount ? 'depositAmount-error' : 'depositAmount-hint'}
              requiredMessage="증거금을 입력해 주세요."
              validate={(v) => (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.'}
              disabled={isPending}
            />
            {errors.depositAmount ? (
              <span id="depositAmount-error" className={styles.error} aria-live="polite">
                {String(errors.depositAmount.message)}
              </span>
            ) : (
              <span id="depositAmount-hint" className={styles.hint}>
                공모가 × 청약수량 × 50%로 채워 드려요. 증권사 기준에 맞게 고치세요.
              </span>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="subscribedAt">청약일</label>
            <input
              id="subscribedAt"
              type="date"
              aria-invalid={!!errors.subscribedAt}
              aria-describedby={errors.subscribedAt ? 'subscribedAt-error' : undefined}
              {...register('subscribedAt', {
                required: '청약일을 선택해 주세요.',
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
      }
    </StyleScope>
  )
}
