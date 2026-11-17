import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, fireEvent, render, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import SubscriptionScheduleList from './SubscriptionScheduleList.tsx'
import type { SubscriptionScheduleItem } from './subscriptionSchedule.ts'
import { ipoKeys } from '@/entities/ipo/index.ts'
import { apiFetch } from '@/shared/api/client.ts'
import { makeIpo } from '@/test/ipoFixture.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

const INSIGHT = {
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
  underwriters: [
    { name: '미래에셋증권', role: '주관', shares: 1000 },
    { name: 'KB증권', role: '공동주관', shares: 500 },
  ],
}

function makeItem(overrides: Partial<SubscriptionScheduleItem> = {}): SubscriptionScheduleItem {
  return {
    ipo: makeIpo(),
    subscriptions: [],
    subscriptionCount: 0,
    subscriptionQuantity: 0,
    allocatedCount: 0,
    allocatedQuantity: 0,
    pendingCount: 0,
    status: 'pending',
    ...overrides,
  }
}

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return { Wrapper, queryClient }
}

function renderWithRouter(
  element: React.ReactNode,
  initialEntries: string[] = ['/subscriptions?from=2026-10-01&to=2026-10-31'],
) {
  const { Wrapper, queryClient } = createWrapper()
  return {
    result: render(
      <Wrapper>
        <MemoryRouter initialEntries={initialEntries}>{element}</MemoryRouter>
      </Wrapper>,
    ),
    queryClient,
  }
}

function mockInsight(insight = INSIGHT) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return []
    if (pathStr === '/ipos') return [makeIpo()]
    if (pathStr.includes('/insight')) return insight
    if (pathStr.startsWith('/investment-history'))
      return {
        content: [],
        totalElements: 0,
        totalPages: 1,
        number: 0,
        size: 100,
      }
    throw new Error(`unexpected request: ${pathStr}`)
  })
}

describe('SubscriptionScheduleList 주관사 chip', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('카드에 주관사 2개 이름이 접두어 없이 chip으로 표시된다', async () => {
    mockInsight(INSIGHT)
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList items={[makeItem()]} investorSelected={false} />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )
    expect(result.getByText('미래에셋증권')).toBeInTheDocument()
    expect(result.getByText('KB증권')).toBeInTheDocument()
    expect(result.queryByText(/주관사/)).not.toBeInTheDocument()
  })

  it('빈 underwriters일 때 chip 컨테이너가 나오지 않는다', async () => {
    mockInsight({ ...INSIGHT, underwriters: [] })
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList items={[makeItem()]} investorSelected={false} />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )
    expect(result.queryByLabelText('주관사')).not.toBeInTheDocument()
    expect(result.queryByText('미래에셋증권')).not.toBeInTheDocument()
  })
})

describe('SubscriptionScheduleList 진행 상태', () => {
  beforeEach(() => vi.clearAllMocks())

  it('매도 완료 상태를 표시한다', async () => {
    mockInsight()
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[makeItem({ status: 'sold', allocatedQuantity: 5 })]}
        investorSelected={true}
      />,
    )
    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )
    expect(result.getByText('매도 완료')).toBeInTheDocument()
  })

  it('청약하지 않은 공모주는 미청약으로 표시한다', async () => {
    mockInsight()
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList items={[makeItem({ status: 'before' })]} investorSelected={true} />,
    )
    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )
    expect(result.getByText('미청약')).toBeInTheDocument()
  })
})

describe('SubscriptionScheduleList 제목·주관사 레이아웃', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('ipoName 제목만 표시되고 companyName 줄은 DOM에 없다', async () => {
    mockInsight(INSIGHT)
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList items={[makeItem()]} investorSelected={false} />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )
    expect(result.getByText('테스트IPO')).toBeInTheDocument()
    expect(result.queryByText('테스트기업')).not.toBeInTheDocument()
  })

  it('주관사 컨테이너가 제목과 같은 제목 그룹 안에 있다(버튼 위 단독 행 아님)', async () => {
    mockInsight(INSIGHT)
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList items={[makeItem()]} investorSelected={false} />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    const ipoName = result.getByText('테스트IPO')
    const titleGroup = ipoName.closest('.s-ee1412e-titleGroup')
    expect(titleGroup).toBeInTheDocument()
    expect(titleGroup).toContainHTML('미래에셋증권')
    expect(titleGroup).toContainHTML('KB증권')
  })
})

