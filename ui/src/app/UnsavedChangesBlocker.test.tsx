import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider, Link, Outlet } from 'react-router-dom'
import UnsavedChangesBlocker from './UnsavedChangesBlocker.tsx'
import UnsavedGuardProvider from '@/shared/navigation/UnsavedGuardProvider.tsx'
import { useRegisterUnsavedGuard } from '@/shared/navigation/unsavedGuard.ts'

function DirtyPage() {
  useRegisterUnsavedGuard('test-page', { dirty: true, pending: false })
  return (
    <div>
      <h1>Dirty Page</h1>
      <Link to="/other">다른 화면으로</Link>
      <Link to="/login">로그인으로</Link>
    </div>
  )
}

function PendingPage() {
  useRegisterUnsavedGuard('test-page', { dirty: false, pending: true })
  return (
    <div>
      <h1>Pending Page</h1>
      <Link to="/other">다른 화면으로</Link>
    </div>
  )
}

function CleanPage() {
  return (
    <div>
      <h1>Clean Page</h1>
      <Link to="/other">다른 화면으로</Link>
    </div>
  )
}

function renderAt(initialPath: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const router = createMemoryRouter(
    [
      {
        element: (
          <UnsavedGuardProvider>
            <QueryClientProvider client={queryClient}>
              <Outlet />
              <UnsavedChangesBlocker />
            </QueryClientProvider>
          </UnsavedGuardProvider>
        ),
        children: [
          { path: 'dirty', element: <DirtyPage /> },
          { path: 'pending', element: <PendingPage /> },
          { path: 'clean', element: <CleanPage /> },
          { path: 'other', element: <h1>Other Page</h1> },
          { path: 'login', element: <h1>Login Page</h1> },
        ],
      },
    ],
    { initialEntries: [initialPath] },
  )
  return render(<RouterProvider router={router} />)
}

describe('UnsavedChangesBlocker', () => {
  beforeEach(() => vi.clearAllMocks())

  it('dirty 상태에서 다른 경로로 이동하면 확인 대화상자가 뜨고 현재 위치에 머문다', async () => {
    renderAt('/dirty')

    await userEvent.click(await screen.findByRole('link', { name: '다른 화면으로' }))

    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByText('저장하지 않은 입력이 있습니다')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Dirty Page' })).toBeInTheDocument()

    // 계속 작성 — 이동하지 않고 대화상자만 닫는다
    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Dirty Page' })).toBeInTheDocument()
  })

  it('확인(이동하기)을 누르면 이동한다', async () => {
    renderAt('/dirty')

    await userEvent.click(await screen.findByRole('link', { name: '다른 화면으로' }))
    await userEvent.click(await screen.findByRole('button', { name: '이동하기' }))

    expect(await screen.findByRole('heading', { name: 'Other Page' })).toBeInTheDocument()
  })

  it('pending 상태에서는 이동을 막고 이동하기 버튼을 잠근다', async () => {
    renderAt('/pending')

    await userEvent.click(await screen.findByRole('link', { name: '다른 화면으로' }))

    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByText('저장이 진행 중입니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이동하기' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(screen.getByRole('heading', { name: 'Pending Page' })).toBeInTheDocument()
  })

  it('/login으로 가는 이동은 dirty여도 막지 않는다', async () => {
    renderAt('/dirty')

    await userEvent.click(await screen.findByRole('link', { name: '로그인으로' }))

    expect(await screen.findByRole('heading', { name: 'Login Page' })).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('dirty가 없으면 확인 없이 이동한다', async () => {
    renderAt('/clean')

    await userEvent.click(await screen.findByRole('link', { name: '다른 화면으로' }))

    expect(await screen.findByRole('heading', { name: 'Other Page' })).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('dirty 상태에서 새로고침(beforeunload)을 경고한다', async () => {
    renderAt('/dirty')
    await screen.findByRole('heading', { name: 'Dirty Page' })

    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })

  it('dirty가 없으면 beforeunload를 경고하지 않는다', async () => {
    renderAt('/clean')
    await screen.findByRole('heading', { name: 'Clean Page' })

    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
  })
})
