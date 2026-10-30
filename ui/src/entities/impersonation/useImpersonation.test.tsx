import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch, csrfBootstrap } from '@/shared/api/client.ts'
import { useImpersonation } from '@/entities/impersonation/index.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
  csrfBootstrap: vi.fn(),
}))

const startResponse = {
  sessionId: 'sess-1',
  expiresAt: '2026-10-30T10:30:00',
  readOnly: true,
}

function renderProbe(isAdmin: boolean) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const cancelQueries = vi.spyOn(queryClient, 'cancelQueries')
  const clear = vi.spyOn(queryClient, 'clear')

  let captured: ReturnType<typeof useImpersonation> | null = null
  function Probe() {
    captured = useImpersonation({ isAdmin })
    return null
  }

  render(
    <QueryClientProvider client={queryClient}>
      <Probe />
    </QueryClientProvider>,
  )

  return {
    cancelQueries,
    clear,
    get captured() {
      return captured!
    },
  }
}

describe('useImpersonation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
    document.cookie = 'XSRF-TOKEN=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('상태 조회는 ADMIN일 때만 수행한다', () => {
    vi.mocked(apiFetch).mockResolvedValue({} as never)
    renderProbe(true)
    expect(vi.mocked(apiFetch)).toHaveBeenCalledWith(
      '/admin/impersonation/status',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('일반 사용자는 상태 조회를 하지 않는다', () => {
    vi.mocked(apiFetch).mockResolvedValue({} as never)
    renderProbe(false)
    expect(vi.mocked(apiFetch)).not.toHaveBeenCalledWith(
      '/admin/impersonation/status',
      expect.anything(),
    )
  })

  it('시작 시 csrfBootstrap과 시작 API를 호출하며 캐시와 저장소를 초기화한다', async () => {
    vi.mocked(apiFetch).mockResolvedValue(startResponse as never)
    vi.mocked(csrfBootstrap).mockResolvedValue()
    const { cancelQueries, clear, captured } = renderProbe(true)
    await act(async () => {
      await captured.start(7)
    })
    expect(csrfBootstrap).toHaveBeenCalled()
    expect(apiFetch).toHaveBeenCalledWith('/admin/impersonation/start/7', { method: 'POST' })
    expect(cancelQueries).toHaveBeenCalled()
    expect(clear).toHaveBeenCalled()
    expect(localStorage.getItem('currentInvestorId')).toBeNull()
  })

  it('종료 시 캐시와 저장소를 초기화한다', async () => {
    vi.mocked(apiFetch).mockResolvedValue(undefined as never)
    vi.mocked(csrfBootstrap).mockResolvedValue()
    const { cancelQueries, clear, captured } = renderProbe(true)
    await act(async () => {
      await captured.end()
    })
    expect(apiFetch).toHaveBeenCalledWith('/admin/impersonation/end', { method: 'POST' })
    expect(cancelQueries).toHaveBeenCalled()
    expect(clear).toHaveBeenCalled()
    expect(localStorage.getItem('currentInvestorId')).toBeNull()
  })
})
