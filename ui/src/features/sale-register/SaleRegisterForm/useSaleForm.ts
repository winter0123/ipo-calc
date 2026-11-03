import { useForm, useWatch } from 'react-hook-form'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useIpo } from '@/entities/ipo/index.ts'
import { useInvestmentHistory, useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import { useSaleRegister } from '../useSaleRegister.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import {
  DEFAULT_IPO_FEE,
  finalProfit,
  listingDate,
  acquisitionAmountForSale,
  otherFeesFromRealizedProfit,
  realizedProfitFromFees,
  allocationUnitPrice,
  resolveIpoPrice,
  toSalePayload,
} from '@/entities/sale/index.ts'
import { useCaptureFill } from './useCaptureFill.ts'
import type { SaleFormValues } from './saleFormTypes.ts'
import { isNum } from './saleFormUtils.ts'
export { isNum, won } from './saleFormUtils.ts'
export type { CaptureApply, CapturedField, SaleFormValues } from './saleFormTypes.ts'

/** 매도 등록 폼의 상태: 청약 선택, 자동 채움, 세금↔실현손익 상호 계산, 제출. */
export function useSaleForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  // 사용자가 청약을 직접 고르기 전(null)에는 ?subscriptionId=<id>로 들어온 청약을 쓴다.
  const [pickedSubscriptionId, setPickedSubscriptionId] = useState<number | undefined | null>(null)
  const presetSubscriptionId = Number(useSearchParams()[0].get('subscriptionId')) || undefined
  const presetIpoId = Number(useSearchParams()[0].get('ipoId')) || undefined
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
  const ipoIdFromSubscription = allocationData?.ipoId
  const effectiveIpoId = presetIpoId ?? ipoIdFromSubscription
  const { data: ipo, isPending: ipoPending } = useIpo(effectiveIpoId)

  // 증권계좌 목록 (직접 매도 등록 시 필요)
  const { data: securitiesAccounts, isLoading: accountsLoading } =
    useSecuritiesAccounts(selectedInvestorId)

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
  const allocation = allocationData?.allocation
  const fixedPrice = ipo?.fixedPrice ?? allocationData?.ipoFixedPrice
  const allocatedAmount = allocation?.allocatedAmount ?? null
  const allocatedQuantity = allocation?.allocatedQuantity ?? null
  const canCalcAcquisition =
    (fixedPrice != null && fixedPrice > 0) ||
    (allocatedAmount != null && allocatedQuantity != null && allocatedQuantity > 0)

  /** 매도단가·수량·공모가가 정해져 있으면 마지막에 고치지 않은 쪽을 공식으로 다시 계산한다. */
  const syncLinked = (override?: {
    price?: number
    quantity?: number
    fixedPrice?: number | null | undefined
    allocatedAmount?: number | null | undefined
    allocatedQuantity?: number | null | undefined
    source?: 'fees' | 'profit'
  }) => {
    const values = getValues()
    const price = override?.price ?? values.price
    const quantity = override?.quantity ?? values.quantity
    const fp = override && 'fixedPrice' in override ? override.fixedPrice : fixedPrice
    const aa =
      override && 'allocatedAmount' in override ? override.allocatedAmount : allocatedAmount
    const aq =
      override && 'allocatedQuantity' in override ? override.allocatedQuantity : allocatedQuantity
    if (!isNum(price) || !isNum(quantity)) return
    if ((override?.source ?? lastEdited) === 'profit') {
      if (isNum(values.realizedProfit)) {
        setValue(
          'otherFees',
          otherFeesFromRealizedProfit(price, quantity, fp, values.realizedProfit, aa, aq),
        )
      }
    } else {
      const fees = isNum(values.otherFees) ? values.otherFees : 0
      setValue('realizedProfit', realizedProfitFromFees(price, quantity, fp, fees, aa, aq))
    }
  }

  /** 세금 및 기타 수수료를 고쳤다: 실현손익을 계산한다. */
  const onFeesChange = () => {
    setLastEdited('fees')
    syncLinked({ source: 'fees' })
  }
  /** 실현손익을 고쳤다: 세금 및 기타 수수료를 계산한다. */
  const onProfitChange = () => {
    setLastEdited('profit')
    syncLinked({ source: 'profit' })
  }

  useEffect(() => {
    if (!selectedSubscriptionId || !allocation) return
    setValue('allocationId', allocation.id)
    // 공모주 정보(상장일·공모가)까지 불러온 뒤 한 번만 기본값을 넣는다. 사용자가 이미 고친 칸은 건드리지 않는다.
    if (ipoPending || prefilledFor.current === selectedSubscriptionId) return
    prefilledFor.current = selectedSubscriptionId
    const basis = resolveIpoPrice(fixedPrice)
    const openPrice =
      typeof ipo?.listingOpenPrice === 'number' && ipo.listingOpenPrice > 0
        ? ipo.listingOpenPrice
        : null
    // 매도단가 기본값: 시초가 → 확정가 → 배정 평균 단가(allocatedAmount/allocatedQuantity)
    const allocationAvgPrice = allocationUnitPrice(fixedPrice, allocatedAmount, allocatedQuantity)
    const priceDefault = openPrice != null ? openPrice : (basis ?? allocationAvgPrice)
    const quantity = allocationData.remainingQuantity ?? allocation.allocatedQuantity
    const priorSales = allocationData.sales.some((sale) => sale.status === 'ACTIVE')
    const saleDate = listingDate(ipo?.listingDate)
    if (!getFieldState('quantity').isDirty && quantity > 0) setValue('quantity', quantity)
    if (!getFieldState('price').isDirty && priceDefault != null) setValue('price', priceDefault)
    if (!getFieldState('saleDate').isDirty && saleDate) setValue('saleDate', saleDate)
    // 공모주수수료는 배정 1건당 한 번이라, 이미 매도 기록이 있으면 0원으로 시작한다.
    if (!getFieldState('ipoFee').isDirty && priorSales) setValue('ipoFee', 0)
    syncLinked({ fixedPrice, allocatedAmount, allocatedQuantity, source: 'fees' })
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
    resetField('allocatedQuantity')
    resetField('securitiesAccountId')
    resetField('ipoId')
    resetField('investorId')
    setLastEdited('fees')
    prefilledFor.current = undefined
  }

  const selectSubscription = (id: number) => {
    setPickedSubscriptionId(id)
    clearSaleFields()
  }

  const captureFill = useCaptureFill({
    selectedSubscriptionId,
    allocationData,
    ipoPending,
    ipoPrice: allocationUnitPrice(fixedPrice, allocatedAmount, allocatedQuantity),
    prefilledFor,
    setValue,
    getValues,
    setLastEdited,
    syncLinked,
    selectSubscription,
  })

  const onSubmit = (data: SaleFormValues) => {
    const directRegistration = data.allocationId == null
    mutate({
      allocationId: data.allocationId,
      ...toSalePayload({
        ...data,
        ipoId: directRegistration ? effectiveIpoId : undefined,
        investorId: directRegistration ? selectedInvestorId : undefined,
      }),
    })
  }

  const handleSubscriptionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value ? Number(e.target.value) : undefined
    if (id === undefined) {
      setPickedSubscriptionId(undefined)
      clearSaleFields()
    } else selectSubscription(id)
  }

  const [realizedProfit, ipoFee, quantityValue, priceValue] = useWatch({
    control,
    name: ['realizedProfit', 'ipoFee', 'quantity', 'price'],
  })

  return {
    register,
    control,
    errors,
    setValue,
    getValues,
    submit: handleSubmit(onSubmit),
    isPending,
    formErrorMessage: toErrorMessage(error, {
      byStatus: { 404: '배정을 찾을 수 없습니다.', 409: (e) => e.message },
    }),
    investorsLoading,
    current,
    selectedInvestorId,
    subscriptions: subscriptionHistory?.content ?? [],
    subscriptionsLoading,
    selectedSubscriptionId,
    allocationData,
    allocationLoading,
    handleSubscriptionChange,
    ...captureFill,
    openPrice:
      typeof ipo?.listingOpenPrice === 'number' && ipo.listingOpenPrice > 0
        ? ipo.listingOpenPrice
        : null,
    ipoPrice: allocationUnitPrice(fixedPrice, allocatedAmount, allocatedQuantity),
    canCalcAcquisition,
    hasPriorSales,
    lastEdited,
    syncLinked,
    onFeesChange,
    onProfitChange,
    finalAmount: isNum(realizedProfit)
      ? finalProfit(realizedProfit, isNum(ipoFee) ? ipoFee : 0)
      : undefined,
    grossValue:
      isNum(quantityValue) && isNum(priceValue)
        ? priceValue * quantityValue -
          acquisitionAmountForSale(quantityValue, fixedPrice, allocatedAmount, allocatedQuantity)
        : undefined,
    securitiesAccounts: securitiesAccounts ?? [],
    accountsLoading,
    effectiveIpoId,
  }
}

export type SaleForm = ReturnType<typeof useSaleForm>
