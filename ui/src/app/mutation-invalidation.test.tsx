import { describe, it, expect, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSubscriptionRegister } from '../features/subscription-register/index.ts'
import { useAllocationRegister } from '../features/allocation-register/index.ts'
import { useSaleRegister } from '../features/sale-register/index.ts'
import { useConfirmSync } from '../features/broker-sync/index.ts'
import { useRecordMutation } from '../features/investment-edit/index.ts'
import { useRegisterInvestor } from '../features/investor-register/index.ts'
import { investorKeys } from '../entities/investor/index.ts'
import { apiFetch } from '../shared/api/client.ts'
import { investmentHistoryKeys } from '../entities/subscription/index.ts'
import { profitKeys } from '../entities/profit/index.ts'
import { dashboardKeys } from '../entities/dashboard/index.ts'
import { brokerKeys } from '../entities/broker/index.ts'

vi.mock('../entities/subscription/api-client.ts', () => ({
  createSubscription: vi.fn().mockResolvedValue({}),
}))
vi.mock('../entities/allocation/api-client.ts', () => ({
  createAllocation: vi.fn().mockResolvedValue({}),
}))
vi.mock('../entities/sale/api-client.ts', () => ({ createSale: vi.fn().mockResolvedValue({}) }))
vi.mock('../features/broker-sync/api-client.ts', () => ({
  confirmBrokerSync: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('../shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

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

describe.each([
  ['투자자 등록', useRegisterInvestor, investorKeys.all],
])('%s 성공 시 invalidation', (_name, useHook, key) => {
  it('관련 목록 쿼리를 무효화한다', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      key === investorKeys.all
        ? {
            id: 1,
            displayName: '테스트 투자자',
            relationship: null,
            accountId: 1,
            status: 'ACTIVE',
            deletedAt: null,
            createdAt: '2026-10-26T00:00:00',
            updatedAt: '2026-10-26T00:00:00',
          }
        : {
            id: 1,
            investorId: 1,
            securitiesFirmId: 1,
            securitiesFirmName: '테스트 증권사',
            accountNumber: '****1234',
            accountName: null,
            isDefault: true,
            status: 'ACTIVE',
            deletedAt: null,
            createdAt: '2026-10-26T00:00:00',
            updatedAt: '2026-10-26T00:00:00',
          },
    )
    const queryClient = new QueryClient()
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => useHook(), { wrapper })

    act(() => result.current.mutate({} as never))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy.mock.calls.map(([filters]) => filters?.queryKey)).toEqual([key])
  })
})

describe('브로커 동기화 확정 성공 시 invalidation', () => {
  it('미리보기와 투자 내역·수익·대시보드 쿼리를 무효화한다', async () => {
    const securitiesAccountId = 42
    const queryClient = new QueryClient()
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => useConfirmSync(securitiesAccountId), { wrapper })

    act(() => result.current.mutate([]))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy.mock.calls.map(([filters]) => filters?.queryKey)).toEqual([
      brokerKeys.preview(securitiesAccountId),
      ...EXPECTED_KEYS,
    ])
  })
})

describe('투자 기록 수정·삭제 성공 시 invalidation', () => {
  it('투자 내역·수익·대시보드 쿼리를 모두 무효화한다', async () => {
    const queryClient = new QueryClient()
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const mutationFn = vi.fn().mockResolvedValue(undefined)
    const { result } = renderHook(() => useRecordMutation(mutationFn), { wrapper })

    act(() => result.current.mutate({ id: 7 }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy.mock.calls.map(([filters]) => filters?.queryKey)).toEqual(EXPECTED_KEYS)
  })
})
