import { useForm, useWatch } from 'react-hook-form'
import { useEffect, useRef, useState } from 'react'
import { useInvestors } from '@/entities/investor/index.ts'
import { useIpo } from '@/entities/ipo/index.ts'
import { useInvestmentHistory, useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import { useSaleRegister } from '../useSaleRegister.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import styles from './SaleRegisterForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import {
  DEFAULT_IPO_FEE,
  finalProfit,
  grossGain,
  listingDateTime,
  otherFeesFromRealizedProfit,
  realizedProfitFromFees,
  resolveIpoPrice,
} from './saleMath.ts'

/** 폼 입력값. 숫자 칸은 비어 있으면 NaN이다(valueAsNumber). */
interface SaleFormValues {
  allocationId: number
  quantity: number
  price: number
  saleDate: string
  ipoFee: number
  otherFees: number
  realizedProfit: number
}

const won = (value: number) => `${value.toLocaleString('ko-KR')}원`
const isNum = (value: unknown): value is number => typeof value === 'number' && !Number.isNaN(value)

export default function SaleRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | undefined>()
  const [selectedSubscriptionId, setSelectedSubscriptionId] = useState<number | undefined>()
  // 세금 및 기타 수수료와 실현손익 중 사용자가 마지막으로 고친 쪽(다른 쪽은 공식으로 계산한다)
  const [lastEdited, setLastEdited] = useState<'fees' | 'profit'>('fees')
  // 기본값을 이미 넣은 청약(같은 청약에서 사용자의 입력을 덮어쓰지 않는다)
  const prefilledFor = useRef<number | undefined>(undefined)

  const { data: investors, isLoading: investorsLoading, error: investorsError } = useInvestors()

  const { data: subscriptionHistory, isLoading: subscriptionsLoading } = useInvestmentHistory(
    selectedInvestorId ? { investorId: selectedInvestorId } : {},
    { enabled: !!selectedInvestorId },
  )

  const { data: allocationData, isLoading: allocationLoading } = useInvestmentHistoryDetail(
    selectedSubscriptionId ?? null,
  )
  const { data: ipo, isPending: ipoPending } = useIpo(allocationData?.ipoId)

  const { mutate, isPending, error } = useSaleRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    getValues,
    getFieldState,
    resetField,
    control,
  } = useForm<SaleFormValues>({ mode: 'onSubmit', defaultValues: { ipoFee: DEFAULT_IPO_FEE } })

  const ipoPrice = resolveIpoPrice(
    ipo?.fixedPrice ?? allocationData?.ipoFixedPrice,
    allocationData?.allocation?.allocationPrice,
  )

  /** 매도단가·수량·공모가가 정해져 있으면 마지막에 고치지 않은 쪽을 공식으로 다시 계산한다. */
  const syncLinked = (override?: {
    price?: number
    quantity?: number
    ipoPrice?: number | null
    source?: 'fees' | 'profit'
  }) => {
    const values = getValues()
    const price = override?.price ?? values.price
    const quantity = override?.quantity ?? values.quantity
    const basis = override && 'ipoPrice' in override ? override.ipoPrice : ipoPrice
    if (!isNum(price) || !isNum(quantity) || basis == null) return
    if ((override?.source ?? lastEdited) === 'profit') {
      if (isNum(values.realizedProfit)) {
        setValue(
          'otherFees',
          otherFeesFromRealizedProfit(price, quantity, basis, values.realizedProfit),
        )
      }
    } else {
      const fees = isNum(values.otherFees) ? values.otherFees : 0
      setValue('realizedProfit', realizedProfitFromFees(price, quantity, basis, fees))
    }
  }

  useEffect(() => {
    const allocation = allocationData?.allocation
    if (!selectedSubscriptionId || !allocation) return
    setValue('allocationId', allocation.id)
    // 공모주 정보(상장일·공모가)까지 불러온 뒤 한 번만 기본값을 넣는다. 사용자가 이미 고친 칸은 건드리지 않는다.
    if (ipoPending || prefilledFor.current === selectedSubscriptionId) return
    prefilledFor.current = selectedSubscriptionId
    const basis = resolveIpoPrice(
      ipo?.fixedPrice ?? allocationData.ipoFixedPrice,
      allocation.allocationPrice,
    )
    const quantity = allocationData.remainingQuantity ?? allocation.allocatedQuantity
    const saleDate = listingDateTime(ipo?.listingDate)
    if (!getFieldState('quantity').isDirty && quantity > 0) setValue('quantity', quantity)
    if (!getFieldState('price').isDirty && basis != null) setValue('price', basis)
    if (!getFieldState('saleDate').isDirty && saleDate) setValue('saleDate', saleDate)
    syncLinked({ ipoPrice: basis, source: 'fees' })
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 청약·공모주 정보가 도착했을 때만 실행한다
  }, [allocationData, ipo, ipoPending, selectedSubscriptionId])

  const clearSaleFields = () => {
    resetField('allocationId')
    resetField('quantity')
    resetField('price')
    resetField('saleDate')
    resetField('otherFees')
    resetField('realizedProfit')
    setLastEdited('fees')
    prefilledFor.current = undefined
  }

  const onSubmit = (data: SaleFormValues) => {
    mutate({
      allocationId: data.allocationId,
      quantity: data.quantity,
      price: data.price,
      amount: data.price * data.quantity,
      saleDate: data.saleDate,
      fee: isNum(data.ipoFee) ? data.ipoFee : 0,
      tax: isNum(data.otherFees) ? data.otherFees : 0,
    })
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: { 404: '배정을 찾을 수 없습니다.', 409: (e) => e.message },
  })

  const handleInvestorChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value ? Number(e.target.value) : undefined
    setSelectedInvestorId(id)
    setSelectedSubscriptionId(undefined)
    clearSaleFields()
  }

  const handleSubscriptionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value ? Number(e.target.value) : undefined
    setSelectedSubscriptionId(id)
    clearSaleFields()
  }

  const validateNonNegativeInteger = (v: unknown) =>
    !isNum(v) || (Number.isInteger(v) && v >= 0) || '0 이상 정수를 입력해 주세요.'

  const [realizedProfit, ipoFee, quantityValue, priceValue] = useWatch({
    control,
    name: ['realizedProfit', 'ipoFee', 'quantity', 'price'],
  })
  const finalAmount = isNum(realizedProfit)
    ? finalProfit(realizedProfit, isNum(ipoFee) ? ipoFee : 0)
    : undefined
  const grossValue =
    ipoPrice != null && isNum(priceValue) && isNum(quantityValue)
      ? grossGain(priceValue, quantityValue, ipoPrice)
      : undefined

  if (investorsLoading) {
    return <div className={styles.loading}>목록을 불러오는 중...</div>
  }

  if (investorsError) {
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
      aria-label="매도 등록 폼"
      noValidate
    >
      <div className={styles.field}>
        <label htmlFor="investorId">투자자</label>
        <select
          id="investorId"
          value={selectedInvestorId ?? ''}
          onChange={handleInvestorChange}
          disabled={isPending}
        >
          <option value="">투자자 선택</option>
          {investors?.map((investor) => (
            <option key={investor.id} value={investor.id}>
              {investor.displayName}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.field}>
        <label htmlFor="subscriptionId">청약</label>
        <select
          id="subscriptionId"
          value={selectedSubscriptionId ?? ''}
          onChange={handleSubscriptionChange}
          disabled={isPending || !selectedInvestorId || subscriptionsLoading}
        >
          <option value="">청약 선택</option>
          {subscriptions.map((item) => (
            <option key={item.id} value={item.id}>
              {item.ipoName} - {item.subscribedAt}
            </option>
          ))}
        </select>
      </div>

      {selectedSubscriptionId && (
        <div className={styles.referenceSection}>
          {allocationLoading && <div className={styles.loading}>로딩 중...</div>}
          {allocationData && allocationData.remainingQuantity !== null && (
            <ReferenceQuantity quantity={allocationData.remainingQuantity} />
          )}
        </div>
      )}

      <div className={styles.field}>
        <label htmlFor="allocationId">배정</label>
        <select
          id="allocationId"
          aria-invalid={!!errors.allocationId}
          aria-describedby={errors.allocationId ? 'allocationId-error' : undefined}
          {...register('allocationId', {
            required: '배정을 선택해 주세요.',
            valueAsNumber: true,
          })}
          disabled={isPending || !selectedSubscriptionId}
        >
          <option value="">배정 선택</option>
          {allocationData?.allocation ? (
            <option key={allocationData.allocation.id} value={allocationData.allocation.id}>
              배정 #{allocationData.allocation.id} ({allocationData.allocation.allocatedQuantity}주)
            </option>
          ) : null}
        </select>
        {errors.allocationId && (
          <span id="allocationId-error" className={styles.error} aria-live="polite">
            {String(errors.allocationId.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="quantity">매도수량</label>
        <input
          id="quantity"
          type="number"
          aria-invalid={!!errors.quantity}
          aria-describedby={errors.quantity ? 'quantity-error' : 'quantity-hint'}
          {...register('quantity', {
            required: '매도수량을 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
            onChange: () => syncLinked(),
          })}
          disabled={isPending}
        />
        {errors.quantity ? (
          <span id="quantity-error" className={styles.error} aria-live="polite">
            {String(errors.quantity.message)}
          </span>
        ) : (
          <span id="quantity-hint" className={styles.hint}>
            배정받은 수량(이미 판 수량이 있으면 남은 수량)이 먼저 들어갑니다. 실제 판 수량으로
            고치세요.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="price">매도단가</label>
        <input
          id="price"
          type="number"
          aria-invalid={!!errors.price}
          aria-describedby={errors.price ? 'price-error' : 'price-hint'}
          {...register('price', {
            required: '매도단가를 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.',
            onChange: () => syncLinked(),
          })}
          disabled={isPending}
        />
        {errors.price ? (
          <span id="price-error" className={styles.error} aria-live="polite">
            {String(errors.price.message)}
          </span>
        ) : (
          <span id="price-hint" className={styles.hint}>
            {ipoPrice != null
              ? `공모가 ${won(ipoPrice)}이 먼저 들어갑니다. 실제 매도 단가로 고치세요.`
              : '공모가를 알 수 없어 비어 있습니다. 실제 매도 단가를 입력하세요.'}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="saleDate">매도일시</label>
        <input
          id="saleDate"
          type="datetime-local"
          aria-invalid={!!errors.saleDate}
          aria-describedby={errors.saleDate ? 'saleDate-error' : 'saleDate-hint'}
          {...register('saleDate', {
            required: '매도일시를 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.saleDate ? (
          <span id="saleDate-error" className={styles.error} aria-live="polite">
            {String(errors.saleDate.message)}
          </span>
        ) : (
          <span id="saleDate-hint" className={styles.hint}>
            해당 공모주의 상장일 09:00가 먼저 들어갑니다. 실제 매도일로 고칠 수 있습니다.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="otherFees">세금 및 기타 수수료 (선택)</label>
        <input
          id="otherFees"
          type="number"
          aria-invalid={!!errors.otherFees}
          aria-describedby={errors.otherFees ? 'otherFees-error' : 'otherFees-hint'}
          {...register('otherFees', {
            valueAsNumber: true,
            validate: validateNonNegativeInteger,
            onChange: () => {
              setLastEdited('fees')
              syncLinked({ source: 'fees' })
            },
          })}
          disabled={isPending}
        />
        {errors.otherFees ? (
          <span id="otherFees-error" className={styles.error} aria-live="polite">
            {String(errors.otherFees.message)}
          </span>
        ) : (
          <span id="otherFees-hint" className={styles.hint}>
            증권사 매도 내역을 불러오면 자동으로 채워집니다. 직접 등록할 때는 비워 두고 아래
            실현손익만 넣어도 됩니다.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="realizedProfit">실현손익</label>
        <input
          id="realizedProfit"
          type="number"
          aria-invalid={!!errors.realizedProfit}
          aria-describedby={errors.realizedProfit ? 'realizedProfit-error' : 'realizedProfit-hint'}
          {...register('realizedProfit', {
            required: '실현손익을 입력해 주세요.',
            valueAsNumber: true,
            validate: (v) => {
              if (!isNum(v) || !Number.isInteger(v)) return '정수를 입력해 주세요.'
              if (lastEdited !== 'profit') return true
              const { price, quantity, otherFees } = getValues()
              if (ipoPrice == null) {
                return (
                  isNum(otherFees) ||
                  '공모가를 알 수 없어 세금 및 기타 수수료를 직접 입력해 주세요.'
                )
              }
              if (!isNum(price) || !isNum(quantity)) return true
              return (
                otherFeesFromRealizedProfit(price, quantity, ipoPrice, v) >= 0 ||
                '실현손익이 (매도단가×수량 − 공모가×수량)보다 클 수 없습니다.'
              )
            },
            onChange: () => {
              setLastEdited('profit')
              syncLinked({ source: 'profit' })
            },
          })}
          disabled={isPending}
        />
        {errors.realizedProfit ? (
          <span id="realizedProfit-error" className={styles.error} aria-live="polite">
            {String(errors.realizedProfit.message)}
          </span>
        ) : (
          <span id="realizedProfit-hint" className={styles.hint}>
            증권사 앱에 표시되는 실현손익을 넣으세요. 계산식은 매도단가×수량 − 공모가×수량 − 세금 및
            기타 수수료이며, 공모주수수료는 빼지 않습니다.
            {grossValue !== undefined && ` (수수료 반영 전 차익 ${won(grossValue)})`}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="ipoFee">공모주수수료</label>
        <input
          id="ipoFee"
          type="number"
          aria-invalid={!!errors.ipoFee}
          aria-describedby={errors.ipoFee ? 'ipoFee-error' : 'ipoFee-hint'}
          {...register('ipoFee', {
            valueAsNumber: true,
            validate: validateNonNegativeInteger,
          })}
          disabled={isPending}
        />
        {errors.ipoFee ? (
          <span id="ipoFee-error" className={styles.error} aria-live="polite">
            {String(errors.ipoFee.message)}
          </span>
        ) : (
          <span id="ipoFee-hint" className={styles.hint}>
            기본 {won(DEFAULT_IPO_FEE)}입니다. 증권사별로 다르면 고치세요. 실현손익에는 포함하지
            않고 최종수익금에서만 뺍니다.
          </span>
        )}
      </div>

      <div className={styles.summary} role="group" aria-label="수익 요약">
        <span className={styles.summaryLabel}>최종수익금 (실현손익 − 공모주수수료)</span>
        <output
          htmlFor="realizedProfit ipoFee"
          aria-live="polite"
          className={`${styles.summaryValue} ${finalAmount !== undefined && finalAmount < 0 ? styles.negative : ''}`}
        >
          {finalAmount === undefined ? '-' : won(finalAmount)}
        </output>
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
