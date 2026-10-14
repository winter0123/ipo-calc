import { useForm, useWatch } from 'react-hook-form'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useIpo } from '@/entities/ipo/index.ts'
import { useInvestmentHistory, useInvestmentHistoryDetail } from '@/entities/subscription/index.ts'
import { useSaleRegister } from '../useSaleRegister.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import {
  DEFAULT_IPO_FEE,
  finalProfit,
  grossGain,
  listingDate,
  otherFeesFromRealizedProfit,
  realizedProfitFromFees,
  acquisitionUnitPrice,
  resolveIpoPrice,
  toSalePayload,
  type CaptureFill,
} from '@/entities/sale/index.ts'

/** 캡처로 채울 내용. subscriptionId가 없으면 사용자가 청약을 고르는 즉시 채운다. */
export interface CaptureApply {
  subscriptionId?: number
  fill: CaptureFill
  notes: string[]
}
export type CapturedField = 'quantity' | 'price' | 'saleDate' | 'otherFees' | 'realizedProfit'

/** 폼 입력값. 숫자 칸은 비어 있으면 NaN이다(valueAsNumber). */
export interface SaleFormValues {
  allocationId: number
  quantity: number
  price: number
  saleDate: string
  ipoFee: number
  otherFees: number
  realizedProfit: number
}

export const won = (value: number) => `${value.toLocaleString('ko-KR')}원`
export const isNum = (value: unknown): value is number =>
  typeof value === 'number' && !Number.isNaN(value)

/** 매도 등록 폼의 상태: 청약 선택, 자동 채움, 세금↔실현손익 상호 계산, 제출. */
export function useSaleForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  // 사용자가 청약을 직접 고르기 전(null)에는 ?subscriptionId=<id>로 들어온 청약을 쓴다.
  const [pickedSubscriptionId, setPickedSubscriptionId] = useState<number | undefined | null>(null)
  const presetSubscriptionId = Number(useSearchParams()[0].get('subscriptionId')) || undefined
  // 세금 및 기타 수수료와 실현손익 중 사용자가 마지막으로 고친 쪽(다른 쪽은 공식으로 계산한다)
  const [lastEdited, setLastEdited] = useState<'fees' | 'profit'>('fees')
  // 기본값을 이미 넣은 청약(같은 청약에서 사용자의 입력을 덮어쓰지 않는다)
  const prefilledFor = useRef<number | undefined>(undefined)
  // 캡처로 채우기: 청약·공모주 정보가 도착한 뒤 적용하려고 잠시 들고 있는다.
  const [pendingCapture, setPendingCapture] = useState<CaptureApply | null>(null)
  const [captured, setCaptured] = useState<Partial<Record<CapturedField, number | string>>>({})
  const [captureNotes, setCaptureNotes] = useState<string[]>([])

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
  // 실현손익 계산의 취득 단가 — 서버와 같은 규칙(배정단가 우선)이어야 저장 후 값이 같다.
  const ipoPrice = acquisitionUnitPrice(
    allocationData?.allocation?.allocationPrice,
    ipo?.fixedPrice ?? allocationData?.ipoFixedPrice,
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

  // 기본값 채우기 다음에 실행돼야 캡처 값이 기본값에 덮이지 않는다(선언 순서가 곧 실행 순서).
  /* eslint-disable react-hooks/set-state-in-effect -- 청약 정보가 도착한 시점에 한 번만 적용한다 */
  useEffect(() => {
    if (!pendingCapture || !selectedSubscriptionId || !allocationData?.allocation || ipoPending)
      return
    if (prefilledFor.current !== selectedSubscriptionId) return
    if (pendingCapture.subscriptionId && pendingCapture.subscriptionId !== selectedSubscriptionId)
      return
    const { fill } = pendingCapture
    const notes = [...pendingCapture.notes]
    const mark: Partial<Record<CapturedField, number | string>> = {}
    const opt = { shouldDirty: true }
    if (fill.quantity !== undefined) setValue('quantity', (mark.quantity = fill.quantity), opt)
    if (fill.price !== undefined) setValue('price', (mark.price = fill.price), opt)
    if (fill.saleDate) setValue('saleDate', (mark.saleDate = fill.saleDate), opt)
    if (fill.realizedProfit !== undefined) {
      // 증권사 앱의 실현손익을 기준으로 삼고 세금 및 기타를 거꾸로 계산한다(저장되는 실현손익이 앱과 같아진다).
      setValue('realizedProfit', (mark.realizedProfit = fill.realizedProfit), opt)
      setLastEdited('profit')
      if (fill.quantity === undefined || fill.price === undefined || ipoPrice == null) {
        // 계산 재료가 모자란다: 캡처의 합계만 넣고(없으면 비움), 수량·단가가 정해지면 폼이 실현손익 기준으로 다시 계산한다.
        setValue('otherFees', fill.fees ?? Number.NaN, opt)
        if (fill.fees !== undefined) mark.otherFees = fill.fees
      } else {
        syncLinked({ source: 'profit' })
        const derived = getValues('otherFees')
        if (fill.fees !== undefined) {
          if (derived !== fill.fees) {
            notes.push(
              `캡처의 수수료·제세금은 합계 ${won(fill.fees)}이지만, 실현손익과 취득 단가(${won(ipoPrice)}) 기준으로 계산한 세금 및 기타는 ${won(derived)}입니다. 실현손익을 우선해 ${won(derived)}으로 채웠습니다.`,
            )
          }
          mark.otherFees = derived
        }
      }
    } else if (fill.fees !== undefined) {
      setValue('otherFees', (mark.otherFees = fill.fees), opt)
      setLastEdited('fees')
      syncLinked({ source: 'fees' })
    }
    setCaptured(mark)
    setCaptureNotes(notes)
    setPendingCapture(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 대기 중인 캡처와 청약 정보가 바뀔 때만 실행한다
  }, [pendingCapture, allocationData, ipoPending, selectedSubscriptionId])
  /* eslint-enable react-hooks/set-state-in-effect */

  /** 캡처로 채우기: 다른 청약이면 선택을 바꾸고(입력값 초기화), 정보가 도착하면 effect가 값을 넣는다. */
  const applyCapture = (apply: CaptureApply) => {
    if (apply.subscriptionId && apply.subscriptionId !== selectedSubscriptionId) {
      setPickedSubscriptionId(apply.subscriptionId)
      clearSaleFields()
    }
    setCaptured({})
    setCaptureNotes([])
    setPendingCapture(apply)
  }

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

  const handleSubscriptionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setPickedSubscriptionId(e.target.value ? Number(e.target.value) : undefined)
    clearSaleFields()
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
    applyCapture,
    captured,
    captureNotes,
    capturePending: pendingCapture !== null && !selectedSubscriptionId,
    ipoPrice,
    hasPriorSales,
    lastEdited,
    syncLinked,
    onFeesChange,
    onProfitChange,
    finalAmount: isNum(realizedProfit)
      ? finalProfit(realizedProfit, isNum(ipoFee) ? ipoFee : 0)
      : undefined,
    grossValue:
      ipoPrice != null && isNum(priceValue) && isNum(quantityValue)
        ? grossGain(priceValue, quantityValue, ipoPrice)
        : undefined,
  }
}

export type SaleForm = ReturnType<typeof useSaleForm>
