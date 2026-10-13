import { useForm, useWatch } from 'react-hook-form'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useIpo } from '@/entities/ipo/index.ts'
import { useInvestmentHistory, useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import { useSaleRegister } from '../useSaleRegister.ts'
import ReferenceQuantity from './ReferenceQuantity.tsx'
import MoneyInput from '@/shared/components/MoneyInput/MoneyInput.tsx'
import styles from './SaleRegisterForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import {
  DEFAULT_IPO_FEE,
  finalProfit,
  grossGain,
  listingDate,
  otherFeesFromRealizedProfit,
  realizedProfitFromFees,
  resolveIpoPrice,
  toSalePayload,
} from '@/entities/sale/index.ts'

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
const formatDate = (value: string) => {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('ko-KR')
}
const isNum = (value: unknown): value is number => typeof value === 'number' && !Number.isNaN(value)

export default function SaleRegisterForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  // 사용자가 청약을 직접 고르기 전(null)에는 ?subscriptionId=<id>로 들어온 청약을 쓴다.
  const [pickedSubscriptionId, setPickedSubscriptionId] = useState<number | undefined | null>(null)
  const presetSubscriptionId = Number(useSearchParams()[0].get('subscriptionId')) || undefined
  // 세금 및 기타 수수료와 실현손익 중 사용자가 마지막으로 고친 쪽(다른 쪽은 공식으로 계산한다)
  const [lastEdited, setLastEdited] = useState<'fees' | 'profit'>('fees')
  // 기본값을 이미 넣은 청약(같은 청약에서 사용자의 입력을 덮어쓰지 않는다)
  const prefilledFor = useRef<number | undefined>(undefined)

  // 투자자는 상단 전환기의 현재 투자자다. 전환하면 페이지가 이 폼을 새로 만든다.
  const { current, isLoading: investorsLoading } = useCurrentInvestor()
  const selectedInvestorId = current?.id

  const { data: subscriptionHistory, isLoading: subscriptionsLoading } = useInvestmentHistory(
    selectedInvestorId ? { investorId: selectedInvestorId } : {},
    { enabled: !!selectedInvestorId },
  )

  // 현재 투자자의 청약이 아니면 미리 선택을 무시한다.
  const selectedSubscriptionId =
    pickedSubscriptionId !== null
      ? pickedSubscriptionId
      : subscriptionHistory?.content.some((item) => item.id === presetSubscriptionId)
        ? presetSubscriptionId
        : undefined

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

  const hasPriorSales = allocationData?.sales.some((sale) => sale.status === 'ACTIVE') ?? false
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
    const priorSales = allocationData.sales.some((sale) => sale.status === 'ACTIVE')
    const saleDate = listingDate(ipo?.listingDate)
    if (!getFieldState('quantity').isDirty && quantity > 0) setValue('quantity', quantity)
    if (!getFieldState('price').isDirty && basis != null) setValue('price', basis)
    if (!getFieldState('saleDate').isDirty && saleDate) setValue('saleDate', saleDate)
    // 공모주수수료는 배정 1건당 한 번이라, 이미 매도 기록이 있으면 0원으로 시작한다.
    if (!getFieldState('ipoFee').isDirty && priorSales) setValue('ipoFee', 0)
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
    resetField('ipoFee')
    setLastEdited('fees')
    prefilledFor.current = undefined
  }

  const onSubmit = (data: SaleFormValues) => {
    mutate({ allocationId: data.allocationId, ...toSalePayload(data) })
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: { 404: '배정을 찾을 수 없습니다.', 409: (e) => e.message },
  })

  const handleSubscriptionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value ? Number(e.target.value) : undefined
    setPickedSubscriptionId(id)
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

  const subscriptions = subscriptionHistory?.content ?? []

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="매도 등록 폼"
      noValidate
    >
      <p className={styles.hint}>
        투자자: <strong>{current?.displayName ?? '선택 안 됨'}</strong>
      </p>

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
              {item.ipoName ?? '이름 없는 공모주'} · {formatDate(item.subscribedAt)}
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

      {/* 청약당 배정은 하나뿐이라 고르게 하지 않고 자동으로 연결한다. */}
      <input type="hidden" {...register('allocationId', { required: true, valueAsNumber: true })} />
      {selectedSubscriptionId && allocationData && !allocationData.allocation && (
        <p className={styles.error} role="alert">
          이 청약은 아직 배정 입력 전입니다. 배정을 먼저 등록해 주세요.{' '}
          <Link to={`/allocations/register?subscriptionId=${selectedSubscriptionId}`}>
            배정 등록하기
          </Link>
        </p>
      )}
      {errors.allocationId && !(selectedSubscriptionId && allocationData?.allocation) && (
        <p className={styles.error} role="alert">
          매도할 청약을 선택해 주세요.
        </p>
      )}

      <div className={styles.field}>
        <label htmlFor="quantity">매도수량</label>
        <input
          id="quantity"
          type="number"
          inputMode="numeric"
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
            남은 수량이 먼저 들어갑니다. 실제 판 수량으로 고치세요.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="price">매도단가</label>
        <MoneyInput
          id="price"
          control={control}
          name="price"
          aria-invalid={!!errors.price}
          aria-describedby={errors.price ? 'price-error' : 'price-hint'}
          requiredMessage="매도단가를 입력해 주세요."
          validate={(v) => (Number.isInteger(v) && v > 0) || '0 초과 정수를 입력해 주세요.'}
          onValueChange={() => syncLinked()}
          allowNegative // 음수를 입력하면 지우지 않고 검증 메시지로 알린다
          disabled={isPending}
        />
        {errors.price ? (
          <span id="price-error" className={styles.error} aria-live="polite">
            {String(errors.price.message)}
          </span>
        ) : (
          <span id="price-hint" className={styles.hint}>
            {ipoPrice != null
              ? `공모가 ${won(ipoPrice)}이 먼저 들어갑니다.`
              : '공모가를 알 수 없어 비어 있습니다.'}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="saleDate">매도일</label>
        <input
          id="saleDate"
          type="date"
          aria-invalid={!!errors.saleDate}
          aria-describedby={errors.saleDate ? 'saleDate-error' : 'saleDate-hint'}
          {...register('saleDate', {
            required: '매도일을 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.saleDate ? (
          <span id="saleDate-error" className={styles.error} aria-live="polite">
            {String(errors.saleDate.message)}
          </span>
        ) : (
          <span id="saleDate-hint" className={styles.hint}>
            상장일이 먼저 들어갑니다.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="otherFees">세금 및 기타 수수료 (선택)</label>
        <MoneyInput
          id="otherFees"
          control={control}
          name="otherFees"
          aria-invalid={!!errors.otherFees}
          aria-describedby={errors.otherFees ? 'otherFees-error' : 'otherFees-hint'}
          validate={validateNonNegativeInteger}
          onValueChange={() => {
            setLastEdited('fees')
            syncLinked({ source: 'fees' })
          }}
          allowNegative
          disabled={isPending}
        />
        {errors.otherFees ? (
          <span id="otherFees-error" className={styles.error} aria-live="polite">
            {String(errors.otherFees.message)}
          </span>
        ) : (
          <span id="otherFees-hint" className={styles.hint}>
            비워 두고 아래 실현손익만 넣어도 됩니다.
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="realizedProfit">실현손익</label>
        <MoneyInput
          id="realizedProfit"
          control={control}
          name="realizedProfit"
          aria-invalid={!!errors.realizedProfit}
          aria-describedby={errors.realizedProfit ? 'realizedProfit-error' : 'realizedProfit-hint'}
          requiredMessage="실현손익을 입력해 주세요."
          validate={(v) => {
            if (!isNum(v) || !Number.isInteger(v)) return '정수를 입력해 주세요.'
            if (lastEdited !== 'profit') return true
            const { price, quantity, otherFees } = getValues()
            if (ipoPrice == null) {
              return (
                isNum(otherFees) || '공모가를 알 수 없어 세금 및 기타 수수료를 직접 입력해 주세요.'
              )
            }
            if (!isNum(price) || !isNum(quantity)) return true
            return (
              otherFeesFromRealizedProfit(price, quantity, ipoPrice, v) >= 0 ||
              '실현손익이 (매도단가×수량 − 공모가×수량)보다 클 수 없습니다.'
            )
          }}
          onValueChange={() => {
            setLastEdited('profit')
            syncLinked({ source: 'profit' })
          }}
          allowNegative
          disabled={isPending}
        />
        {errors.realizedProfit ? (
          <span id="realizedProfit-error" className={styles.error} aria-live="polite">
            {String(errors.realizedProfit.message)}
          </span>
        ) : (
          <span id="realizedProfit-hint" className={styles.hint}>
            증권사 앱의 실현손익을 넣으세요.
            {grossValue !== undefined && ` (수수료 반영 전 차익 ${won(grossValue)})`}
            <details className={styles.help}>
              <summary>계산식 보기</summary>
              실현손익 = 매도단가×수량 − 공모가×수량 − 세금 및 기타 수수료. 공모주수수료는 빼지
              않습니다.
            </details>
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="ipoFee">공모주수수료</label>
        <MoneyInput
          id="ipoFee"
          control={control}
          name="ipoFee"
          aria-invalid={!!errors.ipoFee}
          aria-describedby={errors.ipoFee ? 'ipoFee-error' : 'ipoFee-hint'}
          validate={validateNonNegativeInteger}
          allowNegative
          disabled={isPending}
        />
        {errors.ipoFee ? (
          <span id="ipoFee-error" className={styles.error} aria-live="polite">
            {String(errors.ipoFee.message)}
          </span>
        ) : (
          <span id="ipoFee-hint" className={styles.hint}>
            {hasPriorSales
              ? '이미 매도 기록이 있어 0원으로 시작합니다(배정 1건당 한 번).'
              : `기본 ${won(DEFAULT_IPO_FEE)}입니다. 증권사별로 다르면 고치세요.`}
          </span>
        )}
      </div>

      <div className={styles.summary} role="group" aria-label="수익 요약">
        <span className={styles.summaryLabel}>최종수익금</span>
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
