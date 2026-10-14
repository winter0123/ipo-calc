import { lazy } from 'react'
import { Navigate } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'

const LandingPage = lazy(() => import('../../../pages/LandingPage/LandingPage.tsx'))

// 로그인 사용자는 대시보드로, 비로그인 사용자는 서비스 소개 페이지를 보여 준다
export default function RootRedirect() {
  const { data: currentAccount, isPending } = useCurrentAccount()
  if (isPending) return <p role="status">불러오는 중...</p>
  return currentAccount ? <Navigate to="/dashboard" replace /> : <LandingPage />
}
