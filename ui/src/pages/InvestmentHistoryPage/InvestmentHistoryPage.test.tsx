import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InvestmentHistoryPage from './InvestmentHistoryPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import type { InvestmentHistoryResponse } from '@/entities/subscription/types.ts'
import type { InvestorResponse } from '@/entities/investor/types.ts'
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

const MOCK_SECURITIES_ACCOUNTS_INVESTOR_2 = [
  {
    id: 20,
    investorId: 2,
    securitiesFirmId: 3,
    securitiesFirmName: '삼성증권',
    accountNumber: '111-222-333',
    accountName: '주계좌',
    isDefault: true,
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

async function waitForLoaded() {
  await waitFor(() => {
    expect(screen.getByText('투자 내역')).toBeInTheDocument()
  })
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

function setupInvestorsErrorMock() {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') throw new Error('Failed to fetch')
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
  })

  it('투자자 로딩 상태를 표시함', async () => {
    let resolveInvestors: (value: InvestorResponse[]) => void = () => {}
    let resolveHistory: (value: unknown) => void = () => {}

    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const pathStr = String(path)
      if (pathStr === '/investors') {
        return new Promise((resolve) => {
          resolveInvestors = resolve
        })
      }
      if (pathStr.startsWith('/securities-accounts')) return []
      if (pathStr.startsWith('/investment-history')) {
        return new Promise((resolve) => {
          resolveHistory = resolve
        })
      }
      throw new Error(`unexpected path: ${pathStr}`)
    })

    renderPage()

    await waitForLoaded()
    expect(screen.getByText('투자자 목록을 불러오는 중...')).toBeInTheDocument()

    resolveInvestors(MOCK_INVESTORS)
    resolveHistory(HISTORY_RESPONSE)

    await waitForResultsLoaded()
  })

  it('투자자 목록 로드 실패 시 에러 표시', async () => {
    setupInvestorsErrorMock()
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(
        screen.getByText('투자자 목록을 불러올 수 없습니다. 새로고침 후 다시 시도해 주세요.'),
      ).toBeInTheDocument()
    })
  })

  it('AC-1904: 등록된 투자자가 0명일 때 빈 상태 안내와 등록 링크 표시', async () => {
    setupEmptyInvestorsMock()
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(
        screen.getByText('등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요.'),
      ).toBeInTheDocument()
    })

    const registerLink = screen.getByRole('link', { name: '투자자 등록하기' })
    expect(registerLink).toBeInTheDocument()
    expect(registerLink).toHaveAttribute('href', '/investors/register')
  })

  it('투자자 선택 드롭다운 표시 및 변경 시 UI 업데이트', async () => {
    setupDefaultMocks()
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByLabelText('투자자')).toBeInTheDocument()
      // There are two "전체" options (investor and securities account), check investor's one
      const investorSelect = screen.getByLabelText('투자자')
      expect(investorSelect.querySelector('option[value=""]')).toHaveTextContent('전체')
    })

    await waitForResultsLoaded()

    // 투자자 선택 변경
    const investorSelect = screen.getByLabelText('투자자')
    await fill(investorSelect, '1')

    await waitFor(() => {
      expect(investorSelect).toHaveValue('1')
    })

    // 결과가 다시 로드됨을 확인 (카카오뱅크가 다시 나타남)
    await waitForResultsLoaded()
  })

  it('증권계좌 선택 드롭다운 표시', async () => {
    setupDefaultMocks()
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByLabelText('투자자')).toBeInTheDocument()
    })

    await waitForResultsLoaded()

    // 투자자 먼저 선택
    const investorSelect = screen.getByLabelText('투자자')
    await fill(investorSelect, '1')

    // 증권계좌 로드 완료 대기
    await waitFor(() => {
      expect(screen.getByLabelText('증권계좌')).not.toBeDisabled()
    })

    // 증권계좌 드롭다운이 표시됨
    const accountSelect = screen.getByLabelText('증권계좌')
    expect(accountSelect).toBeInTheDocument()
  })

  it('IPO ID 입력 시 UI 업데이트', async () => {
    setupDefaultMocks()
    renderPage()

    await waitForLoaded()
    await waitForResultsLoaded()

    const ipoInput = screen.getByLabelText('IPO ID')
    await fill(ipoInput, '3')

    await waitFor(() => {
      expect(ipoInput).toHaveValue('3')
    })
  })

  it('초기화 버튼 클릭 시 필터가 비워짐', async () => {
    setupDefaultMocks()
    renderPage()

    await waitForLoaded()
    await waitForResultsLoaded()

    const investorSelect = screen.getByLabelText('투자자')
    await fill(investorSelect, '1')
    await waitFor(() => {
      expect(investorSelect).toHaveValue('1')
    })

    await userEvent.click(screen.getByRole('button', { name: /초기화/i }))

    await waitFor(() => {
      expect(investorSelect).toHaveValue('')
    })
  })

  it('빈 결과 시 EmptyState가 표시되고 초기화 버튼이 동작함', async () => {
    let historyCallCount = 0
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const pathStr = String(path)
      if (pathStr === '/investors') return MOCK_INVESTORS
      if (pathStr.startsWith('/securities-accounts')) return []
      if (pathStr.startsWith('/investment-history')) {
        historyCallCount++
        if (historyCallCount === 1) return HISTORY_RESPONSE
        return EMPTY_HISTORY_RESPONSE
      }
      throw new Error(`unexpected path: ${pathStr}`)
    })

    renderPage()

    await waitForLoaded()
    await waitForResultsLoaded()

    // 빈 결과로 변경
    await userEvent.click(screen.getByRole('button', { name: /초기화/i }))

    await waitFor(() => {
      expect(screen.getByText('해당 조건에 해당하는 기록이 없습니다.')).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: '전체 필터 초기화' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '전체 필터 초기화' }))
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    setupHistoryErrorMock(new Error('Failed to fetch'))
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('404 에러 시 에러 메시지 표시', async () => {
    setupHistoryErrorMock(new ApiError(404, 'Request failed: 404'))
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByText('투자 내역을 찾을 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('기타 API 에러 시 공용 에러 메시지 표시', async () => {
    setupHistoryErrorMock(new ApiError(500, 'Request failed: 500'))
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByText('투자 내역을 불러오는 데 실패했습니다.')).toBeInTheDocument()
    })
  })

  it('FilterBar에 접근성 속성이 올바르게 설정됨', async () => {
    setupDefaultMocks()
    renderPage()

    await waitForLoaded()
    await waitForResultsLoaded()

    expect(screen.getByRole('search')).toBeInTheDocument()
    expect(screen.getByLabelText('투자자')).toBeInTheDocument()
    expect(screen.getByLabelText('증권계좌')).toBeInTheDocument()
    expect(screen.getByLabelText('IPO ID')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: /초기화/i })).toHaveAttribute(
      'aria-label',
      '필터 초기화',
    )
  })

  it('EmptyState에 접근성 속성이 올바르게 설정됨', async () => {
    let historyCallCount = 0
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const pathStr = String(path)
      if (pathStr === '/investors') return MOCK_INVESTORS
      if (pathStr.startsWith('/securities-accounts')) return []
      if (pathStr.startsWith('/investment-history')) {
        historyCallCount++
        if (historyCallCount === 1) return HISTORY_RESPONSE
        return EMPTY_HISTORY_RESPONSE
      }
      throw new Error(`unexpected path: ${pathStr}`)
    })

    renderPage()

    await waitForLoaded()
    await waitForResultsLoaded()

    // 빈 결과로 변경
    await userEvent.click(screen.getByRole('button', { name: /초기화/i }))

    await waitFor(() => {
      const emptyState = screen
        .getByText('해당 조건에 해당하는 기록이 없습니다.')
        .closest('[role="status"]')
      expect(emptyState).toBeInTheDocument()
      expect(emptyState).toHaveAttribute('aria-live', 'polite')
    })
  })

  it('투자자 0명 빈 상태에 접근성 속성이 올바르게 설정됨', async () => {
    setupEmptyInvestorsMock()
    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      const emptyState = screen
        .getByText('등록된 투자자가 없습니다. 투자자를 먼저 등록해 주세요.')
        .closest('[role="status"]')
      expect(emptyState).toBeInTheDocument()
      expect(emptyState).toHaveAttribute('aria-live', 'polite')
    })
  })

  it('AC-1902 통합: 투자자 변경 시 증권계좌 초기화 및 조회 파라미터 검증', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const pathStr = String(path)
      if (pathStr === '/investors') return MOCK_INVESTORS
      if (pathStr.startsWith('/securities-accounts')) {
        if (pathStr.includes('investorId=1')) return MOCK_SECURITIES_ACCOUNTS_INVESTOR_1
        if (pathStr.includes('investorId=2')) return MOCK_SECURITIES_ACCOUNTS_INVESTOR_2
        return []
      }
      if (pathStr.startsWith('/investment-history')) {
        return HISTORY_RESPONSE
      }
      throw new Error(`unexpected path: ${pathStr}`)
    })

    renderPage()

    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByLabelText('투자자')).toBeInTheDocument()
    })

    await waitForResultsLoaded()

    // (1) 투자자 A(김철수, id=1) 선택 -> 증권계좌 목록 재조회 -> 증권계좌 X(10) 선택
    const investorSelect = screen.getByLabelText('투자자')
    await fill(investorSelect, '1')

    await waitFor(() => {
      expect(investorSelect).toHaveValue('1')
    })

    // 증권계좌 로드 대기
    await waitFor(() => {
      expect(screen.getByLabelText('증권계좌')).not.toBeDisabled()
    })

    // 증권계좌 옵션 확인
    await waitFor(() => {
      expect(screen.getByRole('option', { name: '한국투자증권 - 123-456-789' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: '미래에셋증권 - 987-654-321' })).toBeInTheDocument()
    })

    // 증권계좌 X(10, 한국투자증권) 선택
    const accountSelect = screen.getByLabelText('증권계좌')
    await fill(accountSelect, '10')

    await waitFor(() => {
      expect(accountSelect).toHaveValue('10')
    })

    // 조회 호출에 investorId=1, securitiesAccountId=10이 함께 들어가는지 확인
    await waitFor(() => {
      const historyCalls = vi
        .mocked(apiFetch)
        .mock.calls.filter(([path]) => String(path).startsWith('/investment-history'))
      const lastCall = historyCalls[historyCalls.length - 1]
      expect(String(lastCall[0])).toContain('investorId=1')
      expect(String(lastCall[0])).toContain('securitiesAccountId=10')
    })

    // (2) 투자자를 B(이영희, id=2)로 변경 -> 증권계좌 선택 초기화('전체'로) -> 조회에 securitiesAccountId가 빠짐
    await fill(investorSelect, '2')

    await waitFor(() => {
      expect(investorSelect).toHaveValue('2')
    })

    // 증권계좌가 '전체'(빈 값)로 초기화됐는지 확인
    await waitFor(() => {
      expect(accountSelect).toHaveValue('')
    })

    // 조회 호출에 investorId=2만 있고 securitiesAccountId가 없는지 확인
    await waitFor(() => {
      const historyCalls = vi
        .mocked(apiFetch)
        .mock.calls.filter(([path]) => String(path).startsWith('/investment-history'))
      const lastCall = historyCalls[historyCalls.length - 1]
      expect(String(lastCall[0])).toContain('investorId=2')
      expect(String(lastCall[0])).not.toContain('securitiesAccountId')
    })

    // (3) 투자자를 '전체'로 되돌리면 증권계좌 드롭다운이 disabled되고 값이 비워짐
    await fill(investorSelect, '')

    await waitFor(() => {
      expect(investorSelect).toHaveValue('')
    })

    await waitFor(() => {
      expect(accountSelect).toBeDisabled()
      expect(accountSelect).toHaveValue('')
    })
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

    it('URL 쿼리로 필터가 복원되고 조회 파라미터에 반영된다', async () => {
      mockApi()
      renderPage('/investments?investorId=1&ipoId=7')

      await waitFor(() => expect(screen.getByLabelText('투자자')).toHaveValue('1'))
      expect(screen.getByLabelText('IPO ID')).toHaveValue('7')
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

    it('필터 변경은 URL에 반영되고 투자자 변경 시 증권계좌 필터가 제거된다', async () => {
      mockApi()
      renderPage('/investments?investorId=1&securitiesAccountId=10')
      await waitFor(() => expect(screen.getByLabelText('투자자')).toHaveValue('1'))

      await fill(screen.getByLabelText('투자자'), '2')
      await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('?investorId=2'))
      expect(screen.getByTestId('search')).not.toHaveTextContent('securitiesAccountId')

      await fill(screen.getByLabelText('IPO ID'), '9')
      await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('ipoId=9'))
    })

    it('초기화하면 필터 쿼리가 모두 제거된다', async () => {
      mockApi()
      renderPage('/investments?investorId=1&ipoId=7')
      await waitFor(() => expect(screen.getByLabelText('투자자')).toHaveValue('1'))

      await userEvent.click(screen.getByRole('button', { name: '필터 초기화' }))
      await waitFor(() => expect(screen.getByTestId('search')).toBeEmptyDOMElement())
      expect(screen.getByLabelText('IPO ID')).toHaveValue('')
    })
  })
})
