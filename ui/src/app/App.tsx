import { lazy } from 'react'
import { Routes, Route } from 'react-router-dom'
import MainLayout from './layouts/MainLayout/MainLayout.tsx'
import AdminLayout from './layouts/AdminLayout/AdminLayout.tsx'
import { GuestOnly } from './layouts/MainLayout/RouteGuards.tsx'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import RootRedirect from './layouts/MainLayout/RootRedirect.tsx'

const LandingPage = lazy(() => import('../pages/LandingPage/LandingPage.tsx'))
const LoginPage = lazy(() => import('../pages/LoginPage/LoginPage.tsx'))
const SignupPage = lazy(() => import('../pages/SignupPage/SignupPage.tsx'))
const InvestorRegisterPage = lazy(
  () => import('../pages/InvestorRegisterPage/InvestorRegisterPage.tsx'),
)
const DashboardPage = lazy(() => import('../pages/DashboardPage/DashboardPage.tsx'))
const RegisterRedirectPage = lazy(
  () => import('../pages/RegisterRedirectPage/RegisterRedirectPage.tsx'),
)
const SubscriptionListPage = lazy(
  () => import('../pages/SubscriptionListPage/SubscriptionListPage.tsx'),
)
const ProfitViewPage = lazy(() => import('../pages/ProfitViewPage/ProfitViewPage.tsx'))
const InvestmentHistoryPage = lazy(
  () => import('../pages/InvestmentHistoryPage/InvestmentHistoryPage.tsx'),
)
const BrokerSyncPage = lazy(() => import('../pages/BrokerSyncPage/BrokerSyncPage.tsx'))
const AdminAccountsPage = lazy(() => import('../pages/AdminAccountsPage/AdminAccountsPage.tsx'))
const SecuritiesPage = lazy(() => import('../pages/SecuritiesPage/SecuritiesPage.tsx'))
const NotificationsPage = lazy(() => import('../pages/NotificationsPage/NotificationsPage.tsx'))
const NotFoundPage = lazy(() => import('../pages/NotFoundPage/NotFoundPage.tsx'))
const IpoCalendarPage = lazy(() => import('../pages/IpoCalendarPage/IpoCalendarPage.tsx'))
const IpoDetailPage = lazy(() => import('../pages/IpoDetailPage/IpoDetailPage.tsx'))
const TradeManagePage = lazy(() => import('../pages/TradeManagePage/TradeManagePage.tsx'))
const IpoRegisterPage = lazy(() => import('../pages/IpoRegisterPage/IpoRegisterPage.tsx'))
const MyPage = lazy(() => import('../pages/MyPage/MyPage.tsx'))
const AdminAnnouncementsPage = lazy(
  () => import('../pages/AdminAnnouncementsPage/AdminAnnouncementsPage.tsx'),
)
const AnnouncementsPage = lazy(() => import('../pages/AnnouncementsPage/AnnouncementsPage.tsx'))
const AnnouncementDetailPage = lazy(
  () => import('../pages/AnnouncementDetailPage/AnnouncementDetailPage.tsx'),
)
const AdminUsersPage = lazy(() => import('../pages/AdminUsersPage/AdminUsersPage.tsx'))
const AdminUserDetailPage = lazy(
  () => import('../pages/AdminUserDetailPage/AdminUserDetailPage.tsx'),
)

function AboutRoute() {
  const { data: currentAccount } = useCurrentAccount()
  return <LandingPage loggedIn={Boolean(currentAccount)} />
}

function App() {
  return (
    <Routes>
      <Route path="admin" element={<AdminLayout />}>
        <Route index element={<AdminUsersPage />} />
        <Route path="users" element={<AdminUsersPage />} />
        <Route path="users/:accountId" element={<AdminUserDetailPage />} />
        <Route path="accounts" element={<AdminAccountsPage />} />
        <Route path="announcements" element={<AdminAnnouncementsPage />} />
        <Route path="ipos" element={<IpoRegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route element={<MainLayout />}>
        <Route path="/" element={<RootRedirect />} />
        <Route path="/about" element={<AboutRoute />} />
        <Route
          path="/login"
          element={
            <GuestOnly>
              <LoginPage />
            </GuestOnly>
          }
        />
        <Route
          path="/signup"
          element={
            <GuestOnly>
              <SignupPage />
            </GuestOnly>
          }
        />
        <Route path="/investors/register" element={<InvestorRegisterPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/my-page" element={<MyPage />} />
        <Route path="/securities" element={<SecuritiesPage />} />
        {/* 예전 등록 링크 — 문맥(ipoId/subscriptionId)을 확인해 통합 관리 화면으로 replace 이동한다 */}
        <Route path="/subscriptions/register" element={<RegisterRedirectPage />} />
        <Route path="/subscriptions" element={<SubscriptionListPage />} />
        <Route path="/allocations/register" element={<RegisterRedirectPage />} />
        <Route path="/sales/register" element={<RegisterRedirectPage />} />
        <Route path="/profits/view" element={<ProfitViewPage />} />
        <Route path="/investments" element={<InvestmentHistoryPage />} />
        <Route path="/ipo-calendar" element={<IpoCalendarPage />} />
        <Route path="/ipos/:ipoId" element={<IpoDetailPage />} />
        {/* 청약·매도 통합 관리 화면. 공개 상세(/ipos/:ipoId)의 보호는 바꾸지 않는다 — 관리 경로만 투자자 게이트 대상이다. */}
        <Route path="/ipos/:ipoId/manage" element={<TradeManagePage />} />
        <Route path="/broker-sync" element={<BrokerSyncPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/announcements" element={<AnnouncementsPage />} />
        <Route path="/announcements/:announcementId" element={<AnnouncementDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
