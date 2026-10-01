import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useIpoRegister } from './useIpoRegister.ts'
import { ipoKeys } from '@/entities/ipo/index.ts'

vi.mock('@/entities/ipo/index.ts', () => ({
  registerIpo: vi.fn().mockResolvedValue({
    id: 1,
    companyName: '테스트',
    ipoName: '테스트 IPO',
    stockCode: null,
    roundLabel: null,
    lowerBandPrice: null,
    upperBandPrice: null,
    fixedPrice: null,
    subscriptionStartDate: '2026-02-01',
    subscriptionEndDate: '2026-02-02',
    listingDate: null,
    offeringShareCount: null,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }),
  ipoKeys: { all: ['ipos'] },
}))

describe('useIpoRegister', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    vi.clearAllMocks()
  })

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )

  it('성공 시 ipoKeys를 무효화한다', async () => {
    const { registerIpo } = await import('@/entities/ipo/index.ts')
    vi.mocked(registerIpo).mockResolvedValue({
      id: 1,
      companyName: '테스트',
      ipoName: '테스트 IPO',
      stockCode: null,
      roundLabel: null,
      lowerBandPrice: null,
      upperBandPrice: null,
      fixedPrice: null,
      subscriptionStartDate: '2026-02-01',
      subscriptionEndDate: '2026-02-02',
      listingDate: null,
      offeringShareCount: null,
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    })

    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    const { result } = renderHook(() => useIpoRegister(), { wrapper })

    act(() =>
      result.current.mutate({
        companyName: '테스트',
        ipoName: '테스트 IPO',
        subscriptionStartDate: '2026-02-01',
        subscriptionEndDate: '2026-02-02',
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(registerIpo).toHaveBeenCalled()
    expect(spy).toHaveBeenCalledWith({ queryKey: ipoKeys.all })
  })
})
