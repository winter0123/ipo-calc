import { lazy } from 'react'
import { Routes, Route } from 'react-router-dom'
import MainLayout from './layouts/MainLayout/MainLayout.tsx'

const LoginPage = lazy(() => import('../pages/LoginPage/LoginPage.tsx'))
const SignupPage = lazy(() => import('../pages/SignupPage/SignupPage.tsx'))
const InvestorRegisterPage = lazy(
  () => import('../pages/InvestorRegisterPage/InvestorRegisterPage.tsx'),
)
const DashboardPage = lazy(() => import('../pages/DashboardPage/DashboardPage.tsx'))
const SecuritiesAccountRegisterPage = lazy(
  () => import('../pages/SecuritiesAccountRegisterPage/SecuritiesAccountRegisterPage.tsx'),
)
const SubscriptionRegisterPage = lazy(
  () => import('../pages/SubscriptionRegisterPage/SubscriptionRegisterPage.tsx'),
)
const SubscriptionListPage = lazy(
  () => import('../pages/SubscriptionListPage/SubscriptionListPage.tsx'),
)
const AllocationRegisterPage = lazy(
  () => import('../pages/AllocationRegisterPage/AllocationRegisterPage.tsx'),
)
const SaleRegisterPage = lazy(() => import('../pages/SaleRegisterPage/SaleRegisterPage.tsx'))
const CostRegisterPage = lazy(() => import('../pages/CostRegisterPage/CostRegisterPage.tsx'))
const ProfitViewPage = lazy(() => import('../pages/ProfitViewPage/ProfitViewPage.tsx'))
const InvestmentHistoryPage = lazy(
  () => import('../pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx'),
)
const BrokerSyncPage = lazy(() => import('../pages/BrokerSyncPage/BrokerSyncPage.tsx'))
const AdminAccountsPage = lazy(() => import('../pages/AdminAccountsPage/AdminAccountsPage.tsx'))
const NotFoundPage = lazy(() => import('../pages/NotFoundPage/NotFoundPage.tsx'))
const IpoCalendarPage = lazy(() => import('../pages/IpoCalendarPage/IpoCalendarPage.tsx'))
const IpoDetailPage = lazy(() => import('../pages/IpoDetailPage/IpoDetailPage.tsx'))
const IpoRegisterPage = lazy(() => import('../pages/IpoRegisterPage/IpoRegisterPage.tsx'))

function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<div>Hello</div>} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/investors/register" element={<InvestorRegisterPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route
          path="/securities-accounts/register/:investorId"
          element={<SecuritiesAccountRegisterPage />}
        />
        <Route path="/subscriptions/register" element={<SubscriptionRegisterPage />} />
        <Route path="/subscriptions" element={<SubscriptionListPage />} />
        <Route path="/allocations/register" element={<AllocationRegisterPage />} />
        <Route path="/sales/register" element={<SaleRegisterPage />} />
        <Route path="/costs/register" element={<CostRegisterPage />} />
        <Route path="/profits/view" element={<ProfitViewPage />} />
        <Route path="/investments" element={<InvestmentHistoryPage />} />
        <Route path="/ipo-calendar" element={<IpoCalendarPage />} />
        <Route path="/ipos/:ipoId" element={<IpoDetailPage />} />
        <Route path="/broker-sync" element={<BrokerSyncPage />} />
        <Route path="/admin/accounts" element={<AdminAccountsPage />} />
        <Route path="/admin/ipos" element={<IpoRegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
