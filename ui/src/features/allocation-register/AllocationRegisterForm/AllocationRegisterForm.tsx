import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAllocationRegister } from '../useAllocationRegister.ts'
import { useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import { type AllocationRegisterRequest } from '@/entities/allocation/index.ts'
import styles, { StyleScope } from './AllocationRegisterForm.styles.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { toLocalYmd } from '@/shared/lib/date.ts'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useIpo } from '@/entities/ipo/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'

export default function AllocationRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const [searchParams] = useSearchParams()
  const parsedSubscriptionId = Number(searchParams.get('subscriptionId'))
  const subscriptionId =
    Number.isSafeInteger(parsedSubscriptionId) && parsedSubscriptionId > 0
      ? parsedSubscriptionId
      : undefined
  const ipoId = Number(searchParams.get('ipoId')) || undefined
  const { current } = useCurrentInvestor()
  const investorId = Number(searchParams.get('investorId')) || current?.id
  const ipoQuery = useIpo(ipoId)
  const accountsQuery = useSecuritiesAccounts(investorId)
  const navigate = useNavigate()

  const {
    data: subscriptionDetail,
    isLoading: detailLoading,
    error: detailError,
  } = useInvestmentHistoryDetail(subscriptionId ?? null)

  const { mutate, isPending, error } = useAllocationRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
    control,
  } = useForm<AllocationRegisterRequest>({
    mode: 'onSubmit',
    defaultValues: {
      allocatedAt: toLocalYmd(),
      allocationPrice: Number.NaN,
    },
  })

  const onSubmit = (data: AllocationRegisterRequest) => {
    mutate(
      // 날짜만 입력받고 서버 형식에 맞춰 시각은 09:00으로 채운다.
      {
        ...data,
        subscriptionId,
        ipoId,
        investorId,
        allocatedAt: `${data.allocatedAt}T09:00`,
      },
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

  if (subscriptionId && detailLoading) {
    return (
      <StyleScope>{<div className={styles.loading}>청약 정보를 불러오는 중...</div>}</StyleScope>
    )
  }

  if (subscriptionId && (detailError || !subscriptionDetail)) {
    return (
      <StyleScope>
        {
          <div className={styles.blockError}>
            청약 정보를 불러올 수 없습니다. 청약 목록으로 이동합니다.
          </div>
        }
      </StyleScope>
    )
  }

  return (
    <StyleScope>
      {
        <form
          className={styles.form}
          onSubmit={handleSubmit(onSubmit)}
          aria-label="배정 등록 폼"
          noValidate
        >
          {subscriptionDetail ? (
            <div className={styles.referenceSection}>
              <ReferenceQuantity
                ipoName={subscriptionDetail.ipoName}
                subscribedAt={subscriptionDetail.subscribedAt}
                quantity={subscriptionDetail.subscriptionQuantity}
              />
            </div>
          ) : (
            <>
              <div className={styles.referenceSection}>
                {ipoQuery.data?.ipoName ?? '공모주'} · {ipoQuery.data?.companyName ?? ''}
                <p className={styles.hint}>배정 입력 시 청약 연결 기록도 함께 생성됩니다.</p>
              </div>
              <div className={styles.field}>
                <label htmlFor="securitiesAccountId">증권계좌</label>
                <select
                  id="securitiesAccountId"
                  {...register('securitiesAccountId', {
                    required: true,
                    valueAsNumber: true,
                  })}
                  disabled={isPending || accountsQuery.isLoading}
                >
                  <option value="">계좌 선택</option>
                  {(accountsQuery.data ?? []).map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.securitiesFirmName ?? '증권사'} · {account.accountName ?? '계좌'}
                    </option>
                  ))}
                </select>
              </div>
            </>
          )}

          <div className={styles.field}>
            <label htmlFor="allocatedQuantity">배정수량</label>
            <input
              id="allocatedQuantity"
              type="number"
              inputMode="numeric"
              aria-invalid={!!errors.allocatedQuantity}
              aria-describedby={errors.allocatedQuantity ? 'allocatedQuantity-error' : undefined}
              {...register('allocatedQuantity', {
                required: '배정수량을 입력해 주세요.',
                valueAsNumber: true,
                validate: (v) => (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.',
              })}
              disabled={isPending}
            />
            <span className={styles.hint}>
              배정 결과를 확인했지만 배정받지 못한 경우 0주를 입력하세요.
            </span>
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
            <MoneyInput
              id="allocationPrice"
              control={control}
              name="allocationPrice"
              allowNegative // 음수를 입력하면 지우지 않고 검증 메시지로 알린다
              aria-invalid={!!errors.allocationPrice}
              aria-describedby={errors.allocationPrice ? 'allocationPrice-error' : undefined}
              validate={(v) =>
                !v || (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.'
              }
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
      }
    </StyleScope>
  )
}
