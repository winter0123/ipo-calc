import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useLogout } from './useLogout.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

describe('useLogout', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    vi.clearAllMocks()
  })

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )

  it('로그아웃 성공 시 /auth/logout을 POST로 호출하고 캐시가 비워진다', async () => {
    const { apiFetch } = await import('@/shared/api/client.ts')
    vi.mocked(apiFetch).mockResolvedValue(undefined)

    const spy = vi.spyOn(queryClient, 'clear')
    const { result } = renderHook(() => useLogout(), { wrapper })

    act(() => result.current.mutate())

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiFetch).toHaveBeenCalledWith('/auth/logout', { method: 'POST' })
    expect(spy).toHaveBeenCalled()
  })

  it('로그아웃 실패(reject)해도 캐시가 비워진다', async () => {
    const { apiFetch } = await import('@/shared/api/client.ts')
    vi.mocked(apiFetch).mockRejectedValue(new Error('Network error'))

    const spy = vi.spyOn(queryClient, 'clear')
    const { result } = renderHook(() => useLogout(), { wrapper })

    act(() => result.current.mutate())

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(apiFetch).toHaveBeenCalledWith('/auth/logout', { method: 'POST' })
    expect(spy).toHaveBeenCalled()
  })
})
