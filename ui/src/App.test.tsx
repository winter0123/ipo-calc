import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App.tsx'

vi.mock('./pages/LoginPage/LoginPage.tsx', () => ({ default: () => <div>LoginPage</div> }))
vi.mock('./pages/SignupPage/SignupPage.tsx', () => ({ default: () => <div>SignupPage</div> }))
vi.mock('./pages/InvestorRegisterPage/InvestorRegisterPage.tsx', () => ({
  default: () => <div>InvestorRegisterPage</div>,
}))
vi.mock('./pages/DashboardPage/DashboardPage.tsx', () => ({
  default: () => <div>DashboardPage</div>,
}))
vi.mock('./pages/SecuritiesAccountRegisterPage/SecuritiesAccountRegisterPage.tsx', () => ({
  default: () => <div>SecuritiesAccountRegisterPage</div>,
}))
vi.mock('./pages/SubscriptionRegisterPage/SubscriptionRegisterPage.tsx', () => ({
  default: () => <div>SubscriptionRegisterPage</div>,
}))
vi.mock('./pages/SubscriptionListPage/SubscriptionListPage.tsx', () => ({
  default: () => <div>SubscriptionListPage</div>,
}))
vi.mock('./pages/AllocationRegisterPage/AllocationRegisterPage.tsx', () => ({
  default: () => <div>AllocationRegisterPage</div>,
}))
vi.mock('./pages/SaleRegisterPage/SaleRegisterPage.tsx', () => ({
  default: () => <div>SaleRegisterPage</div>,
}))
vi.mock('./pages/CostRegisterPage/CostRegisterPage.tsx', () => ({
  default: () => <div>CostRegisterPage</div>,
}))
vi.mock('./pages/ProfitViewPage/ProfitViewPage.tsx', () => ({
  default: () => <div>ProfitViewPage</div>,
}))
vi.mock('./pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx', () => ({
  default: () => <div>InvestmentHistoryPage</div>,
}))
vi.mock('./pages/IpoCalendarPage/IpoCalendarPage.tsx', () => ({
  default: () => <div>IpoCalendarPage</div>,
}))

describe('App 랜드마크', () => {
  it.each(['/', '/login', '/dashboard', '/investments', '/profits/view', '/ipo-calendar'])(
    '%s 경로는 main 랜드마크를 정확히 1개 가진다',
    (path) => {
      render(
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>,
      )
      expect(screen.getAllByRole('main')).toHaveLength(1)
    },
  )
})
