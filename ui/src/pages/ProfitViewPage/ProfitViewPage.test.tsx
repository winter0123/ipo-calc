import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProfitViewPage from './ProfitViewPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

// jsdom에는 크기가 없어 차트를 그리지 못한다. 차트 대신 같은 데이터를 담은 기간별 표로 검증한다.
vi.mock('@/features/profit-view/ProfitPeriod/ProfitLineChart.tsx', () => ({
  default: () => null,
}))

vi.mock('@/entities/investor/api-client.ts', async () => {
  const actual = await vi.importActual('@/entities/investor/api-client.ts')
  return {
    ...actual,
    fetchInvestors: vi.fn(),
  }
})

import { fetchInvestors } from '@/entities/investor/api-client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

const INVESTORS = [
  {
    id: 1,
    displayName: '홍길동',
    accountId: 1,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
  {
    id: 2,
    displayName: '김철수',
    accountId: 1,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
]

const INVESTOR_PROFITS = {
  investorId: 1,
  sales: [
    {
      saleId: 1,
      saleDate: '2026-09-21T10:00:00Z',
      allocationId: 10,
      quantity: 50,
      saleAmount: 2000000,
      matchedAcquisitionAmount: 1500000,
      saleFee: 5000,
      saleTax: 30000,
      saleOtherCost: 1000,
      preTaxProfit: 364000,
      afterTaxProfit: 361000,
      realizedProfit: 366000,
      listingDate: '2026-09-21',
      ipoName: 'A공모주',
    },
    {
      saleId: 2,
      saleDate: '2026-09-22T10:00:00Z',
      allocationId: 11,
      quantity: 30,
      saleAmount: 1200000,
      matchedAcquisitionAmount: 900000,
      saleFee: 3000,
      saleTax: 18000,
      saleOtherCost: 500,
      preTaxProfit: -5000,
      afterTaxProfit: -8000,
      realizedProfit: -5000,
      listingDate: null,
      ipoName: null,
    },
  ],
  totalSaleAmount: 3200000,
  totalPreTaxProfit: 359000,
  totalAfterTaxProfit: 353000,
  totalRealizedProfit: 361000,
}

type Sale = (typeof INVESTOR_PROFITS)['sales'][number]

const SALES_BY_INVESTOR: Record<string, Sale[]> = {
  '1': INVESTOR_PROFITS.sales,
  '2': [],
}

/**
 * 서버의 기간 필터를 흉내낸다 — `from`/`to` 쿼리를 보고 해당 기간의 매도만
 * 돌려주고 합계도 걸러진 목록 기준으로 다시 계산한다.
 */
function serverProfits(path: string) {
  const [pathname, search = ''] = path.split('?')
  const match = /^\/investors\/(\d+)\/profits$/.exec(pathname)
  const sales = match ? SALES_BY_INVESTOR[match[1]] : undefined
  if (!match || !sales) throw new Error(`unexpected path: ${path}`)

  const params = new URLSearchParams(search)
  const from = params.get('from')
  const to = params.get('to')
  const kept = sales.filter((s) => {
    const day = s.saleDate.slice(0, 10)
    return (from === null || day >= from) && (to === null || day <= to)
  })
  const sum = (pick: (s: Sale) => number) => kept.reduce((acc, s) => acc + pick(s), 0)
  return {
    investorId: Number(match[1]),
    sales: kept,
    totalSaleAmount: sum((s) => s.saleAmount ?? 0),
    totalPreTaxProfit: sum((s) => s.preTaxProfit),
    totalAfterTaxProfit: sum((s) => s.afterTaxProfit),
    totalRealizedProfit: sum((s) => s.realizedProfit),
  }
}

const profitPaths = () =>
  vi
    .mocked(apiFetch)
    .mock.calls.map(([path]) => String(path))
    .filter((path) => path.includes('/profits'))

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProfitViewPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function setupDefaultMocks() {
  vi.mocked(fetchInvestors).mockResolvedValue(INVESTORS)
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => serverProfits(String(path)))
}

describe('ProfitViewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('currentInvestorId', '1')
    setupDefaultMocks()
    // 기본 기간(이번 달 1일~오늘)이 테스트 데이터(2026-09-21·22)를 포함하도록 시계를 맞춘다.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-30T12:00:00'))
  })

  afterEach(() => vi.useRealTimers())

  it('기간 기본값은 접속한 달 1일부터 오늘까지고 그대로 서버에 보낸다', async () => {
    renderPage()
    expect(screen.getByLabelText('시작일')).toHaveValue('2026-09-01')
    expect(screen.getByLabelText('종료일')).toHaveValue('2026-09-30')
    await waitFor(() =>
      expect(profitPaths()).toContain('/investors/1/profits?from=2026-09-01&to=2026-09-30'),
    )
  })

  it('조회 버튼 없이 기간 안의 매도를 최신순으로 보여주고 합계를 낸다', async () => {
    renderPage()

    await waitFor(() => expect(screen.getAllByText('홍길동').length).toBe(2)) // 드롭다운 1 + 행 2)
    expect(screen.queryByRole('button', { name: /^조회/ })).not.toBeInTheDocument()
    const rows = within(screen.getByRole('region', { name: '매도별 수익 표' })).getAllByRole('row')
    // 헤더, 9/22, 9/21, 합계
    expect(rows[1]).toHaveTextContent('2026. 9. 22.')
    expect(rows[2]).toHaveTextContent('2026. 9. 21.')
    expect(screen.getByText('366,000')).toBeInTheDocument()
    expect(screen.getAllByText('3,200,000').length).toBeGreaterThan(0)
    expect(screen.getByText('3,200,000원')).toBeInTheDocument()
    expect(screen.getAllByText('361,000').length).toBeGreaterThan(0)
    expect(screen.getAllByText('353,000').length).toBeGreaterThan(0)
    expect(screen.getByRole('columnheader', { name: '실현손익' })).toBeInTheDocument()
    // 기간별 표 + 매도 내역 표
    expect(screen.getAllByRole('columnheader', { name: '최종수익금' })).toHaveLength(2)
  })

  it('음수 수익(손실)이 표시된다', async () => {
    renderPage()
    await waitFor(() => expect(screen.getAllByText('-5,000')).toHaveLength(1))
    expect(screen.getByText('-8,000')).toBeInTheDocument()
  })

  it('시작일을 바꾸면 그 기간으로 다시 조회한다', async () => {
    renderPage()
    await waitFor(() =>
      expect(profitPaths()).toContain('/investors/1/profits?from=2026-09-01&to=2026-09-30'),
    )
    await fill(screen.getByLabelText('시작일'), '2026-09-22')
    await waitFor(() =>
      expect(profitPaths()).toContain('/investors/1/profits?from=2026-09-22&to=2026-09-30'),
    )
  })

  it('기간을 좁히면 그 기간의 매도만 보이고 합계도 다시 계산된다', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('366,000')).toBeInTheDocument())
    await fill(screen.getByLabelText('시작일'), '2026-09-22')
    await waitFor(() => {
      expect(screen.queryByText('366,000')).not.toBeInTheDocument()
      expect(screen.getAllByText('1,200,000').length).toBeGreaterThan(1)
    })
  })

  it('기간 안에 매도가 없으면 안내가 보인다', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('366,000')).toBeInTheDocument())
    await fill(screen.getByLabelText('시작일'), '2026-08-01')
    await fill(screen.getByLabelText('종료일'), '2026-08-31')
    expect(await screen.findByText('이 기간에 매도 기록이 없습니다.')).toBeInTheDocument()
    await waitFor(() =>
      expect(profitPaths()).toContain('/investors/1/profits?from=2026-08-01&to=2026-08-31'),
    )
  })

  it('시작일이 종료일보다 늦으면 조회하지 않고 안내만 보인다', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('366,000')).toBeInTheDocument())
    vi.mocked(apiFetch).mockClear()

    await fill(screen.getByLabelText('종료일'), '2026-08-01')

    expect(screen.getByRole('alert')).toHaveTextContent('시작일이 종료일보다 늦습니다.')
    expect(screen.getByText('이 기간에 매도 기록이 없습니다.')).toBeInTheDocument()
    expect(profitPaths()).toHaveLength(0)
  })

  it('현재 투자자의 매도만 보인다', async () => {
    localStorage.setItem('currentInvestorId', '2')
    renderPage()
    expect(await screen.findByText('이 기간에 매도 기록이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText('366,000')).not.toBeInTheDocument()
  })

  it('매도가 없으면 매도 등록 링크가 보인다', async () => {
    localStorage.setItem('currentInvestorId', '2')
    renderPage()
    expect(await screen.findByRole('link', { name: '매도 등록' })).toHaveAttribute(
      'href',
      '/sales/register',
    )
  })

  it('기타비용 열이 없고 수익률 카드가 보인다', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('3,200,000원')).toBeInTheDocument())
    expect(screen.queryByRole('columnheader', { name: '기타비용' })).not.toBeInTheDocument()
    // 최종수익금 353,000 / 취득금액 2,400,000
    expect(screen.getByText('14.71%')).toBeInTheDocument()
  })

  it('프리셋: 지난 달·올해·전체가 기간을 바꾸고 선택 상태를 보여 준다', async () => {
    const user = userEvent.setup()
    renderPage()
    expect(screen.getByRole('button', { name: '이번 달' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: '지난 달' }))
    expect(screen.getByLabelText('시작일')).toHaveValue('2026-08-01')
    expect(screen.getByLabelText('종료일')).toHaveValue('2026-08-31')
    expect(screen.getByRole('button', { name: '지난 달' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '이번 달' })).toHaveAttribute('aria-pressed', 'false')

    await user.click(screen.getByRole('button', { name: '올해' }))
    expect(screen.getByLabelText('시작일')).toHaveValue('2026-01-01')

    await user.click(screen.getByRole('button', { name: '전체' }))
    expect(screen.getByLabelText('시작일')).toHaveValue('')
    await waitFor(() => expect(profitPaths()).toContain('/investors/1/profits'))
  })

  it('기간별 수익: 기본은 월 단위, 연·일로 바꾸면 표가 바뀐다', async () => {
    const user = userEvent.setup()
    renderPage()
    const section = await screen.findByRole('region', { name: '기간별 수익' })
    expect(within(section).getByRole('button', { name: '월' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    // 9월: 2건, 매도금액 3,200,000, 최종수익금 361,000 - 8,000 = 353,000
    const month = within(section).getByRole('row', { name: /2026\. 9\./ })
    expect(month).toHaveTextContent('23,200,000353,000')

    await user.click(within(section).getByRole('button', { name: '연' }))
    expect(within(section).getByRole('row', { name: /2026년/ })).toHaveTextContent('2')

    await user.click(within(section).getByRole('button', { name: '일' }))
    expect(within(section).getByRole('row', { name: /2026\. 9\. 21\./ })).toHaveTextContent(
      '361,000',
    )
    expect(within(section).getByRole('row', { name: /2026\. 9\. 22\./ })).toHaveTextContent(
      '-8,000',
    )
    expect(within(section).getByRole('row', { name: /합계/ })).toHaveTextContent('353,000')
  })

  it('기간 안에 매도가 없으면 기간별 수익 구역 없이 빈 상태만 보인다', async () => {
    localStorage.setItem('currentInvestorId', '2')
    renderPage()
    expect(await screen.findByText('이 기간에 매도 기록이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '기간별 수익' })).not.toBeInTheDocument()
  })

  it('조회 실패 시 오류 메시지가 보인다', async () => {
    vi.mocked(apiFetch).mockRejectedValue(new ApiError(500, 'boom'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('수익 조회에 실패했습니다.')
  })

  it('CSV로 내보내기 버튼이 다운로드를 호출한다', async () => {
    const create = vi.fn(() => 'blob:x')
    const revoke = vi.fn()
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'CSV로 내보내기' }))
    expect(create).toHaveBeenCalledTimes(1)
    expect(click).toHaveBeenCalled()
    expect(revoke).toHaveBeenCalledWith('blob:x')
  })
})
