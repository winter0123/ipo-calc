import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { useState } from 'react'
import IpoCalendar from './IpoCalendar.tsx'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { CalendarEventType } from '@/features/ipo-calendar/index.ts'

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
    refundDate: null,
    refundDateEstimated: null,
    offeringShareCount: 1000000,
    status: 'ACTIVE',
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
    ...overrides,
  }
}

const renderWithRouter = (ui: React.ReactElement) =>
  render(<MemoryRouter initialEntries={['/']}>{ui}</MemoryRouter>)

function FilterTestWrapper({
  ipos,
  initialYear,
  initialMonth,
  initialVisibleTypes,
  onVisibleTypesChange,
}: {
  ipos: Ipo[]
  initialYear?: number
  initialMonth?: number
  initialVisibleTypes?: ReadonlySet<CalendarEventType>
  onVisibleTypesChange?: (next: ReadonlySet<CalendarEventType>) => void
}) {
  console.log('FilterTestWrapper: initialVisibleTypes=', initialVisibleTypes)
  const [visibleTypes, setVisibleTypes] = useState<ReadonlySet<CalendarEventType>>(
    initialVisibleTypes ?? new Set<CalendarEventType>(['SUBSCRIPTION', 'REFUND', 'LISTING']),
  )
  console.log('FilterTestWrapper: visibleTypes=', visibleTypes)
  return (
    <IpoCalendar
      ipos={ipos}
      initialYear={initialYear}
      initialMonth={initialMonth}
      visibleTypes={visibleTypes}
      onVisibleTypesChange={onVisibleTypesChange ?? setVisibleTypes}
    />
  )
}

