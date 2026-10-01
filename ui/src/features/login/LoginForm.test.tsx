import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LoginForm from './LoginForm.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({
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

function renderLoginForm(from?: unknown) {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={[{ pathname: '/login', state: from === undefined ? null : { from } }]}
      >
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

  it('세션 만료로 넘어온 경우 로그인 성공 후 원래 경로로 복귀한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({})
    renderLoginForm('/investments?x=1')
    fillAndSubmit('test@example.com', 'password123')
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/investments?x=1'))
  })

  it.each(['https://evil.example', '//evil.example', 42])(
    '앱 내부 경로가 아닌 복귀 경로(%s)는 무시하고 /dashboard로 이동한다',
    async (from) => {
      vi.mocked(apiFetch).mockResolvedValueOnce({})
      renderLoginForm(from)
      fillAndSubmit('test@example.com', 'password123')
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dashboard'))
    },
  )

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

  it('서버 오류는 필드의 aria-invalid로 표시하지 않고 폼 수준 role=alert로 알린다', async () => {
    const user = userEvent.setup()
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(401, 'Unauthorized: 401'))
    renderLoginForm()

    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이메일 또는 비밀번호가 올바르지 않습니다.',
    )
    expect(screen.getByLabelText('이메일')).toHaveAttribute('aria-invalid', 'false')
    expect(screen.getByLabelText('이메일')).not.toHaveAttribute('aria-describedby')
  })

  it('필드 검증 오류가 있을 때만 aria-invalid와 aria-describedby가 연결된다', async () => {
    const user = userEvent.setup()
    renderLoginForm()

    await user.type(screen.getByLabelText('이메일'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    const email = screen.getByLabelText('이메일')
    expect(email).toHaveAttribute('aria-invalid', 'true')
    expect(email).toHaveAccessibleDescription('올바른 이메일 형식을 입력해 주세요.')
  })

  it('401이 아닌 오류는 네트워크 안내 문구를 보여준다', async () => {
    const user = userEvent.setup()
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(500, 'Server Error'))
    renderLoginForm()

    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('네트워크 연결을 확인해 주세요.')
  })
})
