import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import TradeManagePage from './TradeManagePage.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const INVESTOR = {
  id: 1,
  displayName: '홍길동',
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const IPO = {
  id: 7,
  companyName: '테스트기업',
  ipoName: '테스트IPO',
  stockCode: '123456',
  roundLabel: null,
  lowerBandPrice: 20000,
  upperBandPrice: 25000,
  fixedPrice: 25000,
  subscriptionStartDate: '2026-11-20T09:00:00Z',
  subscriptionEndDate: '2026-11-21T17:00:00Z',
  listingDate: '2026-12-01T09:00:00Z',
  refundDate: null,
  refundDateEstimated: null,
  offeringShareCount: 1000000,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
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

const ACCOUNT_NO_NUMBER = {
  id: 10,
  investorId: 1,
  securitiesFirmId: 1,
  securitiesFirmName: '미래에셋증권',
  accountNumber: null,
  accountName: '위탁계좌',
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const ACCOUNT_MASKED = {
  id: 11,
  investorId: 1,
  securitiesFirmId: 2,
  securitiesFirmName: 'KB증권',
  accountNumber: '1101***',
  accountName: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const ACTIVE_ITEM = {
  id: 55,
  subscribedAt: '2026-11-20T09:00:00Z',
  ipoId: 7,
  ipoName: '테스트IPO',
  ipoFixedPrice: 25000,
  subscriptionQuantity: 10,
  subscriptionStatus: 'ACTIVE',
  allocation: {
    id: 77,
    allocatedQuantity: 10,
    allocatedAmount: 250000,
    allocationStatus: 'ACTIVE',
  },
  sales: [
    {
      id: 88,
      quantity: 3,
      amount: 36000,
      fee: 2000,
      tax: 100,
      otherCost: null,
      status: 'ACTIVE',
      soldAt: '2026-11-21T00:00:00Z',
    },
    {
      id: 89,
      quantity: 3,
      amount: 40500,
      fee: 2000,
      tax: 120,
      otherCost: null,
      status: 'ACTIVE',
      soldAt: '2026-11-22T00:00:00Z',
    },
  ],
  remainingQuantity: 4,
  investorName: '홍길동',
  securitiesFirmName: '미래에셋증권',
  securitiesAccountId: 10,
  securitiesAccountName: '위탁계좌',
}

const EMPTY_HISTORY = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 1 }

const SUB_SOURCE = {
  id: 55,
  investorId: 1,
  securitiesAccountId: 10,
  ipoId: 7,
  quantity: 10,
  depositAmount: 125000,
  subscribedAt: '2026-11-20T09:00:00Z',
  memo: null,
  status: 'ACTIVE',
  createdAt: '2026-11-20T00:00:00Z',
  updatedAt: '2026-11-20T00:00:00Z',
}

const ALLOC_SOURCE = {
  id: 77,
  subscriptionId: 55,
  allocatedQuantity: 10,
  allocatedAt: '2026-11-21T00:00:00Z',
  allocatedAmount: 250000,
  note: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-11-21T00:00:00Z',
  updatedAt: '2026-11-21T00:00:00Z',
}

const saleSource = (id: number, price: number, saleDate: string) => ({
  id,
  allocationId: 77,
  quantity: 3,
  price,
  amount: price * 3,
  saleDate,
  fee: 2000,
  tax: 100,
  otherCost: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: saleDate,
  updatedAt: saleDate,
})

const SALE_88 = saleSource(88, 12000, '2026-11-21T00:00:00Z')
const SALE_89 = saleSource(89, 13500, '2026-11-22T00:00:00Z')

interface StubOptions {
  accounts?: object[]
  accountsError?: boolean
  historyError?: boolean
  historyItem?: typeof ACTIVE_ITEM
  totalElementsOverride?: number
  ipoError?: boolean
  /** 이 경로들은 응답을 끝내 돌려주지 않는다(편집 원본 지연 회귀). */
  pendingPaths?: string[]
  subscriptionSourceError?: boolean
  allocationSourceError?: boolean
}

function stubApi({
  accounts = [ACCOUNT_NO_NUMBER],
  accountsError = false,
  historyError = false,
  historyItem,
  totalElementsOverride,
  ipoError = false,
  pendingPaths = [],
  subscriptionSourceError = false,
  allocationSourceError = false,
}: StubOptions = {}) {
  const item = historyItem ?? ACTIVE_ITEM
  const history = historyItem
    ? {
        content: [item],
        totalElements: totalElementsOverride ?? 1,
        totalPages: 1,
        number: 0,
        size: 1,
      }
    : EMPTY_HISTORY

  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const p = String(path)
    if (pendingPaths.includes(p)) return new Promise(() => {})
    if (p === '/investors') return [INVESTOR]
    if (p === '/ipos/7') {
      if (ipoError) throw new ApiError(404, '공모주를 찾을 수 없습니다')
      return IPO
    }
    if (p === '/ipos/7/insight') return INSIGHT
    if (p.startsWith('/securities-accounts')) {
      if (accountsError) throw new ApiError(500, '계좌 목록 조회 실패')
      return accounts
    }
    if (p.startsWith('/investment-history?')) {
      if (historyError) throw new ApiError(500, '투자 내역 조회 실패')
      return history
    }
    if (p === '/investment-history/55') return item
    if (p === '/subscriptions/55') {
      if (subscriptionSourceError) throw new ApiError(500, '편집 원본 조회 실패')
      return SUB_SOURCE
    }
    if (p === '/allocations/77') {
      if (allocationSourceError) throw new ApiError(500, '배정 원본 조회 실패')
      return ALLOC_SOURCE
    }
    if (p === '/sales/88') return SALE_88
    if (p === '/sales/89') return SALE_89
    throw new Error(`unexpected path: ${p}`)
  })
}

