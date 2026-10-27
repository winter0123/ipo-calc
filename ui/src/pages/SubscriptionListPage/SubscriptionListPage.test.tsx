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
    if (path === '/investors') return [INVESTOR]
    if (path === '/ipos')
      return [IPO, { ...IPO, id: 11, ipoName: '다음 달 일정', subscriptionStartDate: '2026-11-01' }]
    if (String(path).startsWith('/investment-history'))
      return {
        content: history,
        totalElements: history.length,
        totalPages: 1,
        number: 0,
        size: 100,
      }
    throw new Error(`unexpected request: ${String(path)}`)
  })
}

describe('SubscriptionListPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('청약 기록이 없어도 기간에 시작하는 IPO를 보이고 등록 링크를 제공한다', async () => {
    mockApi([])
    renderPage('/subscriptions?from=2026-10-01&to=2026-10-31')
    expect(await screen.findByRole('heading', { name: '공모주 청약' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: IPO.ipoName })).toBeInTheDocument()
    expect(screen.getByText('청약 전')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '청약 등록' })).toHaveAttribute(
      'href',
      '/subscriptions/register?ipoId=10',
    )
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
    expect(screen.getByText('청약 완료 · 배정 결과 입력 전')).toBeInTheDocument()
  })

  it('기본 이번 달 기간을 URL에 저장한다', async () => {
    mockApi([])
    renderPage('/subscriptions')
    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(/from=\d{4}-\d{2}-01/),
    )
    expect(screen.getByTestId('search')).toHaveTextContent(/to=\d{4}-\d{2}-\d{2}/)
  })
})
