import { useEffect, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import {
  createSale,
  DEFAULT_IPO_FEE,
  finalProfit,
  listingDate,
  realizedProfitFromFees,
  otherFeesFromRealizedProfit,
  allocationUnitPrice,
  resolveIpoPrice,
  type SaleRegisterRequest,
} from '@/entities/sale/index.ts'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
import type { InvestorResponse } from '@/entities/investor/index.ts'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import Button from '@/shared/components/Button/Button.tsx'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import { toErrorMessage, ApiError } from '@/shared/api/apiError.ts'
import { formatWon } from '@/shared/lib/format.ts'
import CaptureImport from '../SaleRegisterForm/CaptureImport.tsx'
import CapturedBadge from '../SaleRegisterForm/CapturedBadge.tsx'
import { useCaptureFill } from '../SaleRegisterForm/useCaptureFill.ts'
import { isNum } from '../SaleRegisterForm/saleFormUtils.ts'
import type { SaleFormValues, SaleFormCaptureAdapter } from '../SaleRegisterForm/saleFormTypes.ts'
import formStyles, { StyleScope as FormScope } from '../SaleRegisterForm/SaleRegisterForm.styles.ts'
import styles from './SaleSection.styles.ts'

const validateNonNegativeInteger = (v: unknown) =>
  !isNum(v) || (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.'

export interface AddSaleFormProps {
  ipo: Ipo
  investor: InvestorResponse
  account: SecuritiesAccount
  /** 화면 문맥 — null이면 청약·배정이 아직 없어 매도 사후 등록(서버 자동 생성/재사용) 경로다. */
  detail: InvestmentHistoryItem | null
  /** 현재 잔여 배정 수량(상세 재조회 반영값). 매도 상한의 안내용이다. */
  remainingQuantity: number | null
  /** ACTIVE 매도가 이미 있는지 — 공모주수수료 기본값(배정 1건당 한 번) 판정에 쓴다. */
  hasPriorActiveSales: boolean
  /** 캡처 종목 매칭 후보 — 현재 투자자의 ACTIVE 청약 전체. */
  subscriptions: InvestmentHistoryItem[]
  subscriptionsLoading: boolean
  /** 저장 성공 후 상세 재조회가 끝나면 resolve. 실패하면 reject. */
  onSaved: () => Promise<void>
  onDirtyChange: (dirty: boolean) => void
  onPendingChange: (pending: boolean) => void
  /** 다른 섹션 저장 중이거나 조회가 최신이 아니면 true — 입력·제출을 잠근다. */
  locked: boolean
  onCancel: () => void
  /** 저장 성공 후 상세 재조회까지 끝났을 때 부모가 폼을 닫는다. */
  onClosed: () => void
}

/**
 * 매도 인라인 추가 폼. /sales/register의 등록 폼과 같은 계산·캡처·MoneyInput을 재사용하되
 * 공모주·투자자·계좌는 페이지가 고정한 문맥을 쓴다.
 */
export default function AddSaleForm({
  ipo,
  investor,
  account,
  detail,
  remainingQuantity,
  hasPriorActiveSales,
  subscriptions,
  subscriptionsLoading,
  onSaved,
  onDirtyChange,
  onPendingChange,
  locked,
  onCancel,
  onClosed,
}: AddSaleFormProps) {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  // 배정 정보가 없으면(사후 등록) 배정수량을 받아 서버가 청약·배정을 자동 생성/재사용하게 한다.
  const allocation = detail?.allocation ?? null
  const needsAllocatedQuantity = detail == null || allocation == null
  const allocatedAmount = allocation?.allocatedAmount ?? null
  const allocatedQuantity = allocation?.allocatedQuantity ?? null
  const fixedPrice = resolveIpoPrice(ipo.fixedPrice ?? detail?.ipoFixedPrice ?? null)
  const avgPrice = allocationUnitPrice(fixedPrice, allocatedAmount, allocatedQuantity)
  const openPrice =
    typeof ipo.listingOpenPrice === 'number' && ipo.listingOpenPrice > 0
      ? ipo.listingOpenPrice
      : null
  const priceDefault = openPrice ?? fixedPrice ?? avgPrice
  const canCalcAcquisition =
    fixedPrice != null ||
    (allocatedAmount != null && allocatedQuantity != null && allocatedQuantity > 0)

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty, isSubmitting },
    setValue,
    getValues,
  } = useForm<SaleFormValues>({
    mode: 'onSubmit',
    defaultValues: {
      // 남은 수량이 있으면 먼저 채우고, 없으면 비워 사용자가 실제 판 수량을 넣게 한다.
      quantity: remainingQuantity != null && remainingQuantity > 0 ? remainingQuantity : Number.NaN,
      price: priceDefault ?? Number.NaN,
      saleDate: listingDate(ipo.listingDate) ?? '',
      // 공모주수수료는 배정 1건당 한 번이라, 이미 매도 기록이 있으면 0원으로 시작한다.
      ipoFee: hasPriorActiveSales ? 0 : DEFAULT_IPO_FEE,
      otherFees: Number.NaN,
      realizedProfit: Number.NaN,
      allocatedQuantity: Number.NaN,
    },
  })

  // 세금 및 기타와 실현손익 중 사용자가 마지막으로 고친 쪽(다른 쪽은 공식으로 계산한다)
  const [lastEdited, setLastEdited] = useState<'fees' | 'profit'>('fees')
  const syncLinked = (override?: {
    price?: number
    quantity?: number
    source?: 'fees' | 'profit'
  }) => {
    const values = getValues()
    const price = override?.price ?? values.price
    const quantity = override?.quantity ?? values.quantity
    if (!isNum(price) || !isNum(quantity)) return
    if ((override?.source ?? lastEdited) === 'profit') {
      if (isNum(values.realizedProfit)) {
        setValue(
          'otherFees',
          otherFeesFromRealizedProfit(
            price,
            quantity,
            fixedPrice,
            values.realizedProfit,
            allocatedAmount,
            allocatedQuantity,
          ),
        )
      }
    } else {
      const fees = isNum(values.otherFees) ? values.otherFees : 0
      setValue(
        'realizedProfit',
        realizedProfitFromFees(
          price,
          quantity,
          fixedPrice,
          fees,
          allocatedAmount,
          allocatedQuantity,
        ),
      )
    }
  }
  const onFeesChange = () => {
    setLastEdited('fees')
    syncLinked({ source: 'fees' })
  }
  const onProfitChange = () => {
    setLastEdited('profit')
    syncLinked({ source: 'profit' })
  }

  // 기본값 채우기가 끝났다고 표시해 캡처가 같은 화면의 기본값을 기다리지 않고 바로 적용되게 한다.
  const prefilledFor = useRef<number | undefined>(detail?.id ?? 0)
  const captureFill = useCaptureFill({
    connectionId: detail?.id,
    allocationData: detail ?? undefined,
    ipoPending: false,
    connectionReady: true,
    accountId: account.id,
    ipoPrice: avgPrice,
    prefilledFor,
    setValue,
    getValues,
    setLastEdited,
    syncLinked,
    selectSubscription: () => {},
  })

  const save = useMutation({
    mutationFn: (values: SaleFormValues) => {
      const body: SaleRegisterRequest = {
        quantity: Number(values.quantity),
        price: Number(values.price),
        amount: Number(values.price) * Number(values.quantity),
        saleDate: `${values.saleDate}T09:00`,
        fee: isNum(values.ipoFee) ? Number(values.ipoFee) : 0,
        tax: isNum(values.otherFees) ? Number(values.otherFees) : 0,
        ...(needsAllocatedQuantity ? { allocatedQuantity: Number(values.allocatedQuantity) } : {}),
        // 기존 청약이 있으면 그 연결로 보내고(계좌는 서버가 쓴다), 없으면 문맥 id를 보내 서버가 자동 생성/재사용하게 한다.
        ...(detail
          ? { subscriptionId: detail.id }
          : { ipoId: ipo.id, investorId: investor.id, securitiesAccountId: account.id }),
      }
      return createSale(body)
    },
  })

  // 저장/삭제 요청부터 조회 갱신이 끝날 때까지 이 폼을 잠근다.
  const [syncing, setSyncing] = useState(false)
  const [syncError, setSyncError] = useState(false)
  // 서버 반영 여부를 알 수 없는 실패(네트워크·timeout·응답 파싱 실패)인지 여부.
  const [lostResponse, setLostResponse] = useState(false)

  const onDirtyChangeRef = useRef(onDirtyChange)
  useEffect(() => {
    onDirtyChangeRef.current = onDirtyChange
  })
  const onPendingChangeRef = useRef(onPendingChange)
  useEffect(() => {
    onPendingChangeRef.current = onPendingChange
  })
  useEffect(() => {
    onDirtyChangeRef.current(isDirty)
    return () => onDirtyChangeRef.current(false)
  }, [isDirty])
  const sectionPending = save.isPending || syncing
  useEffect(() => {
    onPendingChangeRef.current(sectionPending)
    return () => onPendingChangeRef.current(false)
  }, [sectionPending])

  const onSubmit = async (values: SaleFormValues) => {
    if (locked || syncing || isSubmitting) return
    setSyncing(true)
    setSyncError(false)
    setLostResponse(false)
    try {
      await save.mutateAsync(values)
    } catch (error) {
      // 서버가 거절한 것으로 근거가 확실한 경우(400/403/404/409)는 서버 메시지를 그대로 보여 주고,
      // 입력을 고쳐 다시 시도할 수 있게 잠금을 바로 푼다.
      const definitiveRejection =
        error instanceof ApiError &&
        (error.status === 400 ||
          error.status === 403 ||
          error.status === 404 ||
          error.status === 409)
      if (definitiveRejection) {
        setSyncing(false)
        return
      }
      // 5xx·응답 유실: 서버가 처리했는지 알 수 없다 — 자동으로 다시 보내지 않고,
      // 목록·잔여 재조회가 끝나기 전까지 잠금을 유지해 사용자가 판단하게 한다.
      setLostResponse(true)
      try {
        await onSaved()
      } catch {
        setSyncError(true)
      }
      setSyncing(false)
      return
    }
    try {
      await onSaved()
      onClosed()
    } catch {
      // 저장 자체는 성공했다. 다시 불러오기만 하면 된다.
      setSyncError(true)
    }
    setSyncing(false)
  }

  const retrySync = async () => {
    if (syncing) return
    setSyncing(true)
    try {
      await onSaved()
      setSyncError(false)
    } catch {
      setSyncError(true)
    }
    setSyncing(false)
  }

  const [realizedProfitValue, ipoFeeValue] = useWatch({
    control,
    name: ['realizedProfit', 'ipoFee'],
  })
  const finalAmount = isNum(realizedProfitValue)
    ? finalProfit(realizedProfitValue, isNum(ipoFeeValue) ? ipoFeeValue : 0)
    : undefined
  const formLocked = locked || sectionPending
  const submitError = toErrorMessage(save.error, {
    byStatus: {
      404: '배정을 찾을 수 없습니다.',
      409: (e) => e.message,
    },
  })

  const captureAdapter: SaleFormCaptureAdapter = {
    control,
    subscriptions,
    subscriptionsLoading,
    effectiveIpoId: ipo.id,
    ipoName: ipo.ipoName,
    ipoCompanyName: ipo.companyName,
    ipoStockCode: ipo.stockCode,
    securitiesAccountId: account.id,
    connectionId: detail?.id,
    isPending: sectionPending,
    capturePending: captureFill.capturePending,
    captureNotes: captureFill.captureNotes,
    captured: captureFill.captured,
    noteCapture: captureFill.noteCapture,
    applyCapture: captureFill.applyCapture,
  }

  return (
    <FormScope>
      {
        <form
          className={formStyles.form}
          onSubmit={handleSubmit(onSubmit)}
          aria-label="매도 추가 폼"
          noValidate
        >
          <p className={formStyles.hint}>
            공모주 <strong>{ipo.ipoName}</strong> · 증권계좌는 위에서 고른 계좌로 고정됩니다.
          </p>
          {detail == null && (
            <p className={formStyles.hint} role="status">
              이 조합으로 등록된 청약·배정이 없습니다. 배정수량을 입력하면 저장할 때 청약·배정도
              함께 만들어집니다. 실제 청약 사실이 아닌 연결 기록이 만들어질 수 있습니다.
            </p>
          )}
          {detail != null && allocation == null && (
            <p className={formStyles.hint} role="status">
              이 청약은 아직 배정 입력 전입니다. 배정수량을 입력하면 저장할 때 배정이 함께
              만들어집니다.
            </p>
          )}

          <CaptureImport f={captureAdapter} />

          {needsAllocatedQuantity && (
            <div className={formStyles.field}>
              <label htmlFor="manage-sale-allocatedQuantity">배정수량 *</label>
              <input
                id="manage-sale-allocatedQuantity"
                type="number"
                inputMode="numeric"
                min="1"
                aria-invalid={!!errors.allocatedQuantity}
                aria-describedby={
                  errors.allocatedQuantity ? 'manage-sale-allocatedQuantity-error' : undefined
                }
                {...register('allocatedQuantity', {
                  valueAsNumber: true,
                  validate: (v: number | undefined) => {
                    if (v == null || Number.isNaN(v)) return '배정수량을 입력해 주세요.'
                    return Number.isInteger(v) && v > 0 ? true : '0 초과 정수를 입력해 주세요.'
                  },
                })}
                disabled={formLocked}
              />
              {errors.allocatedQuantity && (
                <span
                  id="manage-sale-allocatedQuantity-error"
                  className={formStyles.error}
                  aria-live="polite"
                >
                  {String(errors.allocatedQuantity.message)}
                </span>
              )}
            </div>
          )}

          <div className={formStyles.field}>
            <label htmlFor="manage-sale-quantity">매도수량</label>
            <input
              id="manage-sale-quantity"
              type="number"
              inputMode="numeric"
              aria-invalid={!!errors.quantity}
              aria-describedby={
                errors.quantity ? 'manage-sale-quantity-error' : 'manage-sale-quantity-hint'
              }
              {...register('quantity', {
                required: '매도수량을 입력해 주세요.',
                valueAsNumber: true,
                validate: (v) =>
                  Number.isInteger(v) && v > 0 ? true : '0 초과 정수를 입력해 주세요.',
                onChange: () => syncLinked(),
              })}
              disabled={formLocked}
            />
            <CapturedBadge f={captureAdapter} name="quantity" />
            {errors.quantity ? (
              <span id="manage-sale-quantity-error" className={formStyles.error} aria-live="polite">
                {String(errors.quantity.message)}
              </span>
            ) : (
              <span id="manage-sale-quantity-hint" className={formStyles.hint}>
                {remainingQuantity != null && remainingQuantity > 0
                  ? `남은 수량 ${remainingQuantity.toLocaleString('ko-KR')}주가 먼저 들어갑니다. 실제 판 수량으로 고치세요.`
                  : '실제 판 수량을 입력하세요.'}
              </span>
            )}
          </div>

          <div className={formStyles.field}>
            <label htmlFor="manage-sale-price">매도단가</label>
            <MoneyInput
              id="manage-sale-price"
              control={control}
              name="price"
              aria-invalid={!!errors.price}
              aria-describedby={errors.price ? 'manage-sale-price-error' : 'manage-sale-price-hint'}
              requiredMessage="매도단가를 입력해 주세요."
              validate={(v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.'}
              onValueChange={() => syncLinked()}
              allowNegative
              disabled={formLocked}
            />
            <CapturedBadge f={captureAdapter} name="price" />
            {errors.price ? (
              <span id="manage-sale-price-error" className={formStyles.error} aria-live="polite">
                {String(errors.price.message)}
              </span>
            ) : (
              <span id="manage-sale-price-hint" className={formStyles.hint}>
                {openPrice != null
                  ? `상장일 시초가 ${formatWon(openPrice)}이 먼저 들어갑니다. 실제 체결가로 고쳐 주세요.`
                  : fixedPrice != null
                    ? `공모가 ${formatWon(fixedPrice)}이 먼저 들어갑니다.`
                    : '공모가를 알 수 없어 비어 있습니다.'}
              </span>
            )}
          </div>

          <div className={formStyles.field}>
            <label htmlFor="manage-sale-saleDate">매도일</label>
            <input
              id="manage-sale-saleDate"
              type="date"
              aria-invalid={!!errors.saleDate}
              aria-describedby={
                errors.saleDate ? 'manage-sale-saleDate-error' : 'manage-sale-saleDate-hint'
              }
              {...register('saleDate', {
                required: '매도일을 선택해 주세요.',
              })}
              disabled={formLocked}
            />
            <CapturedBadge f={captureAdapter} name="saleDate" />
            {errors.saleDate ? (
              <span id="manage-sale-saleDate-error" className={formStyles.error} aria-live="polite">
                {String(errors.saleDate.message)}
              </span>
            ) : (
              <span id="manage-sale-saleDate-hint" className={formStyles.hint}>
                상장일이 먼저 들어갑니다.
              </span>
            )}
          </div>

          <div className={formStyles.field}>
            <label htmlFor="manage-sale-otherFees">세금 및 기타 수수료 (선택)</label>
            <MoneyInput
              id="manage-sale-otherFees"
              control={control}
              name="otherFees"
              aria-invalid={!!errors.otherFees}
              aria-describedby={
                errors.otherFees ? 'manage-sale-otherFees-error' : 'manage-sale-otherFees-hint'
              }
              validate={validateNonNegativeInteger}
              onValueChange={onFeesChange}
              allowNegative
              disabled={formLocked}
            />
            <CapturedBadge f={captureAdapter} name="otherFees" />
            {errors.otherFees ? (
              <span
                id="manage-sale-otherFees-error"
                className={formStyles.error}
                aria-live="polite"
              >
                {String(errors.otherFees.message)}
              </span>
            ) : (
              <span id="manage-sale-otherFees-hint" className={formStyles.hint}>
                비워 두고 아래 실현손익만 넣어도 됩니다.
              </span>
            )}
          </div>

          <div className={formStyles.field}>
            <label htmlFor="manage-sale-realizedProfit">실현손익</label>
            <MoneyInput
              id="manage-sale-realizedProfit"
              control={control}
              name="realizedProfit"
              aria-invalid={!!errors.realizedProfit}
              aria-describedby={
                errors.realizedProfit
                  ? 'manage-sale-realizedProfit-error'
                  : 'manage-sale-realizedProfit-hint'
              }
              requiredMessage="실현손익을 입력해 주세요."
              validate={(v) => {
                if (!isNum(v) || !Number.isInteger(v)) return '정수를 입력해 주세요.'
                if (lastEdited !== 'profit') return true
                if (!canCalcAcquisition) {
                  return isNum(getValues('otherFees'))
                    ? true
                    : '취득금액을 계산할 수 없어 세금 및 기타 수수료를 직접 입력해 주세요.'
                }
                const { price, quantity } = getValues()
                if (!isNum(price) || !isNum(quantity)) return true
                return (
                  otherFeesFromRealizedProfit(
                    price,
                    quantity,
                    fixedPrice,
                    v,
                    allocatedAmount,
                    allocatedQuantity,
                  ) >= 0 || '실현손익이 (매도단가×수량 − 취득금액)보다 클 수 없습니다.'
                )
              }}
              onValueChange={onProfitChange}
              allowNegative
              disabled={formLocked}
            />
            <CapturedBadge f={captureAdapter} name="realizedProfit" />
            {errors.realizedProfit ? (
              <span
                id="manage-sale-realizedProfit-error"
                className={formStyles.error}
                aria-live="polite"
              >
                {String(errors.realizedProfit.message)}
              </span>
            ) : (
              <span id="manage-sale-realizedProfit-hint" className={formStyles.hint}>
                증권사 앱의 실현손익을 넣으세요.
              </span>
            )}
          </div>

          <div className={formStyles.field}>
            <label htmlFor="manage-sale-ipoFee">공모주수수료</label>
            <MoneyInput
              id="manage-sale-ipoFee"
              control={control}
              name="ipoFee"
              aria-invalid={!!errors.ipoFee}
              aria-describedby={
                errors.ipoFee ? 'manage-sale-ipoFee-error' : 'manage-sale-ipoFee-hint'
              }
              validate={validateNonNegativeInteger}
              allowNegative
              disabled={formLocked}
            />
            {errors.ipoFee ? (
              <span id="manage-sale-ipoFee-error" className={formStyles.error} aria-live="polite">
                {String(errors.ipoFee.message)}
              </span>
            ) : (
              <span id="manage-sale-ipoFee-hint" className={formStyles.hint}>
                {hasPriorActiveSales
                  ? '이미 매도 기록이 있어 0원으로 시작합니다(배정 1건당 한 번).'
                  : `기본 ${formatWon(DEFAULT_IPO_FEE)}입니다. 증권사별로 다르면 고치세요.`}
              </span>
            )}
          </div>

          <div className={formStyles.summary} role="group" aria-label="수익 요약">
            <span className={formStyles.summaryLabel}>최종수익금</span>
            <output className={formStyles.summaryValue} aria-live="polite">
              {finalAmount === undefined ? '-' : formatWon(finalAmount)}
            </output>
          </div>

          {submitError && !lostResponse && (
            <div className={formStyles.formError} role="alert">
              {submitError}
            </div>
          )}
          {lostResponse && (
            <div className={formStyles.formError} role="alert">
              서버에 요청이 도착했는지 확인할 수 없습니다. 자동으로 다시 시도하지 않습니다. 목록을
              다시 확인했습니다. 같은 날짜·수량의 정상 분할 매도와 구분할 수 없으므로, 목록을 보고
              직접 판단해 주세요.
            </div>
          )}
          {syncError && (
            <div className={formStyles.formError} role="alert">
              저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.
              <button
                type="button"
                className={formStyles.submitButton}
                onClick={retrySync}
                disabled={formLocked}
              >
                다시 불러오기
              </button>
            </div>
          )}

          <div className={styles.rowActions}>
            <Button type="submit" disabled={formLocked}>
              {sectionPending ? '저장 중...' : '매도 저장'}
            </Button>
            <Button type="button" variant="secondary" onClick={onCancel} disabled={formLocked}>
              취소
            </Button>
          </div>
        </form>
      }
    </FormScope>
  )
}
