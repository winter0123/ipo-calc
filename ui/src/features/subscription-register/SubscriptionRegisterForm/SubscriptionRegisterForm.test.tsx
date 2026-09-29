import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SubscriptionRegisterForm from './SubscriptionRegisterForm.tsx'
import { apiFetch } from '../../../shared/api/client.ts'

vi.mock('../../../shared/api/client.ts', () => ({
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

const INVESTORS = [
  {
    id: 1,
    displayName: '홍길동',
    accountId: 1,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const SECURITIES_ACCOUNTS = [
  {
    id: 1,
    investorId: 1,
    securitiesFirmId: 1,
    securitiesFirmName: '미래에셋증권',
    accountNumber: '****-****-****-3456',
    accountName: '주력 계좌',
    isDefault: true,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const IPOS = [
  {
    id: 1,
    companyName: '테스트기업',
    ipoName: '테스트기업 공모주',
    stockCode: '123456',
    roundLabel: '본청약',
    lowerBandPrice: 10000,
    upperBandPrice: 12000,
    fixedPrice: 11000,
    subscriptionStartDate: '2026-09-01',
    subscriptionEndDate: '2026-09-02',
    listingDate: '2026-09-15',
    offeringShareCount: 1000000,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

function mockDropdownFetches() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return INVESTORS
    if (pathStr === '/securities-accounts' || pathStr.startsWith('/securities-accounts?'))
      return SECURITIES_ACCOUNTS
    if (pathStr === '/ipos') return IPOS
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

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
        <SubscriptionRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForDropdownsLoaded() {
  await waitFor(() => {
    expect(screen.getByRole('option', { name: '홍길동' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '테스트기업 공모주' })).toBeInTheDocument()
  })
}

function fillValidForm() {
  fireEvent.change(screen.getByLabelText('투자자'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('증권계좌'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('공모주'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('청약수량'), { target: { value: '100' } })
  fireEvent.change(screen.getByLabelText('증거금'), { target: { value: '0' } })
  fireEvent.change(screen.getByLabelText('청약일시'), { target: { value: '2026-09-21T10:00' } })
}

describe('SubscriptionRegisterForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('드롭다운 로딩 상태를 표시한 뒤 응답 수신 후 옵션을 채움', async () => {
    let resolveInvestors: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const pathStr = String(path)
      if (pathStr === '/investors') {
        return new Promise((resolve) => {
          resolveInvestors = resolve
        })
      }
      if (pathStr === '/securities-accounts' || pathStr.startsWith('/securities-accounts?'))
        return SECURITIES_ACCOUNTS
      if (pathStr === '/ipos') return IPOS
      throw new Error(`unexpected path: ${pathStr}`)
    })

    renderForm()

    expect(screen.getByText('목록을 불러오는 중...')).toBeInTheDocument()

    resolveInvestors(INVESTORS)

    await waitForDropdownsLoaded()
  })

  it('정상 등록 성공 시 /subscriptions로 이동', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: 1,
      investorId: 1,
      securitiesAccountId: 1,
      ipoId: 1,
      quantity: 100,
      depositAmount: 0,
      subscribedAt: '2026-09-21T10:00:00',
      memo: '',
      status: 'ACTIVE',
      createdAt: '2026-09-21T00:00:00Z',
      updatedAt: '2026-09-21T00:00:00Z',
    })

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/subscriptions',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    const call = vi.mocked(apiFetch).mock.calls.find(([path]) => path === '/subscriptions')!
    const body = JSON.parse((call[1] as RequestInit).body as string)
    expect(body.investorId).toBe(1)
    expect(body.securitiesAccountId).toBe(1)
    expect(body.ipoId).toBe(1)
    expect(body.quantity).toBe(100)
    expect(body.depositAmount).toBe(0)

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/subscriptions')
    })
  })

  it('필수값 미입력 시 클라이언트 사이드 검증 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('투자자를 선택해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('증권계좌를 선택해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('공모주를 선택해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('청약수량을 입력해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('증거금을 입력해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('청약일시를 선택해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('청약수량이 0 이하이면 에러 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    fillValidForm()
    fireEvent.change(screen.getByLabelText('청약수량'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('0 초과 정수를 입력해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalledWith(
      '/subscriptions',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('증거금이 음수이면 에러 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    fillValidForm()
    fireEvent.change(screen.getByLabelText('증거금'), { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('0 이상 정수를 입력해 주세요.')).toBeInTheDocument()
    })
    expect(apiFetch).not.toHaveBeenCalledWith(
      '/subscriptions',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('존재하지 않는 투자자로 등록 시 에러 메시지 표시 (404)', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    vi.mocked(apiFetch).mockRejectedValueOnce(
      new Error('Request failed: 404 - 존재하지 않는 투자자입니다'),
    )

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('투자자를 찾을 수 없습니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('존재하지 않는 공모주로 등록 시 에러 메시지 표시 (404)', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    vi.mocked(apiFetch).mockRejectedValueOnce(
      new Error('Request failed: 404 - 존재하지 않는 공모주입니다'),
    )

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('공모주를 찾을 수 없습니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('중복 청약으로 등록 시 에러 메시지 표시 (409)', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Conflict: 409'))

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('이미 동일한 조건의 청약이 존재합니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('400 응답 시 에러 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Request failed: 400'))

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('입력값을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Failed to fetch'))

    fillValidForm()
    fireEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
