import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
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

function createWrapper(initialEntries: string[] = ['/ipos/7']) {
  const Wrapper = () => (
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/ipos/:ipoId" element={<IpoDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
  Wrapper.displayName = 'TestWrapper'
  return Wrapper
}

const stubFetch = (body: unknown, status = 200) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))

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

   it('환불일이 표시된다', async () => {
     // 환불일이 있고 예상치인 경우
     const ipoWithRefundEstimated = makeIpo({
       refundDate: '2026-10-16T00:00:00Z',
       refundDateEstimated: true,
     })
     stubFetch(ipoWithRefundEstimated)
     render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
     await vi.waitFor(() => expect(screen.getByText('2026-10-16 (예상)')).toBeInTheDocument())

     // 환불일이 있고 예상치가 아닌 경우
     const ipoWithRefundNotEstimated = makeIpo({
       refundDate: '2026-10-16T00:00:00Z',
       refundDateEstimated: false,
     })
     stubFetch(ipoWithRefundNotEstimated)
     render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
     await vi.waitFor(() => expect(screen.getByText('2026-10-16')).toBeInTheDocument())
     // Should not contain "(예상)"
     expect(screen.queryByText('2026-10-16 (예상)')).not.toBeInTheDocument()
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
    stubFetch({
      ...ipoBase,
      subscriptionStartDate: '2026-09-20T09:00:00Z',
      subscriptionEndDate: '2026-09-25T17:00:00Z',
      listingDate: '2026-10-05T09:00:00Z',
    })
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })
    await vi.waitFor(() => expect(screen.getByText('상장 완료')).toBeInTheDocument())
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

  it('404 에러 시 "공모주를 찾을 수 없습니다."와 돌아가기 링크가 보인다', async () => {
    stubFetch({ message: 'Not found' }, 404)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/999']) })

    await vi.waitFor(() =>
      expect(screen.getByText('공모주를 찾을 수 없습니다.')).toBeInTheDocument(),
    )
    expect(screen.getByRole('link', { name: '공모주 일정으로 돌아가기' })).toBeInTheDocument()
  })

  it('숫자가 아닌 id(예: /ipos/abc)일 때 "공모주를 찾을 수 없습니다."가 보인다', async () => {
    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/abc']) })

    expect(screen.getByText('공모주를 찾을 수 없습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '공모주 일정으로 돌아가기' })).toBeInTheDocument()
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

  it('돌아가기 링크는 /ipo-calendar로, 청약 등록 링크는 /subscriptions/register로 간다', async () => {
    const ipo = makeIpo()
    stubFetch(ipo)

    render(<IpoDetailPage />, { wrapper: createWrapper(['/ipos/7']) })

    await vi.waitFor(() => expect(screen.getByText('테스트IPO')).toBeInTheDocument())
    const backLink = screen.getByRole('link', { name: '공모주 일정으로 돌아가기' })
    const registerLink = screen.getByRole('link', { name: '청약 등록하기' })
    expect(backLink).toHaveAttribute('href', '/ipo-calendar')
    expect(registerLink).toHaveAttribute('href', '/subscriptions/register')
  })
})