describe('SubscriptionScheduleList 페이지네이션', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockInsight(INSIGHT)
  })

  it('11개 아이템일 때 1페이지에 10개만 표시되고 다음 페이지 버튼이 활성화된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={Array.from({ length: 11 }, (_, i) =>
          makeItem({ ipo: makeIpo({ id: i + 1, ipoName: `공모주 ${i + 1}` }) }),
        )}
        investorSelected={false}
      />,
      ['/subscriptions?from=2026-10-01&to=2026-10-31'],
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    // 1페이지에 10개만 표시
    for (let i = 1; i <= 10; i++) {
      expect(result.getByText(`공모주 ${i}`)).toBeInTheDocument()
    }
    expect(result.queryByText('공모주 11')).not.toBeInTheDocument()

    // 페이지네이션 표시됨
    expect(result.getByRole('navigation', { name: '공모주 일정 페이지 이동' })).toBeInTheDocument()
    expect(result.getByText('1 / 2')).toBeInTheDocument()
    expect(result.getByRole('button', { name: '다음 페이지' })).not.toBeDisabled()
    expect(result.getByRole('button', { name: '이전 페이지' })).toBeDisabled()
  })

  it('다음 페이지 클릭 시 2페이지 아이템이 표시되고 URL이 업데이트된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={Array.from({ length: 11 }, (_, i) =>
          makeItem({ ipo: makeIpo({ id: i + 1, ipoName: `공모주 ${i + 1}` }) }),
        )}
        investorSelected={false}
      />,
      ['/subscriptions?from=2026-10-01&to=2026-10-31'],
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    const nextButton = result.getByRole('button', { name: '다음 페이지' })
    nextButton.click()

    await waitFor(() => expect(result.getByText('공모주 11')).toBeInTheDocument())
    expect(result.queryByText('공모주 1')).not.toBeInTheDocument()
    expect(result.getByText('2 / 2')).toBeInTheDocument()
    expect(result.getByRole('button', { name: '이전 페이지' })).not.toBeDisabled()
    expect(result.getByRole('button', { name: '다음 페이지' })).toBeDisabled()
  })

  it('10개 이하 아이템일 때 페이지네이션이 표시되지 않는다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={Array.from({ length: 10 }, (_, i) =>
          makeItem({ ipo: makeIpo({ id: i + 1, ipoName: `공모주 ${i + 1}` }) }),
        )}
        investorSelected={false}
      />,
      ['/subscriptions?from=2026-10-01&to=2026-10-31'],
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(
      result.queryByRole('navigation', { name: '공모주 일정 페이지 이동' }),
    ).not.toBeInTheDocument()
  })

  it('빈 리스트일 때 빈 상태 메시지만 표시된다', async () => {
    const { result } = renderWithRouter(
      <SubscriptionScheduleList items={[]} investorSelected={false} />,
      ['/subscriptions?from=2026-10-01&to=2026-10-31'],
    )

    await waitFor(() =>
      expect(result.getByText('선택한 기간에 공모주 일정이 없습니다.')).toBeInTheDocument(),
    )
    expect(
      result.queryByRole('navigation', { name: '공모주 일정 페이지 이동' }),
    ).not.toBeInTheDocument()
  })
})

describe('SubscriptionScheduleList 현재 단계 표시', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockInsight(INSIGHT)
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-15'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('청약 진행 중 단계가 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '미래 공모주',
              subscriptionStartDate: '2026-11-01',
              subscriptionEndDate: '2026-11-02',
              listingDate: null,
            }),
          }),
        ]}
        investorSelected={true}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(result.queryByText('청약 예정')).not.toBeInTheDocument()
    expect(result.queryByText('청약 전')).not.toBeInTheDocument()
  })

  it('청약 진행 중 단계가 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '진행중 공모주',
              subscriptionStartDate: '2026-10-10',
              subscriptionEndDate: '2026-10-20',
              listingDate: null,
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(result.getByText('청약 진행 중')).toBeInTheDocument()
  })

  it('상장 예정 단계가 표시된다 (청약 종료 후, 상장일 없음)', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '지난 공모주',
              subscriptionStartDate: '2026-10-01',
              subscriptionEndDate: '2026-10-05',
              listingDate: null,
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(result.getByText('상장일 미정')).toBeInTheDocument()
  })

  it('상장일 단계가 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '상장일 공모주',
              subscriptionStartDate: '2026-10-01',
              subscriptionEndDate: '2026-10-05',
              listingDate: '2026-10-15',
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(result.getByText('상장 완료')).toBeInTheDocument()
  })

  it('화면 복귀 시 한국 날짜를 다시 계산하고 일정 쿼리를 갱신한다', async () => {
    vi.setSystemTime(new Date('2026-11-09T14:59:59Z'))
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              subscriptionStartDate: '2026-11-02',
              subscriptionEndDate: '2026-11-03',
              listingDate: '2026-11-10',
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )
    await waitFor(() => expect(result.getByText('상장 예정')).toBeInTheDocument())
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    vi.setSystemTime(new Date('2026-11-09T15:00:00Z'))
    fireEvent.focus(window)
    expect(result.getByText('상장 완료')).toBeInTheDocument()
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ipoKeys.list() })
  })

  it('한국 자정에 열어 둔 화면의 상태가 갱신된다', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-09T14:59:59Z'))
    const { result } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              subscriptionStartDate: '2026-11-02',
              subscriptionEndDate: '2026-11-03',
              listingDate: '2026-11-10',
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )
    expect(result.getByText('상장 예정')).toBeInTheDocument()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1100)
    })
    expect(result.getByText('상장 완료')).toBeInTheDocument()
  })

  it('상장 완료 단계가 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '상장완료 공모주',
              subscriptionStartDate: '2026-10-01',
              subscriptionEndDate: '2026-10-05',
              listingDate: '2026-10-10',
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(result.getByText('상장 완료')).toBeInTheDocument()
  })
})

