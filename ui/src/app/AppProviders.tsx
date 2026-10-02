import { useState, type ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, useNavigate } from 'react-router-dom'
import ErrorBoundary from './ErrorBoundary.tsx'
import { isPublicPath } from './publicPaths.ts'
import { createQueryClient } from './queryClient.ts'

// 세션 만료(401) 시 캐시를 비우고 로그인 화면으로 보낸다. 복귀 경로는 location state로 전달한다.
// navigate는 절대 경로('/login')로만 호출하므로 첫 렌더의 함수를 계속 써도 안전하고,
// 현재 위치는 BrowserRouter가 따르는 window.location에서 읽는다.
function QueryProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [queryClient] = useState(() =>
    createQueryClient({
      onUnauthorized: (client) => {
        const { pathname, search } = window.location
        if (isPublicPath(pathname)) return
        client.clear()
        navigate('/login', { replace: true, state: { from: `${pathname}${search}` } })
      },
    }),
  )

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

export default function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <QueryProvider>{children}</QueryProvider>
      </BrowserRouter>
    </ErrorBoundary>
  )
}
