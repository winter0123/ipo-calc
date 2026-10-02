import { describe, it, expect, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSubscriptionRegister } from '../features/subscription-register/index.ts'
import { useAllocationRegister } from '../features/allocation-register/index.ts'
import { useSaleRegister } from '../features/sale-register/index.ts'
import { investmentHistoryKeys } from '../entities/subscription/index.ts'
import { profitKeys } from '../entities/profit/index.ts'
import { dashboardKeys } from '../entities/dashboard/index.ts'

vi.mock('../entities/subscription/api-client.ts', () => ({
  createSubscription: vi.fn().mockResolvedValue({}),
}))
vi.mock('../entities/allocation/api-client.ts', () => ({
  createAllocation: vi.fn().mockResolvedValue({}),
}))
vi.mock('../entities/sale/api-client.ts', () => ({ createSale: vi.fn().mockResolvedValue({}) }))

const EXPECTED_KEYS = [investmentHistoryKeys.all, profitKeys.all, dashboardKeys.all]

describe.each([
  ['청약 등록', useSubscriptionRegister],
  ['배정 등록', useAllocationRegister],
  ['매도 등록', useSaleRegister],
])('%s 성공 시 invalidation', (_name, useHook) => {
  it('투자 내역·수익·대시보드 쿼리를 무효화한다', async () => {
    const queryClient = new QueryClient()
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    )
    const { result } = renderHook(() => useHook(), { wrapper })

    act(() => result.current.mutate({} as never))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy.mock.calls.map(([filters]) => filters?.queryKey)).toEqual(EXPECTED_KEYS)
  })
})
