import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import MainLayout from './MainLayout.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const anonymous = () => vi.mocked(apiFetch).mockRejectedValue(new ApiError(401, 'Unauthorized'))
const loggedInAs = (role: 'USER' | 'ADMIN', investors: object[] = []) =>
  vi
    .mocked(apiFetch)
    .mockImplementation(async (path: unknown) =>
      String(path).startsWith('/investors')
        ? investors
        : { accountId: 1, email: 'a@b.com', name: '나', nickname: '나', role },
    )

function TestChild() {
  return <div data-testid="child-content">Child Page</div>
}

function LoginPage() {
  return <div data-testid="login-page">Login Page</div>
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
          <Route path="login" element={<LoginPage />} />
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

  it('설정에는 마이페이지·로그아웃·알림이 있고 계좌등록·증권사 연동은 없다', async () => {
    loggedInAs('USER')
    renderLayout()

    const nav = await screen.findByRole('navigation', { name: '주요 메뉴' })
    await userEvent.click(within(nav).getByRole('button', { name: '설정' }))
    expect(within(nav).getByRole('link', { name: '마이페이지' })).toHaveAttribute(
      'href',
      '/my-page',
    )
    expect(within(nav).getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: '알림 설정' })).toHaveAttribute(
      'href',
      '/notifications',
    )
    expect(within(nav).queryByRole('link', { name: '투자자 추가' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: '증권사 계좌 등록' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: '증권사 연동' })).not.toBeInTheDocument()
    expect(screen.getByRole('banner')).toContainElement(nav)
  })

  it('조회 중에는 주요 메뉴를 보이지 않음', () => {
    vi.mocked(apiFetch).mockReturnValue(new Promise(() => undefined))
    renderLayout()

    expect(screen.queryByRole('navigation', { name: '주요 메뉴' })).not.toBeInTheDocument()
  })

  it('로그인 전 메뉴에는 홈·공모주 일정만 있고 로그인·가입하기는 헤더 우측 버튼임', async () => {
    renderLayout()

    const nav = await screen.findByRole('navigation', { name: '주요 메뉴' })
    expect(
      within(nav)
        .getAllByRole('link')
        .map((l) => l.textContent),
    ).toEqual(['홈'])
    expect(within(nav).getByRole('link', { name: '홈' })).toHaveAttribute('href', '/')
    expect(within(nav).queryByRole('link', { name: '공모주 일정' })).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('banner')).getByRole('link', { name: '공모주 일정' }),
    ).toHaveAttribute('href', '/ipo-calendar')
    expect(screen.getByRole('link', { name: '홈으로' })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('button', { name: '메뉴' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: '대시보드' })).not.toBeInTheDocument()

    const header = screen.getByRole('banner')
    const login = within(header).getByRole('link', { name: '로그인' })
    const signup = within(header).getByRole('link', { name: '가입하기' })
    expect(login).toHaveAttribute('href', '/login')
    expect(signup).toHaveAttribute('href', '/signup')
    expect(nav).not.toContainElement(login)
    expect(nav).not.toContainElement(signup)
  })

  it('관리자에게만 가입 승인 링크가 보임', async () => {
    loggedInAs('ADMIN')
    renderLayout()

    await userEvent.click(await screen.findByRole('button', { name: '관리자' }))
    expect(await screen.findByRole('link', { name: '가입 승인' })).toHaveAttribute(
      'href',
      '/admin/accounts',
    )
    expect(await screen.findByRole('link', { name: '공모주 등록' })).toHaveAttribute(
      'href',
      '/admin/ipos',
    )
  })

  it('일반 계정과 로그인 전에는 가입 승인 링크가 없음', async () => {
    loggedInAs('USER')
    const { unmount } = renderLayout()
    await screen.findByRole('button', { name: '설정' })
    await vi.waitFor(() => expect(apiFetch).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: '가입 승인' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '공모주 등록' })).not.toBeInTheDocument()
    unmount()

    anonymous()
    renderLayout()
    await vi.waitFor(() => expect(vi.mocked(apiFetch).mock.calls.length).toBeGreaterThanOrEqual(2))
    expect(screen.queryByRole('link', { name: '가입 승인' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '공모주 등록' })).not.toBeInTheDocument()
  })

  describe('드롭다운 메뉴', () => {
    it('로그인 메뉴의 대분류는 공모주 청약·매매기록·설정이며 관리자는 일반 계정에 없음', async () => {
      loggedInAs('USER')
      renderLayout()

      const nav = await screen.findByRole('navigation', { name: '주요 메뉴' })
      await screen.findByRole('button', { name: '설정' })
      expect(
        within(nav)
          .getAllByRole('button')
          .map((button) => button.textContent?.trim()),
      ).toEqual(['공모주 청약', '매매기록', '설정'])
      expect(within(nav).queryByRole('button', { name: '관리자' })).not.toBeInTheDocument()
    })

    it('공모주 청약 그룹은 청약 목록만 접고 펼쳐 표시함', async () => {
      loggedInAs('USER')
      renderLayout()

      const nav = await screen.findByRole('navigation', { name: '주요 메뉴' })
      const calendarLink = within(nav).getByRole('link', { name: '공모주 일정' })
      expect(nav.firstElementChild).toBe(calendarLink)
      expect(calendarLink.closest('[role="group"]')).toBeNull()

      const groupButton = await screen.findByRole('button', { name: '공모주 청약' })
      expect(groupButton).toHaveAttribute('aria-expanded', 'false')
      await userEvent.click(groupButton)
      expect(groupButton).toHaveAttribute('aria-expanded', 'true')
      expect(screen.getByRole('link', { name: '청약 목록' })).toHaveAttribute(
        'href',
        '/subscriptions',
      )
      expect(screen.queryByRole('link', { name: '공모주 배정' })).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: '공모주 매도' })).not.toBeInTheDocument()
      await userEvent.click(groupButton)
      expect(groupButton).toHaveAttribute('aria-expanded', 'false')
    })

    it('매매기록 그룹은 청약내역과 실현손익으로 표시함', async () => {
      loggedInAs('USER')
      renderLayout()

      await userEvent.click(await screen.findByRole('button', { name: '매매기록' }))
      expect(screen.getByRole('link', { name: '청약내역' })).toHaveAttribute('href', '/investments')
      expect(screen.getByRole('link', { name: '실현손익' })).toHaveAttribute(
        'href',
        '/profits/view',
      )
    })

    it('Esc로 닫히고 버튼으로 포커스가 돌아감', async () => {
      loggedInAs('USER')
      renderLayout()

      const button = await screen.findByRole('button', { name: '설정' })
      await userEvent.click(button)
      expect(button).toHaveAttribute('aria-expanded', 'true')
      await userEvent.keyboard('{Escape}')
      expect(button).toHaveAttribute('aria-expanded', 'false')
      expect(button).toHaveFocus()
    })

    it('바깥을 클릭하면 닫힘', async () => {
      loggedInAs('USER')
      renderLayout()

      const button = await screen.findByRole('button', { name: '설정' })
      await userEvent.click(button)
      await userEvent.click(screen.getByTestId('child-content'))
      expect(button).toHaveAttribute('aria-expanded', 'false')
    })

    it('화살표 키로 열고 항목 사이를 이동함', async () => {
      loggedInAs('USER')
      renderLayout()

      const button = await screen.findByRole('button', { name: '설정' })
      button.focus()
      await userEvent.keyboard('{ArrowDown}')
      await waitFor(() => expect(screen.getByRole('link', { name: '마이페이지' })).toHaveFocus())
      await userEvent.keyboard('{ArrowDown}')
      await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toHaveFocus())
      await userEvent.keyboard('{ArrowDown}')
      expect(screen.getByRole('link', { name: '알림 설정' })).toHaveFocus()
    })

    it('하위 페이지에 있으면 부모 버튼도 현재 위치로 표시됨', async () => {
      loggedInAs('USER')
      const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/notifications']}>
            <Routes>
              <Route element={<MainLayout />}>
                <Route path="notifications" element={<TestChild />} />
              </Route>
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      )

      const button = await screen.findByRole('button', { name: '설정' })
      expect(button.className).toMatch(/active/)
      expect(screen.getByRole('button', { name: '매매기록' }).className).not.toMatch(/active/)
    })
  })

  it('스킵 링크가 존재하고 href="#main-content"를 가리킴', () => {
    renderLayout()

    const skipLink = screen.getByRole('link', { name: '본문으로 건너뛰기' })
    expect(skipLink).toBeInTheDocument()
    expect(skipLink).toHaveAttribute('href', '#main-content')
  })

  it('헤더에 사이트 이름이 표시됨', () => {
    renderLayout()

    expect(screen.getByText('공모주계산기')).toBeInTheDocument()
  })

  describe('로그아웃 버튼', () => {
    it('로그인한 계정에는 로그아웃 버튼이 보임', async () => {
      loggedInAs('USER')
      renderLayout()

      await userEvent.click(await screen.findByRole('button', { name: '설정' }))
      const logoutButton = await screen.findByRole('button', { name: '로그아웃' })
      expect(logoutButton).toBeInTheDocument()
    })

    it('로그인하지 않은 상태에서는 로그아웃 버튼이 없음', async () => {
      renderLayout()

      await vi.waitFor(() => expect(apiFetch).toHaveBeenCalled())
      expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument()
    })

    it('로그아웃 버튼을 클릭하면 /auth/logout을 호출하고 /login으로 이동함', async () => {
      const { apiFetch } = await import('@/shared/api/client.ts')
      vi.mocked(apiFetch)
        .mockResolvedValueOnce({
          accountId: 1,
          email: 'a@b.com',
          name: '나',
          nickname: '나',
          role: 'USER',
        }) // /auth/me
        .mockResolvedValueOnce(undefined) // /auth/logout
      const { unmount } = renderLayout()

      await userEvent.click(await screen.findByRole('button', { name: '설정' }))
      const logoutButton = await screen.findByRole('button', { name: '로그아웃' })
      await userEvent.click(logoutButton)

      await waitFor(() => expect(apiFetch).toHaveBeenCalledWith('/auth/logout', { method: 'POST' }))
      await waitFor(() => expect(screen.getByTestId('login-page')).toBeInTheDocument())
      unmount()
    })

    it('로그아웃 실패해도 /login으로 이동함', async () => {
      const { apiFetch } = await import('@/shared/api/client.ts')
      vi.mocked(apiFetch)
        .mockResolvedValueOnce({
          accountId: 1,
          email: 'a@b.com',
          name: '나',
          nickname: '나',
          role: 'USER',
        }) // /auth/me
        .mockRejectedValueOnce(new Error('Network error')) // /auth/logout
      const { unmount } = renderLayout()

      await userEvent.click(await screen.findByRole('button', { name: '설정' }))
      const logoutButton = await screen.findByRole('button', { name: '로그아웃' })
      await userEvent.click(logoutButton)

      await waitFor(() => expect(apiFetch).toHaveBeenCalledWith('/auth/logout', { method: 'POST' }))
      await waitFor(() => expect(screen.getByTestId('login-page')).toBeInTheDocument())
      unmount()
    })
  })
})
