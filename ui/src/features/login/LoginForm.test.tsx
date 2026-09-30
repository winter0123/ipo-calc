import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LoginForm from './LoginForm.tsx'
import { apiFetch } from '../../shared/api/client.ts'
import { ApiError } from '../../shared/api/apiError.ts'

vi.mock('../../shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

function renderLoginForm() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LoginForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function fillAndSubmit(email: string, password: string) {
  if (email) {
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: email } })
  }
  if (password) {
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: password } })
  }
  fireEvent.click(screen.getByRole('button', { name: /로그인/i }))
}

describe('LoginForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('정상 제출 시 API 호출 확인 및 성공 시 /dashboard로 이동', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({})
    renderLoginForm()

    fillAndSubmit('test@example.com', 'password123')

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/auth/login',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ email: 'test@example.com', password: 'password123' }),
        }),
      )
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
    })
  })

  it('401 시 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(401, 'Unauthorized: 401'))
    renderLoginForm()

    fillAndSubmit('test@example.com', 'password123')

    expect(await screen.findByText('이메일 또는 비밀번호가 올바르지 않습니다.')).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('이메일 형식 오류 시 클라이언트 검증 메시지', async () => {
    renderLoginForm()

    fillAndSubmit('not-an-email', 'password123')

    expect(await screen.findByText('올바른 이메일 형식을 입력해 주세요.')).toBeInTheDocument()
    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('필수값 누락 시 검증 메시지', async () => {
    renderLoginForm()

    fillAndSubmit('', '')

    const requiredMessages = await screen.findAllByText('필수 입력 항목입니다.')
    expect(requiredMessages).toHaveLength(2)
    expect(apiFetch).not.toHaveBeenCalled()
  })
})
