import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useImpersonation } from '@/entities/impersonation/index.ts'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import ImpersonationBanner from '@/widgets/banner/ImpersonationBanner.tsx'

vi.mock('@/entities/impersonation/index.ts', () => ({
  useImpersonation: vi.fn(),
  impersonationKeys: { status: () => ['impersonation', 'status'] },
}))
vi.mock('@/entities/current-account/index.ts', () => ({
  useCurrentAccount: vi.fn(),
}))

function setup(impersonating: boolean) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const end = vi.fn()
  vi.mocked(useImpersonation).mockReturnValue({
    isAdmin: true,
    isImpersonating: impersonating,
    targetAccountId: 7,
    expiresAt: '2026-10-30T10:30:00',
    end,
    isPending: false,
    endError: null,
  } as unknown as ReturnType<typeof useImpersonation>)
  vi.mocked(useCurrentAccount).mockReturnValue({
    data: { accountId: 1, email: 'a@b.com', name: '나', nickname: '나', role: 'ADMIN' },
    isPending: false,
  } as unknown as ReturnType<typeof useCurrentAccount>)
  render(
    <QueryClientProvider client={queryClient}>
      <ImpersonationBanner />
    </QueryClientProvider>,
  )
  return { end, queryClient }
}

describe('ImpersonationBanner', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
  })

  it('임퍼소네이션 중이면 대상·읽기전용·만료와 종료 버튼을 보여준다', async () => {
    const { end } = setup(true)
    expect(await screen.findByText('임퍼소네이션 중')).toBeInTheDocument()
    expect(screen.getByText('대상 계정: 7')).toBeInTheDocument()
    expect(screen.getByText('읽기 전용')).toBeInTheDocument()
    expect(screen.getByText('만료: 2026-10-30')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '임퍼소네이션 종료' }))
    expect(end).toHaveBeenCalled()
  })

  it('임퍼소네이션 중이 아니면 렌더링되지 않는다', () => {
    setup(false)
    expect(screen.queryByText('임퍼소네이션 중')).toBeNull()
  })

  it('storage 이벤트 key=impersonation-changed 시 캐시를 비우고 status query를 invalidate한다', () => {
    const { queryClient } = setup(true)
    const cancelSpy = vi.spyOn(queryClient, 'cancelQueries')
    const clearSpy = vi.spyOn(queryClient, 'clear')
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    window.dispatchEvent(new StorageEvent('storage', { key: 'impersonation-changed' }))
    expect(cancelSpy).toHaveBeenCalled()
    expect(clearSpy).toHaveBeenCalled()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['impersonation', 'status'] })
  })

  it('관련 없는 storage key 이벤트는 캐시를 비우지 않는다', () => {
    const { queryClient } = setup(true)
    const cancelSpy = vi.spyOn(queryClient, 'cancelQueries')
    const clearSpy = vi.spyOn(queryClient, 'clear')
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    window.dispatchEvent(new StorageEvent('storage', { key: 'other-key' }))
    expect(cancelSpy).not.toHaveBeenCalled()
    expect(clearSpy).not.toHaveBeenCalled()
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
