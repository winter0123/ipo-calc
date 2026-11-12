import { useState, type ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { Outlet, createBrowserRouter, RouterProvider, useNavigate } from 'react-router-dom'
import ErrorBoundary from './ErrorBoundary.tsx'
import { isPublicPath } from './publicPaths.ts'
import { createQueryClient } from './queryClient.ts'
import UnsavedChangesBlocker from './UnsavedChangesBlocker.tsx'
import UnsavedGuardProvider from '@/shared/navigation/UnsavedGuardProvider.tsx'

// 세션 만료(401) 시 캐시를 비우고 로그인 화면으로 보낸다. 복귀 경로는 location state로 전달한다.
// navigate는 절대 경로('/login')로만 호출하므로 첫 렌더의 함수를 계속 써도 안전하고,
// 현재 위치는 window.location에서 읽는다.
function QueryProviderLayout() {
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

  return (
    <QueryClientProvider client={queryClient}>
      <Outlet />
      <UnsavedChangesBlocker />
    </QueryClientProvider>
  )
}

// 최소 data-router 구성으로 전환한다.
// 이유: react-router-dom 7.18.4의 useBlocker(미저장 입력 이동 차단에 필요)는
// data router(DataRouterContext) 전용이라 기존 BrowserRouter에서는 동작하지 않는다.
// 기존 route 의미(/, admin, MainLayout·Outlet, lazy 페이지, QueryProvider, ErrorBoundary,
// 401 중앙 처리와 state.from 복귀)는 그대로 유지하고, 라우터 종류만 바꾼다.
export default function AppProviders({ children }: { children: ReactNode }) {
  const [router] = useState(() =>
    createBrowserRouter([
      {
        element: (
          <UnsavedGuardProvider>
            <QueryProviderLayout />
          </UnsavedGuardProvider>
        ),
        children: [{ path: '*', element: children }],
      },
    ]),
  )

  return (
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  )
}
