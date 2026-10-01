import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MainLayout from './MainLayout.tsx'

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

  it('주요 메뉴에서 증권사 연동 화면으로 이동할 수 있음', () => {
    renderLayout()

    const nav = screen.getByRole('navigation', { name: '주요 메뉴' })
    expect(within(nav).getByRole('link', { name: '증권사 연동' })).toHaveAttribute(
      'href',
      '/broker-sync',
    )
    expect(screen.getByRole('banner')).toContainElement(nav)
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
