import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useIpo } from './useIpo.ts'
import type { Ipo } from './types.ts'

const makeIpo = (overrides: Partial<Ipo> = {}): Ipo => ({
  id: 1,
  companyName: '테스트기업',
  ipoName: '테스트IPO',
  stockCode: '123456',
  roundLabel: '1',
  lowerBandPrice: 10000,
  upperBandPrice: 15000,
  fixedPrice: 0,
  subscriptionStartDate: '2026-10-01T09:00:00Z',
  subscriptionEndDate: '2026-10-02T17:00:00Z',
  listingDate: '2026-10-15T09:00:00Z',
  offeringShareCount: 1000000,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
  ...overrides,
})

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    {children}
  </QueryClientProvider>
)

const stubFetch = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))

describe('useIpo', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('id가 주어지면 조회하고 데이터를 반환한다', async () => {
    const ipo = makeIpo({ id: 7 })
    stubFetch(ipo)

    const { result } = renderHook(() => useIpo(7), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(ipo)
  })

  it('id가 undefined이면 조회하지 않고 enabled가 false다', () => {
    const { result } = renderHook(() => useIpo(undefined), { wrapper })

    expect(result.current.isLoading).toBe(false)
    expect(result.current.isSuccess).toBe(false)
    expect(result.current.data).toBeUndefined()
  })

  it('id가 NaN이면 조회하지 않고 enabled가 false다', () => {
    const { result } = renderHook(() => useIpo(NaN), { wrapper })

    expect(result.current.isLoading).toBe(false)
    expect(result.current.isSuccess).toBe(false)
    expect(result.current.data).toBeUndefined()
  })
})
