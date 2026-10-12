import { Navigate } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'

export default function RootRedirect() {
  const { data: currentAccount, isPending } = useCurrentAccount()
  if (isPending) return <p role="status">불러오는 중...</p>
  return <Navigate to={currentAccount ? '/dashboard' : '/ipo-calendar'} replace />
}
