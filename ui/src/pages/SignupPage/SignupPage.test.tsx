import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import SignupPage from './SignupPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

function renderSignupPage() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/signup']}>
        <SignupPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function fillAndSubmit() {
  await fill(screen.getByLabelText('이메일'), 'test@example.com')
  await fill(screen.getByLabelText('비밀번호'), 'passw0rd!')
  await fill(screen.getByLabelText('이름'), '홍길동')
  await fill(screen.getByLabelText('표시 이름'), '홍길동')
  await userEvent.click(screen.getByRole('button', { name: /가입/i }))
}

describe('SignupPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('회원가입 폼이 렌더링됨', () => {
    renderSignupPage()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('회원가입')
    expect(screen.getByRole('button', { name: /가입/i })).toBeInTheDocument()
  })

  it('로그인 링크가 있고 /login으로 이동함', async () => {
    renderSignupPage()

    const loginLink = screen.getByRole('link', { name: '로그인' })
    expect(loginLink).toHaveAttribute('href', '/login')
    expect(loginLink).toHaveTextContent('로그인')
  })

  it('이미 계정이 있으신가요? 문구가 표시됨', () => {
    renderSignupPage()
    expect(screen.getByText('이미 계정이 있으신가요?')).toBeInTheDocument()
  })

  it('승인 대기 안내 상태에서는 "이미 계정이 있으신가요?" 문구가 없음', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ accountId: 1, email: 'a@b.com', approved: false })
    renderSignupPage()

    await fillAndSubmit()

    await waitFor(() => expect(screen.getByRole('status')).toBeInTheDocument())
    expect(screen.queryByText('이미 계정이 있으신가요?')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '로그인 화면으로' })).toHaveAttribute('href', '/login')
  })
})
