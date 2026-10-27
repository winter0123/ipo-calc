import { describe, it, expect, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useSaleForm } from './useSaleForm.ts'

const mocks = vi.hoisted(() => ({ mutate: vi.fn() }))

vi.mock('@/entities/investor/index.ts', () => ({
  useCurrentInvestor: () => ({ current: { id: 1, displayName: '홍길동' }, isLoading: false }),
}))
vi.mock('@/entities/ipo/index.ts', () => ({
  useIpo: () => ({ data: { fixedPrice: 10000, listingDate: '2026-10-23' }, isPending: false }),
}))
vi.mock('@/entities/subscription/index.ts', () => ({
  useInvestmentHistory: () => ({ data: { content: [{ id: 1 }] }, isLoading: false }),
  useInvestmentHistoryDetail: () => ({
    isLoading: false,
    data: {
      ipoId: 10,
      ipoFixedPrice: 10000,
      allocation: { id: 1, allocatedQuantity: 50, allocationPrice: null },
      sales: [],
      remainingQuantity: 30,
    },
  }),
}))
vi.mock('@/entities/securities-account/index.ts', () => ({
  useSecuritiesAccounts: () => ({
    data: [{ id: 1, securitiesFirmName: '테스트증권', accountName: '테스트계좌' }],
    isLoading: false,
  }),
}))
vi.mock('../useSaleRegister.ts', () => ({
  useSaleRegister: () => ({ mutate: mocks.mutate, isPending: false, error: null }),
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

describe('useSaleForm 상호 계산', () => {
  it('청약 없이 매도하면 현재 투자자와 URL의 공모주 ID를 함께 전송한다', async () => {
    mocks.mutate.mockClear()
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

  it('세금 및 기타 수수료를 고치면 실현손익이 (단가 − 공모가) × 수량 − 수수료로 계산된다', () => {
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
})
