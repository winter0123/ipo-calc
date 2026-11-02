import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import {
  useSecuritiesAccounts,
  type SecuritiesAccount,
} from '@/entities/securities-account/index.ts'
import { useIpos, type Ipo, type IpoInsight, useIpoInsight } from '@/entities/ipo/index.ts'
import type { AllocationRegisterRequest } from '@/entities/allocation/index.ts'
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
import { useEffect, useRef } from 'react'

type SubscriptionFormValues = Omit<SubscriptionRegisterRequest, 'investorId'>

interface CombinedFormValues extends SubscriptionFormValues {
  // 배정 필드 (선택적)
  allocatedQuantity?: number
  allocatedAt?: string
  allocationMethod?: string
  allocationPrice?: number
  note?: string
}

// 주관사 이름과 계좌 증권사 이름 사이에 확정된 ID 연결이 없어 공백만 정규화해 정확 일치한다.
function normalizeName(name: string | null | undefined): string {
  return (name ?? '').trim().replace(/\s+/g, ' ')
}

const EMPTY_UNDERWRITERS: IpoInsight['underwriters'] = []

function isUnderwriterAccount(
  underwriters: IpoInsight['underwriters'],
  securitiesFirmName: string | null | undefined,
): boolean {
  const firmName = normalizeName(securitiesFirmName)
  if (!firmName) return false
  return underwriters.some((underwriter) => normalizeName(underwriter.name) === firmName)
}

interface SubscriptionRegisterFormProps {
  registerAllocation?: (request: AllocationRegisterRequest) => Promise<unknown>
  allocationError?: unknown
  allocationPending?: boolean
}

export default function SubscriptionRegisterForm({
  registerAllocation,
  allocationError,
  allocationPending,
}: SubscriptionRegisterFormProps = {}) {
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

  return (
    <SubscriptionForm
      ipos={sortIposForSubscription(ipos ?? [], toLocalYmd())}
      registerAllocation={registerAllocation}
      allocationError={allocationError}
      allocationPending={allocationPending}
    />
  )
}

