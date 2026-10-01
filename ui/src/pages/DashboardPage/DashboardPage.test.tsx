import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
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

// Mock the hooks
vi.mock('../../features/dashboard/useDashboardSummary.ts')
vi.mock('../../features/dashboard/useDashboardByInvestor.ts')
vi.mock('../../features/dashboard/useDashboardBySecuritiesFirm.ts')
vi.mock('../../features/dashboard/useDashboardByPeriod.ts')
vi.mock('../../features/dashboard/useDashboardTabs.ts')
vi.mock('../../features/dashboard/PeriodSelector/PeriodSelector.tsx', () => ({
  default: function MockPeriodSelector(props: {
    unit: string
    initialYear?: number
    initialMonth?: number
    onSearch: (year: number, month?: number) => void
  }) {
    const [year, setYear] = useState(props.initialYear ?? 2026)
    const [month, setMonth] = useState(props.initialMonth ?? 9)

    const handlePrevPeriod = () => {
      if (props.unit === 'month') {
        if (month === 1) {
          const newYear = Math.max(2000, year - 1)
          setMonth(12)
          setYear(newYear)
          props.onSearch(newYear, 12)
        } else {
          const newMonth = month - 1
          setMonth(newMonth)
          props.onSearch(year, newMonth)
        }
      } else {
        const newYear = Math.max(2000, year - 1)
        setYear(newYear)
        props.onSearch(newYear)
      }
    }

    const handleNextPeriod = () => {
      if (props.unit === 'month') {
        if (month === 12) {
          if (year < 2026) {
            const newYear = year + 1
            setMonth(1)
            setYear(newYear)
            props.onSearch(newYear, 1)
          }
        } else if (year < 2026 || (year === 2026 && month < 9)) {
          const newMonth = month + 1
          setMonth(newMonth)
          props.onSearch(year, newMonth)
        }
      } else if (year < 2026) {
        const newYear = year + 1
        setYear(newYear)
        props.onSearch(newYear)
      }
    }

    return (
      <div data-testid="period-selector" data-unit={props.unit}>
        <h2 id="period-heading" data-testid="period-heading">
          {props.unit === 'month' ? `${year}년 ${month}월` : `${year}년`}
        </h2>
        <button
          type="button"
          data-testid="prev-button"
          onClick={handlePrevPeriod}
          disabled={year <= 2000 && (props.unit !== 'month' || month <= 1)}
        >
          {props.unit === 'month' ? '◀ 이전 달' : '◀ 이전 연도'}
        </button>
        <input
          type="number"
          data-testid="year-input"
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
        />
        {props.initialMonth && (
          <select
            data-testid="month-input"
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
          >
            <option value="1">1월</option>
            <option value="2">2월</option>
            <option value="3">3월</option>
            <option value="4">4월</option>
            <option value="5">5월</option>
            <option value="6">6월</option>
            <option value="7">7월</option>
            <option value="8">8월</option>
            <option value="9">9월</option>
            <option value="10">10월</option>
            <option value="11">11월</option>
            <option value="12">12월</option>
          </select>
        )}
        <button
          type="button"
          data-testid="next-button"
          onClick={handleNextPeriod}
          disabled={
            (props.unit === 'month' && (year > 2026 || (year === 2026 && month >= 9))) ||
            (props.unit === 'year' && year >= 2026)
          }
        >
          {props.unit === 'month' ? '다음 달 ▶' : '다음 연도 ▶'}
        </button>
        <button
          type="button"
          data-testid="search-button"
          onClick={() => props.onSearch(year, props.unit === 'month' ? month : undefined)}
        >
          조회
        </button>
      </div>
    )
  },
}))

import { useDashboardSummary } from '../../features/dashboard/useDashboardSummary.ts'
import { useDashboardByInvestor } from '../../features/dashboard/useDashboardByInvestor.ts'
import { useDashboardBySecuritiesFirm } from '../../features/dashboard/useDashboardBySecuritiesFirm.ts'
import { useDashboardByPeriod } from '../../features/dashboard/useDashboardByPeriod.ts'
import { useDashboardTabs } from '../../features/dashboard/useDashboardTabs.ts'

