import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MainLayout from './MainLayout.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const anonymous = () => vi.mocked(apiFetch).mockRejectedValue(new ApiError(401, 'Unauthorized'))
const loggedInAs = (role: 'USER' | 'ADMIN') =>
  vi.mocked(apiFetch).mockResolvedValue({ accountId: 1, email: 'a@b.com', name: '나', role })

function TestChild() {
  return <div data-testid="child-content">Child Page</div>
}

function renderLayout() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/test']}>
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="test" element={<TestChild />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('MainLayout', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    anonymous()
  })

  it('랜드마크가 정확히 하나씩만 존재함: main 1개, banner 1개', () => {
    renderLayout()

    const mains = screen.getAllByRole('main')
    const banners = screen.getAllByRole('banner')

    expect(mains).toHaveLength(1)
    expect(banners).toHaveLength(1)
  })

  it('메인 콘텐츠에 id="main-content"가 있음', () => {
    renderLayout()

    const main = screen.getByRole('main')
    expect(main).toHaveAttribute('id', 'main-content')
  })

  it('자식 라우트(Outlet)의 내용이 main 안에 렌더링됨', () => {
    renderLayout()

    const main = screen.getByRole('main')
    expect(screen.getByTestId('child-content')).toBeInTheDocument()
    expect(main).toContainElement(screen.getByTestId('child-content'))
  })

  it('로그인한 계정에는 주요 메뉴에서 증권사 연동 화면으로 이동할 수 있음', async () => {
    loggedInAs('USER')
    renderLayout()

    const nav = await screen.findByRole('navigation', { name: '주요 메뉴' })
    expect(within(nav).getByRole('link', { name: '증권사 연동' })).toHaveAttribute(
      'href',
      '/broker-sync',
    )
    expect(screen.getByRole('banner')).toContainElement(nav)
  })

  it('로그인 전에는 주요 메뉴를 보이지 않음', async () => {
    renderLayout()

    await vi.waitFor(() => expect(apiFetch).toHaveBeenCalled())
    expect(screen.queryByRole('navigation', { name: '주요 메뉴' })).not.toBeInTheDocument()
  })

  it('관리자에게만 가입 승인 링크가 보임', async () => {
    loggedInAs('ADMIN')
    renderLayout()

    expect(await screen.findByRole('link', { name: '가입 승인' })).toHaveAttribute(
      'href',
      '/admin/accounts',
    )
  })

  it('일반 계정과 로그인 전에는 가입 승인 링크가 없음', async () => {
    loggedInAs('USER')
    const { unmount } = renderLayout()
    await screen.findByRole('link', { name: '증권사 연동' })
    await vi.waitFor(() => expect(apiFetch).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: '가입 승인' })).not.toBeInTheDocument()
    unmount()

    anonymous()
    renderLayout()
    await vi.waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('link', { name: '가입 승인' })).not.toBeInTheDocument()
  })

  it('스킵 링크가 존재하고 href="#main-content"를 가리킴', () => {
    renderLayout()

    const skipLink = screen.getByRole('link', { name: '본문으로 건너뛰기' })
    expect(skipLink).toBeInTheDocument()
    expect(skipLink).toHaveAttribute('href', '#main-content')
  })

  it('헤더에 사이트 이름이 표시됨', () => {
    renderLayout()

    expect(screen.getByText('투자 관리 시스템')).toBeInTheDocument()
  })
})
