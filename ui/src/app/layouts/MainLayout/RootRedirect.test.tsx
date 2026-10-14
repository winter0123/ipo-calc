import { Suspense } from 'react'
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
          <Route
            path="/"
            element={
              <Suspense fallback={null}>
                <RootRedirect />
              </Suspense>
            }
          />
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

  it('로그인 전이면 주소는 그대로 두고 서비스 소개 페이지를 보여줌', async () => {
    vi.mocked(apiFetch).mockRejectedValue(new ApiError(401, 'Unauthorized'))
    renderRoot()
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      '공모주 수익, 수수료와 세금까지 빼고 계산합니다',
    )
    expect(screen.queryByText('일정 화면')).not.toBeInTheDocument()
    // 소개 페이지가 계정 조회를 다시 구독해 재조회가 반복되지 않는다
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(apiFetch).toHaveBeenCalledTimes(1)
  })
})
