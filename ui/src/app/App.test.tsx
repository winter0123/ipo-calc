import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

// MainLayout이 현재 계정(/auth/me)을 조회한다. 이 테스트는 라우팅만 보므로 로그인 전(401)으로 둔다.
vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

vi.mock('../pages/LoginPage/LoginPage.tsx', () => ({ default: () => <div>LoginPage</div> }))
vi.mock('../pages/SignupPage/SignupPage.tsx', () => ({ default: () => <div>SignupPage</div> }))
vi.mock('../pages/InvestorRegisterPage/InvestorRegisterPage.tsx', () => ({
  default: () => <div>InvestorRegisterPage</div>,
}))
vi.mock('../pages/DashboardPage/DashboardPage.tsx', () => ({
  default: () => <div>DashboardPage</div>,
}))
vi.mock('../pages/SecuritiesAccountRegisterPage/SecuritiesAccountRegisterPage.tsx', () => ({
  default: () => <div>SecuritiesAccountRegisterPage</div>,
}))
vi.mock('../pages/SubscriptionRegisterPage/SubscriptionRegisterPage.tsx', () => ({
  default: () => <div>SubscriptionRegisterPage</div>,
}))
vi.mock('../pages/SubscriptionListPage/SubscriptionListPage.tsx', () => ({
  default: () => <div>SubscriptionListPage</div>,
}))
vi.mock('../pages/AllocationRegisterPage/AllocationRegisterPage.tsx', () => ({
  default: () => <div>AllocationRegisterPage</div>,
}))
vi.mock('../pages/SaleRegisterPage/SaleRegisterPage.tsx', () => ({
  default: () => <div>SaleRegisterPage</div>,
}))
vi.mock('../pages/ProfitViewPage/ProfitViewPage.tsx', () => ({
  default: () => <div>ProfitViewPage</div>,
}))
vi.mock('../pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx', () => ({
  default: () => <div>InvestmentHistoryPage</div>,
}))
vi.mock('../pages/IpoCalendarPage/IpoCalendarPage.tsx', () => ({
  default: () => <div>IpoCalendarPage</div>,
}))

const ROUTES: Array<[string, string]> = [
  ['/', 'IpoCalendarPage'],
  ['/login', 'LoginPage'],
  ['/signup', 'SignupPage'],
  ['/investors/register', 'InvestorRegisterPage'],
  ['/dashboard', 'DashboardPage'],
  ['/securities-accounts/register/1', 'SecuritiesAccountRegisterPage'],
  ['/subscriptions/register', 'SubscriptionRegisterPage'],
  ['/subscriptions', 'SubscriptionListPage'],
  ['/allocations/register', 'AllocationRegisterPage'],
  ['/sales/register', 'SaleRegisterPage'],
  ['/profits/view', 'ProfitViewPage'],
  ['/investments', 'InvestmentHistoryPage'],
  ['/ipo-calendar', 'IpoCalendarPage'],
]

function renderAt(path: string) {
  vi.mocked(apiFetch).mockRejectedValue(new ApiError(401, 'Unauthorized'))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('App 라우트', () => {
  it.each(ROUTES)('%s 경로는 %s를 렌더링한다', async (path, text) => {
    renderAt(path)
    expect(await screen.findByText(text)).toBeInTheDocument()
  })

  it('정의되지 않은 경로는 NotFound 화면을 MainLayout 안에 보여준다', async () => {
    renderAt('/no-such-route')
    expect(
      await screen.findByRole('heading', { name: '페이지를 찾을 수 없습니다' }),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('main')).toHaveLength(1)
  })
})

describe('App 랜드마크', () => {
  it.each(ROUTES.map(([path]) => path))(
    '%s 경로는 main 랜드마크를 정확히 1개 가진다',
    async (path) => {
      renderAt(path)
      expect(await screen.findAllByRole('main')).toHaveLength(1)
    },
  )
})
