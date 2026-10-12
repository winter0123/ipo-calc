import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SubscriptionListPage from './SubscriptionListPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import type { InvestmentHistoryResponse } from '@/entities/subscription/index.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

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

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="search">{location.search}</p>
}

function renderPage(entry = '/subscriptions') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[entry]}>
        <SubscriptionListPage />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForListLoaded() {
  await waitFor(() => {
    expect(screen.getByText('카카오뱅크')).toBeInTheDocument()
  })
}

const INVESTOR = {
  id: 1,
  displayName: '홍길동',
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-01-01',
  updatedAt: '2026-01-01',
}

/** /investors는 투자자 1명(자동 선택), /investment-history는 주어진 동작으로 응답한다. */
function mockHistory(history: () => Promise<unknown>) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) =>
    String(path) === '/investors' ? [INVESTOR] : history(),
  )
}

describe('SubscriptionListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('목록 로딩 상태를 표시한 뒤 응답 수신 후 청약 카드를 채움 (AC-008)', async () => {
    let resolveHistory: (value: unknown) => void = () => {}
    let historyRequested = false
    mockHistory(
      () =>
        new Promise((resolve) => {
          historyRequested = true
          resolveHistory = resolve
        }),
    )
    renderPage()

    expect(screen.getByText('목록을 불러오는 중...')).toBeInTheDocument()

    // 투자자 조회가 끝나 청약 조회가 시작된 뒤에 응답을 준다
    await waitFor(() => expect(historyRequested).toBe(true))
    resolveHistory(HISTORY_RESPONSE)

    await waitForListLoaded()
    expect(screen.getByText('삼성바이오로직스')).toBeInTheDocument()
  })

  it('기본 필터(전체)는 미배정/배정완료 청약을 모두 표시함', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    expect(screen.getByText('삼성바이오로직스')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('"미배정" 필터 클릭 시 allocation이 null인 청약만 표시함 (AC-009)', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    await userEvent.click(screen.getByRole('button', { name: '미배정' }))

    expect(screen.getByText('카카오뱅크')).toBeInTheDocument()
    expect(screen.queryByText('삼성바이오로직스')).not.toBeInTheDocument()
  })

  it('"배정완료" 필터 클릭 시 allocation이 null이 아닌 청약만 표시함 (AC-010)', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    await userEvent.click(screen.getByRole('button', { name: '배정완료' }))

    expect(screen.getByText('삼성바이오로직스')).toBeInTheDocument()
    expect(screen.queryByText('카카오뱅크')).not.toBeInTheDocument()
  })

  it('전체 → 미배정 → 배정완료 순으로 필터를 전환해도 정상 동작함 (E2E 시나리오 3)', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    expect(screen.getAllByRole('listitem')).toHaveLength(2)

    await userEvent.click(screen.getByRole('button', { name: '미배정' }))
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByText('카카오뱅크')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '배정완료' }))
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByText('삼성바이오로직스')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '전체' }))
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('미배정 청약 카드에만 "배정 입력" 버튼이 표시됨 (AC-011)', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    const links = screen.getAllByRole('link', { name: '배정 입력' })
    expect(links).toHaveLength(1)
  })

  it('배정완료 카드에 배정·남은 수량과 매도 등록 링크가 보인다', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    expect(screen.getByText('남은 수량').nextElementSibling).toHaveTextContent('20주')
    expect(screen.getByRole('link', { name: '매도 등록' })).toHaveAttribute(
      'href',
      '/sales/register?subscriptionId=2',
    )
  })

  it('남은 수량이 0이면 "모두 매도" 배지만 보인다', async () => {
    const [first, second] = HISTORY_RESPONSE.content
    mockHistory(() =>
      Promise.resolve({
        ...HISTORY_RESPONSE,
        content: [first, { ...second, remainingQuantity: 0 }],
      }),
    )
    renderPage()
    await waitForListLoaded()

    expect(screen.getByText('모두 매도')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '매도 등록' })).not.toBeInTheDocument()
  })

  it('"새 청약 등록" 링크가 등록 화면으로 이어진다', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    expect(screen.getByRole('link', { name: '새 청약 등록' })).toHaveAttribute(
      'href',
      '/subscriptions/register',
    )
  })

  it('"배정 입력" 버튼은 subscriptionId를 URL 파라미터로 전달함 (AC-012)', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage()
    await waitForListLoaded()

    const link = screen.getByRole('link', { name: '배정 입력' })
    expect(link).toHaveAttribute('href', '/allocations/register?subscriptionId=1')
  })

  it('청약이 없으면 빈 상태 메시지를 표시함', async () => {
    mockHistory(() =>
      Promise.resolve({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    )
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('표시할 청약이 없습니다.')).toBeInTheDocument()
    })
  })

  it('투자자 정보 404 응답 시 에러 메시지 표시', async () => {
    mockHistory(() => Promise.reject(new ApiError(404, 'Request failed: 404')))
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('투자자 정보를 찾을 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    mockHistory(() => Promise.reject(new Error('Failed to fetch')))
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('기타 API 에러 시 공용 에러 메시지 표시', async () => {
    mockHistory(() => Promise.reject(new ApiError(500, 'Request failed: 500')))
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('청약 목록을 불러오는 데 실패했습니다.')).toBeInTheDocument()
    })
  })

  it('배정 필터는 URL status 쿼리와 동기화된다', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage('/subscriptions?status=allocated')
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '배정완료' })).toHaveAttribute(
        'aria-pressed',
        'true',
      ),
    )

    await userEvent.click(screen.getByRole('button', { name: '전체' }))
    await waitFor(() => expect(screen.getByTestId('search')).toBeEmptyDOMElement())

    await userEvent.click(screen.getByRole('button', { name: '미배정' }))
    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent('?status=unallocated'),
    )
  })

  it('알 수 없는 status 값은 전체로 처리한다', async () => {
    mockHistory(() => Promise.resolve(HISTORY_RESPONSE))
    renderPage('/subscriptions?status=bogus')
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '전체' })).toHaveAttribute('aria-pressed', 'true'),
    )
  })
})
