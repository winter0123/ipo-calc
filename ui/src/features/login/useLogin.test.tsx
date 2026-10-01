import { describe, it, expect, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useLogin } from './useLogin.ts'
import { currentAccountKeys } from '@/entities/current-account/index.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn().mockResolvedValue(undefined) }))

describe('useLogin', () => {
  it('로그인에 성공하면 현재 계정 정보를 다시 받도록 무효화한다', async () => {
    const queryClient = new QueryClient()
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => useLogin(), { wrapper })

    act(() => result.current.mutate({ email: 'a@b.com', password: 'passw0rd!' }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy).toHaveBeenCalledWith({ queryKey: currentAccountKeys.all })
  })
})
