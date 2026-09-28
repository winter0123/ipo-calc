import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage/LoginPage.tsx'
import SignupPage from './pages/SignupPage/SignupPage.tsx'
import InvestorRegisterPage from './pages/InvestorRegisterPage/InvestorRegisterPage.tsx'
import DashboardPage from './pages/DashboardPage/DashboardPage.tsx'
import SecuritiesAccountRegisterPage from './pages/SecuritiesAccountRegisterPage/SecuritiesAccountRegisterPage.tsx'
import SubscriptionRegisterPage from './pages/SubscriptionRegisterPage/SubscriptionRegisterPage.tsx'
import SubscriptionListPage from './pages/SubscriptionListPage/SubscriptionListPage.tsx'
import AllocationRegisterPage from './pages/AllocationRegisterPage/AllocationRegisterPage.tsx'
import SaleRegisterPage from './pages/SaleRegisterPage/SaleRegisterPage.tsx'

function App() {
  return (
    <Routes>
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
    </Routes>
  )
}

export default App
