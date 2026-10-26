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

async function fillAndSubmit(email: string, password: string, name: string, nickname = '닉네임') {
  if (email !== undefined) {
    await fill(screen.getByLabelText('이메일'), email)
  }
  if (password !== undefined) {
    await fill(screen.getByLabelText('비밀번호'), password)
  }
  if (name !== undefined) {
    await fill(screen.getByLabelText('이름'), name)
  }
  if (nickname) {
    await fill(screen.getByLabelText('닉네임'), nickname)
  }
  await userEvent.click(screen.getByRole('button', { name: /가입/i }))
}

describe('SignupForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('이름과 닉네임을 각각 받고 투자자 관계는 본인으로 고정한다', () => {
    renderSignupForm()

    expect(screen.getByRole('option', { name: '본인' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: '배우자' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('이름')).toBeInTheDocument()
    expect(screen.getByLabelText('닉네임')).toBeInTheDocument()
    expect(screen.queryByLabelText('표시 이름')).not.toBeInTheDocument()
  })

  it('비공개 운영에서 승인 대기 응답이면 이동하지 않고 안내를 보여준다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      accountId: 1,
      email: 'a@b.com',
      name: '홍길동',
      nickname: '길동',
      approved: false,
    })
    renderSignupForm()

    await fillAndSubmit('test@example.com', 'passw0rd!', '홍길동')

    expect(await screen.findByRole('status')).toHaveTextContent(
      '관리자가 승인하면 로그인할 수 있습니다',
    )
    expect(screen.getByRole('link', { name: '로그인 화면으로' })).toHaveAttribute('href', '/login')
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('가입이 닫혀 있으면(403) 서버 안내를 보여준다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(403, 'Forbidden', '지금은 가입을 받지 않습니다'),
    )
    renderSignupForm()

    await fillAndSubmit('test@example.com', 'passw0rd!', '홍길동')

    expect(await screen.findByRole('alert')).toHaveTextContent('지금은 가입을 받지 않습니다')
  })

  it('정상 제출 시 201 응답 후 /dashboard로 이동', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      accountId: 1,
      email: 'test@example.com',
      name: '홍길동',
      nickname: '길동',
      approved: true,
    })
    renderSignupForm()

    await fillAndSubmit('test@example.com', 'passw0rd!', '홍길동', '길동')

    await waitFor(() => expect(apiFetch).toHaveBeenCalled())
    const [url, options] = vi.mocked(apiFetch).mock.calls[0]
    expect(url).toBe('/accounts')
    expect(options).toMatchObject({ method: 'POST' })
    expect(JSON.parse(String(options?.body))).toEqual({
      relationship: 'SELF',
      email: 'test@example.com',
      password: 'passw0rd!',
      name: '홍길동',
      nickname: '길동',
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
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

  it.each([
    ['abcdefg1', '특수문자'],
    ['abcdefg!', '숫자'],
    ['1234567!', '영문'],
    ['aa1!', '8자 이상'],
  ])('비밀번호 %s는 부족한 조건(%s)을 알려 준다', async (pw, missing) => {
    renderSignupForm()
    await fillAndSubmit('test@example.com', pw, '홍길동')
    expect(await screen.findByText(new RegExp(missing))).toBeInTheDocument()
    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('UTF-8 72바이트를 넘으면 거부한다', async () => {
    renderSignupForm()
    await fillAndSubmit(
      'test@example.com',
      '가나다라마바사아자차카타파하가나다라마바사아자차카타1!a',
      '홍길동',
    )
    expect(await screen.findByText(/72바이트/)).toBeInTheDocument()
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