const requestedPaths = () => vi.mocked(apiFetch).mock.calls.map((call) => String(call[0]))

const postCallsOf = (path: string) =>
  vi.mocked(apiFetch).mock.calls.filter(([p, o]) => p === path && o?.method === 'POST')

function renderPage(url = '/ipos/7/manage') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/ipos/:ipoId/manage" element={<TradeManagePage />} />
          <Route path="/ipo-calendar" element={<div>공모주 일정 화면</div>} />
          <Route path="/my-page" element={<div>마이페이지</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('청약·매도 관리 화면', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    localStorage.setItem('currentInvestorId', '1')
  })

  it('공모주명·공모가·현재 투자자가 고정되어 열린다', async () => {
    stubApi()
    const { container } = renderPage()

    expect(await screen.findByRole('heading', { name: '청약·매도 관리' })).toBeInTheDocument()
    expect(await screen.findByText('테스트IPO')).toBeInTheDocument()
    expect(screen.getByText('25,000원')).toBeInTheDocument()
    expect(screen.getByText('홍길동')).toBeInTheDocument()
    // 미등록 기본 화면 — 청약/배정/매도 영역이 모두 보이고 청약부터 입력하는 준비 상태다.
    expect(container.querySelector('#section-title-subscription')).toHaveTextContent('청약')
    expect(container.querySelector('#section-title-allocation')).toHaveTextContent('배정')
    expect(container.querySelector('#section-title-sale')).toHaveTextContent('매도')
  })

  it('계좌가 1개면 자동 선택되고 정확 필터(size:1)로 투자 내역을 조회한다', async () => {
    stubApi()
    renderPage()

    const select = (await screen.findByLabelText('증권계좌')) as HTMLSelectElement
    await waitFor(() => expect(select.value).toBe('10'))

    await waitFor(() =>
      expect(
        requestedPaths().some(
          (p) =>
            p.startsWith('/investment-history?') &&
            p.includes('investorId=1') &&
            p.includes('ipoId=7') &&
            p.includes('securitiesAccountId=10') &&
            p.includes('size=1'),
        ),
      ).toBe(true),
    )
    expect(await screen.findByText(/아직 등록된 청약이 없습니다/)).toBeInTheDocument()
  })

  it('여러 계좌면 구분해 선택하고, 번호 없는 계좌도 고를 수 있다', async () => {
    stubApi({ accounts: [ACCOUNT_NO_NUMBER, ACCOUNT_MASKED] })
    renderPage()

    const select = (await screen.findByLabelText('증권계좌')) as HTMLSelectElement
    // 복수 계좌 — 자동선택 없이 사용자가 고르게 한다.
    expect(select.value).toBe('')
    // 번호 없는 계좌와 마스킹 번호 계좌가 모두 보인다.
    expect(screen.getByRole('option', { name: '미래에셋증권 · 위탁계좌' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'KB증권 · 1101***' })).toBeInTheDocument()
    // 선택 전에는 투자 내역을 조회하지 않는다(임의 계좌 fallback 금지).
    expect(requestedPaths().some((p) => p.startsWith('/investment-history'))).toBe(false)

    await userEvent.selectOptions(select, '11')
    expect(select.value).toBe('11')
    await waitFor(() =>
      expect(
        requestedPaths().some(
          (p) => p.startsWith('/investment-history?') && p.includes('securitiesAccountId=11'),
        ),
      ).toBe(true),
    )
  })

  it('계좌 목록을 불러오지 못하면 명확한 안내와 재시도를 보여 주고 임의 계좌를 쓰지 않는다', async () => {
    stubApi({ accountsError: true })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '증권계좌 목록을 불러오지 못했습니다',
    )
    expect(screen.queryByLabelText('증권계좌')).not.toBeInTheDocument()
    expect(requestedPaths().some((p) => p.startsWith('/investment-history'))).toBe(false)
  })

  it('보유 계좌가 0개면 등록 안내를 보여 주고 임의 계좌를 만들지 않는다', async () => {
    stubApi({ accounts: [] })
    renderPage()

    expect(await screen.findByText(/등록된 증권계좌가 없습니다/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /마이페이지에서 증권계좌 등록하기/ })).toHaveAttribute(
      'href',
      '/my-page',
    )
    expect(screen.queryByLabelText('증권계좌')).not.toBeInTheDocument()
  })

  it('투자 내역을 불러오지 못하면 기록 없음이 아니라 오류를 보여 준다', async () => {
    stubApi({ historyError: true })
    renderPage()

    expect(
      await screen.findByText(/투자 내역을 불러오지 못했습니다/, { exact: false }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/아직 등록된 청약이 없습니다/)).not.toBeInTheDocument()
  })

  it('투자 내역 기록이 ACTIVE가 아니면 신규 입력 가능 상태로 바꾸지 않는다', async () => {
    stubApi({ historyItem: { ...ACTIVE_ITEM, subscriptionStatus: 'CANCELLED' } })
    renderPage()

    expect(await screen.findByText(/상태를 확인할 수 없습니다/)).toBeInTheDocument()
    expect(screen.queryByText(/아직 등록된 청약이 없습니다/)).not.toBeInTheDocument()
  })

  it('같은 조건의 청약이 2건 이상이면 오류를 보여 준다', async () => {
    stubApi({ historyItem: ACTIVE_ITEM, totalElementsOverride: 2 })
    renderPage()

    expect(await screen.findByText(/2건 이상 조회되어/)).toBeInTheDocument()
  })

  it('이미 기록이 있으면 상세(잔여·매도)와 편집 원본을 개별 GET으로 불러온다', async () => {
    stubApi({ historyItem: ACTIVE_ITEM })
    renderPage()

    expect(await screen.findByText(/청약 기록이 있습니다/)).toBeInTheDocument()
    expect(screen.getByRole('status', { name: '매도 요약' })).toHaveTextContent('잔여 4주')
    expect(screen.getByText('36,000원')).toBeInTheDocument()
    expect(screen.getByText('40,500원')).toBeInTheDocument()
    expect(screen.getByText(/배정 10주/)).toBeInTheDocument()

    await waitFor(() => {
      const paths = requestedPaths()
      expect(paths).toContain('/investment-history/55')
      expect(paths).toContain('/subscriptions/55')
      expect(paths).toContain('/allocations/77')
      expect(paths).toContain('/sales/88')
      expect(paths).toContain('/sales/89')
    })
  })

  it('주관사와 다른 증권사 계좌는 선택지에서 제외한다', async () => {
    stubApi({
      accounts: [
        ACCOUNT_NO_NUMBER,
        { ...ACCOUNT_MASKED, id: 12, securitiesFirmName: '한국투자증권' },
      ],
    })
    renderPage()

    expect(
      await screen.findByRole('option', { name: '미래에셋증권 · 위탁계좌' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: '한국투자증권 · 1101***' })).not.toBeInTheDocument()
  })

  it('공모주를 찾을 수 없으면 안내를 보여 준다', async () => {
    stubApi({ ipoError: true })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('공모주를 찾을 수 없습니다.')
  })

  it('청약 편집 원본이 아직 오지 않으면 로딩 상태로 두고 신규 등록 폼을 열지 않는다', async () => {
    stubApi({ historyItem: ACTIVE_ITEM, pendingPaths: ['/subscriptions/55'] })
    renderPage()

    expect(await screen.findByText('청약 기록을 불러오는 중...')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '청약 저장' })).not.toBeInTheDocument()
    expect(requestedPaths()).not.toContain('/subscriptions')
  })

  it('배정 편집 원본이 아직 오지 않으면 로딩 상태로 두고 배정 폼을 열지 않는다', async () => {
    stubApi({ historyItem: ACTIVE_ITEM, pendingPaths: ['/allocations/77'] })
    renderPage()

    expect(await screen.findByText('배정 기록을 불러오는 중...')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '배정 저장' })).not.toBeInTheDocument()
  })

  it('청약 기록 불러오기 실패 시 오류만 표시하고 신규 폼으로 열지 않는다', async () => {
    stubApi({ historyItem: ACTIVE_ITEM, subscriptionSourceError: true })
    renderPage()

    expect(await screen.findByText(/청약 기록을 불러오지 못했습니다/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '청약 저장' })).not.toBeInTheDocument()
    expect(postCallsOf('/subscriptions')).toHaveLength(0)
  })

  it('배정 기록 불러오기 실패 시 오류만 표시하고 신규 폼으로 열지 않는다', async () => {
    stubApi({ historyItem: ACTIVE_ITEM, allocationSourceError: true })
    renderPage()

    expect(await screen.findByText(/배정 기록을 불러오지 못했습니다/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '배정 저장' })).not.toBeInTheDocument()
  })

  it('계좌가 1개면 자동 선택되고 빈 옵션은 보이지 않는다', async () => {
    stubApi({ accounts: [ACCOUNT_NO_NUMBER] })
    renderPage()

    const select = (await screen.findByLabelText('증권계좌')) as HTMLSelectElement
    await waitFor(() => expect(select.value).toBe('10'))
    expect(screen.queryByRole('option', { name: '증권계좌 선택' })).not.toBeInTheDocument()
  })

  it('미저장 입력이 있으면 계좌 전환을 확인으로 되돌리고, 확인하면 새 문맥을 조회한다', async () => {
    stubApi({ accounts: [ACCOUNT_NO_NUMBER, ACCOUNT_MASKED] })
    renderPage()

    const select = (await screen.findByLabelText('증권계좌')) as HTMLSelectElement
    expect(
      screen.getByText('증권계좌를 선택하면 청약을 입력하거나 불러올 수 있습니다.'),
    ).toBeInTheDocument()
    await userEvent.selectOptions(select, '10')
    await waitFor(() => expect(select.value).toBe('10'))

    // 청약 폼에 미저장 입력을 만든다
    const quantity = await screen.findByLabelText('청약수량')
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '5')

    // 다른 계좌로 바꾸려 하면 확인 대화상자를 띄운다
    await userEvent.selectOptions(select, '11')
    expect(await screen.findByRole('alertdialog')).toHaveTextContent(
      '저장하지 않은 입력이 있습니다',
    )

    // 취소 — 기존 계좌·입력 유지
    await userEvent.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(select.value).toBe('10')
    expect(screen.getByLabelText('청약수량')).toHaveValue(5)

    // 확인 — 새 계좌로 바뀌고 입력은 초기화된다
    await userEvent.selectOptions(select, '11')
    await userEvent.click(screen.getByRole('button', { name: '바꾸기' }))
    expect(select.value).toBe('11')
    await waitFor(() =>
      expect(
        requestedPaths().some(
          (p) => p.startsWith('/investment-history?') && p.includes('securitiesAccountId=11'),
        ),
      ).toBe(true),
    )
    expect(screen.getByLabelText('청약수량')).toHaveValue(null)
  })

  it('배정 저장으로 청약이 함께 만들어져도 청약 폼에 입력 중이던 값은 유지된다', async () => {
    const AUTO_ITEM = {
      id: 99,
      subscribedAt: '2026-11-12T09:00:00Z',
      ipoId: 7,
      ipoName: '테스트IPO',
      ipoFixedPrice: 25000,
      subscriptionQuantity: 3,
      subscriptionStatus: 'ACTIVE',
      allocation: {
        id: 88,
        allocatedQuantity: 3,
        allocatedAmount: 75000,
        allocationStatus: 'ACTIVE',
      },
      sales: [],
      remainingQuantity: 3,
      investorName: '홍길동',
      securitiesFirmName: '미래에셋증권',
      securitiesAccountId: 10,
      securitiesAccountName: '위탁계좌',
    }
    const AUTO_ALLOC = {
      id: 88,
      subscriptionId: 99,
      allocatedQuantity: 3,
      allocatedAt: '2026-11-12T00:00:00Z',
      allocatedAmount: 75000,
      note: null,
      status: 'ACTIVE',
      deletedAt: null,
      createdAt: '2026-11-12T00:00:00Z',
      updatedAt: '2026-11-12T00:00:00Z',
    }
    const AUTO_SUB = {
      id: 99,
      investorId: 1,
      securitiesAccountId: 10,
      ipoId: 7,
      quantity: 3,
      depositAmount: 0,
      subscribedAt: '2026-11-12T09:00:00Z',
      memo: '매도/배정 등록에서 자동 생성된 청약 연결 기록',
      status: 'ACTIVE',
      createdAt: '2026-11-12T00:00:00Z',
      updatedAt: '2026-11-12T00:00:00Z',
    }
    let autoCreated = false
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p === '/allocations' && method === 'POST') {
        autoCreated = true
        return AUTO_ALLOC
      }
      if (p.startsWith('/investment-history?')) {
        return autoCreated
          ? { content: [AUTO_ITEM], totalElements: 1, totalPages: 1, number: 0, size: 1 }
          : EMPTY_HISTORY
      }
      if (p === '/investment-history/99') return AUTO_ITEM
      if (p === '/allocations/88') return AUTO_ALLOC
      if (p === '/subscriptions/99') return AUTO_SUB
      throw new Error(`unexpected path: ${p} (${method})`)
    })

    renderPage()

    // 청약 폼에 아직 저장하지 않은 입력을 만든다
    const quantity = await screen.findByLabelText('청약수량')
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '5')
    await userEvent.type(screen.getByLabelText('메모 (선택)'), '내 입력')
    // 청약 없이 배정을 먼저 저장하면 서버가 청약을 자동 생성한다
    const allocated = await screen.findByLabelText('배정수량')
    await userEvent.clear(allocated)
    await userEvent.type(allocated, '3')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    // 자동 생성 후에도 청약 폼에 입력 중이던 값이 유지된다
    await waitFor(() => expect(screen.getByLabelText('청약수량')).toHaveValue(5))
    expect(screen.getByLabelText('메모 (선택)')).toHaveValue('내 입력')

    // 청약 저장을 누르면 POST가 아니라 새 청약의 PATCH로 전송된다
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))
    await waitFor(() => expect(postCallsOf('/subscriptions')).toHaveLength(0))
    const patch = vi
      .mocked(apiFetch)
      .mock.calls.find(([p3, o]) => p3 === '/subscriptions/99' && o?.method === 'PATCH')
    expect(patch).toBeTruthy()
    const body = JSON.parse((patch![1] as RequestInit).body as string)
    expect(body).toEqual({ quantity: 5, memo: '내 입력' })
  })

  it('섹션 저장이 진행되는 동안 증권계좌 select를 바꾸지 못하게 한다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER, ACCOUNT_MASKED]
      if (p.startsWith('/investment-history?')) return EMPTY_HISTORY
      if (p === '/subscriptions' && method === 'POST') return new Promise(() => {})
      throw new Error(`unexpected path: ${p} (${method})`)
    })
    renderPage()

    const select = (await screen.findByLabelText('증권계좌')) as HTMLSelectElement
    await userEvent.selectOptions(select, '10')
    await waitFor(() => expect(select).toBeEnabled())

    const quantity = await screen.findByLabelText('청약수량')
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '5')
    await userEvent.click(screen.getByRole('button', { name: '청약 저장' }))

    // 청약 저장 요청이 끝날 때까지 select는 잠긴다
    await waitFor(() => expect(select).toBeDisabled())
  })

  it('분할 매도를 채워 저장하고 재조회가 끝나면 잔여 0으로 추가가 잠긴다', async () => {
    const SALE_90 = saleSource(90, 13000, '2026-11-23T00:00:00Z')
    // saleSource는 수량 3 고정이므로 표시용 원본만 따로 만든다(금액·단가 보존)
    const SALE_90_FOUR = { ...SALE_90, quantity: 4, amount: 52000, fee: 0, tax: 1000 }
    const SPLIT_ITEM = {
      ...ACTIVE_ITEM,
      sales: [
        ...ACTIVE_ITEM.sales,
        {
          id: 90,
          quantity: 4,
          amount: 52000,
          fee: 0,
          tax: 1000,
          otherCost: null,
          status: 'ACTIVE',
          soldAt: '2026-11-23T00:00:00Z',
        },
      ],
      remainingQuantity: 0,
    }
    const SPLIT_HISTORY = {
      content: [SPLIT_ITEM],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    }
    let created = false
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p.startsWith('/investment-history?')) {
        return created
          ? SPLIT_HISTORY
          : {
              content: [ACTIVE_ITEM],
              totalElements: 1,
              totalPages: 1,
              number: 0,
              size: 1,
            }
      }
      if (p === '/investment-history/55') return created ? SPLIT_ITEM : ACTIVE_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') return SALE_88
      if (p === '/sales/89') return SALE_89
      if (p === '/sales/90') return SALE_90_FOUR
      if (p === '/sales' && method === 'POST') {
        created = true
        return {
          ...SALE_90_FOUR,
          createdAt: '2026-11-23T00:00:00Z',
          updatedAt: '2026-11-23T00:00:00Z',
        }
      }
      throw new Error(`unexpected path: ${p} (${method})`)
    })
    renderPage()

    // 기존 2건 표시 확인 후 잔여 4주로 시작
    expect(await screen.findByText('12,000원')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '+ 매도 추가' })).toBeEnabled()

    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    // 잔여 수량이 기본으로 채워진다
    expect(screen.getByLabelText('매도수량')).toHaveValue(4)
    await userEvent.clear(screen.getByLabelText('매도수량'))
    await userEvent.type(screen.getByLabelText('매도수량'), '4')
    await userEvent.clear(screen.getByLabelText('매도단가'))
    await userEvent.type(screen.getByLabelText('매도단가'), '13000')
    await userEvent.clear(screen.getByLabelText('세금 및 기타 수수료 (선택)'))
    await userEvent.type(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    await waitFor(() => {
      const post = postCallsOf('/sales')
      expect(post).toHaveLength(1)
      const body = JSON.parse((post[0][1] as RequestInit).body as string)
      expect(body).toMatchObject({
        quantity: 4,
        price: 13000,
        amount: 52000,
        subscriptionId: 55,
        // 기존 ACTIVE 매도가 있으므로 공모주수수료 기본값은 0원
        fee: 0,
        tax: 1000,
      })
    })

    // 저장 후 재조회가 끝나면 잔여 0으로 다음 추가가 잠긴다
    await waitFor(() => expect(screen.getByRole('button', { name: '+ 매도 추가' })).toBeDisabled())
    expect(screen.getByRole('status', { name: '매도 요약' })).toHaveTextContent('잔여 0주')
    expect(
      requestedPaths().filter((p) => p === '/investment-history/55').length,
    ).toBeGreaterThanOrEqual(2)
  })

  it('10주 분할 3건이 이미 있으면 잔여 0으로 추가만 잠기고 수정·삭제는 유지된다', async () => {
    const SALE_90 = saleSource(90, 11000, '2026-11-23T00:00:00Z')
    const SPLIT_ITEM = {
      ...ACTIVE_ITEM,
      sales: [
        ...ACTIVE_ITEM.sales,
        {
          id: 90,
          quantity: 3,
          amount: 33000,
          fee: 2000,
          tax: 130,
          otherCost: null,
          status: 'ACTIVE',
          soldAt: '2026-11-23T00:00:00Z',
        },
      ],
      remainingQuantity: 0,
    }
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const p = String(path)
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p.startsWith('/investment-history?')) {
        return {
          content: [SPLIT_ITEM],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 1,
        }
      }
      if (p === '/investment-history/55') return SPLIT_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') return SALE_88
      if (p === '/sales/89') return SALE_89
      if (p === '/sales/90') return SALE_90
      throw new Error(`unexpected path: ${p}`)
    })
    renderPage()

    expect(await screen.findByText('11,000원')).toBeInTheDocument()
    expect(screen.getByRole('status', { name: '매도 요약' })).toHaveTextContent('잔여 0주')
    expect(screen.getByRole('button', { name: '+ 매도 추가' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /매도 수정/ }).length).toBe(3)
    expect(screen.getAllByRole('button', { name: /매도 삭제/ })[0]).toBeEnabled()
  })

  it('배정 저장 후 필수 재조회가 실패하면 갱신 실패 안내를 보여 준다(저장은 성공, 재전송 없음)', async () => {
    let allocationCreated = false
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p === '/allocations' && method === 'POST') {
        allocationCreated = true
        return ALLOC_SOURCE
      }
      if (p.startsWith('/investment-history?')) {
        if (allocationCreated) throw new ApiError(500, '투자 내역 조회 실패')
        return EMPTY_HISTORY
      }
      if (p === '/investment-history/55') return ACTIVE_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') return SALE_88
      if (p === '/sales/89') return SALE_89
      throw new Error(`unexpected path:  ()`)
    })
    renderPage()

    const allocated = await screen.findByLabelText('배정수량')
    await userEvent.clear(allocated)
    await userEvent.type(allocated, '3')
    await userEvent.click(screen.getByRole('button', { name: '배정 저장' }))

    expect(
      await screen.findByText('저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'),
    ).toBeInTheDocument()
    expect(postCallsOf('/allocations')).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: '다시 불러오기' }).length).toBeGreaterThanOrEqual(
      1,
    )
  })

  it('매도 저장 후 필수 재조회가 실패하면 갱신 실패 안내를 보여 준다(저장은 성공, 재전송 없음)', async () => {
    const SPLIT_ITEM = {
      ...ACTIVE_ITEM,
      sales: [
        ...ACTIVE_ITEM.sales,
        {
          id: 90,
          quantity: 4,
          amount: 52000,
          fee: 0,
          tax: 1000,
          otherCost: null,
          status: 'ACTIVE',
          soldAt: '2026-11-23T00:00:00Z',
        },
      ],
      remainingQuantity: 0,
    }
    let saleCreated = false
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p === '/sales' && method === 'POST') {
        saleCreated = true
        return {
          ...SPLIT_ITEM.sales[2],
          allocationId: 77,
          price: 13000,
          createdAt: '2026-11-23T00:00:00Z',
          updatedAt: '2026-11-23T00:00:00Z',
          deletedAt: null,
        }
      }
      if (p.startsWith('/investment-history?')) {
        if (saleCreated) throw new ApiError(500, '투자 내역 조회 실패')
        return {
          content: [ACTIVE_ITEM],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 1,
        }
      }
      if (p === '/investment-history/55') return saleCreated ? SPLIT_ITEM : ACTIVE_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') return SALE_88
      if (p === '/sales/89') return SALE_89
      if (p === '/sales/90')
        return {
          ...SPLIT_ITEM.sales[2],
          allocationId: 77,
          price: 13000,
          saleDate: '2026-11-23T00:00:00Z',
          deletedAt: null,
          createdAt: '2026-11-23T00:00:00Z',
          updatedAt: '2026-11-23T00:00:00Z',
        }
      throw new Error(`unexpected path:  ()`)
    })
    renderPage()

    expect(await screen.findByText('12,000원')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await userEvent.clear(screen.getByLabelText('매도수량'))
    await userEvent.type(screen.getByLabelText('매도수량'), '4')
    await userEvent.clear(screen.getByLabelText('매도단가'))
    await userEvent.type(screen.getByLabelText('매도단가'), '13000')
    await userEvent.clear(screen.getByLabelText('세금 및 기타 수수료 (선택)'))
    await userEvent.type(screen.getByLabelText('세금 및 기타 수수료 (선택)'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    expect(
      await screen.findByText('저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'),
    ).toBeInTheDocument()
    expect(postCallsOf('/sales')).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: '다시 불러오기' }).length).toBeGreaterThanOrEqual(
      1,
    )
  })

  it('매도 POST 성공 후 새 id의 원본 GET이 지연되면 다음 저장이 잠기고 POST를 재전송하지 않는다', async () => {
    const NEW_SALE_90 = {
      ...ACTIVE_ITEM.sales[0],
      id: 90,
      quantity: 4,
      amount: 52000,
      fee: 0,
      tax: 1000,
      soldAt: '2026-11-23T00:00:00Z',
    }
    const SPLIT_ITEM = {
      ...ACTIVE_ITEM,
      sales: [...ACTIVE_ITEM.sales, NEW_SALE_90],
      remainingQuantity: 0,
    }
    let saleCreated = false
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p === '/sales' && method === 'POST') {
        saleCreated = true
        return {
          ...NEW_SALE_90,
          allocationId: 77,
          price: 13000,
          createdAt: '2026-11-23T00:00:00Z',
          updatedAt: '2026-11-23T00:00:00Z',
          deletedAt: null,
        }
      }
      if (p.startsWith('/investment-history?')) {
        const item = saleCreated ? SPLIT_ITEM : ACTIVE_ITEM
        return {
          content: [item],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 1,
        }
      }
      if (p === '/investment-history/55') return saleCreated ? SPLIT_ITEM : ACTIVE_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') return SALE_88
      if (p === '/sales/89') return SALE_89
      if (p === '/sales/90') return new Promise(() => {}) // 새 원본 GET이 끝내 응답하지 않는다
      throw new Error(`unexpected path: ${p} (${method})`)
    })
    renderPage()

    expect(await screen.findByText('12,000원')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await userEvent.clear(screen.getByLabelText('매도수량'))
    await userEvent.type(screen.getByLabelText('매도수량'), '4')
    await userEvent.clear(screen.getByLabelText('매도단가'))
    await userEvent.type(screen.getByLabelText('매도단가'), '13000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    // POST는 정확히 1번 — 새 id의 원본 GET이 지연되면 화면이 '불러오는 중'에 갇혀
    // 추가/저장을 다시 누를 수 없다(다음 저장 차단)
    await waitFor(() => expect(postCallsOf('/sales')).toHaveLength(1))
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: '+ 매도 추가' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('button', { name: '매도 저장' })).not.toBeInTheDocument()
  })

  it('매도 POST 성공 후 새 id의 원본 GET이 실패하면 갱신 실패로 처리하고 POST를 재전송하지 않는다', async () => {
    const NEW_SALE_90 = {
      ...ACTIVE_ITEM.sales[0],
      id: 90,
      quantity: 4,
      amount: 52000,
      fee: 0,
      tax: 1000,
      soldAt: '2026-11-23T00:00:00Z',
    }
    const SPLIT_ITEM = {
      ...ACTIVE_ITEM,
      sales: [...ACTIVE_ITEM.sales, NEW_SALE_90],
      remainingQuantity: 0,
    }
    let saleCreated = false
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p === '/sales' && method === 'POST') {
        saleCreated = true
        return {
          ...NEW_SALE_90,
          allocationId: 77,
          price: 13000,
          createdAt: '2026-11-23T00:00:00Z',
          updatedAt: '2026-11-23T00:00:00Z',
          deletedAt: null,
        }
      }
      if (p.startsWith('/investment-history?')) {
        const item = saleCreated ? SPLIT_ITEM : ACTIVE_ITEM
        return {
          content: [item],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 1,
        }
      }
      if (p === '/investment-history/55') return saleCreated ? SPLIT_ITEM : ACTIVE_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') return SALE_88
      if (p === '/sales/89') return SALE_89
      if (p === '/sales/90') throw new ApiError(500, '새 매도 원본 조회 실패')
      throw new Error(`unexpected path: ${p} (${method})`)
    })
    renderPage()

    expect(await screen.findByText('12,000원')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '+ 매도 추가' }))
    await userEvent.clear(screen.getByLabelText('매도수량'))
    await userEvent.type(screen.getByLabelText('매도수량'), '4')
    await userEvent.clear(screen.getByLabelText('매도단가'))
    await userEvent.type(screen.getByLabelText('매도단가'), '13000')
    await userEvent.click(screen.getByRole('button', { name: '매도 저장' }))

    expect(
      await screen.findByText('저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'),
    ).toBeInTheDocument()
    expect(postCallsOf('/sales')).toHaveLength(1)
  })

  it('삭제 후 최신 목록·상세에서 빠진 매도 원본은 기다리지 않고 삭제 흐름을 완료한다', async () => {
    let sale88Deleted = false
    const DETAIL_WITHOUT_88 = {
      ...ACTIVE_ITEM,
      sales: ACTIVE_ITEM.sales.filter((sale) => sale.id !== 88),
      remainingQuantity: 7,
    }
    const LIST_WITHOUT_88 = {
      content: [DETAIL_WITHOUT_88],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    }
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p === '/sales/88' && method === 'DELETE') {
        sale88Deleted = true
        return undefined
      }
      if (p.startsWith('/investment-history?')) {
        return sale88Deleted
          ? LIST_WITHOUT_88
          : {
              content: [ACTIVE_ITEM],
              totalElements: 1,
              totalPages: 1,
              number: 0,
              size: 1,
            }
      }
      if (p === '/investment-history/55') return sale88Deleted ? DETAIL_WITHOUT_88 : ACTIVE_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') {
        // 최신 상세에서 빠진 id — 이 테스트에서는 재조회 자체가 오지 않아야 한다
        if (sale88Deleted) throw new ApiError(404, '매도를 찾을 수 없습니다.')
        return SALE_88
      }
      if (p === '/sales/89') return SALE_89
      throw new Error(`unexpected path: ${p} (${method})`)
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '2026-11-21 매도 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    await waitFor(() =>
      expect(
        screen.queryByText('저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'),
      ).not.toBeInTheDocument(),
    )
    expect(
      vi
        .mocked(apiFetch)
        .mock.calls.some(
          ([p, o]) => p === '/sales/88' && (o as RequestInit | undefined)?.method === 'DELETE',
        ),
    ).toBe(true)
  })

  it('최신 상세에 아직 ACTIVE로 남은 원본이 404면 갱신 실패로 처리한다(재전송 없음)', async () => {
    let sale88Deleted = false
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, options?: RequestInit) => {
      const p = String(path)
      const method = options?.method ?? 'GET'
      if (p === '/investors') return [INVESTOR]
      if (p === '/ipos/7') return IPO
      if (p === '/ipos/7/insight') return INSIGHT
      if (p.startsWith('/securities-accounts')) return [ACCOUNT_NO_NUMBER]
      if (p === '/sales/88' && method === 'DELETE') {
        sale88Deleted = true
        return undefined
      }
      // 서버가 원본 조회에 응답하지 않는(또는 잘못 삭제 처리한) 경우 — 최신 목록/상세는
      // 여전히 sale 88을 ACTIVE로 돌려준다. 이 404는 성공으로 삼키면 안 된다.
      if (p.startsWith('/investment-history?')) {
        return {
          content: [ACTIVE_ITEM],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 1,
        }
      }
      if (p === '/investment-history/55') return ACTIVE_ITEM
      if (p === '/subscriptions/55') return SUB_SOURCE
      if (p === '/allocations/77') return ALLOC_SOURCE
      if (p === '/sales/88') {
        // 최신 목록/상세는 sale 88을 ACTIVE로 돌려준다. 삭제는 됐지만 조회는 아직 성공하는 상태부터 시작해,
        // 재조회(invalidate) 이후의 404만 관찰하도록 한다.
        if (sale88Deleted) throw new ApiError(404, '매도를 찾을 수 없습니다.')
        return SALE_88
      }
      if (p === '/sales/89') return SALE_89
      throw new Error(`unexpected path: ${p} (${method})`)
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '2026-11-21 매도 삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    expect(
      await screen.findByText('저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'),
    ).toBeInTheDocument()
    // 삭제 요청은 한 번만 — 갱신 실패 후 다시 보내지 않는다
    expect(
      vi
        .mocked(apiFetch)
        .mock.calls.filter(
          ([p, o]) => p === '/sales/88' && (o as RequestInit | undefined)?.method === 'DELETE',
        ),
    ).toHaveLength(1)
  })

  it('잘못된 ipoId면 주소 오류 안내를 보여 준다', async () => {
    stubApi()
    renderPage('/ipos/notanumber/manage')

    expect(await screen.findByRole('alert')).toHaveTextContent('공모주 주소가 올바르지 않습니다.')
  })
  it('링크로 전달된 유효 계좌를 그대로 선택해 조회한다', async () => {
    stubApi({ accounts: [ACCOUNT_NO_NUMBER, ACCOUNT_MASKED] })
    renderPage('/ipos/7/manage?securitiesAccountId=10')

    const select = (await screen.findByLabelText('증권계좌')) as HTMLSelectElement
    await waitFor(() => expect(select.value).toBe('10'))
    await waitFor(() =>
      expect(
        requestedPaths().some(
          (p) => p.startsWith('/investment-history?') && p.includes('securitiesAccountId=10'),
        ),
      ).toBe(true),
    )
  })

  it('유효하지 않은 링크 계좌는 자동 선택하지 않고 조회하지 않는다', async () => {
    stubApi({ accounts: [ACCOUNT_NO_NUMBER] })
    renderPage('/ipos/7/manage?securitiesAccountId=999')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '링크의 계좌를 이 공모주에서 사용할 수 없습니다',
    )
    const select = (await screen.findByLabelText('증권계좌')) as HTMLSelectElement
    expect(select.value).toBe('')
    expect(
      screen.getByRole('option', { name: /관리할 증권계좌를 선택해 주세요|증권계좌 선택/ }),
    ).toBeInTheDocument()
    expect(requestedPaths().some((p) => p.startsWith('/investment-history'))).toBe(false)

    await userEvent.selectOptions(select, '10')
    await waitFor(() =>
      expect(
        requestedPaths().some(
          (p) => p.startsWith('/investment-history?') && p.includes('securitiesAccountId=10'),
        ),
      ).toBe(true),
    )
    expect(screen.getByRole('button', { name: '청약 저장' })).not.toBeDisabled()
  })

  it('링크 계좌가 현재 투자자 소유가 아니면 자동 선택하지 않는다', async () => {
    stubApi({ accounts: [ACCOUNT_NO_NUMBER] })
    renderPage('/ipos/7/manage?securitiesAccountId=12')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '링크의 계좌를 이 공모주에서 사용할 수 없습니다',
    )
    expect(requestedPaths().some((p) => p.startsWith('/investment-history'))).toBe(false)
  })
})
