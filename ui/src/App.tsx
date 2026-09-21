import { Routes, Route } from 'react-router-dom'
import LoginPage from './app/pages/LoginPage/LoginPage.tsx'
import SignupPage from './app/pages/SignupPage/SignupPage.tsx'
import InvestorRegisterPage from './app/pages/InvestorRegisterPage/InvestorRegisterPage.tsx'
import DashboardPage from './app/pages/DashboardPage/DashboardPage.tsx'
import SecuritiesAccountRegisterPage from './app/pages/SecuritiesAccountRegisterPage/SecuritiesAccountRegisterPage.tsx'

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
    </Routes>
  )
}

export default App
