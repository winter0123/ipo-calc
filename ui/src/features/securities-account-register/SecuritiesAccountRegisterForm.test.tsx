import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SecuritiesAccountRegisterForm from './SecuritiesAccountRegisterForm.tsx'
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
    useParams: () => ({ investorId: '42' }),
  }
})

const FIRMS = [
  {
    id: 1,
    name: '미래에셋증권',
    externalCode: 'MS',
    businessRegistrationNumber: '111-11-11111',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SecuritiesAccountRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForFirmsLoaded() {
  await waitFor(() => {
    expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument()
  })
}

describe('SecuritiesAccountRegisterForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('증권사 목록 로딩 상태를 표시한 뒤 응답 수신 후 옵션을 채움', async () => {
    let resolveFirms: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveFirms = resolve
        }),
    )
    renderForm()

    expect(screen.getByText('증권사 목록을 불러오는 중...')).toBeInTheDocument()

    resolveFirms(FIRMS)

    await waitForFirmsLoaded()
  })

  it('정상 등록 성공 시 마스킹된 계좌번호를 표시하고 /dashboard로 이동', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: 1,
      investorId: 42,
      securitiesFirmId: 1,
      securitiesFirmName: '미래에셋증권',
      accountNumber: '****-****-****-3456',
      accountName: '주력 계좌',
      isDefault: false,
      status: 'ACTIVE',
      deletedAt: null,
      createdAt: '2026-09-21T00:00:00Z',
      updatedAt: '2026-09-21T00:00:00Z',
    })
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})

    renderForm()
    await waitForFirmsLoaded()

    await fill(screen.getByLabelText('증권사'), '1')
    await fill(screen.getByLabelText('계좌번호'), '1234567890123456')
    await fill(screen.getByLabelText('계좌명 (선택)'), '주력 계좌')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/securities-accounts',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    const call = vi.mocked(apiFetch).mock.calls.find(([path]) => path === '/securities-accounts')!
    const body = JSON.parse((call[1] as RequestInit).body as string)
    expect(body.accountNumber).toBe('1234-5678-9012-3456')
    expect(body.securitiesFirmId).toBe(1)
    expect(body.investorId).toBe(42)

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith(expect.stringContaining('****-****-****-3456'))
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
    })

    alertSpy.mockRestore()
  })

  it('계좌번호 입력 시 4자리마다 하이픈이 자동 삽입됨', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    renderForm()
    await waitForFirmsLoaded()

    const input = screen.getByLabelText('계좌번호') as HTMLInputElement
    await fill(input, '123456789')

    expect(input.value).toBe('1234-5678-9')

    await fill(input, '1234567890123456')
    expect(input.value).toBe('1234-5678-9012-3456')
  })

  it('존재하지 않는 투자자로 등록 시 에러 메시지 표시 (404)', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(404, '존재하지 않는 투자자입니다', '존재하지 않는 투자자입니다'),
    )

    renderForm()
    await waitForFirmsLoaded()

    await fill(screen.getByLabelText('증권사'), '1')
    await fill(screen.getByLabelText('계좌번호'), '1234567890123456')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('투자자 정보를 찾을 수 없습니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalledWith('/dashboard')
  })

  it('백엔드 400 "존재하지 않는 투자자입니다"도 투자자 없음 메시지로 표시', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(400, '존재하지 않는 투자자입니다', '존재하지 않는 투자자입니다'),
    )

    renderForm()
    await waitForFirmsLoaded()

    await fill(screen.getByLabelText('증권사'), '1')
    await fill(screen.getByLabelText('계좌번호'), '1234567890123456')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    expect(await screen.findByText('투자자 정보를 찾을 수 없습니다.')).toBeInTheDocument()
  })

  it('존재하지 않는 증권사로 등록 시 에러 메시지 표시 (404)', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(404, '존재하지 않는 증권사입니다', '존재하지 않는 증권사입니다'),
    )

    renderForm()
    await waitForFirmsLoaded()

    await fill(screen.getByLabelText('증권사'), '1')
    await fill(screen.getByLabelText('계좌번호'), '1234567890123456')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('선택한 증권사를 찾을 수 없습니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalledWith('/dashboard')
  })

  it('400 응답 시 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(400, 'Request failed: 400'))

    renderForm()
    await waitForFirmsLoaded()

    await fill(screen.getByLabelText('증권사'), '1')
    await fill(screen.getByLabelText('계좌번호'), '1234567890123456')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('입력값을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalledWith('/dashboard')
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Failed to fetch'))

    renderForm()
    await waitForFirmsLoaded()

    await fill(screen.getByLabelText('증권사'), '1')
    await fill(screen.getByLabelText('계좌번호'), '1234567890123456')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalledWith('/dashboard')
  })

  it('필수값 미입력 시 클라이언트 사이드 검증 메시지 표시', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(FIRMS)
    renderForm()
    await waitForFirmsLoaded()

    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('증권사를 선택해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('계좌번호를 입력해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).toHaveBeenCalledTimes(1)
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
