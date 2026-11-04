import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useSaleForm } from './useSaleForm.ts'

const mocks = vi.hoisted(() => ({
  mutate: vi.fn(),
  useCurrentInvestor: vi.fn(() => ({
    current: { id: 1, displayName: '홍길동' },
    isLoading: false,
  })),
  useIpo: vi.fn(() => ({
    data: { fixedPrice: 10000, listingDate: '2026-10-23', listingOpenPrice: null },
    isPending: false,
  })),
  useIpoInsight: vi.fn(() => ({
    data: { underwriters: [{ name: '테스트증권', role: '주관', shares: 1 }] },
    isLoading: false,
    error: null,
  })),
  useInvestmentHistory: vi.fn(() => ({ data: { content: [{ id: 1 }] }, isLoading: false })),
  useInvestmentHistoryDetail: vi.fn(() => ({
    isLoading: false,
    data: {
      ipoId: 10,
      ipoFixedPrice: 10000,
      allocation: { id: 1, allocatedQuantity: 50, allocatedAmount: 500000 },
      sales: [],
      remainingQuantity: 30,
    },
  })),
  useSecuritiesAccounts: vi.fn(() => ({
    data: [{ id: 1, securitiesFirmName: '테스트증권', accountName: '테스트계좌' }],
    isLoading: false,
  })),
  useSaleRegister: vi.fn(() => ({ mutate: mocks.mutate, isPending: false, error: null })),
}))

vi.mock('@/entities/investor/index.ts', () => ({
  useCurrentInvestor: () => mocks.useCurrentInvestor(),
}))
vi.mock('@/entities/ipo/index.ts', () => ({
  useIpo: () => mocks.useIpo(),
  useIpoInsight: () => mocks.useIpoInsight(),
}))
vi.mock('@/entities/subscription/index.ts', () => ({
  useInvestmentHistory: () => mocks.useInvestmentHistory(),
  useInvestmentHistoryDetail: () => mocks.useInvestmentHistoryDetail(),
}))
vi.mock('@/entities/securities-account/index.ts', () => ({
  useSecuritiesAccounts: () => mocks.useSecuritiesAccounts(),
}))
vi.mock('../useSaleRegister.ts', () => ({
  useSaleRegister: () => mocks.useSaleRegister(),
}))

const wrapper = ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={['/sales/register?subscriptionId=1']}>{children}</MemoryRouter>
)

const directWrapper = ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={['/sales/register?ipoId=10']}>{children}</MemoryRouter>
)

function setup() {
  const { result } = renderHook(() => useSaleForm(), { wrapper })
  act(() => {
    result.current.setValue('price', 12000)
    result.current.setValue('quantity', 10)
  })
  return result
}

function setInvestmentHistoryDetail(data: {
  isLoading: boolean
  data: {
    ipoId: number
    ipoFixedPrice: number | null
    allocation: { id: number; allocatedQuantity: number; allocatedAmount: number | null }
    sales: unknown[]
    remainingQuantity: number
  } | null
}) {
  mocks.useInvestmentHistoryDetail.mockReturnValue(
    data as ReturnType<typeof mocks.useInvestmentHistoryDetail>,
  )
}

function setIpo(data: {
  data: { fixedPrice: number | null; listingDate: string; listingOpenPrice: number | null }
  isPending: boolean
}) {
  mocks.useIpo.mockReturnValue(data as ReturnType<typeof mocks.useIpo>)
}

