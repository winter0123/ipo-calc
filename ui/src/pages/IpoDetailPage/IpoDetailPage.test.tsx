import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoDetailPage from './IpoDetailPage.tsx'
import type { Ipo } from '@/entities/ipo/index.ts'

function makeIpo(overrides: Partial<Ipo> = {}): Ipo {
  return {
    id: 7,
    companyName: '테스트기업',
    ipoName: '테스트IPO',
    stockCode: '123456',
    roundLabel: '1',
    lowerBandPrice: 20000,
    upperBandPrice: 25000,
    fixedPrice: 25000,
    subscriptionStartDate: '2026-10-01T09:00:00Z',
    subscriptionEndDate: '2026-10-02T17:00:00Z',
    listingDate: '2026-10-15T09:00:00Z',
    refundDate: null,
    refundDateEstimated: null,
    offeringShareCount: 1000000,
    status: 'ACTIVE',
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
    ...overrides,
  }
}

const INSIGHT = {
  syncedAt: null,
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
  underwriters: [
    { name: '미래에셋증권', role: '주관', shares: 1000 },
    { name: 'KB증권', role: '공동주관', shares: 500 },
  ],
}

const stubFetch = (body: unknown, status = 200) =>
  vi.stubGlobal(
    'fetch',
    vi.fn((input: string) =>
      Promise.resolve(
        new Response(JSON.stringify(String(input).endsWith('/insight') ? INSIGHT : body), {
          status,
        }),
      ),
    ),
  )

function createWrapper(initialEntries: string[] = ['/ipos/7']) {
  const Wrapper = () => (
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/ipos/:ipoId" element={<IpoDetailPage />} />
          <Route path="/login" element={<LoginLocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
  Wrapper.displayName = 'TestWrapper'
  return Wrapper
}

// LoginLocationProbe: /login 경로에서 location.state를 data-testid 요소로 렌더링
function LoginLocationProbe() {
  const location = useLocation()
  // location.state가 있으면 즉시 렌더링, 없으면 no-state
  const stateText = location.state ? JSON.stringify(location.state) : 'no-state'
  return <div data-testid="login-location-state">{stateText}</div>
}

// /auth/me와 /ipos/7을 구분해 응답한다. account가 null이면 로그인 전(401)이다.
const OPEN_PERIOD = {
  subscriptionStartDate: '2026-10-09T09:00:00Z',
  subscriptionEndDate: '2026-10-12T17:00:00Z',
}
const stubIpoAndAccount = (account: object | null, overrides: Partial<Ipo> = OPEN_PERIOD) =>
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/auth/me')
          ? new Response(JSON.stringify(account), { status: account ? 200 : 401 })
          : new Response(JSON.stringify(makeIpo(overrides)), { status: 200 }),
      ),
    ),
  )

