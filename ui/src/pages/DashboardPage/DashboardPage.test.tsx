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

vi.mock('../../features/dashboard/mock-hooks.ts', () => ({
  useDashboardSummary: vi.fn().mockReturnValue({
    data: { totalInvestmentAmount: 100000000, participationCount: 45 },
    isLoading: false,
    error: null,
  }),
  useDashboardByInvestor: vi.fn().mockReturnValue({
    data: [{ investorName: '김철수', totalAmount: 50000000 }],
    isLoading: false,
    error: null,
  }),
  useDashboardBySecuritiesFirm: vi
    .fn()
    .mockReturnValue({ data: [], isLoading: false, error: null }),
  useDashboardByPeriod: vi.fn().mockReturnValue({ data: [], isLoading: false, error: null }),
}))

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
    const { useDashboardSummary } = await import('../../features/dashboard/mock-hooks.ts')
    vi.mocked(useDashboardSummary).mockReturnValue({
      data: { totalInvestmentAmount: 100000000, participationCount: 45 },
      isLoading: true,
      error: null,
    } as any)

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('대시보드 데이터를 불러오는 중...')).toBeInTheDocument()
  })

  it('빈 결과 시 EmptyState가 표시됨', async () => {
    const { useDashboardSummary } = await import('../../features/dashboard/mock-hooks.ts')
    const { useDashboardByInvestor } = await import('../../features/dashboard/mock-hooks.ts')
    const { useDashboardBySecuritiesFirm } = await import('../../features/dashboard/mock-hooks.ts')
    const { useDashboardByPeriod } = await import('../../features/dashboard/mock-hooks.ts')

    const emptyQuery = { data: [] as unknown, isLoading: false, error: null } as any

    vi.mocked(useDashboardSummary).mockReturnValue(emptyQuery)
    vi.mocked(useDashboardByInvestor).mockReturnValue(emptyQuery)
    vi.mocked(useDashboardBySecuritiesFirm).mockReturnValue(emptyQuery)
    vi.mocked(useDashboardByPeriod).mockReturnValue(emptyQuery)

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('해당 조건에 해당하는 기록이 없습니다.')).toBeInTheDocument()
  })

  it('투자 내역 화면으로 이동 링크가 존재함', async () => {
    renderPage()
    await waitForLoaded()

    const link = screen.getByText('투자 내역 화면으로 이동')
    expect(link.closest('a')).toHaveAttribute('href', '/investments')
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    const { useDashboardSummary } = await import('../../features/dashboard/mock-hooks.ts')
    const { useDashboardByInvestor } = await import('../../features/dashboard/mock-hooks.ts')
    const { useDashboardBySecuritiesFirm } = await import('../../features/dashboard/mock-hooks.ts')
    const { useDashboardByPeriod } = await import('../../features/dashboard/mock-hooks.ts')
    vi.mocked(useDashboardSummary).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch'),
    } as any)
    vi.mocked(useDashboardByInvestor).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch'),
    } as any)
    vi.mocked(useDashboardBySecuritiesFirm).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch'),
    } as any)
    vi.mocked(useDashboardByPeriod).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch'),
    } as any)

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
  })
})