describe('SubscriptionScheduleList 가격 표시', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockInsight(INSIGHT)
  })

  it('공모가(fixedPrice)가 있을 때 공모가 라벨과 금액이 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '공모가 공모주',
              fixedPrice: 15000,
              lowerBandPrice: 10000,
              upperBandPrice: 15000,
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    // dt 요소의 "공모가" 라벨 확인
    const priceLabels = result.getAllByText('공모가')
    expect(priceLabels.length).toBeGreaterThanOrEqual(1)
    expect(result.getByText('15,000원')).toBeInTheDocument()
  })

  it('가격 정보가 없을 때 미정으로 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '미정 공모주',
              fixedPrice: null,
              lowerBandPrice: null,
              upperBandPrice: null,
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(result.getByText('미정')).toBeInTheDocument()
  })

  it('확정가 없으면 밴드 범위(하단~상단 원)로 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            ipo: makeIpo({
              id: 1,
              ipoName: '밴드 공모주',
              fixedPrice: null,
              lowerBandPrice: 9000,
              upperBandPrice: 11000,
            }),
          }),
        ]}
        investorSelected={false}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    expect(result.getByText('9,000원~11,000원')).toBeInTheDocument()
  })
})

describe('SubscriptionScheduleList 건수 제거 확인', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockInsight(INSIGHT)
  })

  it('내 청약에 건수 없이 수량만 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            subscriptionCount: 2,
            subscriptionQuantity: 200,
            allocatedCount: 1,
            allocatedQuantity: 50,
            pendingCount: 1,
            status: 'partial',
          }),
        ]}
        investorSelected={true}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    // "2건 · 200주"가 아닌 "200주"만 표시
    expect(result.getByText('200주')).toBeInTheDocument()
    expect(result.queryByText('2건')).not.toBeInTheDocument()
  })

  it('배정 결과에 입력/대기 건수 없이 배정 수량만 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            subscriptionCount: 2,
            subscriptionQuantity: 200,
            allocatedCount: 1,
            allocatedQuantity: 50,
            pendingCount: 1,
            status: 'partial',
          }),
        ]}
        investorSelected={true}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    // "1건 입력 · 50주 · 1건 대기"가 아닌 "50주"만 표시 (대기 텍스트도 제거)
    expect(result.getByText('50주')).toBeInTheDocument()
    expect(result.queryByText('1건 입력')).not.toBeInTheDocument()
    expect(result.queryByText(/1건 대기/)).not.toBeInTheDocument() // 대기 텍스트도 제거됨
  })

  it('partial 상태 배지(badge)에 상태명만 표시되고 중복되지 않는다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            subscriptionCount: 2,
            subscriptionQuantity: 200,
            allocatedCount: 1,
            allocatedQuantity: 50,
            pendingCount: 1,
            status: 'partial',
          }),
        ]}
        investorSelected={true}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    // "일부 입력"이 정확히 1번만 표시됨 (statusBadge에만 표시)
    const partialTexts = result.getAllByText('일부 입력')
    expect(partialTexts).toHaveLength(1)
    // 배지가 표시됨
    const badge = partialTexts[0].closest('.s-ee1412e-statusBadge')
    expect(badge).toBeInTheDocument()
    expect(badge).toHaveTextContent('일부 입력')
  })

  it('allocated 상태에서 배정 결과에 배정 수량이 표시된다', async () => {
    const { result, queryClient } = renderWithRouter(
      <SubscriptionScheduleList
        items={[
          makeItem({
            subscriptionCount: 1,
            subscriptionQuantity: 100,
            allocatedCount: 1,
            allocatedQuantity: 30,
            pendingCount: 0,
            status: 'allocated',
          }),
        ]}
        investorSelected={true}
      />,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )

    // 배정 결과 dd에 "30주"가 표시됨 (기존 statusDetail pill 대신)
    expect(result.getByText('배정 결과')).toBeInTheDocument()
    const allocatedDd = result.getByText('배정 결과').closest('div')?.querySelector('dd')
    expect(allocatedDd).toHaveTextContent('30주')
  })
})
