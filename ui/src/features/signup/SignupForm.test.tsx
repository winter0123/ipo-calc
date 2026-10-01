import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SignupForm from './SignupForm.tsx'
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

function renderSignupForm() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SignupForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function fillAndSubmit(email: string, password: string, name: string) {
  if (email !== undefined) {
    await fill(screen.getByLabelText('이메일'), email)
  }
  if (password !== undefined) {
    await fill(screen.getByLabelText('비밀번호'), password)
  }
  if (name !== undefined) {
    await fill(screen.getByLabelText('이름'), name)
  }
  await userEvent.click(screen.getByRole('button', { name: /가입/i }))
}

describe('SignupForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('정상 제출 시 201 응답 후 /investors/register로 이동', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ accountId: 1, email: 'test@example.com' })
    renderSignupForm()

    await fillAndSubmit('test@example.com', 'passw0rd!', '홍길동')

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/accounts',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            email: 'test@example.com',
            password: 'passw0rd!',
            name: '홍길동',
          }),
        }),
      )
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/investors/register', expect.anything())
    })
  })

  it('409 중복 시 이메일 중복 메시지 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(409, 'Request failed: 409'))
    renderSignupForm()

    await fillAndSubmit('dup@example.com', 'passw0rd!', '홍길동')

    expect(await screen.findByText('이미 사용 중인 이메일입니다.')).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('백엔드 409 메시지("이미 사용 중인 이메일입니다")도 마침표가 붙은 메시지로 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(409, '이미 사용 중인 이메일입니다', '이미 사용 중인 이메일입니다'),
    )
    renderSignupForm()

    await fillAndSubmit('dup@example.com', 'passw0rd!', '홍길동')

    expect(await screen.findByText('이미 사용 중인 이메일입니다.')).toBeInTheDocument()
  })

  it('400 오류 시 API 메시지 매핑', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(400, '이름은 2자 이상이어야 합니다.', '이름은 2자 이상이어야 합니다.'),
    )
    renderSignupForm()

    await fillAndSubmit('bad@example.com', 'passw0rd!', '홍길동')

    await waitFor(() => {
      expect(screen.queryByText('이름은 2자 이상이어야 합니다.')).toBeInTheDocument()
    })
  })

  it('이메일 형식 오류 시 클라이언트 검증 메시지', async () => {
    renderSignupForm()

    await fillAndSubmit('not-email', 'passw0rd!', '홍길동')

    expect(await screen.findByText('올바른 이메일 형식을 입력해 주세요.')).toBeInTheDocument()
    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('비밀번호 조건 미충족 시 메시지', async () => {
    renderSignupForm()

    await fillAndSubmit('test@example.com', 'short', '홍길동')

    expect(await screen.findByText('비밀번호는 최소 8자 이상이어야 합니다.')).toBeInTheDocument()
    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('필수값 누락 시 검증 메시지', async () => {
    renderSignupForm()

    await fillAndSubmit('', '', '')

    const messages = await screen.findAllByText('필수 입력 항목입니다.')
    expect(messages.length).toBeGreaterThanOrEqual(1)
    expect(apiFetch).not.toHaveBeenCalled()
  })
})