describe('IpoCalendar', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('데이터 있는 달에 청약·상장 막대가 보인다', async () => {
    const ipos = [makeIpo()]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    expect(screen.getByRole('link', { name: /청약/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
  })

  it('청약 막대는 시작일~종료일을 가로지른다', async () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-01T09:00:00Z',
        subscriptionEndDate: '2026-10-03T17:00:00Z',
      }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const bar = screen.getByRole('link', { name: /테스트기업 청약 10월 1일부터 10월 3일까지/ })
    expect(bar).toBeInTheDocument()
    expect(bar).toHaveAttribute('href', '/ipos/1')
  })

  it('상장 막대는 하루만 표시된다', async () => {
    const ipos = [makeIpo({ listingDate: '2026-10-15T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const bar = screen.getByRole('link', { name: /테스트기업 상장 10월 15일/ })
    expect(bar).toBeInTheDocument()
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
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    expect(screen.getByText('2026년 10월')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /청약/ })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '다음 달' }))

    expect(screen.getByText('2026년 11월')).toBeInTheDocument()
    const novBars = screen.getAllByRole('link', { name: /11월/ })
    expect(novBars.length).toBeGreaterThan(0)
    const octBars = screen.queryAllByRole('link', { name: /10월/ })
    expect(octBars.length).toBe(0)
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
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    expect(screen.getByText('2026년 10월')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '이전 달' }))

    expect(screen.getByText('2026년 9월')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /청약/ }).length).toBeGreaterThan(0)
  })

  it('오늘 버튼을 누르면 현재 월로 이동한다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={11} />)

    expect(screen.getByText('2026년 11월')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '오늘' }))

    expect(screen.getByText('2026년 10월')).toBeInTheDocument()
  })

  it('날짜 칸 클릭 시 상세 영역에 회사명이 나온다', async () => {
    const ipos = [
      makeIpo({ companyName: '테스트기업', subscriptionStartDate: '2026-10-05T09:00:00Z' }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 5일/ })
    await userEvent.click(dateButton)

    const detailRegion = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    expect(detailRegion).toHaveTextContent('테스트기업')
    expect(detailRegion).toHaveTextContent('청약')
  })

  it('이벤트 없는 날 클릭 시 "일정 없음"이 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 10일/ })
    await userEvent.click(dateButton)

    expect(screen.getByText('일정 없음')).toBeInTheDocument()
  })

  it('선택된 날짜는 aria-pressed로 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-05T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 5일/ })
    expect(dateButton).not.toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(dateButton)

    expect(dateButton).toHaveAttribute('aria-pressed', 'true')
  })

  it('오늘 날짜는 aria-current="date"로 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-10T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const todayButton = screen.getByRole('button', { name: /10월 10일/ })
    expect(todayButton).toHaveAttribute('aria-current', 'date')
  })

  it('월 제목은 aria-live="polite" 영역에 있다', async () => {
    const ipos = [makeIpo()]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const monthTitle = screen.getByText('2026년 10월')
    expect(monthTitle).toHaveAttribute('aria-live', 'polite')
  })

  it('월을 이동하면 선택한 날짜가 해제된다', async () => {
    renderWithRouter(<IpoCalendar ipos={[makeIpo()]} initialYear={2026} initialMonth={10} />)

    await userEvent.click(screen.getByRole('button', { name: /^10월 1일/ }))
    expect(screen.getByRole('heading', { name: '2026-10-01 일정' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '다음 달' }))

    expect(screen.getByText('날짜를 선택하세요')).toBeInTheDocument()
    expect(screen.queryByText('일정 없음')).not.toBeInTheDocument()
  })

  it('12월에서 다음 달은 다음 해 1월, 1월에서 이전 달은 전년 12월이다', async () => {
    renderWithRouter(<IpoCalendar ipos={[]} initialYear={2026} initialMonth={12} />)

    await userEvent.click(screen.getByRole('button', { name: '다음 달' }))
    expect(screen.getByText('2027년 1월')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '이전 달' }))
    expect(screen.getByText('2026년 12월')).toBeInTheDocument()
  })

  it('상세 패널의 각 항목이 올바른 상세 페이지 링크(href)를 가진다', async () => {
    const ipos = [
      makeIpo({ id: 7, companyName: '테스트기업', subscriptionStartDate: '2026-10-05T09:00:00Z' }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /10월 5일/ })
    await userEvent.click(dateButton)

    const link = screen.getByRole('link', { name: /테스트기업 테스트IPO 상세 보기/ })
    expect(link).toHaveAttribute('href', '/ipos/7')
  })

   describe('필터', () => {
     it('필터 버튼 3개가 aria-pressed=true로 시작한다', async () => {
       const ipos = [makeIpo()]
       renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

       expect(screen.getByRole('button', { name: '청약' })).toHaveAttribute('aria-pressed', 'true')
       expect(screen.getByRole('button', { name: '환불' })).toHaveAttribute('aria-pressed', 'true')
       expect(screen.getByRole('button', { name: '상장' })).toHaveAttribute('aria-pressed', 'true')
     })

     it('필터 버튼을 누르면 onVisibleTypesChange에 해당 종류가 빠진 집합이 전달된다', async () => {
       const onVisibleTypesChange = vi.fn()
       const ipos = [makeIpo()]
       renderWithRouter(
         <FilterTestWrapper
           ipos={ipos}
           initialYear={2026}
           initialMonth={10}
           onVisibleTypesChange={onVisibleTypesChange}
         />,
       )

       await userEvent.click(screen.getByRole('button', { name: '청약' }))

       expect(onVisibleTypesChange).toHaveBeenCalledTimes(1)
       const calledWith = onVisibleTypesChange.mock.calls[0][0]
       expect(calledWith).toBeInstanceOf(Set)
       expect(calledWith.has('SUBSCRIPTION')).toBe(false)
       expect(calledWith.has('REFUND')).toBe(true)
       expect(calledWith.has('LISTING')).toBe(true)
     })

     it('꺼진 종류의 막대가 안 보이고 켜진 종류만 보인다', async () => {
       const ipos = [
         makeIpo({
           subscriptionStartDate: '2026-10-01T09:00:00Z',
           subscriptionEndDate: '2026-10-02T17:00:00Z',
           listingDate: '2026-10-15T09:00:00Z',
           refundDate: '2026-10-10T09:00:00Z',
         }),
       ]
       renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

       // Initially, all three types of bars should be visible
       expect(screen.getByRole('link', { name: /청약/ })).toBeInTheDocument()
       expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
       expect(screen.getByRole('link', { name: /환불/ })).toBeInTheDocument()

       // 청약 끄기
       await userEvent.click(screen.getByRole('button', { name: '청약' }))
       expect(screen.queryByRole('link', { name: /청약/ })).not.toBeInTheDocument()
       expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
       expect(screen.getByRole('link', { name: /환불/ })).toBeInTheDocument()

       // 환불 끄기
       await userEvent.click(screen.getByRole('button', { name: '환불' }))
       expect(screen.queryByRole('link', { name: /청약/ })).not.toBeInTheDocument()
       expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
       expect(screen.queryByRole('link', { name: /환불/ })).not.toBeInTheDocument()

       // 상장 끄기
       await userEvent.click(screen.getByRole('button', { name: '상장' }))
       expect(screen.queryByRole('link', { name: /청약/ })).not.toBeInTheDocument()
       expect(screen.queryByRole('link', { name: /상장/ })).not.toBeInTheDocument()
       expect(screen.queryByRole('link', { name: /환불/ })).not.toBeInTheDocument()
     })

     it('날짜 칸 aria-label의 일정 개수가 필터를 따른다', async () => {
       const ipos = [
         makeIpo({
           subscriptionStartDate: '2026-10-05T09:00:00Z',
           listingDate: '2026-10-05T09:00:00Z',
           refundDate: '2026-10-05T09:00:00Z',
         }),
       ]
       renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

       // 세 종 모두 켜짐: 3개
       const dateButtonAll = screen.getByRole('button', { name: /10월 5일/ })
       expect(dateButtonAll).toHaveAttribute('aria-label', '10월 5일, 일정 3개')

       // 청약 끄기
       await userEvent.click(screen.getByRole('button', { name: '청약' }))
       const dateButtonTwo = screen.getByRole('button', { name: /10월 5일/ })
       expect(dateButtonTwo).toHaveAttribute('aria-label', '10월 5일, 일정 2개')

       // 환불 끄기
       await userEvent.click(screen.getByRole('button', { name: '환불' }))
       const dateButtonOne = screen.getByRole('button', { name: /10월 5일/ })
       expect(dateButtonOne).toHaveAttribute('aria-label', '10월 5일, 일정 1개')

       // 상장 끄기
       await userEvent.click(screen.getByRole('button', { name: '상장' }))
       const dateButtonZero = screen.getByRole('button', { name: /10월 5일/ })
       expect(dateButtonZero).toHaveAttribute('aria-label', '10월 5일, 일정 0개')
     })

     it('3종 모두 꺼짐 안내가 보인다', async () => {
       const ipos = [makeIpo()]
       renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

       await userEvent.click(screen.getByRole('button', { name: '청약' }))
       await userEvent.click(screen.getByRole('button', { name: '환불' }))
       await userEvent.click(screen.getByRole('button', { name: '상장' }))

       expect(screen.getByText('표시할 일정 종류를 선택해 주세요.')).toBeInTheDocument()
     })

     it('모두 보기 버튼은 3종이 모두 켜져 있으면 disabled', async () => {
       const ipos = [makeIpo()]
       renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

       const showAllButton = screen.getByRole('button', { name: '모두 보기' })
       expect(showAllButton).toBeDisabled()
     })

     it('모두 보기 버튼을 누르면 모든 종류가 켜진다', async () => {
       const ipos = [makeIpo()]
       renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

       await userEvent.click(screen.getByRole('button', { name: '청약' }))
       await userEvent.click(screen.getByRole('button', { name: '환불' }))
       await userEvent.click(screen.getByRole('button', { name: '상장' }))

       const showAllButton = screen.getByRole('button', { name: '모두 보기' })
       expect(showAllButton).not.toBeDisabled()

       await userEvent.click(showAllButton)

       // 필터 버튼이 모두 켜진 상태가 됨
       expect(screen.getByRole('button', { name: '청약' })).toHaveAttribute('aria-pressed', 'true')
       expect(screen.getByRole('button', { name: '환불' })).toHaveAttribute('aria-pressed', 'true')
       expect(screen.getByRole('button', { name: '상장' })).toHaveAttribute('aria-pressed', 'true')
     })

    it('상세 패널 항목에 청약 기간 글자가 보인다', async () => {
      const ipos = [
        makeIpo({
          subscriptionStartDate: '2026-10-01T09:00:00Z',
          subscriptionEndDate: '2026-10-03T17:00:00Z',
        }),
      ]
      renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

      const dateButton = screen.getByRole('button', { name: /10월 1일/ })
      await userEvent.click(dateButton)

      expect(screen.getByText('2026-10-01 ~ 2026-10-03')).toBeInTheDocument()
    })

    it('visibleTypes props 없이도 동작한다(기존 테스트 호환)', async () => {
      const ipos = [makeIpo()]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      expect(screen.getByRole('link', { name: /청약/ })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
    })
  })

  describe('월을 넘기는 청약', () => {
    it('주를 넘기면 다음 주 줄에서 이어진다(continuesAfter/continuesBefore)', async () => {
      const ipos = [
        makeIpo({
          subscriptionStartDate: '2026-10-08T09:00:00Z',
          subscriptionEndDate: '2026-10-12T17:00:00Z',
        }),
      ]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      // 10/8(목)~10/12(월)은 2주에 걸침
      const bars = screen.getAllByRole('link', { name: /청약/ })
      expect(bars.length).toBe(2)
    })
  })
})