function asResult<T>(value: unknown): T {
  return value as T
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-15T12:00:00'))
    vi.resetAllMocks()

    vi.mocked(useDashboardSummary).mockReturnValue(asResult(defaultSummaryQuery))
    vi.mocked(useDashboardByInvestor).mockReturnValue(asResult(defaultEmptyQuery))
    vi.mocked(useDashboardBySecuritiesFirm).mockReturnValue(asResult(defaultEmptyQuery))
    vi.mocked(useDashboardByPeriod).mockReturnValue(asResult(defaultEmptyQuery))
    vi.mocked(useDashboardTabs).mockReturnValue({
      currentTab: 'all',
      setCurrentTab: vi.fn(),
      invalidateCurrentTab: vi.fn(),
    })
  })

  afterEach(() => {
    vi.useRealTimers()
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
    vi.mocked(useDashboardSummary).mockReturnValue(
      asResult({
        data: {
          totalInvestmentAmount: 100000000,
          totalPreTaxProfit: 15000000,
          totalAfterTaxProfit: 12000000,
          totalProfitRate: 12.5,
          participationCount: 45,
        },
        isLoading: true,
        error: null,
      }),
    )

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('대시보드 데이터를 불러오는 중...')).toBeInTheDocument()
  })

  it('빈 결과 시 EmptyState가 표시됨', async () => {
    vi.mocked(useDashboardSummary).mockReturnValue(
      asResult({
        data: {
          totalInvestmentAmount: 0,
          totalPreTaxProfit: 0,
          totalAfterTaxProfit: 0,
          totalProfitRate: 0,
          participationCount: 0,
        },
        isLoading: false,
        error: null,
      }),
    )
    vi.mocked(useDashboardByInvestor).mockReturnValue(asResult(defaultEmptyQuery))
    vi.mocked(useDashboardBySecuritiesFirm).mockReturnValue(asResult(defaultEmptyQuery))
    vi.mocked(useDashboardByPeriod).mockReturnValue(asResult(defaultEmptyQuery))

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('해당 조건에 해당하는 기록이 없습니다.')).toBeInTheDocument()
  })

  it('월별 탭에서 PeriodSelector가 렌더링되고 현재 월이 기본값으로 설정됨 (AC-1920)', async () => {
    vi.mocked(useDashboardByPeriod).mockReturnValue(
      asResult({
        data: [] as unknown,
        isLoading: false,
        error: null,
      }),
    )

    vi.mocked(useDashboardTabs).mockReturnValue({
      currentTab: 'month',
      setCurrentTab: vi.fn(),
      invalidateCurrentTab: vi.fn(),
    })

    renderPage()
    await waitForLoaded()

    expect(screen.getByTestId('period-selector')).toBeInTheDocument()
    expect(screen.getByTestId('year-input')).toHaveValue(2026)
    expect(screen.getByTestId('month-input')).toHaveValue('9')
  })

  it('연도별 탭에서 PeriodSelector가 렌더링되고 현재 연도가 기본값으로 설정됨', async () => {
    vi.mocked(useDashboardByPeriod).mockReturnValue(
      asResult({
        data: [] as unknown,
        isLoading: false,
        error: null,
      }),
    )

    vi.mocked(useDashboardTabs).mockReturnValue({
      currentTab: 'year',
      setCurrentTab: vi.fn(),
      invalidateCurrentTab: vi.fn(),
    })

    renderPage()
    await waitForLoaded()

    expect(screen.getByTestId('period-selector')).toBeInTheDocument()
    expect(screen.getByTestId('year-input')).toHaveValue(2026)
    expect(screen.queryByTestId('month-input')).not.toBeInTheDocument()
  })

  it('투자 내역 화면으로 이동 링크가 존재함', async () => {
    renderPage()
    await waitForLoaded()

    const link = screen.getByText('투자 내역 화면으로 이동')
    expect(link.closest('a')).toHaveAttribute('href', '/investments')
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    vi.mocked(useDashboardSummary).mockReturnValue(asResult(defaultErrorQuery))
    vi.mocked(useDashboardByInvestor).mockReturnValue(asResult(defaultErrorQuery))
    vi.mocked(useDashboardBySecuritiesFirm).mockReturnValue(asResult(defaultErrorQuery))
    vi.mocked(useDashboardByPeriod).mockReturnValue(asResult(defaultErrorQuery))

    renderPage()
    await waitForLoaded()

    expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
  })

  it('월별 탭에서 "이전 달" 클릭 시 getDashboardByPeriod가 전월 인자로 호출되고 제목이 갱신됨 (조회 버튼 없이)', async () => {
    vi.mocked(useDashboardByPeriod).mockReturnValue(
      asResult({
        data: [] as unknown,
        isLoading: false,
        error: null,
      }),
    )

    vi.mocked(useDashboardTabs).mockReturnValue({
      currentTab: 'month',
      setCurrentTab: vi.fn(),
      invalidateCurrentTab: vi.fn(),
    })

    renderPage()
    await waitForLoaded()

    // 초기 상태 확인 (2026년 9월)
    expect(screen.getByTestId('period-heading')).toHaveTextContent('2026년 9월')

    // "이전 달" 버튼 클릭
    fireEvent.click(screen.getByTestId('prev-button'))

    // 제목이 2026년 8월로 변경됨
    expect(screen.getByTestId('period-heading')).toHaveTextContent('2026년 8월')

    // useDashboardByPeriod가 2026, 8로 호출되었는지 확인
    expect(vi.mocked(useDashboardByPeriod)).toHaveBeenCalledWith(
      expect.objectContaining({ year: 2026, month: 8 }),
    )
  })

  it('월별 탭에서 "다음 달" 클릭 시 getDashboardByPeriod가 다음 월 인자로 호출되고 제목이 갱신됨', async () => {
    vi.mocked(useDashboardByPeriod).mockReturnValue(
      asResult({
        data: [] as unknown,
        isLoading: false,
        error: null,
      }),
    )

    vi.mocked(useDashboardTabs).mockReturnValue({
      currentTab: 'month',
      setCurrentTab: vi.fn(),
      invalidateCurrentTab: vi.fn(),
    })

    renderPage()
    await waitForLoaded()

    // 초기 상태 확인 (2026년 9월)
    expect(screen.getByTestId('period-heading')).toHaveTextContent('2026년 9월')

    // "다음 달" 버튼 클릭 (9월은 현재 월이므로 비활성화됨, 8월로 먼저 이동)
    fireEvent.click(screen.getByTestId('prev-button')) // 2026년 8월
    await waitFor(() => {
      expect(screen.getByTestId('period-heading')).toHaveTextContent('2026년 8월')
    })

    fireEvent.click(screen.getByTestId('next-button')) // 2026년 9월

    // 제목이 2026년 9월로 변경됨
    expect(screen.getByTestId('period-heading')).toHaveTextContent('2026년 9월')

    // useDashboardByPeriod가 2026, 9로 호출되었는지 확인
    expect(vi.mocked(useDashboardByPeriod)).toHaveBeenCalledWith(
      expect.objectContaining({ year: 2026, month: 9 }),
    )
  })
})
