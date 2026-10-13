import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'

// 관리자 전용 화면: 일반 사용자가 주소로 직접 와도 화면 대신 안내만 보여 준다(실제 권한은 서버가 검사한다).
export function AdminOnly({ children }: { children: ReactNode }) {
  const { data: account, isPending } = useCurrentAccount()
  if (isPending) return <p role="status">불러오는 중...</p>
  if (account?.role !== 'ADMIN') return <p role="alert">관리자만 볼 수 있는 화면입니다.</p>
  return children
}

// 로그인 전용 화면(/login, /signup): 이미 로그인했으면 대시보드로 보낸다.
export function GuestOnly({ children }: { children: ReactNode }) {
  const { data: account, isPending } = useCurrentAccount()
  if (isPending) return <p role="status">불러오는 중...</p>
  if (account) return <Navigate to="/dashboard" replace />
  return children
}