describe('IpoDetailPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('정상 조회 시 공모주명, 종목코드, 가격, 주식 수가 보인다', async () => {
    const ipo = makeIpo()
    stubFetch(ipo)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())
    expect(screen.getByText('123456')).toBeInTheDocument()
    expect(screen.getByText('25,000원')).toBeInTheDocument()
    expect(screen.getByText('1,000,000주')).toBeInTheDocument()
    expect(screen.getByText('-')).toBeInTheDocument() // refund date
  })

  it('계산된 환불일은 날짜 뒤에 (예상)이 붙는다', async () => {
    stubFetch(makeIpo({ refundDate: '2026-10-16T00:00:00Z', refundDateEstimated: true }))
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('2026-10-16 (예상)')).toBeInTheDocument())
  })

  it('증권사 데이터의 환불일은 (예상) 없이 날짜만 보인다', async () => {
    stubFetch(makeIpo({ refundDate: '2026-10-16T00:00:00Z', refundDateEstimated: false }))
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('2026-10-16')).toBeInTheDocument())
    expect(screen.queryByText(/예상/)).not.toBeInTheDocument()
  })

  it('상태 칩 4종(청약 예정, 청약 진행 중, 청약 마감, 상장 완료)이 올바르게 표시된다', async () => {
    const ipoBase = makeIpo()

    // 청약 예정: 오늘(2026-10-10) < 시작일(2026-10-20)
    stubFetch({
      ...ipoBase,
      subscriptionStartDate: '2026-10-20T09:00:00Z',
      subscriptionEndDate: '2026-10-21T17:00:00Z',
    })
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('청약 예정')).toBeInTheDocument())

    // 청약 진행 중: 시작일(2026-10-05) <= 오늘(2026-10-10) <= 종료일(2026-10-12)
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    stubFetch({
      ...ipoBase,
      subscriptionStartDate: '2026-10-05T09:00:00Z',
      subscriptionEndDate: '2026-10-12T17:00:00Z',
    })
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('청약 진행 중')).toBeInTheDocument())

    // 청약 마감: 종료일(2026-10-05) < 오늘(2026-10-10) 이고 상장일(2026-10-20) > 오늘
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    stubFetch({
      ...ipoBase,
      subscriptionStartDate: '2026-10-01T09:00:00Z',
      subscriptionEndDate: '2026-10-05T17:00:00Z',
      listingDate: '2026-10-20T09:00:00Z',
    })
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('청약 마감')).toBeInTheDocument())

    // 상장 완료: 상장일(2026-10-05) <= 오늘(2026-10-10)
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    stubFetch(
      makeIpo({
        subscriptionStartDate: '2026-09-20T09:00:00Z',
        subscriptionEndDate: '2026-09-25T17:00:00Z',
        listingDate: '2026-10-05T09:00:00Z',
      }),
    )
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('상장 완료')).toBeInTheDocument())
  })

  it('상장 당일에는 상태를 상장일로 표시한다', async () => {
    vi.setSystemTime(new Date('2026-10-05T12:00:00Z'))
    stubFetch(
      makeIpo({
        subscriptionStartDate: '2026-09-20T09:00:00Z',
        subscriptionEndDate: '2026-09-25T17:00:00Z',
        listingDate: '2026-10-05T09:00:00Z',
      }),
    )

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => {
      expect(screen.getByText('상장일', { selector: 'span' })).toBeInTheDocument()
    })
  })

  it('상태 옆에 주관사 전체 이름을 표시한다', async () => {
    stubFetch(makeIpo())
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    expect(await screen.findByText('주관사 미래에셋증권 · KB증권')).toBeInTheDocument()
  })

  it('null 필드는 "-"로 표시된다', async () => {
    const ipo = makeIpo({
      stockCode: null,
      roundLabel: null,
      listingDate: null,
      fixedPrice: null,
      lowerBandPrice: null,
      upperBandPrice: null,
      offeringShareCount: null,
    })
    stubFetch(ipo)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getAllByText('-').length).toBeGreaterThanOrEqual(5))
  })

  it('희망 공모가: 둘 다 있을 때 "하단~상단원", 하나만 있으면 있는 값만, 둘 다 없으면 "-"', async () => {
    // 둘 다 있음
    stubFetch(makeIpo({ lowerBandPrice: 20000, upperBandPrice: 25000, fixedPrice: null }))
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('20,000~25,000원')).toBeInTheDocument())

    // 하단만 있음
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    stubFetch(makeIpo({ lowerBandPrice: 20000, upperBandPrice: null, fixedPrice: null }))
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('20,000원')).toBeInTheDocument())

    // 상단만 있음
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    stubFetch(makeIpo({ lowerBandPrice: null, upperBandPrice: 25000, fixedPrice: null }))
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('25,000원')).toBeInTheDocument())

    // 둘 다 없음
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    stubFetch(makeIpo({ lowerBandPrice: null, upperBandPrice: null, fixedPrice: null }))
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getAllByText('-').length).toBeGreaterThanOrEqual(5))
  })

  it('404 에러 시 "공모주를 찾을 수 없습니다."가 보인다', async () => {
    stubFetch({ message: 'Not found' }, 404)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/999']) })

    await vi.waitFor(() =>
      expect(screen.getByText('공모주를 찾을 수 없습니다.')).toBeInTheDocument(),
    )
  })

  it('숫자가 아닌 id(예: /ipos/abc)일 때 "공모주를 찾을 수 없습니다."가 보인다', async () => {
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/abc']) })

    expect(screen.getByText('공모주를 찾을 수 없습니다.')).toBeInTheDocument()
  })

  it('로딩 중일 때 Spinner가 보인다', async () => {
    let resolveFetch: (value: unknown) => void
    const promise = new Promise((resolve) => {
      resolveFetch = resolve
    })
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(promise))

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    expect(screen.getByRole('status')).toHaveTextContent('공모주 정보를 불러오는 중...')

    resolveFetch!(new Response(JSON.stringify(makeIpo())))
    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())
  })

  it('공모주명(h1)과 회사명이 헤더에 표시된다', async () => {
    const ipo = makeIpo({ ipoName: '내공모주', companyName: '내회사' })
    stubFetch(ipo)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => {
      const h1 = screen.getByRole('heading', { level: 1 })
      expect(h1).toHaveTextContent('내공모주')
      expect(screen.getByText('내회사')).toBeInTheDocument()
    })
  })

  it('로그인한 경우 청약 등록 링크는 /subscriptions/register로 간다', async () => {
    stubIpoAndAccount({ accountId: 1, email: 'a@b.com', name: '나', nickname: '나', role: 'USER' })

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())
    expect(await screen.findByRole('link', { name: '청약 등록하기' })).toHaveAttribute(
      'href',
      '/subscriptions/register?ipoId=7',
    )
  })

  it('청약 기간이 끝났으면 청약 등록 링크가 보이지 않는다', async () => {
    stubIpoAndAccount(
      { accountId: 1, email: 'a@b.com', name: '나', nickname: '나', role: 'USER' },
      {},
    )

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())
    expect(screen.queryByRole('link', { name: '청약 등록하기' })).not.toBeInTheDocument()
  })

  it('로그인 전에는 청약 등록 대신 로그인 링크가 보인다', async () => {
    stubIpoAndAccount(null)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    expect(await screen.findByRole('link', { name: '로그인하고 청약 등록하기' })).toHaveAttribute(
      'href',
      '/login',
    )
    expect(screen.queryByRole('link', { name: '청약 등록하기' })).not.toBeInTheDocument()
  })

  it('로그인한 사용자는 헤더 CTA에 청약 등록 링크 1개와 매도 등록 링크 1개가 있다', async () => {
    stubIpoAndAccount({ accountId: 1, email: 'a@b.com', name: '나', nickname: '나', role: 'USER' })

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())

    // 헤더 CTA 영역의 링크만 확인 (하단 nav에는 링크가 없어야 함)
    const headerCtaLinks = screen.getAllByRole('link')
    const registerLinks = headerCtaLinks.filter((link) => link.textContent === '청약 등록하기')
    const saleLinks = headerCtaLinks.filter((link) => link.textContent === '매도 등록하기')

    expect(registerLinks).toHaveLength(1)
    expect(registerLinks[0]).toHaveAttribute('href', '/subscriptions/register?ipoId=7')

    expect(saleLinks).toHaveLength(1)
    expect(saleLinks[0]).toHaveAttribute('href', '/sales/register?ipoId=7')
  })

  it('청약 종료 후에는 청약 등록 링크가 없고 매도 등록 링크만 1개 남는다', async () => {
    stubIpoAndAccount(
      { accountId: 1, email: 'a@b.com', name: '나', nickname: '나', role: 'USER' },
      {},
    )

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())
    expect(screen.queryByRole('link', { name: '청약 등록하기' })).not.toBeInTheDocument()

    // 매도 등록 링크는 정확히 1개만 있어야 함 (헤더 CTA에만)
    const saleLinks = screen.getAllByRole('link', { name: '매도 등록하기' })
    expect(saleLinks).toHaveLength(1)
    expect(saleLinks[0]).toHaveAttribute('href', '/sales/register?ipoId=7')
  })

  it('비로그인 사용자의 매도 CTA는 로그인 경로 및 from 상태를 보존한다', async () => {
    stubIpoAndAccount(null)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())

    // 비로그인 시 매도 CTA는 로그인 링크로 연결되고 state.from이 설정되어야 함
    const saleLink = await screen.findByRole('link', { name: '로그인하고 매도 등록하기' })
    expect(saleLink).toHaveAttribute('href', '/login')

    // Link 클릭으로 navigation 발생
    await userEvent.click(saleLink)

    // /login 경로로 이동하고 LoginLocationProbe가 location.state를 렌더링했는지 확인
    await vi.waitFor(() => {
      const stateElement = screen.getByTestId('login-location-state')
      const text = stateElement.textContent || ''
      expect(text).not.toBe('no-state')
      const state = JSON.parse(text)
      expect(state).toEqual({ from: '/sales/register?ipoId=7' })
    })
  })
})