function SubscriptionForm({
  ipos,
  registerAllocation,
  allocationError,
  allocationPending,
}: {
  ipos: Ipo[]
  registerAllocation?: (request: AllocationRegisterRequest) => Promise<unknown>
  allocationError?: unknown
  allocationPending?: boolean
}) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { current, isLoading: investorsLoading } = useCurrentInvestor()

  // ?ipoId=<id>로 들어오면 그 공모주를 미리 고른다(목록에 없으면 무시).
  const presetIpoId = Number(searchParams.get('ipoId'))
  const presetIpo = ipos.find((ipo) => ipo.id === presetIpoId)

  const {
    mutateAsync: mutateSubAsync,
    isPending: isPendingSub,
    error: errorSub,
  } = useSubscriptionRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
    control,
    getValues,
    setValue,
    getFieldState,
    watch,
  } = useForm<CombinedFormValues>({
    mode: 'onSubmit',
    defaultValues: {
      subscribedAt: toLocalYmd(),
      ipoId: presetIpo?.id,
      depositAmount: Number.NaN,
      allocatedQuantity: 0,
    },
  })

  // eslint-disable-next-line react-hooks/incompatible-library -- react-hook-form watch() is intentionally used in this non-memoized form.
  const watchedIpoId = watch('ipoId')
  const selectedIpo = ipos.find((ipo) => ipo.id === Number(watchedIpoId))
  const watchedAllocatedQty = watch('allocatedQuantity')
  const showAllocationFields = Number(watchedAllocatedQty ?? 0) > 0

  // 주관사 인사이트 조회 (GET /ipos/{id}/insight의 underwriters)
  const {
    data: ipoInsight,
    isLoading: ipoInsightLoading,
    error: ipoInsightError,
  } = useIpoInsight(watchedIpoId || undefined)

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

  const selectedIpoId = Number(watchedIpoId)
  const ipoSelected = Number.isFinite(selectedIpoId)
  const underwriters = ipoInsight?.underwriters ?? EMPTY_UNDERWRITERS
  // 주관사를 실제로 받았을 때만 계좌를 걸러낸다. 로딩·조회 오류·빈 배열은 기존 계좌 선택 동작을 유지한다(AC2·AC4).
  const underwritersReady = !ipoInsightLoading && !ipoInsightError && underwriters.length > 0

  // 필터링된 증권계좌 리스트 (render 시점 계산)
  let filteredAccounts: SecuritiesAccount[] = securitiesAccounts ?? []
  if (ipoSelected && underwritersReady) {
    filteredAccounts = filteredAccounts.filter((account) =>
      isUnderwriterAccount(underwriters, account.securitiesFirmName),
    )
  }

  // 선택 IPO의 주관사 계좌가 하나도 없으면 알림 후 마이페이지로 보낸다(AC5). 선택 IPO마다 1회만 처리한다.
  const warnedIpoIds = useRef<Set<number>>(new Set())
  useEffect(() => {
    // 로딩 중에는 경고/이동을 하지 않는다(AC2)
    // 상위 컴포넌트가 IPO/투자자 목록 로딩을 마친 뒤에만 폼을 렌더링한다.
    if (investorsLoading || securitiesAccountsLoading || ipoInsightLoading) return
    // 투자자가 선택되지 않았거나 계좌 목록을 아직 받지 못했다면 "주관사 계좌 없음"이 아니다
    if (!investorSelected || !securitiesAccounts) return
    // 계좌/주관사 조회 실패나 빈 주관사 배열은 "계좌 없음"이 아니다(AC4)
    if (securitiesAccountsError || ipoInsightError) return
    if (!ipoSelected || underwriters.length === 0) return
    if (warnedIpoIds.current.has(selectedIpoId)) return

    const matchedAccounts = securitiesAccounts.filter((account) =>
      isUnderwriterAccount(underwriters, account.securitiesFirmName),
    )
    if (matchedAccounts.length > 0) return

    warnedIpoIds.current.add(selectedIpoId)
    const underwriterNames = underwriters
      .map((underwriter) => normalizeName(underwriter.name))
      .filter((name) => name.length > 0)
      .join(' · ')
    window.alert(`${underwriterNames} 주관사 증권계좌 등록이 필요합니다.`)
    navigate('/my-page')
  }, [
    investorsLoading,
    investorSelected,
    securitiesAccounts,
    securitiesAccountsLoading,
    securitiesAccountsError,
    ipoInsightLoading,
    ipoInsightError,
    underwriters,
    ipoSelected,
    selectedIpoId,
    navigate,
  ])

  const onSubmit = async (data: CombinedFormValues) => {
    if (selectedInvestorId === undefined) return

    // 1) 청약 등록
    const subData: SubscriptionRegisterRequest = {
      investorId: selectedInvestorId,
      securitiesAccountId: data.securitiesAccountId,
      ipoId: data.ipoId,
      quantity: data.quantity,
      depositAmount: data.depositAmount,
      subscribedAt: `${data.subscribedAt}T09:00`,
      memo: data.memo,
    }

    try {
      const subResult = await mutateSubAsync(subData)

      // 2) 배정 필드가 채워져 있으면 배정도 등록 (allocatedQuantity > 0일 때만)
      const allocatedQty = Number(data.allocatedQuantity ?? 0)
      if (allocatedQty > 0) {
        const allocData = {
          subscriptionId: subResult.id,
          ipoId: data.ipoId,
          investorId: selectedInvestorId,
          securitiesAccountId: data.securitiesAccountId,
          allocatedQuantity: allocatedQty,
          allocatedAt: `${data.allocatedAt}T09:00`,
          allocationMethod: data.allocationMethod,
          allocationPrice: data.allocationPrice,
          note: data.note,
        }
        if (!registerAllocation) {
          throw new Error('배정 등록 기능이 연결되지 않았습니다.')
        }
        await registerAllocation(allocData)
      }

      navigate('/subscriptions')
    } catch {
      // 에러는 react-query mutation이 처리
    }
  }

  const formErrorMessage = toErrorMessage(errorSub ?? allocationError, {
    byStatus: {
      404: (e) => {
        const msg = e.serverMessage ?? ''
        if (msg.includes('증권계좌')) return '증권계좌를 찾을 수 없습니다.'
        if (msg.includes('공모주')) return '공모주를 찾을 수 없습니다.'
        if (msg.includes('청약')) return '청약을 찾을 수 없습니다.'
        return '투자자를 찾을 수 없습니다.'
      },
      409: '이미 동일한 조건의 청약/배정이 존재합니다.',
    },
  })

  return (
    <StyleScope>
      {
        <form
          className={styles.form}
          onSubmit={handleSubmit(onSubmit)}
          aria-label="청약·배정 등록 폼"
          noValidate
        >
          <p className={styles.hint}>
            투자자: <strong>{current?.displayName ?? '선택 안 됨'}</strong>
          </p>

          {/* === 청약 섹션 === */}
          <fieldset className={styles.section}>
            <legend className={styles.sectionTitle}>청약 등록</legend>

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
                disabled={isPendingSub || !investorSelected || securitiesAccountsLoading}
              >
                <option value="">
                  {!investorSelected
                    ? '투자자를 먼저 선택해 주세요'
                    : securitiesAccountsLoading
                      ? '불러오는 중...'
                      : '증권계좌 선택'}
                </option>
                {filteredAccounts.map((account) => (
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
                disabled={isPendingSub}
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
                  {ipoPriceInfo(selectedIpo)} · 청약 {formatDate(selectedIpo.subscriptionStartDate)}{' '}
                  ~ {formatDate(selectedIpo.subscriptionEndDate)}
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
                disabled={isPendingSub}
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
                allowNegative
                aria-invalid={!!errors.depositAmount}
                aria-describedby={
                  errors.depositAmount ? 'depositAmount-error' : 'depositAmount-hint'
                }
                requiredMessage="증거금을 입력해 주세요."
                validate={(v) => (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.'}
                disabled={isPendingSub}
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
                disabled={isPendingSub}
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
                disabled={isPendingSub}
              />
              {errors.memo && (
                <span id="memo-error" className={styles.error} aria-live="polite">
                  {String(errors.memo.message)}
                </span>
              )}
            </div>
          </fieldset>

          {/* === 배정 섹션 (선택적) === */}
          <fieldset
            className={`${styles.section} ${showAllocationFields ? '' : styles.sectionCollapsed}`}
          >
            <legend className={styles.sectionTitle}>
              <label className={styles.allocationToggle}>
                <input
                  type="checkbox"
                  checked={showAllocationFields}
                  onChange={(e) => {
                    setValue('allocatedQuantity', e.target.checked ? 1 : 0, {
                      shouldValidate: true,
                    })
                  }}
                  aria-label="배정 정보 입력"
                />
                배정 결과 입력 (선택)
              </label>
            </legend>

            {showAllocationFields && (
              <>
                <div className={styles.field}>
                  <label htmlFor="allocatedQuantity">배정수량</label>
                  <input
                    id="allocatedQuantity"
                    type="number"
                    inputMode="numeric"
                    min="0"
                    aria-invalid={!!errors.allocatedQuantity}
                    aria-describedby={
                      errors.allocatedQuantity
                        ? 'allocatedQuantity-error'
                        : 'allocatedQuantity-hint'
                    }
                    {...register('allocatedQuantity', {
                      valueAsNumber: true,
                      validate: (v) =>
                        v === undefined ||
                        v === null ||
                        (Number.isInteger(Number(v)) && Number(v) >= 0) ||
                        '0 이상 정수를 입력해 주세요.',
                    })}
                    disabled={isPendingSub}
                  />
                  <span id="allocatedQuantity-hint" className={styles.hint}>
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
                    disabled={isPendingSub}
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
                    aria-describedby={
                      errors.allocationMethod ? 'allocationMethod-error' : undefined
                    }
                    {...register('allocationMethod')}
                    disabled={isPendingSub}
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
                    allowNegative
                    aria-invalid={!!errors.allocationPrice}
                    aria-describedby={errors.allocationPrice ? 'allocationPrice-error' : undefined}
                    validate={(v) =>
                      !v || (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.'
                    }
                    disabled={isPendingSub}
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
                    disabled={isPendingSub}
                  />
                  {errors.note && (
                    <span id="note-error" className={styles.error} aria-live="polite">
                      {String(errors.note.message)}
                    </span>
                  )}
                </div>
              </>
            )}
          </fieldset>

          {formErrorMessage && (
            <div className={styles.formError} role="alert">
              {formErrorMessage}
            </div>
          )}

          <button
            type="submit"
            disabled={isPendingSub || allocationPending}
            className={styles.submitButton}
          >
            {isPendingSub || allocationPending ? '등록 중...' : '등록'}
          </button>
        </form>
      }
    </StyleScope>
  )
}
