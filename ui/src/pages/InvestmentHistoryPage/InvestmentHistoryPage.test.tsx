import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InvestmentHistoryPage from './InvestmentHistoryPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import type { InvestmentHistoryResponse } from '@/entities/subscription/index.ts'
import type { InvestorResponse } from '@/entities/investor/index.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

const MOCK_INVESTORS: InvestorResponse[] = [
  {
    id: 1,
    displayName: '김철수',
    accountId: 100,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    displayName: '이영희',
    accountId: 200,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const MOCK_SECURITIES_ACCOUNTS_INVESTOR_1 = [
  {
    id: 10,
    investorId: 1,
    securitiesFirmId: 1,
    securitiesFirmName: '한국투자증권',
    accountNumber: '123-456-789',
    accountName: '주계좌',
    isDefault: true,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 11,
    investorId: 1,
    securitiesFirmId: 2,
    securitiesFirmName: '미래에셋증권',
    accountNumber: '987-654-321',
    accountName: '부계좌',
    isDefault: false,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const HISTORY_RESPONSE: InvestmentHistoryResponse = {
  content: [
    {
      id: 1,
      subscribedAt: '2026-09-01T09:00:00Z',
      ipoId: 10,
      ipoName: '카카오뱅크',
      ipoFixedPrice: 39000,
      subscriptionQuantity: 100,
      subscriptionStatus: 'ACTIVE',
      allocation: null,
      sales: [],
      remainingQuantity: null,
      investorName: '김철수',
      securitiesFirmName: '한국투자증권',
    },
    {
      id: 2,
      subscribedAt: '2026-09-02T09:00:00Z',
      ipoId: 11,
      ipoName: '삼성바이오로직스',
      ipoFixedPrice: 55000,
      subscriptionQuantity: 50,
      subscriptionStatus: 'ACTIVE',
      allocation: {
        id: 1,
        allocatedQuantity: 20,
        allocationPrice: 55000,
        allocationStatus: 'CONFIRMED',
      },
      sales: [],
      remainingQuantity: 20,
      investorName: null,
      securitiesFirmName: null,
    },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
}

const EMPTY_HISTORY_RESPONSE: InvestmentHistoryResponse = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
}

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="search">{location.search}</p>
}

function renderPage(entry = '/investments') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[entry]}>
        <InvestmentHistoryPage />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForResultsLoaded() {
  await waitFor(() => {
    expect(screen.getByText('카카오뱅크')).toBeInTheDocument()
  })
}

function setupDefaultMocks() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return MOCK_INVESTORS
    if (pathStr.startsWith('/securities-accounts')) return []
    if (pathStr.startsWith('/investment-history')) return HISTORY_RESPONSE
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

function setupEmptyInvestorsMock() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return []
    if (pathStr.startsWith('/securities-accounts')) return []
    if (pathStr.startsWith('/investment-history')) return HISTORY_RESPONSE
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

function setupHistoryErrorMock(error: Error) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return MOCK_INVESTORS
    if (pathStr.startsWith('/securities-accounts')) return []
    if (pathStr.startsWith('/investment-history')) throw error
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

describe('InvestmentHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 투자자는 상단 전환기의 현재 투자자로 정해진다.
    localStorage.setItem('currentInvestorId', '1')
  })

  it('현재 투자자 기준으로 투자 내역을 조회해 보여 준다', async () => {
    setupDefaultMocks()
    renderPage()

    await waitForResultsLoaded()
    const paths = vi.mocked(apiFetch).mock.calls.map((call) => String(call[0]))
    expect(
      paths.some((p) => p.startsWith('/investment-history') && p.includes('investorId=1')),
    ).toBe(true)
    // 투자자 선택은 이 화면이 아니라 상단 전환기가 담당한다.
    expect(screen.queryByLabelText('투자자')).not.toBeInTheDocument()
  })

  it('투자자 목록을 불러오는 동안 로딩을 표시한다', async () => {
    vi.mocked(apiFetch).mockImplementation(() => new Promise(() => undefined))
    renderPage()
    expect(await screen.findByText('투자자 목록을 불러오는 중...')).toBeInTheDocument()
  })

  it('AC-1904: 등록된 투자자가 0명일 때 빈 상태 안내와 등록 링크 표시', async () => {
    setupEmptyInvestorsMock()
    renderPage()

    const message = await screen.findByText(
      '등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요.',
    )
    expect(message.closest('[role="status"]')).toHaveAttribute('aria-live', 'polite')
    expect(screen.getByRole('link', { name: '투자자 등록하기' })).toHaveAttribute(
      'href',
      '/investors/register',
    )
  })

  it('FilterBar에 접근성 속성이 올바르게 설정됨', async () => {
    setupDefaultMocks()
    renderPage()
    await waitForResultsLoaded()

    expect(screen.getByRole('search')).toBeInTheDocument()
    expect(screen.getByLabelText('증권계좌')).toBeInTheDocument()
    expect(screen.getByLabelText('IPO ID')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /초기화/i })).toHaveAttribute(
      'aria-label',
      '필터 초기화',
    )
  })

  it('빈 결과 시 EmptyState가 표시되고 접근성 속성이 설정됨', async () => {
    let historyCallCount = 0
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const pathStr = String(path)
      if (pathStr === '/investors') return MOCK_INVESTORS
      if (pathStr.startsWith('/securities-accounts')) return []
      if (pathStr.startsWith('/investment-history')) {
        historyCallCount++
        return historyCallCount === 1 ? HISTORY_RESPONSE : EMPTY_HISTORY_RESPONSE
      }
      throw new Error(`unexpected path: ${pathStr}`)
    })
    renderPage()
    await waitForResultsLoaded()

    await userEvent.click(screen.getByRole('button', { name: /초기화/i }))

    const empty = await screen.findByText('해당 조건에 해당하는 기록이 없습니다.')
    expect(empty.closest('[role="status"]')).toHaveAttribute('aria-live', 'polite')
    expect(screen.getByRole('button', { name: '전체 필터 초기화' })).toBeInTheDocument()
  })

  it.each([
    [new Error('Failed to fetch'), '네트워크 연결을 확인해 주세요.'],
    [new ApiError(404, 'Request failed: 404'), '투자 내역을 찾을 수 없습니다.'],
    [new ApiError(500, 'Request failed: 500'), '투자 내역을 불러오는 데 실패했습니다.'],
  ])('조회 오류(%#)에 맞는 메시지를 보여 준다', async (error, message) => {
    setupHistoryErrorMock(error)
    renderPage()
    expect(await screen.findByText(message)).toBeInTheDocument()
  })

  describe('필터 URL 동기화', () => {
    const mockApi = () =>
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const pathStr = String(path)
        if (pathStr === '/investors') return MOCK_INVESTORS
        if (pathStr.startsWith('/securities-accounts')) return MOCK_SECURITIES_ACCOUNTS_INVESTOR_1
        if (pathStr.startsWith('/investment-history')) return HISTORY_RESPONSE
        throw new Error(`unexpected path: ${pathStr}`)
      })

    it('URL 쿼리의 IPO 필터가 복원되고 현재 투자자와 함께 조회된다', async () => {
      mockApi()
      renderPage('/investments?ipoId=7')

      await waitFor(() => expect(screen.getByLabelText('IPO ID')).toHaveValue('7'))
      await waitFor(() => {
        const paths = vi.mocked(apiFetch).mock.calls.map((call) => String(call[0]))
        expect(
          paths.some(
            (p) =>
              p.startsWith('/investment-history') &&
              p.includes('investorId=1') &&
              p.includes('ipoId=7'),
          ),
        ).toBe(true)
      })
    })

    it('필터 변경은 URL에 반영된다', async () => {
      mockApi()
      renderPage()
      await waitForResultsLoaded()

      await fill(screen.getByLabelText('IPO ID'), '9')
      await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('ipoId=9'))
    })

    it('초기화하면 필터 쿼리가 모두 제거된다', async () => {
      mockApi()
      renderPage('/investments?securitiesAccountId=10&ipoId=7')
      await waitFor(() => expect(screen.getByLabelText('IPO ID')).toHaveValue('7'))

      await userEvent.click(screen.getByRole('button', { name: '필터 초기화' }))
      await waitFor(() => expect(screen.getByTestId('search')).toBeEmptyDOMElement())
      expect(screen.getByLabelText('IPO ID')).toHaveValue('')
    })
  })
})
