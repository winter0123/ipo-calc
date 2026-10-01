import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LoginPage from './LoginPage.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/login']}>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('로그인 폼이 렌더링됨', () => {
    renderLoginPage()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('로그인')
    expect(screen.getByRole('button', { name: /로그인/i })).toBeInTheDocument()
  })

  it('가입하기 링크가 있고 /signup으로 이동함', async () => {
    renderLoginPage()

    const signupLink = screen.getByRole('link', { name: '가입하기' })
    expect(signupLink).toHaveAttribute('href', '/signup')
    expect(signupLink).toHaveTextContent('가입하기')
  })

  it('계정이 없으신가요? 문구가 표시됨', () => {
    renderLoginPage()
    expect(screen.getByText('계정이 없으신가요?')).toBeInTheDocument()
  })
})
