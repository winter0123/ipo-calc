import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InvestorRegisterForm from './InvestorRegisterForm.tsx'
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

function renderInvestorRegisterForm() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <InvestorRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function fillAndSubmit(displayName: string, relationship?: string) {
  if (displayName !== undefined) {
    await fill(screen.getByLabelText('표시 이름'), displayName)
  }
  if (relationship !== undefined) {
    await fill(screen.getByLabelText('관계'), relationship)
  }
  await userEvent.click(screen.getByRole('button', { name: /등록/i }))
}

describe('InvestorRegisterForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('정상 제출 시 201 응답 후 /dashboard로 이동', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: 1,
      displayName: '홍길동',
      accountId: 100,
      status: 'ACTIVE',
      deletedAt: null,
      createdAt: '2026-09-21T00:00:00Z',
      updatedAt: '2026-09-21T00:00:00Z',
    })
    renderInvestorRegisterForm()

    await fillAndSubmit('홍길동', 'SELF')

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/investors',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ displayName: '홍길동', relationship: 'SELF' }),
        }),
      )
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
    })
  })

  it('연인과 친구 관계 선택지를 제공하고 친구 값을 전송한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: 1,
      displayName: '친구투자자',
      accountId: 100,
      status: 'ACTIVE',
      deletedAt: null,
      createdAt: '2026-09-21T00:00:00Z',
      updatedAt: '2026-09-21T00:00:00Z',
    })
    renderInvestorRegisterForm()

    const relationship = screen.getByLabelText('관계')
    expect(screen.getByRole('option', { name: '연인' })).toHaveValue('PARTNER')
    expect(screen.getByRole('option', { name: '친구' })).toHaveValue('FRIEND')
    expect(screen.queryByRole('option', { name: '배우자' })).not.toBeInTheDocument()
    await fill(screen.getByLabelText('표시 이름'), '친구투자자')
    await fill(relationship, 'FRIEND')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith(
        '/investors',
        expect.objectContaining({
          body: JSON.stringify({ displayName: '친구투자자', relationship: 'FRIEND' }),
        }),
      ),
    )
  })

  it('빈 displayName 제출 시 필수 검증 메시지 표시 (클라이언트 사이드)', async () => {
    // 클라이언트 사이드 검증으로 API 호출 안 함 → 모킹 불필요
    renderInvestorRegisterForm()

    await fillAndSubmit('')

    await waitFor(() => {
      expect(screen.queryByText('표시 이름은 필수입니다.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalled()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('50자 초과 displayName 제출 시 검증 메시지 표시 (클라이언트 사이드)', async () => {
    // 클라이언트 사이드 검증으로 API 호출 안 함 → 모킹 불필요
    renderInvestorRegisterForm()

    const longName = '가'.repeat(51)
    await fillAndSubmit(longName)

    await waitFor(() => {
      expect(screen.queryByText('표시 이름은 50자 이하여야 합니다.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalled()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('유효하지 않은 relationship로 API 400 응답 시 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(400, '유효한 관계를 선택해 주세요.', '유효한 관계를 선택해 주세요.'),
    )
    renderInvestorRegisterForm()

    await fillAndSubmit('홍길동', 'SELF')

    await waitFor(() => {
      expect(screen.queryByText('유효한 관계를 선택해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Network error'))
    renderInvestorRegisterForm()

    await fillAndSubmit('홍길동', 'SELF')

    await waitFor(() => {
      expect(screen.queryByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('relationship 미선택 시 빈 값으로 전송', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: 2,
      displayName: '김철수',
      accountId: 100,
      status: 'ACTIVE',
      deletedAt: null,
      createdAt: '2026-09-21T00:00:00Z',
      updatedAt: '2026-09-21T00:00:00Z',
    })
    renderInvestorRegisterForm()

    await fillAndSubmit('김철수')

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/investors',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ displayName: '김철수', relationship: '' }),
        }),
      )
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
    })
  })

  it('로딩 상태에서 제출 버튼 비활성화', async () => {
    let resolveFn: (value: unknown) => void
    vi.mocked(apiFetch).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveFn = resolve
        }),
    )
    renderInvestorRegisterForm()

    await fillAndSubmit('홍길동', 'SELF')

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /등록 중/i })).toBeDisabled()
    })

    resolveFn!({
      id: 3,
      displayName: '홍길동',
      accountId: 100,
      status: 'ACTIVE',
      deletedAt: null,
      createdAt: '2026-09-21T00:00:00Z',
      updatedAt: '2026-09-21T00:00:00Z',
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
    })
  })
})
