import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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
    status: 'before',
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

describe('SubscriptionScheduleList 주관사 chip', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('카드에 주관사 2개 이름이 접두어 없이 chip으로 표시된다', async () => {
    vi.mocked(apiFetch).mockResolvedValue(INSIGHT)
    const { Wrapper, queryClient } = createWrapper()

    render(
      <Wrapper>
        <SubscriptionScheduleList items={[makeItem()]} investorSelected={false} />
      </Wrapper>,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )
    expect(screen.getByText('미래에셋증권')).toBeInTheDocument()
    expect(screen.getByText('KB증권')).toBeInTheDocument()
    expect(screen.queryByText(/주관사/)).not.toBeInTheDocument()
  })

  it('빈 underwriters일 때 chip 컨테이너가 나오지 않는다', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ ...INSIGHT, underwriters: [] })
    const { Wrapper, queryClient } = createWrapper()

    render(
      <Wrapper>
        <SubscriptionScheduleList items={[makeItem()]} investorSelected={false} />
      </Wrapper>,
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(ipoKeys.insight(1))?.status).toBe('success'),
    )
    expect(screen.queryByLabelText('주관사')).not.toBeInTheDocument()
    expect(screen.queryByText('미래에셋증권')).not.toBeInTheDocument()
  })
})
