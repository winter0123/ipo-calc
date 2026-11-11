import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import { useIpo, type Ipo, type IpoInsight, useIpoInsight } from '@/entities/ipo/index.ts'
import type { AllocationRegisterRequest } from '@/entities/allocation/index.ts'
import { useSubscriptionRegister } from '../useSubscriptionRegister.ts'
import { type SubscriptionRegisterRequest } from '@/entities/subscription/index.ts'
import styles, { StyleScope } from './SubscriptionRegisterForm.styles.ts'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import {
  allocationTotalFor,
  defaultSubscribedAt,
  depositFor,
  formatDate,
  ipoPriceInfo,
  koreanToday,
  subscriptionPeriodDates,
} from './subscriptionForm.ts'
import { formatWon } from '@/shared/lib/format.ts'
import { useEffect, useMemo, useRef } from 'react'

type SubscriptionFormValues = Omit<SubscriptionRegisterRequest, 'investorId'>

interface CombinedFormValues extends SubscriptionFormValues {
  // 배정 필드 (선택적)
  allocatedQuantity?: number
  note?: string
}

// 주관사 이름과 계좌 증권사 이름 사이에 확정된 ID 연결이 없어 공백만 정규화해 정확 일치한다.
function normalizeName(name: string | null | undefined): string {
  return (name ?? '')
    .normalize('NFKC')
    .replace(/주식회사|\(주\)|㈜/g, '')
    .replace(/\s+/g, '')
    .replace(/^아이비케이/, 'IBK')
    .replace(/^ibk/i, 'IBK')
    .replace(/[^a-zA-Z0-9가-힣]/g, '')
}

const EMPTY_UNDERWRITERS: IpoInsight['underwriters'] = []

function isUnderwriterAccount(
  underwriters: IpoInsight['underwriters'],
  securitiesFirmName: string | null | undefined,
): boolean {
  const firmName = normalizeName(securitiesFirmName)
  if (!firmName) return false
  return underwriters.some(
    (underwriter) =>
      normalizeName(underwriter.name).toLocaleLowerCase() === firmName.toLocaleLowerCase(),
  )
}

const parseIdParam = (raw: string | null): number | undefined => {
  if (raw == null) return undefined
  const value = Number(raw)
  return Number.isInteger(value) && value > 0 ? value : undefined
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
  const [searchParams] = useSearchParams()
  const rawIpoId = searchParams.get('ipoId')
  const presetIpoId = parseIdParam(rawIpoId)
  const ipoQuery = useIpo(presetIpoId)

  if (investorsLoading) {
    return <StyleScope>{<div className={styles.loading}>목록을 불러오는 중...</div>}</StyleScope>
  }

  // 문맥(공모주) 없는 /subscriptions/register — 임의 선택 없이 청약 목록으로 안내만 한다
  if (rawIpoId == null) {
    return (
      <StyleScope>
        <div className={styles.guide}>
          <h2>공모주를 선택해 들어와 주세요</h2>
          <p>
            공모주 청약 목록에서 공모주를 누르고 그 화면의 <strong>청약 등록</strong>을 열어 주세요.
          </p>
          <Link to="/subscriptions">공모주 청약 목록으로 이동</Link>
        </div>
      </StyleScope>
    )
  }

  // 문맥은 있지만 id가 올바르지 않으면 폼을 열지 않는다
  if (presetIpoId === undefined) {
    return (
      <StyleScope>
        <div className={styles.blockError} role="alert">
          공모주 주소가 올바르지 않습니다. 공모주 청약 목록에서 다시 열어 주세요.{' '}
          <Link to="/subscriptions">공모주 청약 목록으로 이동</Link>
        </div>
      </StyleScope>
    )
  }

  if (ipoQuery.isPending) {
    return <StyleScope>{<div className={styles.loading}>목록을 불러오는 중...</div>}</StyleScope>
  }

  if (ipoQuery.isError || !ipoQuery.data) {
    return (
      <StyleScope>
        <div className={styles.blockError} role="alert">
          공모주를 찾을 수 없습니다. 공모주 청약 목록에서 다시 열어 주세요.{' '}
          <Link to="/subscriptions">공모주 청약 목록으로 이동</Link>
        </div>
      </StyleScope>
    )
  }

  return (
    <SubscriptionForm
      ipo={ipoQuery.data}
      registerAllocation={registerAllocation}
      allocationError={allocationError}
      allocationPending={allocationPending}
    />
  )
}

