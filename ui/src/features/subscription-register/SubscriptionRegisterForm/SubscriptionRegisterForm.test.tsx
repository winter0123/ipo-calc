import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SubscriptionRegisterForm from './SubscriptionRegisterForm.tsx'
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
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    investorId: 1,
    securitiesFirmId: 2,
    securitiesFirmName: 'KB증권',
    accountNumber: '****-****-****-1111',
    accountName: '보조 계좌',
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 3,
    investorId: 1,
    securitiesFirmId: 3,
    securitiesFirmName: '하나은행',
    accountNumber: '****-****-****-5555',
    accountName: '은행 계좌',
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
  {
    id: 2,
    companyName: '희망가기업',
    ipoName: '희망가만 공모주',
    stockCode: '654321',
    roundLabel: '본청약',
    lowerBandPrice: 9000,
    upperBandPrice: 13000,
    fixedPrice: null,
    subscriptionStartDate: '2026-09-03',
    subscriptionEndDate: '2026-09-04',
    listingDate: '2026-09-18',
    offeringShareCount: 500000,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const IPOS_WITH_UNDERWRITERS = [
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
  {
    id: 2,
    companyName: '희망가기업',
    ipoName: '희망가만 공모주',
    stockCode: '654321',
    roundLabel: '본청약',
    lowerBandPrice: 9000,
    upperBandPrice: 13000,
    fixedPrice: null,
    subscriptionStartDate: '2026-09-03',
    subscriptionEndDate: '2026-09-04',
    listingDate: '2026-09-18',
    offeringShareCount: 500000,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

function mockDropdownFetches() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return INVESTORS
    if (pathStr === '/securities-accounts?investorId=1') return SECURITIES_ACCOUNTS
    if (pathStr === '/ipos/1') return IPOS_WITH_UNDERWRITERS[0]
    if (pathStr === '/ipos/2') return IPOS_WITH_UNDERWRITERS[1]
    if (pathStr.startsWith('/ipos/') && pathStr.endsWith('/insight')) return IPO_INSIGHT
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

// GET /ipos/{id}/insight 응답. 주관사는 미래에셋증권·KB증권 둘 다다.
const IPO_INSIGHT = {
  syncedAt: '2026-01-01T00:00:00Z',
  sourceReportName: null,
  sourceUrl: null,
  ceoName: null,
  establishedOn: null,
  address: null,
  parValue: null,
  capitalStock: null,
  largestShareholderName: null,
  largestShareholderRatio: null,
  institutionalCompetitionRate: null,
  lockUpCommitmentRatio: null,
  financials: [],
  floatSchedule: [
    { label: '미래에셋증권', shares: 1000, ratio: 10 },
    { label: 'KB증권', shares: 500, ratio: 5 },
  ],
  underwriters: [
    { name: '미래에셋증권', role: '주관', shares: 1000 },
    { name: 'KB증권', role: '공동주관', shares: 500 },
  ],
}

// 계좌 목록만 바꿔 쓰는 드롭다운 목록 목 (주관사 인사이트는 IPO_INSIGHT 고정).
function mockFetchesWithAccounts(accounts: typeof SECURITIES_ACCOUNTS) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return INVESTORS
    if (pathStr === '/securities-accounts?investorId=1') return accounts
    if (pathStr === '/ipos/1') return IPOS_WITH_UNDERWRITERS[0]
    if (pathStr === '/ipos/2') return IPOS_WITH_UNDERWRITERS[1]
    if (pathStr.startsWith('/ipos/') && pathStr.endsWith('/insight')) return IPO_INSIGHT
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

function renderForm(
  url = '/subscriptions/register?ipoId=1',
  props: {
    registerAllocation?: (request: unknown) => Promise<unknown>
  } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <SubscriptionRegisterForm {...props} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForDropdownsLoaded() {
  await waitFor(() => {
    expect(screen.getByText('홍길동')).toBeInTheDocument()
    expect(screen.getByTestId('ipo-info')).toBeInTheDocument()
  })
}

async function fillValidForm() {
  await waitFor(() =>
    expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument(),
  )
  await fill(screen.getByLabelText('증권계좌'), '1')
  await fill(screen.getByLabelText('청약수량'), '100')
  await fill(screen.getByLabelText('증거금'), '0')
  // 청약일은 기본값(한국 오늘이 기간 밖이면 첫날)이 이미 선택돼 있다 — 둘째 날로 바꿔 선택을 확인한다
  await userEvent.click(screen.getByRole('button', { name: '2026-09-02' }))
}

describe('SubscriptionRegisterForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('currentInvestorId', '1')
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
      if (pathStr === '/securities-accounts?investorId=1') return SECURITIES_ACCOUNTS
      if (pathStr === '/ipos/1') return IPOS[0]
      throw new Error(`unexpected path: ${pathStr}`)
    })

    renderForm()

    expect(screen.getByText('목록을 불러오는 중...')).toBeInTheDocument()

    resolveInvestors(INVESTORS)

    await waitForDropdownsLoaded()
  })

  it('현재 투자자의 증권계좌만 조회해 보여 준다', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    // 공모주는 URL 문맥으로 고정 — 주관사 확인 후 계좌가 활성화된다
    await waitFor(() =>
      expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument(),
    )
    expect(screen.getByLabelText('증권계좌')).toBeEnabled()
    expect(screen.queryByLabelText('투자자')).not.toBeInTheDocument()
    // 공모주 select는 없다 — 이름은 고정 텍스트로 표시된다
    expect(screen.queryByRole('combobox', { name: '공모주' })).not.toBeInTheDocument()
    expect(apiFetch).toHaveBeenCalledWith('/securities-accounts?investorId=1', expect.anything())
  })

  it('정상 등록 성공 시 /subscriptions로 이동', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    await fillValidForm()

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
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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
    expect(body.subscribedAt).toBe('2026-09-02T09:00')

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/subscriptions')
    })
  })

  it('필수값 미입력 시 클라이언트 사이드 검증 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('증권계좌를 선택해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('청약수량을 입력해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('증거금을 입력해 주세요.')).toBeInTheDocument()
    })
    // 청약일은 기본값이 이미 선택돼 있다 — 공모주 select는 없다
    expect(screen.queryByText('공모주를 선택해 주세요.')).not.toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('청약수량이 0 이하이면 에러 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    await fillValidForm()
    await fill(screen.getByLabelText('청약수량'), '0')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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

    await fillValidForm()
    await fill(screen.getByLabelText('증거금'), '-1')
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

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

    await fillValidForm()

    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(404, '존재하지 않는 투자자입니다', '존재하지 않는 투자자입니다'),
    )
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('투자자를 찾을 수 없습니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('존재하지 않는 공모주로 등록 시 에러 메시지 표시 (404)', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    await fillValidForm()

    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(404, '존재하지 않는 공모주입니다', '존재하지 않는 공모주입니다'),
    )
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('공모주를 찾을 수 없습니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('중복 청약으로 등록 시 에러 메시지 표시 (409)', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    await fillValidForm()

    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(409, 'Request failed: 409'))
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('이미 동일한 조건의 청약/배정이 존재합니다.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('400 응답 시 에러 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    await fillValidForm()

    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(400, 'Request failed: 400'))
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('입력값을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()

    await fillValidForm()

    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Failed to fetch'))
    await userEvent.click(screen.getByRole('button', { name: /등록/i }))

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  describe('입력 편의', () => {
    it('한국 오늘이 기간 밖이면 첫날이 기본값으로 선택돼 있다', async () => {
      mockDropdownFetches()
      renderForm()
      await waitForDropdownsLoaded()
      // fixture 청약 기간은 2026-09-01~2026-09-02라 한국 오늘과 무관하게 첫날이 기본값
      expect(screen.getByRole('button', { name: '2026-09-01' })).toHaveAttribute(
        'aria-pressed',
        'true',
      )
      expect(screen.getByRole('button', { name: '2026-09-02' })).toHaveAttribute(
        'aria-pressed',
        'false',
      )
    })

    it('한국 오늘이 기간 안이면 오늘이 기본값으로 선택돼 있다', async () => {
      const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/securities-accounts?investorId=1') return SECURITIES_ACCOUNTS
        if (pathStr === '/ipos/9')
          return {
            ...IPOS_WITH_UNDERWRITERS[0],
            id: 9,
            ipoName: '오늘 진행 공모주',
            subscriptionStartDate: today,
            subscriptionEndDate: today,
          }
        if (pathStr.startsWith('/ipos/') && pathStr.endsWith('/insight')) return IPO_INSIGHT
        throw new Error(`unexpected path: ${pathStr}`)
      })
      renderForm('/subscriptions/register?ipoId=9')
      await waitFor(() => {
        expect(screen.getByRole('button', { name: today })).toHaveAttribute('aria-pressed', 'true')
      })
    })

    it('청약일 날짜 버튼을 누르면 해당 날짜가 선택되고 payload에 그 날짜가 실린다', async () => {
      mockDropdownFetches()
      renderForm()
      await waitForDropdownsLoaded()
      const second = screen.getByRole('button', { name: '2026-09-02' })
      await userEvent.click(second)
      expect(second).toHaveAttribute('aria-pressed', 'true')
      expect(screen.getByRole('button', { name: '2026-09-01' })).toHaveAttribute(
        'aria-pressed',
        'false',
      )
      // 키보드(Enter)로도 조작할 수 있다
      second.focus()
      await userEvent.keyboard('{Enter}')
      expect(second).toHaveAttribute('aria-pressed', 'true')
    })

    it('공모주는 고정 텍스트로 표시되고 변경할 수 없다', async () => {
      mockDropdownFetches()
      renderForm()
      await waitForDropdownsLoaded()
      expect(screen.getByText('테스트기업 공모주')).toBeInTheDocument()
      expect(screen.queryByRole('combobox', { name: '공모주' })).not.toBeInTheDocument()
      expect(screen.getByTestId('ipo-info')).toHaveTextContent(
        '공모가 11,000원 · 청약 2026. 9. 1. ~ 2026. 9. 2.',
      )
      await fill(screen.getByLabelText('청약수량'), '10')
      // 11,000 × 10 × 50%
      expect(screen.getByLabelText('증거금')).toHaveDisplayValue('55,000')
      expect(screen.getByLabelText('증거금')).toHaveAttribute('inputmode', 'numeric')
    })

    it('사용자가 고친 증거금은 수량을 바꿔도 덮어쓰지 않는다', async () => {
      mockDropdownFetches()
      renderForm()
      await waitForDropdownsLoaded()
      await fill(screen.getByLabelText('청약수량'), '10')
      await fill(screen.getByLabelText('증거금'), '70000')
      await fill(screen.getByLabelText('청약수량'), '20')
      expect(screen.getByLabelText('증거금')).toHaveDisplayValue('70,000')
    })

    it('문맥(ipoId)이 없는 직접 URL은 공모주 청약 목록 안내만 보여 준다', async () => {
      mockDropdownFetches()
      renderForm('/subscriptions/register')
      expect(await screen.findByText('공모주를 선택해 들어와 주세요')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: '공모주 청약 목록으로 이동' })).toHaveAttribute(
        'href',
        '/subscriptions',
      )
      expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument()
      expect(screen.queryByLabelText('증권계좌')).not.toBeInTheDocument()
    })

    it('잘못된 ipoId(abc)는 오류 안내만 보여 주고 등록할 수 없다', async () => {
      mockDropdownFetches()
      renderForm('/subscriptions/register?ipoId=abc')
      expect(await screen.findByRole('alert')).toHaveTextContent('공모주 주소가 올바르지 않습니다.')
      expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument()
    })

    it('없는 공모주 id는 오류 안내만 보여 주고 등록할 수 없다', async () => {
      mockDropdownFetches()
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/ipos/999') throw new ApiError(404, '공모주를 찾을 수 없습니다')
        throw new Error(`unexpected path: ${pathStr}`)
      })
      renderForm('/subscriptions/register?ipoId=999')
      expect(await screen.findByRole('alert')).toHaveTextContent('공모주를 찾을 수 없습니다.')
      expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument()
    })
  })

  describe('배정 결과 입력', () => {
    it('체크박스 없이 항상 보이고 배정일·배정단가 입력은 없다', async () => {
      mockDropdownFetches()
      renderForm()
      await waitForDropdownsLoaded()

      expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('배정 정보 입력')).not.toBeInTheDocument()
      expect(screen.getByLabelText('배정수량')).toBeInTheDocument()
      expect(screen.queryByLabelText('배정일')).not.toBeInTheDocument()
      expect(screen.queryByLabelText(/배정단가/)).not.toBeInTheDocument()
    })

    it('확정 공모가 × 배정수량을 읽기 전용 총액으로 실시간 표시한다', async () => {
      mockDropdownFetches()
      renderForm()
      await waitForDropdownsLoaded()
      await fill(screen.getByLabelText('배정수량'), '3')
      expect(screen.getByTestId('allocation-total')).toHaveTextContent('33,000원')

      await fill(screen.getByLabelText('배정수량'), '5')
      expect(screen.getByTestId('allocation-total')).toHaveTextContent('55,000원')

      await fill(screen.getByLabelText('배정수량'), '0')
      expect(screen.getByTestId('allocation-total')).toHaveTextContent('0원')
    })

    it('희망가만 있는 공모주는 공모가로 노출하지 않고 증거금·총액을 계산하지 않는다', async () => {
      mockDropdownFetches()
      renderForm('/subscriptions/register?ipoId=2')
      await waitForDropdownsLoaded()

      expect(screen.getByTestId('ipo-info')).toHaveTextContent('공모가 미정')
      expect(screen.getByTestId('ipo-info')).not.toHaveTextContent('희망 공모가')
      expect(screen.getByTestId('ipo-info')).not.toHaveTextContent('13,000원')
      expect(screen.getByLabelText('증거금')).toHaveDisplayValue('')
      expect(
        screen.getByText(/확정 공모가가 없어 증거금을 자동 계산하지 않습니다/),
      ).toBeInTheDocument()
      expect(screen.getByTestId('allocation-total')).toHaveTextContent(
        '확정 공모가가 없어 자동 계산할 수 없습니다.',
      )
    })

    it('배정 등록 요청에 배정일·배정단가·총액을 보내지 않는다', async () => {
      const registerAllocation = vi.fn().mockResolvedValue({ id: 9 })
      mockDropdownFetches()
      renderForm('/subscriptions/register?ipoId=1', { registerAllocation })
      await waitForDropdownsLoaded()
      await fillValidForm()
      await fill(screen.getByLabelText('배정수량'), '3')
      // 배정방식 입력 요소는 존재하지 않아야 한다
      expect(screen.queryByLabelText('배정방식 (선택)')).not.toBeInTheDocument()

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
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))

      await waitFor(() => expect(registerAllocation).toHaveBeenCalledTimes(1))
      const request = registerAllocation.mock.calls[0][0] as Record<string, unknown>
      expect(request).toMatchObject({
        subscriptionId: 1,
        ipoId: 1,
        investorId: 1,
        securitiesAccountId: 1,
        allocatedQuantity: 3,
      })
      expect(request).not.toHaveProperty('allocationMethod')
      expect(request).not.toHaveProperty('allocatedAt')
      expect(request).not.toHaveProperty('allocationPrice')
      expect(request).not.toHaveProperty('allocatedAmount')
      expect(JSON.stringify(request)).not.toContain('2026-09-21T09:00')
      expect(JSON.stringify(request)).not.toContain('33000')
    })
  })

  it('공모주 이름이 증권계좌 필드보다 DOM 앞에 나타난다', async () => {
    mockDropdownFetches()
    renderForm()
    await waitForDropdownsLoaded()
    const ipoField = screen.getByText('테스트기업 공모주')
    const accountField = screen.getByLabelText('증권계좌')
    const position = ipoField.compareDocumentPosition(accountField as Node)
    // accountField이 ipoField 뒤에 오면 FOLLOWING(4) 비트 설정
    expect(position & Node.DOCUMENT_POSITION_FOLLOWING).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
  })

  describe('청약일 날짜 버튼', () => {
    const mockIpo = (id: number, start: string, end: string) => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/securities-accounts?investorId=1') return SECURITIES_ACCOUNTS
        if (pathStr === `/ipos/${id}`)
          return {
            ...IPOS_WITH_UNDERWRITERS[0],
            id,
            ipoName: `기간 테스트 ${id}`,
            subscriptionStartDate: start,
            subscriptionEndDate: end,
          }
        if (pathStr.startsWith('/ipos/') && pathStr.endsWith('/insight')) return IPO_INSIGHT
        throw new Error(`unexpected path: ${pathStr}`)
      })
    }

    it('같은 날이면 버튼이 1개만 보인다', async () => {
      mockIpo(10, '2026-11-10', '2026-11-10')
      renderForm('/subscriptions/register?ipoId=10')
      await waitForDropdownsLoaded()
      expect(screen.getByRole('button', { name: '2026-11-10' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '2026-11-11' })).not.toBeInTheDocument()
    })

    it('3일 기간이면 실제 날짜 버튼 3개가 모두 보인다', async () => {
      mockIpo(11, '2026-11-10', '2026-11-12')
      renderForm('/subscriptions/register?ipoId=11')
      await waitForDropdownsLoaded()
      expect(screen.getByRole('button', { name: '2026-11-10' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '2026-11-11' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '2026-11-12' })).toBeInTheDocument()
    })

    it('청약 일정이 누락되면 안내만 보이고 등록이 차단된다', async () => {
      mockIpo(12, '', '')
      renderForm('/subscriptions/register?ipoId=12')
      expect(await screen.findByRole('alert')).toHaveTextContent(
        '청약 일정을 확인할 수 없어 등록할 수 없습니다.',
      )
      expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
      expect(screen.queryByRole('button', { name: '2026-11-10' })).not.toBeInTheDocument()
    })

    it('청약 일정이 역전되면 안내만 보이고 등록이 차단된다', async () => {
      mockIpo(13, '2026-11-11', '2026-11-10')
      renderForm('/subscriptions/register?ipoId=13')
      expect(await screen.findByRole('alert')).toHaveTextContent(
        '청약 일정을 확인할 수 없어 등록할 수 없습니다.',
      )
      expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
    })

    it('둘째 날을 고르면 payload에 그 날짜가 실린다', async () => {
      mockIpo(14, '2026-11-10', '2026-11-11')
      renderForm('/subscriptions/register?ipoId=14')
      await waitForDropdownsLoaded()
      // 두 날짜 버튼 중 둘째 날을 고르면 payload에 그 날짜가 실린다
      await userEvent.click(screen.getByRole('button', { name: '2026-11-11' }))
      vi.mocked(apiFetch).mockResolvedValueOnce({
        id: 1,
        investorId: 1,
        securitiesAccountId: 1,
        ipoId: 14,
        quantity: 100,
        depositAmount: 0,
        subscribedAt: '2026-11-11T09:00:00',
        memo: '',
        status: 'ACTIVE',
        createdAt: '2026-11-11T00:00:00Z',
        updatedAt: '2026-11-11T00:00:00Z',
      })
      await waitFor(() =>
        expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument(),
      )
      await fill(screen.getByLabelText('증권계좌'), '1')
      await fill(screen.getByLabelText('청약수량'), '100')
      await fill(screen.getByLabelText('증거금'), '0')
      await userEvent.click(screen.getByRole('button', { name: /등록/i }))
      await waitFor(() => {
        expect(apiFetch).toHaveBeenCalledWith(
          '/subscriptions',
          expect.objectContaining({ method: 'POST' }),
        )
      })
      const call = vi.mocked(apiFetch).mock.calls.find(([path]) => path === '/subscriptions')!
      const body = JSON.parse((call[1] as RequestInit).body as string)
      expect(body.subscribedAt).toBe('2026-11-11T09:00')
    })
  })

  describe('주관사 기반 증권계좌 필터링', () => {
    beforeEach(() => {
      vi.clearAllMocks()
      localStorage.setItem('currentInvestorId', '1')
      vi.spyOn(window, 'alert').mockImplementation(() => {})
    })

    afterEach(() => {
      vi.restoreAllMocks()
    })

    it('선택 IPO의 주관사 계좌만 표시하고 다른 증권사 계좌는 노출하지 않는다', async () => {
      const alertSpy = vi.mocked(window.alert)
      mockDropdownFetches()
      renderForm('/subscriptions/register?ipoId=1')
      await waitForDropdownsLoaded()

      // 주관사 정보가 반영된 뒤에 검증한다 (로딩 중에는 기존 계좌 목록을 유지한다)
      expect(await screen.findByRole('option', { name: '미래에셋증권' })).toBeInTheDocument()
      await waitFor(() => {
        expect(screen.queryByRole('option', { name: '하나은행' })).not.toBeInTheDocument()
      })

      // option label은 계좌의 securitiesFirmName이다
      expect(screen.getByRole('option', { name: '미래에셋증권' })).toHaveValue('1')
      expect(screen.getByRole('option', { name: 'KB증권' })).toHaveValue('2')
      expect(screen.queryByRole('option', { name: '주력 계좌' })).not.toBeInTheDocument()
      expect(screen.queryByRole('option', { name: '보조 계좌' })).not.toBeInTheDocument()
      expect(alertSpy).not.toHaveBeenCalled()
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('번호 없는 IBK 보유 등록 계좌를 매칭하고 단일 주관사 계좌를 기본 선택한다', async () => {
      const alertSpy = vi.mocked(window.alert)
      const account = {
        ...SECURITIES_ACCOUNTS[0],
        securitiesFirmName: 'IBK투자증권',
        accountNumber: null,
      }
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/securities-accounts?investorId=1') return [account]
        if (pathStr === '/ipos/1') return IPOS_WITH_UNDERWRITERS[0]
        if (pathStr === '/ipos/2') return IPOS_WITH_UNDERWRITERS[1]
        if (pathStr === '/ipos/1/insight') {
          return {
            ...IPO_INSIGHT,
            underwriters: [{ name: 'IBK투자증권', role: '주관', shares: 100 }],
          }
        }
        if (pathStr === '/ipos/2/insight') {
          return {
            ...IPO_INSIGHT,
            underwriters: [{ name: '아이비케이투자증권 주식회사', role: '주관', shares: 100 }],
          }
        }
        throw new Error(`unexpected path: ${pathStr}`)
      })

      const first = renderForm('/subscriptions/register?ipoId=1')
      await waitForDropdownsLoaded()
      await waitFor(() => expect(screen.getByLabelText('증권계좌')).toHaveValue('1'))
      first.unmount()

      // 다른 공모주(별칭 주관사명)로 열면 이전 선택 없이 다시 기본 선택한다
      renderForm('/subscriptions/register?ipoId=2')
      await waitFor(() => expect(screen.getByLabelText('증권계좌')).toHaveValue('1'))
      expect(alertSpy).not.toHaveBeenCalled()
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('일치하는 주관사 계좌가 없으면 안내 문구와 함께 /my-page로 이동한다', async () => {
      const alertSpy = vi.mocked(window.alert)
      // 계좌는 있지만 주관사와 매칭되지 않는 경우
      mockFetchesWithAccounts([SECURITIES_ACCOUNTS[2]])

      renderForm('/subscriptions/register?ipoId=1')

      await waitFor(() => {
        expect(alertSpy).toHaveBeenCalledWith(
          '미래에셋증권 · KB증권 주관사 증권계좌 등록이 필요합니다.',
        )
        expect(mockNavigate).toHaveBeenCalledWith('/my-page')
      })
      expect(alertSpy).toHaveBeenCalledTimes(1)
    })

    it('주관사 정보 로딩 중에는 알럿·이동이 없고, 로드 완료 후 1회만 처리한다', async () => {
      const alertSpy = vi.mocked(window.alert)
      let resolveInsight: (value: unknown) => void = () => {}
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/securities-accounts?investorId=1') return [SECURITIES_ACCOUNTS[2]]
        if (pathStr === '/ipos/1') return IPOS_WITH_UNDERWRITERS[0]
        if (pathStr === '/ipos/2') return IPOS_WITH_UNDERWRITERS[1]
        if (pathStr === '/ipos/1/insight') {
          return new Promise((resolve) => {
            resolveInsight = resolve
          })
        }
        throw new Error(`unexpected path: ${pathStr}`)
      })

      renderForm('/subscriptions/register?ipoId=1')
      await waitForDropdownsLoaded()

      // 로딩 중에는 알럿도 이동도 없다 (AC2)
      expect(alertSpy).not.toHaveBeenCalled()
      expect(mockNavigate).not.toHaveBeenCalled()

      resolveInsight(IPO_INSIGHT)

      await waitFor(() => {
        expect(alertSpy).toHaveBeenCalledWith(
          '미래에셋증권 · KB증권 주관사 증권계좌 등록이 필요합니다.',
        )
        expect(mockNavigate).toHaveBeenCalledWith('/my-page')
      })
      expect(alertSpy).toHaveBeenCalledTimes(1)
    })

    it('일부 주관사 계좌만 있으면 해당 계좌를 표시하고 알럿·이동하지 않는다', async () => {
      const alertSpy = vi.mocked(window.alert)
      // 주관사 2곳 중 미래에셋증권 계좌만 등록된 경우
      mockFetchesWithAccounts([SECURITIES_ACCOUNTS[0], SECURITIES_ACCOUNTS[2]])

      renderForm('/subscriptions/register?ipoId=1')
      await waitForDropdownsLoaded()

      await waitFor(() => {
        expect(screen.queryByRole('option', { name: '하나은행' })).not.toBeInTheDocument()
      })
      expect(screen.getByRole('option', { name: '미래에셋증권' })).toHaveValue('1')
      expect(screen.queryByRole('option', { name: 'KB증권' })).not.toBeInTheDocument()
      expect(alertSpy).not.toHaveBeenCalled()
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('주관사 확인 후 주관사 계좌만 표시된다', async () => {
      const alertSpy = vi.mocked(window.alert)
      mockDropdownFetches()
      renderForm()

      await waitForDropdownsLoaded()

      // AC-2: insight 로딩/완료 중 전체 계좌 노출 없음 (loading이 빠른 mock에서 바로 필터링)
      await waitFor(() => {
        expect(screen.queryByRole('option', { name: '하나은행' })).not.toBeInTheDocument()
      })

      // AC-3: 인사이트 완료 후 주관사 매칭 계좌만 표시
      await waitFor(() => {
        expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument()
        expect(screen.getByRole('option', { name: 'KB증권' })).toBeInTheDocument()
      })
      expect(screen.queryByRole('option', { name: '하나은행' })).not.toBeInTheDocument()
      expect(screen.getByLabelText('증권계좌')).toBeEnabled()
      expect(alertSpy).not.toHaveBeenCalled()
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('AC-4: 공모주를 고정하기 때문에 이전 계좌 선택이 다른 공모주에 새지 않는다', async () => {
      mockDropdownFetches()
      // ipoId=2의 주관사가 다르면 계좌 목록도 그 주관사 계좌만 남는다
      const first = renderForm('/subscriptions/register?ipoId=1')
      await waitForDropdownsLoaded()
      await waitFor(() =>
        expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument(),
      )
      await fill(screen.getByLabelText('증권계좌'), '1')
      first.unmount()
      // ipoId=2 진입(페이지가 key로 폼을 새로 만든다)에는 이전 선택이 남아 있지 않다
      renderForm('/subscriptions/register?ipoId=2')
      await waitFor(() => expect(screen.getByLabelText('증권계좌')).toHaveValue(''))
    })

    it('AC-2 (pending): IPO 선택 후 insight pending 동안 disabled·옵션 없음, resolve 후 필터링', async () => {
      let resolveInsight: (value: unknown) => void = () => {}
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/securities-accounts?investorId=1') return SECURITIES_ACCOUNTS
        if (pathStr === '/ipos/1') return IPOS_WITH_UNDERWRITERS[0]
        if (pathStr === '/ipos/2') return IPOS_WITH_UNDERWRITERS[1]
        if (pathStr === '/ipos/1/insight') {
          return new Promise((resolve) => {
            resolveInsight = resolve
          })
        }
        throw new Error(`unexpected path: ${pathStr}`)
      })
      renderForm()
      await waitForDropdownsLoaded()
      // pending 상태: disabled, 전체 계좌 옵션 없음
      expect(screen.getByLabelText('증권계좌')).toBeDisabled()
      expect(screen.queryByRole('option', { name: '하나은행' })).not.toBeInTheDocument()
      expect(screen.queryByRole('option', { name: '미래에셋증권' })).not.toBeInTheDocument()
      resolveInsight(IPO_INSIGHT)
      await waitFor(() => {
        expect(screen.getByRole('option', { name: '미래에셋증권' })).toBeInTheDocument()
        expect(screen.getByLabelText('증권계좌')).toBeEnabled()
      })
    })

    it('AC-5 (rejection): insight 요청 실패 시 disabled·옵션 없음·오류 안내', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/securities-accounts?investorId=1') return SECURITIES_ACCOUNTS
        if (pathStr === '/ipos/1') return IPOS_WITH_UNDERWRITERS[0]
        if (pathStr === '/ipos/2') return IPOS_WITH_UNDERWRITERS[1]
        if (pathStr.startsWith('/ipos/') && pathStr.endsWith('/insight')) {
          throw new ApiError(500, '인사이트 조회 실패')
        }
        throw new Error(`unexpected path: ${pathStr}`)
      })
      renderForm()
      await waitForDropdownsLoaded()
      await waitFor(() => {
        expect(screen.getByLabelText('증권계좌')).toBeDisabled()
        expect(screen.queryByRole('option', { name: '미래에셋증권' })).not.toBeInTheDocument()
      })
      expect(
        screen.getByRole('option', { name: '주관사 정보를 확인할 수 없습니다.' }),
      ).toBeInTheDocument()
    })

    it('AC-5 (empty underwriters): 빈 주관사 목록 시 disabled·옵션 없음·안내', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return INVESTORS
        if (pathStr === '/securities-accounts?investorId=1') return SECURITIES_ACCOUNTS
        if (pathStr === '/ipos/1') return IPOS_WITH_UNDERWRITERS[0]
        if (pathStr === '/ipos/2') return IPOS_WITH_UNDERWRITERS[1]
        if (pathStr.startsWith('/ipos/') && pathStr.endsWith('/insight')) {
          return { underwriters: [] }
        }
        throw new Error(`unexpected path: ${pathStr}`)
      })
      renderForm()
      await waitForDropdownsLoaded()
      await waitFor(() => {
        expect(screen.getByLabelText('증권계좌')).toBeDisabled()
        expect(screen.queryByRole('option', { name: '미래에셋증권' })).not.toBeInTheDocument()
      })
      expect((screen.getByLabelText('증권계좌') as HTMLSelectElement).value).toBe('')
    })
  })
})