describe('useSaleForm 상호 계산', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.mutate.mockReset()
    mocks.useCurrentInvestor.mockReturnValue({
      current: { id: 1, displayName: '홍길동' },
      isLoading: false,
    })
    mocks.useIpo.mockReturnValue({
      data: { fixedPrice: 10000, listingDate: '2026-10-23', listingOpenPrice: null },
      isPending: false,
    })
    mocks.useInvestmentHistory.mockReturnValue({ data: { content: [{ id: 1 }] }, isLoading: false })
    mocks.useInvestmentHistoryDetail.mockReturnValue({
      isLoading: false,
      data: {
        ipoId: 10,
        ipoFixedPrice: 10000,
        allocation: { id: 1, allocatedQuantity: 50, allocatedAmount: 500000 },
        sales: [],
        remainingQuantity: 30,
      },
    })
    mocks.useSecuritiesAccounts.mockReturnValue({
      data: [{ id: 1, securitiesFirmName: '테스트증권', accountName: '테스트계좌' }],
      isLoading: false,
    })
    mocks.useSaleRegister.mockReturnValue({ mutate: mocks.mutate, isPending: false, error: null })
  })

  it('청약 없이 매도하면 현재 투자자와 URL의 공모주 ID를 함께 전송한다', async () => {
    const { result } = renderHook(() => useSaleForm(), { wrapper: directWrapper })
    act(() => {
      result.current.setValue('allocatedQuantity', 10)
      result.current.setValue('securitiesAccountId', 1)
      result.current.setValue('quantity', 10)
      result.current.setValue('price', 12000)
      result.current.setValue('saleDate', '2026-10-23')
      result.current.setValue('ipoFee', 2000)
      result.current.setValue('otherFees', 500)
    })

    await act(async () => {
      await result.current.submit()
    })

    expect(mocks.mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        allocationId: undefined,
        allocatedQuantity: 10,
        securitiesAccountId: 1,
        ipoId: 10,
        investorId: 1,
      }),
    )
  })

  it('IPO 진입(직접 등록) 시 해당 공모주 상장일을 매도일 기본값으로 넣는다', async () => {
    mocks.useIpo.mockReturnValue({
      data: { fixedPrice: 10000, listingDate: '2026-10-23', listingOpenPrice: null },
      isPending: false,
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper: directWrapper })
    await waitFor(() => expect(result.current.getValues('saleDate')).toBe('2026-10-23'))
  })

  it('IPO 진입 시 해당 주관사 계좌만 남기고 한 건이면 자동 선택한다', async () => {
    mocks.useIpoInsight.mockReturnValue({
      data: { underwriters: [{ name: '테스트증권', role: '주관', shares: 1 }] },
      isLoading: false,
      error: null,
    })
    mocks.useSecuritiesAccounts.mockReturnValue({
      data: [
        { id: 7, securitiesFirmName: '테스트증권', accountName: '테스트계좌' },
        { id: 8, securitiesFirmName: '다른증권', accountName: '다른계좌' },
      ],
      isLoading: false,
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper: directWrapper })
    await waitFor(() => expect(result.current.securitiesAccounts).toHaveLength(1))
    expect(result.current.securitiesAccounts[0]).toMatchObject({
      id: 7,
      securitiesFirmName: '테스트증권',
    })
  })

  it('IPO 진입 시 복수 주관사 계좌가 주관사 순, 같은 주관사 내 계좌명 순으로 정렬되어 노출된다', async () => {
    mocks.useIpo.mockReturnValue({
      data: { fixedPrice: 10000, listingDate: '2026-10-23', listingOpenPrice: null },
      isPending: false,
    })
    mocks.useIpoInsight.mockReturnValue({
      data: {
        underwriters: [
          { name: 'AAA증권', role: '주관', shares: 1 },
          { name: 'BBB증권', role: '주관', shares: 1 },
        ],
      },
      isLoading: false,
      error: null,
    })
    mocks.useSecuritiesAccounts.mockReturnValue({
      data: [
        { id: 2, securitiesFirmName: 'BBB증권', accountName: 'BBB계좌' },
        { id: 1, securitiesFirmName: 'AAA증권', accountName: 'AAA계좌' },
        { id: 3, securitiesFirmName: 'BBB증권', accountName: 'AAA계좌' },
      ],
      isLoading: false,
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper: directWrapper })
    await waitFor(() => expect(result.current.securitiesAccounts).toHaveLength(3))
    const accounts = result.current.securitiesAccounts
    expect(accounts.map((account) => account.id)).toEqual([1, 3, 2])
    expect(accounts.map((account) => account.securitiesFirmName)).toEqual([
      'AAA증권',
      'BBB증권',
      'BBB증권',
    ])
  })

  it('IPO 진입 시 증권계좌 조회가 로딩 중이면 타사 계좌를 노출하지 않는다', async () => {
    mocks.useIpo.mockReturnValue({
      data: { fixedPrice: 10000, listingDate: '2026-10-23', listingOpenPrice: null },
      isPending: false,
    })
    mocks.useIpoInsight.mockReturnValue({
      data: { underwriters: [] },
      isLoading: true,
      error: null,
    })
    mocks.useSecuritiesAccounts.mockReturnValue({
      data: [
        { id: 8, securitiesFirmName: '다른증권', accountName: '다른계좌' },
        { id: 7, securitiesFirmName: 'AAA증권', accountName: 'AAA계좌' },
      ],
      isLoading: false,
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper: directWrapper })
    expect(result.current.securitiesAccounts).toHaveLength(0)
  })

  it('IPO 진입 시 증권계좌 조회가 실패하면 타사 계좌를 노출하지 않는다', async () => {
    mocks.useIpo.mockReturnValue({
      data: { fixedPrice: 10000, listingDate: '2026-10-23', listingOpenPrice: null },
      isPending: false,
    })
    mocks.useIpoInsight.mockReturnValue({
      data: { underwriters: [] },
      isLoading: false,
      error: new Error('ipo insight failed'),
    } as unknown as ReturnType<typeof mocks.useIpoInsight>)
    mocks.useSecuritiesAccounts.mockReturnValue({
      data: [
        { id: 8, securitiesFirmName: '다른증권', accountName: '다른계좌' },
        { id: 7, securitiesFirmName: 'AAA증권', accountName: 'AAA계좌' },
      ],
      isLoading: false,
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper: directWrapper })
    expect(result.current.securitiesAccounts).toHaveLength(0)
  })

  it('세금 및 기타 수수료를 고치면 실현손익이 매도금액 − 취득금액 − 수수료로 계산된다 (allocatedAmount 사용)', () => {
    const result = setup()
    act(() => {
      result.current.setValue('otherFees', 500)
      result.current.onFeesChange()
    })
    expect(result.current.getValues('realizedProfit')).toBe(19500)
  })

  it('실현손익을 고치면 세금 및 기타 수수료가 거꾸로 계산된다', () => {
    const result = setup()
    act(() => {
      result.current.setValue('realizedProfit', 19000)
      result.current.onProfitChange()
    })
    expect(result.current.getValues('otherFees')).toBe(1000)
    expect(result.current.lastEdited).toBe('profit')
  })

  it('allocatedAmount가 null이면 fixedPrice로 취득금액 계산 (fallback)', () => {
    setInvestmentHistoryDetail({
      isLoading: false,
      data: {
        ipoId: 10,
        ipoFixedPrice: 10000,
        allocation: { id: 1, allocatedQuantity: 50, allocatedAmount: null },
        sales: [],
        remainingQuantity: 30,
      },
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper })
    act(() => {
      result.current.setValue('price', 12000)
      result.current.setValue('quantity', 10)
      result.current.setValue('otherFees', 500)
      result.current.onFeesChange()
    })
    expect(result.current.getValues('realizedProfit')).toBe(19500)
  })

  it('캡처 값을 적용하고 손익 기준 수수료를 계산하며 차이를 안내한다', () => {
    const result = setup()
    act(() => {
      result.current.applyCapture({
        subscriptionId: 1,
        fill: {
          quantity: 5,
          price: 12000,
          saleDate: '2026-10-23',
          realizedProfit: 9000,
          fees: 500,
        },
        notes: ['캡처 확인 안내'],
      })
    })

    expect(result.current.getValues('quantity')).toBe(5)
    expect(result.current.getValues('price')).toBe(12000)
    expect(result.current.getValues('saleDate')).toBe('2026-10-23')
    expect(result.current.getValues('realizedProfit')).toBe(9000)
    expect(result.current.getValues('otherFees')).toBe(1000)
    expect(result.current.captured).toMatchObject({
      quantity: 5,
      price: 12000,
      saleDate: '2026-10-23',
      realizedProfit: 9000,
      otherFees: 1000,
    })
    expect(result.current.captureNotes).toEqual([
      '캡처 확인 안내',
      expect.stringContaining('실현손익을 우선해 1,000원으로 채웠습니다.'),
    ])
  })

  it('allocatedAmount=95000, allocatedQuantity=10, 매도 5주 → 취득금액 47500 (테스트 a)', () => {
    setInvestmentHistoryDetail({
      isLoading: false,
      data: {
        ipoId: 10,
        ipoFixedPrice: 10000,
        allocation: { id: 1, allocatedQuantity: 10, allocatedAmount: 95000 },
        sales: [],
        remainingQuantity: 5,
      },
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper })
    act(() => {
      result.current.setValue('price', 15000)
      result.current.setValue('quantity', 5)
      result.current.setValue('otherFees', 0)
      result.current.onFeesChange()
    })
    expect(result.current.getValues('realizedProfit')).toBe(27500)
  })

  it('allocatedAmount=100, allocatedQuantity=3, 매도 2주 → 취득금액 67 (HALF_UP, 테스트 b)', () => {
    setInvestmentHistoryDetail({
      isLoading: false,
      data: {
        ipoId: 10,
        ipoFixedPrice: 10000,
        allocation: { id: 1, allocatedQuantity: 3, allocatedAmount: 100 },
        sales: [],
        remainingQuantity: 2,
      },
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper })
    act(() => {
      result.current.setValue('price', 10000)
      result.current.setValue('quantity', 2)
      result.current.setValue('otherFees', 0)
      result.current.onFeesChange()
    })
    expect(result.current.getValues('realizedProfit')).toBe(19933)
  })

  it('매도 단가 기본값: 상장 시초가 우선, 없으면 fixedPrice, 없으면 배정 평균 단가', () => {
    setIpo({
      data: { fixedPrice: 10000, listingOpenPrice: 15000, listingDate: '2026-10-23' },
      isPending: false,
    })
    setInvestmentHistoryDetail({
      isLoading: false,
      data: {
        ipoId: 10,
        ipoFixedPrice: 10000,
        allocation: { id: 1, allocatedQuantity: 50, allocatedAmount: 500000 },
        sales: [],
        remainingQuantity: 30,
      },
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper })
    expect(result.current.openPrice).toBe(15000)
    expect(result.current.ipoPrice).toBe(10000)
  })

  it('grossValue는 공모주수수료/기타 수수료를 제외한 수수료 전 차익이며 acquisitionAmountForSale 기준이다', () => {
    setInvestmentHistoryDetail({
      isLoading: false,
      data: {
        ipoId: 10,
        ipoFixedPrice: 10000,
        allocation: { id: 1, allocatedQuantity: 3, allocatedAmount: 10000 },
        sales: [],
        remainingQuantity: 2,
      },
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper })
    act(() => {
      result.current.setValue('price', 5000)
      result.current.setValue('quantity', 2)
    })
    expect(result.current.grossValue).toBe(3333)
  })

  it('fixedPrice 없을 때 배정 평균 단가를 매도 단가 기본값으로 사용', () => {
    setIpo({
      data: { fixedPrice: null, listingOpenPrice: null, listingDate: '2026-10-23' },
      isPending: false,
    })
    setInvestmentHistoryDetail({
      isLoading: false,
      data: {
        ipoId: 10,
        ipoFixedPrice: null,
        allocation: { id: 1, allocatedQuantity: 50, allocatedAmount: 500000 },
        sales: [],
        remainingQuantity: 30,
      },
    })
    const { result } = renderHook(() => useSaleForm(), { wrapper })
    expect(result.current.ipoPrice).toBe(10000)
    expect(result.current.openPrice).toBeNull()
  })
})
