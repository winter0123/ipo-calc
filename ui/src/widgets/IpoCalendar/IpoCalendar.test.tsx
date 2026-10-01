import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import IpoCalendar from './IpoCalendar.tsx'
import type { Ipo } from '@/entities/ipo/index.ts'

function makeIpo(overrides: Partial<Ipo> = {}): Ipo {
  return {
    id: 1,
    companyName: '테스트기업',
    ipoName: '테스트IPO',
    stockCode: '123456',
    roundLabel: '1',
    lowerBandPrice: 10000,
    upperBandPrice: 15000,
    fixedPrice: 0,
    subscriptionStartDate: '2026-10-01T09:00:00Z',
    subscriptionEndDate: '2026-10-02T17:00:00Z',
    listingDate: '2026-10-15T09:00:00Z',
    offeringShareCount: 1000000,
    status: 'ACTIVE',
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
    ...overrides,
  }
}

describe('IpoCalendar', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('데이터 있는 달에 이벤트 라벨이 텍스트로 보인다', async () => {
    const ipos = [makeIpo()]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    expect(screen.getByText('청약 시작')).toBeInTheDocument()
    expect(screen.getByText('청약 마감')).toBeInTheDocument()
    expect(screen.getByText('상장')).toBeInTheDocument()
  })

  it('다음달 버튼을 누르면 표시 월 제목이 바뀌고 그 달의 이벤트가 보인다', async () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-01T09:00:00Z',
        subscriptionEndDate: '2026-10-02T17:00:00Z',
        listingDate: '2026-10-15T09:00:00Z',
      }),
      makeIpo({
        id: 2,
        subscriptionStartDate: '2026-11-05T09:00:00Z',
        subscriptionEndDate: '2026-11-06T17:00:00Z',
        listingDate: '2026-11-20T09:00:00Z',
      }),
    ]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    expect(screen.getByText('2026년 10월')).toBeInTheDocument()
    expect(screen.getByText('청약 시작')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '다음 달' }))

    expect(screen.getByText('2026년 11월')).toBeInTheDocument()
    const dateButtons = screen.getAllByRole('button', { name: /^11월 \d+일/ })
    const hasEventInNov = dateButtons.some((btn) => btn.textContent?.includes('청약 시작'))
    expect(hasEventInNov).toBe(true)
    const dateButtonsOct = screen.queryAllByRole('button', { name: /^10월 \d+일/ })
    expect(dateButtonsOct.length).toBe(0)
  })

  it('이전달 버튼을 누르면 표시 월 제목이 바뀐다', async () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-01T09:00:00Z',
      }),
      makeIpo({
        id: 2,
        subscriptionStartDate: '2026-09-15T09:00:00Z',
      }),
    ]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    expect(screen.getByText('2026년 10월')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '이전 달' }))

    expect(screen.getByText('2026년 9월')).toBeInTheDocument()
    expect(screen.getByText('청약 시작')).toBeInTheDocument()
  })

  it('오늘 버튼을 누르면 현재 월로 이동한다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={11} />)

    expect(screen.getByText('2026년 11월')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '오늘' }))

    expect(screen.getByText('2026년 10월')).toBeInTheDocument()
  })

  it('날짜 칸 클릭 시 상세 영역에 회사명이 나온다', async () => {
    const ipos = [
      makeIpo({ companyName: '테스트기업', subscriptionStartDate: '2026-10-05T09:00:00Z' }),
    ]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 5일/ })
    await userEvent.click(dateButton)

    expect(screen.getByText('테스트기업')).toBeInTheDocument()
    const detailRegion = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    expect(detailRegion).toHaveTextContent('청약 시작')
  })

  it('이벤트 없는 날 클릭 시 "일정 없음"이 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 10일/ })
    await userEvent.click(dateButton)

    expect(screen.getByText('일정 없음')).toBeInTheDocument()
  })

  it('선택된 날짜는 aria-pressed로 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-05T09:00:00Z' })]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 5일/ })
    expect(dateButton).not.toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(dateButton)

    expect(dateButton).toHaveAttribute('aria-pressed', 'true')
  })

  it('오늘 날짜는 aria-current="date"로 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-10T09:00:00Z' })]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const todayButton = screen.getByRole('button', { name: /10월 10일/ })
    expect(todayButton).toHaveAttribute('aria-current', 'date')
  })

  it('월 제목은 aria-live="polite" 영역에 있다', async () => {
    const ipos = [makeIpo()]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const monthTitle = screen.getByText('2026년 10월')
    expect(monthTitle).toHaveAttribute('aria-live', 'polite')
  })

  it('여러 이벤트가 같은 날에 있으면 모두 표시된다', async () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-05T09:00:00Z',
        subscriptionEndDate: '2026-10-05T17:00:00Z',
      }),
    ]
    render(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 5일/ })
    expect(dateButton).toHaveTextContent('청약 시작')
    expect(dateButton).toHaveTextContent('청약 마감')
  })

  it('월을 이동하면 선택한 날짜가 해제된다', async () => {
    render(<IpoCalendar ipos={[makeIpo()]} initialYear={2026} initialMonth={10} />)

    await userEvent.click(screen.getByRole('button', { name: /^10월 1일/ }))
    expect(screen.getByRole('heading', { name: '2026-10-01 일정' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '다음 달' }))

    expect(screen.getByText('날짜를 선택하세요')).toBeInTheDocument()
    expect(screen.queryByText('일정 없음')).not.toBeInTheDocument()
  })

  it('12월에서 다음 달은 다음 해 1월, 1월에서 이전 달은 전년 12월이다', async () => {
    render(<IpoCalendar ipos={[]} initialYear={2026} initialMonth={12} />)

    await userEvent.click(screen.getByRole('button', { name: '다음 달' }))
    expect(screen.getByText('2027년 1월')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '이전 달' }))
    expect(screen.getByText('2026년 12월')).toBeInTheDocument()
  })
})
