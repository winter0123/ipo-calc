import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardHome from './DashboardHome.tsx'
import { buildUpcomingEvents } from './upcomingEvents.ts'
import type { Ipo } from '@/entities/ipo/index.ts'

vi.mock('@/entities/investor/index.ts')
vi.mock('@/entities/subscription/index.ts')
vi.mock('@/entities/ipo/index.ts')

import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useInvestmentHistory } from '@/entities/subscription/index.ts'
import { useIpos } from '@/entities/ipo/index.ts'

const as = <T,>(v: unknown) => v as T

const ipo = (o: Partial<Ipo>): Ipo =>
  ({
    id: 1,
    companyName: 'A사',
    ipoName: '에이공모',
    subscriptionStartDate: '2026-10-20',
    subscriptionEndDate: '2026-10-21',
    listingDate: null,
    refundDate: null,
    ...o,
  }) as Ipo

const item = (o: Record<string, unknown>) => ({
  id: 1,
  ipoName: '에이공모',
  subscribedAt: '2026-10-10T00:00:00',
  allocation: null,
  remainingQuantity: null,
  ...o,
})

function setup(history: unknown[], ipos: Ipo[], current: unknown = { id: 7 }) {
  vi.mocked(useCurrentInvestor).mockReturnValue(as({ current, isLoading: false }))
  vi.mocked(useInvestmentHistory).mockReturnValue(
    as({ data: { content: history }, isLoading: false, error: null }),
  )
  vi.mocked(useIpos).mockReturnValue(as({ data: ipos, isLoading: false, error: null }))
  render(
    <MemoryRouter>
      <DashboardHome />
    </MemoryRouter>,
  )
}

describe('DashboardHome', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-13T12:00:00'))
  })
  afterEach(() => vi.useRealTimers())

  it('배정 미입력 청약은 배정 입력, 남은 수량이 있는 배정은 매도 등록 링크를 보여 준다', () => {
    setup(
      [
        item({ id: 3 }),
        item({ id: 4, allocation: { id: 1 }, remainingQuantity: 5 }),
        item({ id: 5, allocation: { id: 2 }, remainingQuantity: 0 }),
      ],
      [],
    )
    expect(screen.getByRole('link', { name: '배정 입력' })).toHaveAttribute(
      'href',
      '/allocations/register?subscriptionId=3',
    )
    expect(screen.getByRole('link', { name: '매도 등록' })).toHaveAttribute(
      'href',
      '/sales/register?subscriptionId=4',
    )
    expect(screen.getAllByRole('link', { name: '매도 등록' })).toHaveLength(1)
  })

  it('할 일이 없으면 안내 문구, 투자자가 없으면 선택 안내', () => {
    setup([], [])
    expect(screen.getByText('처리할 일이 없습니다.')).toBeInTheDocument()
  })

  it('투자자가 선택되지 않았으면 안내만 보여 준다', () => {
    setup([], [], null)
    expect(screen.getByText(/투자자를 선택하면/)).toBeInTheDocument()
  })

  it('7일 안의 일정만, 청약 기간 중이면 청약 등록 링크와 함께 보여 준다', () => {
    setup(
      [],
      [
        ipo({ id: 1, subscriptionStartDate: '2026-10-12', subscriptionEndDate: '2026-10-14' }),
        ipo({
          id: 2,
          ipoName: '멀다',
          subscriptionStartDate: '2026-11-20',
          subscriptionEndDate: '2026-11-21',
        }),
      ],
    )
    expect(screen.getByRole('link', { name: '에이공모' })).toHaveAttribute('href', '/ipos/1')
    expect(screen.getByText(/청약 마감/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '청약 등록' })).toHaveAttribute(
      'href',
      '/subscriptions/register?ipoId=1',
    )
    expect(screen.queryByText('멀다')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '공모주 일정 전체 보기' })).toHaveAttribute(
      'href',
      '/ipo-calendar',
    )
  })
})

describe('buildUpcomingEvents', () => {
  it('최대 8건으로 자르고 날짜순으로 정렬한다', () => {
    const ipos = Array.from({ length: 6 }, (_, i) =>
      ipo({ id: i + 1, subscriptionStartDate: '2026-10-15', subscriptionEndDate: '2026-10-16' }),
    )
    const events = buildUpcomingEvents(ipos, new Date('2026-10-13T09:00:00'))
    expect(events).toHaveLength(8)
    expect(events[0].date).toBe('2026-10-15')
  })

  it('청약 기간 중이어도 환불·상장 줄에는 청약 등록을 붙이지 않는다', () => {
    const events = buildUpcomingEvents(
      [
        ipo({
          subscriptionStartDate: '2026-10-12',
          subscriptionEndDate: '2026-10-14',
          refundDate: '2026-10-16',
          listingDate: '2026-10-18',
        }),
      ],
      new Date('2026-10-13T09:00:00'),
    )
    expect(events.map((e) => [e.kind, e.canSubscribe])).toEqual([
      ['청약 마감', true],
      ['환불', false],
      ['상장', false],
    ])
  })
})