function SubscriptionForm({
  ipo,
  registerAllocation,
  allocationError,
  allocationPending,
}: {
  ipo: Ipo
  registerAllocation?: (request: AllocationRegisterRequest) => Promise<unknown>
  allocationError?: unknown
  allocationPending?: boolean
}) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const navigate = useNavigate()
  const { current, isLoading: investorsLoading } = useCurrentInvestor()

  // 청약 기간의 실제 날짜(없거나 역전이면 null — 임의 값으로 대체하지 않는다)
  const periodDates = subscriptionPeriodDates(ipo)
  const defaultDate = defaultSubscribedAt(periodDates ?? [], koreanToday())

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
      subscribedAt: defaultDate,
      ipoId: ipo.id,
      depositAmount: Number.NaN,
      allocatedQuantity: 0,
    },
  })

  // subscribedAt은 입력 UI 없이 날짜 버튼이 setValue로 채운다. 검증 규칙만 등록한다.
  register('subscribedAt', {
    required: '청약일을 선택해 주세요.',
    validate: (v) =>
      (periodDates?.includes(v) ?? false) || '청약 기간 안의 날짜만 선택할 수 있습니다.',
  })

  // eslint-disable-next-line react-hooks/incompatible-library -- react-hook-form watch() is intentionally used in this non-memoized form.
  const watchedSubscribedAt = watch('subscribedAt')
  const watchedAllocatedQty = watch('allocatedQuantity')
  const allocationTotal = allocationTotalFor(ipo, Number(watchedAllocatedQty ?? 0))
  const allocationPriceMissing = ipo.fixedPrice == null

  /** 증거금 기본값(확정 공모가×수량×50%). 사용자가 고친 칸은 덮어쓰지 않는다. */
  const syncDeposit = () => {
    if (getFieldState('depositAmount').isDirty) return
    const { quantity } = getValues()
    const deposit = depositFor(ipo, quantity)
    // 확정 공모가가 없으면 계산하지 않고, 이전 자동값이 남지 않도록 비운다.
    setValue('depositAmount', deposit ?? Number.NaN)
  }

  const selectedInvestorId = current?.id
  const investorSelected = selectedInvestorId !== undefined
  const {
    data: securitiesAccounts,
    isLoading: securitiesAccountsLoading,
    error: securitiesAccountsError,
  } = useSecuritiesAccounts(selectedInvestorId)

  const selectedIpoId = ipo.id

  // 주관사 인사이트 조회 (GET /ipos/{id}/insight의 underwriters)
  const {
    data: ipoInsight,
    isLoading: ipoInsightLoading,
    error: ipoInsightError,
  } = useIpoInsight(selectedIpoId)
  const resolvedUnderwriters = ipoInsight?.underwriters ?? EMPTY_UNDERWRITERS
  // 주관사를 실제로 받았을 때만 계좌를 걸러낸다. 로딩/오류/빈 배열은 계좌 옵션을 노출하지 않고 select를 disabled로 유지한다(AC2·AC3·AC5).
  const underwritersReady =
    !ipoInsightLoading && !ipoInsightError && resolvedUnderwriters.length > 0

  // 필터링된 증권계좌 리스트 (인사이트 로딩·오류·빈 주관사는 옵션 노출 금지)
  const filteredAccounts = useMemo(() => {
    if (!underwritersReady) return []
    return (securitiesAccounts ?? []).filter((account) =>
      isUnderwriterAccount(resolvedUnderwriters, account.securitiesFirmName),
    )
  }, [underwritersReady, securitiesAccounts, resolvedUnderwriters])

  // 단일 주관사에 일치하는 계좌가 하나뿐이면 그 계좌를 기본 선택한다.
  useEffect(() => {
    if (!underwritersReady || securitiesAccountsLoading || resolvedUnderwriters.length !== 1) return
    if (filteredAccounts.length !== 1) return
    setValue('securitiesAccountId', filteredAccounts[0].id, {
      shouldValidate: false,
      shouldDirty: false,
    })
  }, [
    underwritersReady,
    securitiesAccountsLoading,
    resolvedUnderwriters.length,
    filteredAccounts,
    setValue,
  ])

  // 선택 IPO의 주관사 계좌가 하나도 없으면 알림 후 마이페이지로 보낸다(AC5). 공모주마다 1회만 처리한다.
  const warnedIpoIds = useRef<Set<number>>(new Set())
  useEffect(() => {
    // 로딩 중에는 경고/이동을 하지 않는다(AC2)
    // 상위 컴포넌트가 IPO/투자자 목록 로딩을 마친 뒤에만 폼을 렌더링한다.
    if (investorsLoading || securitiesAccountsLoading || ipoInsightLoading) return
    // 투자자가 선택되지 않았거나 계좌 목록을 아직 받지 못했다면 "주관사 계좌 없음"이 아니다
    if (!investorSelected || !securitiesAccounts) return
    // 계좌/주관사 조회 실패나 빈 주관사 배열은 "계좌 없음"이 아니다(AC4)
    if (securitiesAccountsError || ipoInsightError) return
    if (resolvedUnderwriters.length === 0) return
    if (warnedIpoIds.current.has(selectedIpoId)) return

    const matchedAccounts = securitiesAccounts.filter((account) =>
      isUnderwriterAccount(resolvedUnderwriters, account.securitiesFirmName),
    )
    if (matchedAccounts.length > 0) return

    warnedIpoIds.current.add(selectedIpoId)
    const underwriterNames = resolvedUnderwriters
      .map((underwriter) => formatSecuritiesFirmName(normalizeName(underwriter.name)))
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
    resolvedUnderwriters,
    selectedIpoId,
    navigate,
  ])

  const onSubmit = async (data: CombinedFormValues) => {
    if (selectedInvestorId === undefined) return
    // 유효기간 밖 날짜는 어떤 경로로도 보내지 않는다
    if (!periodDates || !periodDates.includes(data.subscribedAt)) return

    // 1) 청약 등록
    const subData: SubscriptionRegisterRequest = {
      investorId: selectedInvestorId,
      securitiesAccountId: data.securitiesAccountId,
      ipoId: ipo.id,
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
        // 배정일과 배정 총액은 서버가 계산한다(요청에 보내지 않는다).
        const allocData = {
          subscriptionId: subResult.id,
          ipoId: ipo.id,
          investorId: selectedInvestorId,
          securitiesAccountId: data.securitiesAccountId,
          allocatedQuantity: allocatedQty,
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
              <span id="ipoNameLabel" className={styles.fieldLabel}>
                공모주
              </span>
              <p aria-labelledby="ipoNameLabel" className={styles.staticValue}>
                {ipo.ipoName}
              </p>
              <p className={styles.ipoInfo} data-testid="ipo-info">
                {ipoPriceInfo(ipo)} · 청약 {formatDate(ipo.subscriptionStartDate)} ~{' '}
                {formatDate(ipo.subscriptionEndDate)}
              </p>
            </div>

            <div className={styles.field}>
              <label htmlFor="securitiesAccountId">증권계좌</label>
              <select
                id="securitiesAccountId"
                aria-invalid={errors.securitiesAccountId ? true : false}
                aria-describedby={
                  errors.securitiesAccountId ? 'securitiesAccountId-error' : undefined
                }
                {...register('securitiesAccountId', {
                  required: '증권계좌를 선택해 주세요.',
                  valueAsNumber: true,
                })}
                disabled={
                  !!(
                    isPendingSub ||
                    !investorSelected ||
                    securitiesAccountsLoading ||
                    ipoInsightLoading ||
                    ipoInsightError ||
                    resolvedUnderwriters.length === 0
                  )
                }
              >
                <option value="">
                  {!investorSelected
                    ? '투자자를 먼저 선택해 주세요'
                    : securitiesAccountsLoading
                      ? '불러오는 중...'
                      : ipoInsightLoading
                        ? '주관사 확인 중...'
                        : ipoInsightError
                          ? '주관사 정보를 확인할 수 없습니다.'
                          : resolvedUnderwriters.length === 0
                            ? '등록된 주관사 정보가 없습니다.'
                            : '증권계좌 선택'}
                </option>
                {filteredAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {formatSecuritiesFirmName(
                      account.securitiesFirmName ?? account.accountNumber ?? '증권사',
                    )}
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
                  {allocationPriceMissing
                    ? '확정 공모가가 없어 증거금을 자동 계산하지 않습니다. 직접 입력하세요.'
                    : '확정 공모가 × 청약수량 × 50%로 채워 드려요. 증권사 기준에 맞게 고치세요.'}
                </span>
              )}
            </div>

            <div className={styles.field}>
              <span id="subscribedAtLabel" className={styles.fieldLabel}>
                청약일
              </span>
              {periodDates ? (
                <div
                  role="group"
                  aria-labelledby="subscribedAtLabel"
                  aria-describedby={errors.subscribedAt ? 'subscribedAt-error' : undefined}
                  className={styles.dateRow}
                >
                  {periodDates.map((date) => (
                    <button
                      key={date}
                      type="button"
                      className={styles.dateButton}
                      aria-pressed={watchedSubscribedAt === date}
                      disabled={isPendingSub}
                      onClick={() =>
                        setValue('subscribedAt', date, {
                          shouldDirty: true,
                          shouldValidate: true,
                        })
                      }
                    >
                      {date}
                    </button>
                  ))}
                </div>
              ) : (
                <div className={styles.error} role="alert">
                  청약 일정을 확인할 수 없어 등록할 수 없습니다. 공모주 상세에서 청약 기간을 확인해
                  주세요.
                </div>
              )}
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

          {/* === 배정 섹션 === */}
          <fieldset className={styles.section}>
            <legend className={styles.sectionTitle}>배정 결과 입력</legend>

            <div className={styles.field}>
              <label htmlFor="allocatedQuantity">배정수량</label>
              <input
                id="allocatedQuantity"
                type="number"
                inputMode="numeric"
                min="0"
                aria-invalid={!!errors.allocatedQuantity}
                aria-describedby={
                  errors.allocatedQuantity ? 'allocatedQuantity-error' : 'allocatedQuantity-hint'
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
              <span className={styles.fieldLabel}>배정 공모주 총 가격</span>
              <output
                className={styles.allocationTotal}
                aria-label="배정 공모주 총 가격"
                data-testid="allocation-total"
              >
                {allocationTotal === undefined
                  ? '확정 공모가가 없어 자동 계산할 수 없습니다.'
                  : formatWon(allocationTotal)}
              </output>
              <span className={styles.hint}>
                확정 공모가 × 배정수량으로 자동 계산됩니다. 서버에 금액을 보내지 않습니다.
              </span>
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
          </fieldset>

          {formErrorMessage && (
            <div className={styles.formError} role="alert">
              {formErrorMessage}
            </div>
          )}

          <button
            type="submit"
            disabled={isPendingSub || allocationPending || !periodDates}
            className={styles.submitButton}
          >
            {isPendingSub || allocationPending ? '등록 중...' : '등록'}
          </button>
        </form>
      }
    </StyleScope>
  )
}
