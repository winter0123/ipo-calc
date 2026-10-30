import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import AdminUserDetail from '@/features/admin-users/AdminUserDetail.tsx'
import { useCurrentAccount } from '@/entities/current-account/index.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
  csrfBootstrap: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/entities/current-account/index.ts', () => ({
  useCurrentAccount: vi.fn(),
}))

const detailResponse = {
  id: 7,
  email: 'admin@target.com',
  name: '타겟',
  nickname: '타겟닉',
  role: 'USER',
  approved: true,
  locked: false,
  lockedUntil: null,
  lastLoginAt: '2026-10-30T09:00:00',
  createdAt: '2026-10-01T00:00:00',
  investors: [
    { id: 71, displayName: '자녀', relationship: 'CHILD', status: 'ACTIVE' },
    { id: 72, displayName: '배우자', relationship: 'PARTNER', status: 'ACTIVE' },
  ],
  stats: { subscriptionCount: 5, allocationCount: 2, saleCount: 1, totalProfit: 123000 },
}

const startResponse = {
  sessionId: 'sess-1',
  expiresAt: '2026-10-30T10:30:00',
  readOnly: true,
}

function setup(response: unknown | Error, path = '/admin/users/7') {
  vi.mocked(useCurrentAccount).mockReturnValue({
    data: { accountId: 1, email: 'admin@b.com', name: '관리자', nickname: '관리자', role: 'ADMIN' },
    isPending: false,
  } as any)
  vi.mocked(apiFetch).mockImplementation(async (p: unknown) => {
    if (response instanceof Error) throw response
    if (String(p) === '/admin/users/7') return response
    if (String(p) === '/admin/users/7/investors')
      return [{ id: 1, displayName: '자녀', relationship: 'CHILD', status: 'ACTIVE' }]
    if (String(p) === '/admin/impersonation/start/7') return startResponse
    return {}
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="admin/users/:accountId" element={<AdminUserDetail />} />
          <Route path="/dashboard" element={<div data-testid="dashboard">대시보드</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AdminUserDetail', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
    localStorage.clear()
  })

  it('기본정보 탭에 회원 정보를 보여준다', async () => {
    setup(detailResponse)
    expect(await screen.findByText('admin@target.com')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '타겟닉' })).toBeInTheDocument()
    expect(screen.getByText('USER')).toBeInTheDocument()
    expect(screen.getByText('승인됨')).toBeInTheDocument()
    await waitFor(() => expect(apiFetch).toHaveBeenCalledWith('/admin/users/7', expect.anything()))
  })

  it('실제통계 탭에 통계를 보여준다', async () => {
    setup(detailResponse)
    await screen.findByText('admin@target.com')
    await userEvent.click(screen.getByRole('button', { name: '실제통계' }))
    expect(await screen.findByText('123,000원')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('내역 탭에 투자자 목록을 보여준다', async () => {
    setup(detailResponse)
    await screen.findByText('admin@target.com')
    await userEvent.click(screen.getByRole('button', { name: '내역' }))
    expect(await screen.findByText('자녀')).toBeInTheDocument()
    expect(screen.getByText('CHILD')).toBeInTheDocument()
  })

  it('존재하지 않는 회원은 404 안내를 보여준다', async () => {
    setup(new ApiError(404, 'Not Found'))
    expect(await screen.findByRole('alert')).toHaveTextContent('회원을 찾을 수 없습니다.')
  })

  it('관리자는 임퍼소네이션 시작 버튼을 보고 확인 후 시작하며 대시보드로 이동한다', async () => {
    setup(detailResponse)
    await screen.findByText('admin@target.com')
    await userEvent.click(screen.getByRole('button', { name: /임퍼소네이션 시작/ }))
    expect(await screen.findByRole('button', { name: '시작' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '시작' }))
    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        '/admin/impersonation/start/7',
        expect.objectContaining({ method: 'POST' }),
      ),
    )
    expect(await screen.findByTestId('dashboard')).toBeInTheDocument()
  })
})
