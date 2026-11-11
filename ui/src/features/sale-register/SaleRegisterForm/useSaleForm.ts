import { useForm, useWatch } from 'react-hook-form'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useIpo, useIpoInsight } from '@/entities/ipo/index.ts'
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

const parseIdParam = (raw: string | null): number | undefined => {
  if (raw == null) return undefined
  const value = Number(raw)
  return Number.isInteger(value) && value > 0 ? value : undefined
}

/** 매도 등록 폼의 상태. 공모주는 문맥(URL 프리셋·청약 연결)에서 고정하고, 청약·배정은 서버가 자동으로 연결한다. */
export function useSaleForm() {
  'use no memo' // react-hook-form의 formState는 제자리 변경되는 객체라 컴파일러 메모이제이션과 맞지 않는다
  // 캡처가 찾은 청약(URL 프리셋보다 우선). 사용자에게 청약을 고르라고 요구하지 않는다.
  const [pickedSubscriptionId, setPickedSubscriptionId] = useState<number | undefined>(undefined)
  const [searchParams] = useSearchParams()
  const rawSubscriptionId = searchParams.get('subscriptionId')
  const rawIpoId = searchParams.get('ipoId')
  const presetSubscriptionId = parseIdParam(rawSubscriptionId)
  const presetIpoId = parseIdParam(rawIpoId)
  // 문맥은 있지만 id가 올바르지 않으면 문맥 없음 안내가 아니라 오류 안내를 띄운다
  const invalidPresetId =
    (rawIpoId != null && presetIpoId === undefined) ||
    (rawSubscriptionId != null && presetSubscriptionId === undefined)
  // 명시 연결(URL 프리셋·캡처 매칭) — 있으면 그 연결만 쓴다
  const explicitConnectionId = pickedSubscriptionId ?? presetSubscriptionId
  const needsAccountSelect = explicitConnectionId == null
  // URL 문맥(공모주·청약 프리셋) 키 — 바뀌면 이전 계좌·캡처 연결·연결 기본값을 지우고 새로 시작한다
  const contextKey = `${presetIpoId ?? ''}|${presetSubscriptionId ?? ''}`
  // 세금 및 기타 수수료와 실현손익 중 사용자가 마지막으로 고친 쪽(다른 쪽은 공식으로 계산한다)
  const [lastEdited, setLastEdited] = useState<'fees' | 'profit'>('fees')
  // 기본값을 이미 넣은 연결(같은 연결에서 사용자의 입력을 덮어쓰지 않는다)
  const prefilledFor = useRef<number | undefined>(undefined)
  // 배정 없이 공모가로 채운 가격·매도일(공모주가 바뀌면 다시 판정한다)
  const pricePrefilledFor = useRef<number | undefined>(undefined)
  // 이전 계좌 ID(계좌를 바꾸면 연결 기반 기본값을 다시 계산한다)
  const previousAccountId = useRef<number | undefined>(undefined)
  // 이전 URL 문맥(공모주·청약 프리셋) — 바뀌면 이전 계좌·캡처 연결·연결 기본값을 지운다
  const previousContextKey = useRef<string | undefined>(undefined)

  // 투자자는 상단 전환기의 현재 투자자다. 전환하면 페이지가 이 폼을 새로 만든다.
  const { current, isLoading: investorsLoading } = useCurrentInvestor()
  const selectedInvestorId = current?.id

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    getValues,
    getFieldState,
    control,
  } = useForm<SaleFormValues>({ mode: 'onSubmit', defaultValues: { ipoFee: DEFAULT_IPO_FEE } })

  // 현재 투자자의 청약 목록 — 캡처가 종목을 매칭할 때만 쓴다(화면에 선택지로 노출하지 않는다)
  const { data: subscriptionHistory, isLoading: subscriptionsLoading } = useInvestmentHistory(
    selectedInvestorId ? { investorId: selectedInvestorId } : {},
    { enabled: !!selectedInvestorId },
  )

  const accountValue = useWatch({ control, name: 'securitiesAccountId' })
  const selectedAccountId =
    typeof accountValue === 'number' && !Number.isNaN(accountValue) ? accountValue : undefined

  // 계좌를 고른 뒤 (투자자, 공모주, 계좌)로 기존 연결을 조회한다
  const connectionLookup = useInvestmentHistory(
    {
      investorId: selectedInvestorId,
      ipoId: presetIpoId,
      securitiesAccountId: selectedAccountId,
      size: 1,
    },
    {
      enabled:
        needsAccountSelect &&
        !!selectedInvestorId &&
        presetIpoId != null &&
        selectedAccountId != null,
    },
  )
  const { refetch: refetchConnectionLookup } = connectionLookup
  const foundConnectionId = connectionLookup.data?.content.find(
    (item) => item.ipoId === presetIpoId && item.subscriptionStatus === 'ACTIVE',
  )?.id
  const resolvedConnectionId = explicitConnectionId ?? foundConnectionId

  // 연결(청약) 상세 — 배정·남은 수량·공모주 ID를 여기서 확정한다
  const detailQuery = useInvestmentHistoryDetail(resolvedConnectionId ?? null)
  const { refetch: refetchConnectionDetail } = detailQuery
  const allocationData = detailQuery.data
  const connectionLoading =
    (resolvedConnectionId != null && detailQuery.isLoading) ||
    (needsAccountSelect && selectedAccountId != null && connectionLookup.isLoading)
  const detailIpoId = allocationData?.ipoId
  // 공모주 확정: 청약 연결의 ipoId → (연결이 없으면) URL 프리셋. 연결 로딩 중에는 비워 둔다.
  const effectiveIpoId = detailIpoId ?? (resolvedConnectionId != null ? undefined : presetIpoId)
  // URL 프리셋이 가리키는 공모주와 청약이 서로 다르면 등록하지 않는다(캡처로 연결을 바꾼 경우는 허용)
  const presetMismatch =
    pickedSubscriptionId == null &&
    presetSubscriptionId != null &&
    presetIpoId != null &&
    detailIpoId != null &&
    presetIpoId !== detailIpoId

  const {
    data: ipo,
    isPending: ipoPending,
    error: ipoError,
    refetch: refetchIpo,
  } = useIpo(effectiveIpoId)

  const {
    data: ipoInsight,
    isLoading: ipoInsightLoading,
    error: ipoInsightError,
    refetch: refetchIpoInsight,
  } = useIpoInsight(effectiveIpoId)

  // 증권계좌 목록 (공모주별 매도 등록에서 선택)
  const {
    data: securitiesAccounts,
    isLoading: accountsLoading,
    isError: accountsError,
    refetch: refetchSecuritiesAccounts,
  } = useSecuritiesAccounts(selectedInvestorId)

  const underwriters = useMemo(() => ipoInsight?.underwriters ?? [], [ipoInsight])
  const underwritersReady = !ipoInsightLoading && !ipoInsightError && underwriters.length > 0
  const filteredSecuritiesAccounts = useMemo(() => {
    const accounts = securitiesAccounts ?? []
    if (!needsAccountSelect) return accounts
    if (effectiveIpoId == null) return accounts
    if (!underwritersReady) return []
    const matched = accounts.filter((account) =>
      underwriters.some(
        (underwriter) => underwriter.name.trim() === (account.securitiesFirmName ?? '').trim(),
      ),
    )
    if (matched.length > 0) {
      return [...matched].sort((a, b) => {
        const firmOrder = (a.securitiesFirmName ?? '').localeCompare(
          b.securitiesFirmName ?? '',
          'ko-KR',
        )
        if (firmOrder !== 0) return firmOrder
        return (a.accountName ?? '').localeCompare(b.accountName ?? '', 'ko-KR')
      })
    }
    return []
  }, [securitiesAccounts, needsAccountSelect, effectiveIpoId, underwritersReady, underwriters])
  const missingUnderwriterAccount =
    needsAccountSelect &&
    effectiveIpoId != null &&
    underwritersReady &&
    !accountsLoading &&
    !accountsError &&
    filteredSecuritiesAccounts.length === 0

  // 화면에 보여 줄 진입 문맥: 안내(문맥 없음) / 오류 / 정상 폼
  const guide =
    !invalidPresetId &&
    presetIpoId == null &&
    presetSubscriptionId == null &&
    pickedSubscriptionId == null
  const entryProblem = invalidPresetId
    ? rawIpoId != null
      ? ('ipo-not-found' as const)
      : ('subscription-not-found' as const)
    : presetMismatch
      ? ('mismatch' as const)
      : resolvedConnectionId != null && detailQuery.isError
        ? ('subscription-not-found' as const)
        : effectiveIpoId != null && ipoError
          ? ('ipo-not-found' as const)
          : null

  // 허용 계좌와 대조한다 — 공모주 주관사에 해당하는 계좌만 등록에 사용할 수 있다
  const accountContextReady =
    needsAccountSelect &&
    !accountsLoading &&
    !accountsError &&
    effectiveIpoId != null &&
    underwritersReady
  const selectedAccountValid =
    !needsAccountSelect ||
    (selectedAccountId != null &&
      filteredSecuritiesAccounts.some((account) => account.id === selectedAccountId))

  // 필수 조회 중/오류를 명시적 상태로 노출한다 — 이 사유가 있으면 등록 버튼을 비활성화하고 onSubmit도 거부한다
  const submitBlockReason:
    | 'ipo-loading'
    | 'insight-loading'
    | 'insight-failed'
    | 'accounts-loading'
    | 'accounts-failed'
    | 'lookup-loading'
    | 'lookup-failed'
    | 'detail-loading'
    | 'account-required'
    | 'account-not-allowed'
    | null = (() => {
    if (guide) return null // 폼 자체가 렌더링되지 않는다
    if (entryProblem) return null // 오류 안내 카드가 대신 표시된다
    if (effectiveIpoId != null && ipoPending) return 'ipo-loading'
    if (needsAccountSelect) {
      if (ipoInsightLoading) return 'insight-loading'
      if (ipoInsightError) return 'insight-failed'
      if (accountsLoading) return 'accounts-loading'
      if (accountsError) return 'accounts-failed'
      if (connectionLookup.isLoading) return 'lookup-loading'
      // 연결 확인에 실패한 상태를 "기존 연결 없음"으로 간주하지 않는다
      if (connectionLookup.isError) return 'lookup-failed'
      if (selectedAccountId == null) return 'account-required'
      if (accountContextReady && !selectedAccountValid) return 'account-not-allowed'
    }
    if (resolvedConnectionId != null && detailQuery.isLoading) return 'detail-loading'
    return null
  })()
  const submitBlocked = submitBlockReason != null

  // 실패한 필수 조회를 다시 시도한다
  const retryEssentialQueries = () => {
    void Promise.allSettled([
      refetchIpo(),
      refetchIpoInsight(),
      refetchSecuritiesAccounts(),
      refetchConnectionLookup(),
      refetchConnectionDetail(),
    ])
  }

  // 계좌가 하나면 기본 선택한다(직접 고를 계좌가 없으므로).
  useEffect(() => {
    if (!needsAccountSelect || effectiveIpoId == null || !underwritersReady || accountsLoading)
      return
    const accounts = filteredSecuritiesAccounts
    if (accounts.length !== 1) return
    setValue('securitiesAccountId', accounts[0].id)
  }, [
    needsAccountSelect,
    effectiveIpoId,
    underwritersReady,
    accountsLoading,
    filteredSecuritiesAccounts,
    setValue,
  ])

  const { mutate, isPending, error } = useSaleRegister()

  const hasPriorSales = allocationData?.sales.some((sale) => sale.status === 'ACTIVE') ?? false
  const allocation = allocationData?.allocation
  const fixedPrice = ipo?.fixedPrice ?? allocationData?.ipoFixedPrice
  const allocatedAmount = allocation?.allocatedAmount ?? null
  const allocatedQuantity = allocation?.allocatedQuantity ?? null
  const connectedAccountId = allocationData?.securitiesAccountId ?? null
  const canCalcAcquisition =
    (fixedPrice != null && fixedPrice > 0) ||
    (allocatedAmount != null && allocatedQuantity != null && allocatedQuantity > 0)

  // 배정이 없을 때만 새 배정 생성에 필요한 수량을 요구한다
  const needsAllocatedQuantity = (() => {
    if (resolvedConnectionId != null) {
      if (allocationData == null) return false // 로딩·오류는 화면에서 가른다
      return allocation == null
    }
    return selectedAccountId != null && connectionLookup.isSuccess && foundConnectionId == null
  })()

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

  // 연결이 바뀌면(계좌 선택·청약 프리셋) 연결 기반 값들을 다시 계산한다. 사용자 입력은 덮어쓰지 않는다.
  const resetConnectionDefaults = () => {
    // 배정수량은 연결마다 의미가 다른 값이라 항상 비운다
    setValue('allocatedQuantity', undefined)
    for (const name of [
      'quantity',
      'price',
      'saleDate',
      'otherFees',
      'realizedProfit',
      'ipoFee',
    ] as const) {
      if (!getFieldState(name).isDirty) setValue(name, undefined as never)
    }
    setLastEdited('fees')
    prefilledFor.current = undefined
    pricePrefilledFor.current = undefined
  }

  // 문맥(공모주·청약 프리셋)이 바뀌면 이전 계좌·캡처 연결·연결 기반 기본값을 지운다
  // (사용자가 직접 입력한 매도값은 그대로 두되, 이전 연결로 등록되는 일은 없다)
  useEffect(() => {
    if (previousContextKey.current === undefined) {
      previousContextKey.current = contextKey
      return
    }
    if (previousContextKey.current === contextKey) return
    previousContextKey.current = contextKey
    setPickedSubscriptionId(undefined)
    setValue('securitiesAccountId', undefined)
    previousAccountId.current = undefined
    resetConnectionDefaults()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 문맥 키가 바뀔 때만 정리한다
  }, [contextKey])

  // 계좌를 바꾸면 이전 연결의 기본값(수량·단가·매도일·수수료)을 비우고 새 연결로 다시 채운다
  useEffect(() => {
    const accountId = selectedAccountId
    if (previousAccountId.current !== undefined && previousAccountId.current !== accountId) {
      resetConnectionDefaults()
    }
    previousAccountId.current = accountId
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 계좌 ID가 바뀔 때만 다시 계산한다
  }, [selectedAccountId])

  // 배정이 있는 연결이면 배정 기준 기본값을 채운다(사용자가 고친 칸은 건드리지 않는다)
  useEffect(() => {
    if (!resolvedConnectionId || !allocation) return
    if (ipoPending || prefilledFor.current === resolvedConnectionId) return
    prefilledFor.current = resolvedConnectionId
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
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 연결·공모주 정보가 도착했을 때만 실행한다
  }, [allocationData, ipo, ipoPending, resolvedConnectionId])

  // 배정이 없는 문맥(첫 직접 등록·배정 입력 전 청약)이면 공모가·시초가를 기본값으로 넣는다
  useEffect(() => {
    const hasAllocation = resolvedConnectionId != null && allocationData?.allocation != null
    if (hasAllocation || effectiveIpoId == null) return
    if (ipoPending || pricePrefilledFor.current === effectiveIpoId) return
    const basis = resolveIpoPrice(ipo?.fixedPrice)
    const openPrice =
      typeof ipo?.listingOpenPrice === 'number' && ipo.listingOpenPrice > 0
        ? ipo.listingOpenPrice
        : null
    const priceDefault = openPrice != null ? openPrice : basis
    if (!getFieldState('price').isDirty && priceDefault != null) setValue('price', priceDefault)
    const listing = ipo ? listingDate(ipo.listingDate) : undefined
    if (!getFieldState('saleDate').isDirty && listing) setValue('saleDate', listing)
    pricePrefilledFor.current = effectiveIpoId
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 공모주·연결 문맥이 바뀔 때만 실행한다
  }, [resolvedConnectionId, effectiveIpoId, ipo, ipoPending, allocationData])

  const selectSubscription = (id: number) => {
    setPickedSubscriptionId(id)
    // 연결이 바뀌면 이전 연결의 기본값·캡처 표시를 지운다(사용자 입력은 유지)
    resetConnectionDefaults()
  }

  // 캡처 값을 적용할 준비가 됐는지: 문맥·계좌 확정, 연결이 있으면 상세가 도착한 뒤
  const lookupPending =
    needsAccountSelect && selectedAccountId != null && connectionLookup.isLoading
  const connectionReady =
    !ipoPending &&
    effectiveIpoId != null &&
    !lookupPending &&
    !(needsAccountSelect && connectionLookup.isError) &&
    (resolvedConnectionId == null || allocationData != null)

  const captureFill = useCaptureFill({
    connectionId: resolvedConnectionId,
    allocationData,
    ipoPending,
    connectionReady,
    accountId: selectedAccountId,
    ipoPrice: allocationUnitPrice(fixedPrice, allocatedAmount, allocatedQuantity),
    prefilledFor,
    setValue,
    getValues,
    setLastEdited,
    syncLinked,
    selectSubscription,
  })

  const onSubmit = (data: SaleFormValues) => {
    // 필수 조회가 끝나지 않았거나 실패한 상태에서는 절대 제출하지 않는다
    if (submitBlocked) return
    const connectionId = resolvedConnectionId
    mutate({
      ...toSalePayload({
        ...data,
        allocatedQuantity: needsAllocatedQuantity ? data.allocatedQuantity : undefined,
        // 연결이 있으면 서버가 청약의 계좌를 쓰므로 계좌를 보내지 않는다
        securitiesAccountId: connectionId != null ? undefined : data.securitiesAccountId,
        ipoId: effectiveIpoId,
        investorId: selectedInvestorId,
      }),
      ...(connectionId != null ? { subscriptionId: connectionId } : {}),
    })
  }

  const [realizedProfit, ipoFee, quantityValue, priceValue] = useWatch({
    control,
    name: ['realizedProfit', 'ipoFee', 'quantity', 'price'],
  })

  const connectionSubscriptionName =
    subscriptionHistory?.content.find((item) => item.id === resolvedConnectionId)?.ipoName ?? null

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
    effectiveIpoId,
    ipoName: ipo?.ipoName ?? connectionSubscriptionName,
    ipoCompanyName: ipo?.companyName ?? null,
    ipoStockCode: ipo?.stockCode ?? null,
    ipoNameLoading: effectiveIpoId != null && ipoPending && ipo == null,
    guide,
    entryProblem,
    connectionId: resolvedConnectionId,
    connectionLoading,
    allocationData,
    connectionError: resolvedConnectionId != null && detailQuery.isError,
    submitBlocked,
    submitBlockReason,
    retryEssentialQueries,
    selectedAccountValid,
    needsAllocatedQuantity,
    needsAccountSelect,
    connectedAccountId,
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
    securitiesAccountId: selectedAccountId,
    securitiesAccounts: filteredSecuritiesAccounts,
    accountsLoading,
    missingUnderwriterAccount,
    underwriterNames: [...new Set(underwriters.map(({ name }) => name.trim()).filter(Boolean))],
  }
}

export type SaleForm = ReturnType<typeof useSaleForm>
