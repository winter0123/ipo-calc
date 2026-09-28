import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DashboardPage from './DashboardPage.tsx'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForLoaded() {
  await waitFor(() => {
    expect(screen.getByText('대시보드')).toBeInTheDocument()
  })
}

const defaultSummaryQuery = {
  data: {
    totalInvestmentAmount: 100000000,
    totalPreTaxProfit: 15000000,
    totalAfterTaxProfit: 12000000,
    totalProfitRate: 12.5,
    participationCount: 45,
  },
  isLoading: false,
  error: null,
}
const defaultEmptyQuery = { data: [] as unknown, isLoading: false, error: null }
const defaultErrorQuery = { data: undefined, isLoading: false, error: new Error('Failed to fetch') }

vi.mock('../../features/dashboard/useDashboardSummary.ts', () => ({
  useDashboardSummary: vi.fn(() => defaultSummaryQuery),
}))
vi.mock('../../features/dashboard/useDashboardByInvestor.ts', () => ({
  useDashboardByInvestor: vi.fn(() => defaultEmptyQuery),
}))
vi.mock('../../features/dashboard/useDashboardBySecuritiesFirm.ts', () => ({
  useDashboardBySecuritiesFirm: vi.fn(() => defaultEmptyQuery),
}))
vi.mock('../../features/dashboard/useDashboardByPeriod.ts', () => ({
  useDashboardByPeriod: vi.fn(() => defaultEmptyQuery),
}))
vi.mock('../../features/dashboard/useDashboardTabs.ts', () => ({
  useDashboardTabs: vi.fn(() => ({
    currentTab: 'all',
    setCurrentTab: vi.fn(),
    invalidateCurrentTab: vi.fn(),
    getTabQueryConfig: vi.fn(),
  })),
}))
vi.mock('../../features/dashboard/PeriodSelector/PeriodSelector.tsx', () => ({
  default: function MockPeriodSelector(props: {
    unit: string
    initialYear?: number
    onSearch: (year: number) => void
  }) {
    return (
      <div data-testid="period-selector" data-unit={props.unit}>
        <input type="number" data-testid="year-input" defaultValue={props.initialYear} />
        <button
          type="button"
          data-testid="search-button"
          onClick={() => props.onSearch(props.initialYear ?? 2026)}
        >
          조회
        </button>
      </div>
    )
  },
}))

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('기본 탭이 전체로 설정되고 탭이 렌더링됨', async () => {
    renderPage()
    await waitForLoaded()

    expect(screen.getByText('전체')).toBeInTheDocument()
    expect(screen.getByText('투자자별')).toBeInTheDocument()
    expect(screen.getByText('증권사별')).toBeInTheDocument()
    expect(screen.getByText('월별')).toBeInTheDocument()
    expect(screen.getByText('연도별')).toBeInTheDocument()
  })

  it('로딩 상태를 표시함', async () => {
    const { useDashboardSummary } = await import('../../features/dashboard/useDashboardSummary.ts')
    vi.mocked(useDashboardSummary).mockReturnValue({
      data: {
        totalInvestmentAmount: 100000000,
        totalPreTaxProfit: 15000000,
        totalAfterTaxProfit: 12000000,
        totalProfitRate: 12.5,
        participationCount: 45,
      },
      isLoading: true,
      error: null,
    } as any)

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('대시보드 데이터를 불러오는 중...')).toBeInTheDocument()
  })

  it('빈 결과 시 EmptyState가 표시됨', async () => {
    const { useDashboardSummary } = await import('../../features/dashboard/useDashboardSummary.ts')
    const { useDashboardByInvestor } =
      await import('../../features/dashboard/useDashboardByInvestor.ts')
    const { useDashboardBySecuritiesFirm } =
      await import('../../features/dashboard/useDashboardBySecuritiesFirm.ts')
    const { useDashboardByPeriod } =
      await import('../../features/dashboard/useDashboardByPeriod.ts')

    vi.mocked(useDashboardSummary).mockReturnValue(defaultEmptyQuery as any)
    vi.mocked(useDashboardByInvestor).mockReturnValue(defaultEmptyQuery as any)
    vi.mocked(useDashboardBySecuritiesFirm).mockReturnValue(defaultEmptyQuery as any)
    vi.mocked(useDashboardByPeriod).mockReturnValue(defaultEmptyQuery as any)

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('해당 조건에 해당하는 기록이 없습니다.')).toBeInTheDocument()
  })

  it('월별 탭에서 PeriodSelector가 렌더링됨', async () => {
    const { useDashboardByPeriod } =
      await import('../../features/dashboard/useDashboardByPeriod.ts')
    vi.mocked(useDashboardByPeriod).mockReturnValue({
      data: [] as unknown,
      isLoading: false,
      error: null,
    } as any)

    const { useDashboardTabs } = await import('../../features/dashboard/useDashboardTabs.ts')
    vi.mocked(useDashboardTabs).mockReturnValue({
      currentTab: 'month',
      setCurrentTab: vi.fn(),
      invalidateCurrentTab: vi.fn(),
      getTabQueryConfig: vi.fn(),
    })

    renderPage()
    await waitForLoaded()

    expect(screen.getByTestId('period-selector')).toBeInTheDocument()
  })

  it('투자 내역 화면으로 이동 링크가 존재함', async () => {
    renderPage()
    await waitForLoaded()

    const link = screen.getByText('투자 내역 화면으로 이동')
    expect(link.closest('a')).toHaveAttribute('href', '/investments')
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    const { useDashboardSummary } = await import('../../features/dashboard/useDashboardSummary.ts')
    const { useDashboardByInvestor } =
      await import('../../features/dashboard/useDashboardByInvestor.ts')
    const { useDashboardBySecuritiesFirm } =
      await import('../../features/dashboard/useDashboardBySecuritiesFirm.ts')
    const { useDashboardByPeriod } =
      await import('../../features/dashboard/useDashboardByPeriod.ts')

    vi.mocked(useDashboardSummary).mockReturnValue(defaultErrorQuery as any)
    vi.mocked(useDashboardByInvestor).mockReturnValue(defaultErrorQuery as any)
    vi.mocked(useDashboardBySecuritiesFirm).mockReturnValue(defaultErrorQuery as any)
    vi.mocked(useDashboardByPeriod).mockReturnValue(defaultErrorQuery as any)

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
  })
})
