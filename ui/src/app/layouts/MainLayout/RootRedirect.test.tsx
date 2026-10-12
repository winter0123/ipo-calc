import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RootRedirect from './RootRedirect.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

function renderRoot() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/dashboard" element={<div>대시보드 화면</div>} />
          <Route path="/ipo-calendar" element={<div>일정 화면</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RootRedirect', () => {
  beforeEach(() => vi.resetAllMocks())

  it('로그인 상태면 대시보드로 이동', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      accountId: 1,
      email: 'a@b.com',
      name: '나',
      role: 'USER',
    })
    renderRoot()
    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument()
  })

  it('로그인 전이면 공모주 일정으로 이동', async () => {
    vi.mocked(apiFetch).mockRejectedValue(new ApiError(401, 'Unauthorized'))
    renderRoot()
    expect(await screen.findByText('일정 화면')).toBeInTheDocument()
  })
})
