import { describe, it, expect, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useSaleForm } from './useSaleForm.ts'

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
vi.mock('../useSaleRegister.ts', () => ({
  useSaleRegister: () => ({ mutate: vi.fn(), isPending: false, error: null }),
}))

const wrapper = ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={['/sales/register?subscriptionId=1']}>{children}</MemoryRouter>
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
})
