import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminLayout from './AdminLayout.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { apiGetParsed } from '@/shared/api/parsed.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn(), csrfBootstrap: vi.fn() }))
vi.mock('@/shared/api/parsed.ts', () => ({ apiGetParsed: vi.fn() }))

function DashboardPage() {
  return <div data-testid="dashboard-page">Dashboard</div>
}

function renderLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/users']}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="users" element={<div data-testid="admin-users">users</div>} />
          </Route>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AdminLayout', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(apiFetch).mockResolvedValue(undefined as never)
    vi.mocked(apiGetParsed).mockImplementation(async (path: unknown) => {
      if (String(path) === '/auth/me') {
        return { accountId: 1, email: 'a@b.com', name: '관리자', nickname: '관리자', role: 'ADMIN' }
      }
      if (String(path) === '/admin/impersonation/status') {
        return { impersonating: true, targetAccountId: 2, expiresAt: null }
      }
      return {}
    })
  })

  it('임퍼소네이션 중 /admin/users 진입은 /dashboard로 replace redirect된다', async () => {
    renderLayout()
    await waitFor(() => expect(screen.getByTestId('dashboard-page')).toBeInTheDocument())
    expect(screen.queryByTestId('admin-users')).not.toBeInTheDocument()
  })

  it('임퍼소네이션 중 관리자 전용 fetch/API 요청은 발생하지 않는다', async () => {
    renderLayout()
    await waitFor(() => expect(screen.getByTestId('dashboard-page')).toBeInTheDocument())
    const calls = vi.mocked(apiGetParsed).mock.calls.map((c) => String(c[0]))
    expect(
      calls.filter((p) => p.startsWith('/admin/') && p !== '/admin/impersonation/status'),
    ).toHaveLength(0)
    const fetchCalls = vi.mocked(apiFetch).mock.calls.map((c) => String(c[0]))
    expect(fetchCalls.filter((p) => p.startsWith('/admin/'))).toHaveLength(0)
  })

  it('임퍼소네이션 false인 관리자는 /admin/users를 정상 노출한다', async () => {
    vi.mocked(apiGetParsed).mockImplementation(async (path: unknown) => {
      if (String(path) === '/auth/me') {
        return { accountId: 1, email: 'a@b.com', name: '관리자', nickname: '관리자', role: 'ADMIN' }
      }
      if (String(path) === '/admin/impersonation/status') {
        return { impersonating: false, targetAccountId: null, expiresAt: null }
      }
      return {}
    })
    renderLayout()
    await waitFor(() => expect(screen.getByTestId('admin-users')).toBeInTheDocument())
  })
})
