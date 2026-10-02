import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useMarketHolidays } from './useMarketHolidays.ts'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useMarketHolidays', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('year로 쿼리 키를 만들고 데이터를 가져온다', async () => {
    const holidays = [
      { date: '2026-01-01', name: '신정' },
      { date: '2026-10-09', name: '한글날' },
    ]
    vi.mocked(apiFetch).mockResolvedValueOnce(holidays)

    const { result } = renderHook(() => useMarketHolidays(2026), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(holidays)
  })

  it('응답이 빈 배열이면 빈 배열을 반환한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])

    const { result } = renderHook(() => useMarketHolidays(2026), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it('네트워크 오류 시 isError가 true다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Failed to fetch'))

    const { result } = renderHook(() => useMarketHolidays(2026), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('다른 year로 호출하면 다른 쿼리 키를 사용한다', async () => {
    vi.mocked(apiFetch).mockResolvedValue([])

    renderHook(() => useMarketHolidays(2026), { wrapper: createWrapper() })
    renderHook(() => useMarketHolidays(2027), { wrapper: createWrapper() })

    expect(apiFetch).toHaveBeenCalledTimes(2)
    expect(apiFetch).toHaveBeenNthCalledWith(1, '/market-holidays?year=2026', expect.any(Object))
    expect(apiFetch).toHaveBeenNthCalledWith(2, '/market-holidays?year=2027', expect.any(Object))
  })
})
