import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SubscriptionListPage from './SubscriptionListPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const IPO = {
  id: 10,
  companyName: '한 회사',
  ipoName: '가나다 공모주',
  stockCode: null,
  roundLabel: null,
  lowerBandPrice: 10000,
  upperBandPrice: 12000,
  fixedPrice: null,
  listingOpenPrice: null,
  subscriptionStartDate: '2026-10-12',
  subscriptionEndDate: '2026-10-13',
  listingDate: null,
  refundDate: null,
  refundDateEstimated: null,
  offeringShareCount: null,
  status: 'ACTIVE',
  createdAt: '',
  updatedAt: '',
}
const HISTORY = {
  id: 7,
  subscribedAt: '2026-10-12T09:00:00',
  ipoId: 10,
  ipoName: IPO.ipoName,
  ipoFixedPrice: null,
  subscriptionQuantity: 100,
  subscriptionStatus: 'ACTIVE',
  allocation: null,
  sales: [],
  remainingQuantity: null,
  investorName: null,
  securitiesFirmName: '한국투자증권',
  securitiesAccountId: 33,
  securitiesAccountName: '주력 계좌',
}
const INVESTOR = {
  id: 1,
  displayName: '홍길동',
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '',
  updatedAt: '',
}

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="search">{location.search}</p>
}

function renderPage(entry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[entry]}>
        <SubscriptionListPage />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function mockApi(history: unknown[] = [HISTORY]) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return [INVESTOR]
    if (pathStr === '/ipos')
      return [IPO, { ...IPO, id: 11, ipoName: '다음 달 일정', subscriptionStartDate: '2026-11-01' }]
    if (pathStr.includes('/insight'))
      return {
        syncedAt: '2026-10-01T00:00:00Z',
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
        floatSchedule: [],
        underwriters: [],
      }
    if (pathStr.startsWith('/investment-history'))
      return {
        content: history,
        totalElements: history.length,
        totalPages: 1,
        number: 0,
        size: 100,
      }
    throw new Error(`unexpected request: ${pathStr}`)
  })
}

describe('SubscriptionListPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('청약 기록이 없어도 기간에 시작하는 IPO를 보이고 등록 링크를 제공한다', async () => {
    mockApi([])
    renderPage('/subscriptions?from=2026-10-01&to=2026-10-31')
    expect(await screen.findByRole('heading', { name: '공모주 청약' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: IPO.ipoName })).toBeInTheDocument()
    // 청약 전 상태는 pill로 표시되지 않음
    expect(screen.queryByText('청약 전')).not.toBeInTheDocument()
    // 카드 내의 청약 등록 링크 (상단 버튼 그룹이 아닌 카드 액션 그룹)
    const card = screen.getByText('가나다 공모주').closest('li')
    const cardActionLink = card?.querySelector('a[href="/subscriptions/register?ipoId=10"]')
    expect(cardActionLink).toBeInTheDocument()
    expect(screen.queryByText('다음 달 일정')).not.toBeInTheDocument()
  })

  it('청약 시작일 오름차순으로 표시하고 청약별 계좌와 배정 입력 이동을 보여준다', async () => {
    mockApi([HISTORY])
    renderPage('/subscriptions?from=2026-10-01&to=2026-10-31')
    expect(await screen.findByText(/한국투자증권 · 주력 계좌/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '배정 등록' })).toHaveAttribute(
      'href',
      '/allocations/register?subscriptionId=7',
    )
    expect(screen.getByText('청약 진행 중')).toBeInTheDocument()
  })

  it('기본 이번 달 기간을 URL에 저장한다', async () => {
    mockApi([])
    renderPage('/subscriptions')
    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(/from=\d{4}-\d{2}-01/),
    )
    expect(screen.getByTestId('search')).toHaveTextContent(/to=\d{4}-\d{2}-\d{2}/)
  })

  it('상단 버튼 순서가 왼쪽 청약 등록, 오른쪽 매도 등록이며 청약 버튼은 "청약 등록" 텍스트를 가진다', async () => {
    mockApi([])
    renderPage('/subscriptions?from=2026-10-01&to=2026-10-31')

    // titleRow 내의 버튼 그룹 찾기 (h1 다음의 첫 번째 div)
    const titleRow = screen.getByText('공모주 청약').closest('div')?.parentElement
    const buttonGroup = titleRow?.querySelector('.s-d6e906b-buttonGroup')
    const buttons = buttonGroup?.querySelectorAll('a') ?? []

    // 첫 번째 버튼이 청약 등록, 두 번째가 매도 등록
    expect(buttons[0]).toHaveTextContent('청약 등록')
    expect(buttons[0]).toHaveAttribute('href', '/subscriptions/register')
    expect(buttons[1]).toHaveTextContent('매도 등록')
    expect(buttons[1]).toHaveAttribute('href', '/sales/register')
  })

  it('투자자가 없을 때 선택할 투자자가 없습니다 메시지가 표시된다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      if (path === '/investors') return []
      if (path === '/ipos') return [IPO]
      throw new Error(`unexpected request: ${String(path)}`)
    })
    renderPage('/subscriptions?from=2026-10-01&to=2026-10-31')
    expect(await screen.findByText('선택할 투자자가 없습니다.')).toBeInTheDocument()
  })
})
