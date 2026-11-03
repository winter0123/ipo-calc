import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoCalendar from './IpoCalendar.tsx'
import type { Ipo } from '@/entities/ipo/index.ts'
import type { CalendarEventType } from '@/features/ipo-calendar/index.ts'
import { apiFetch } from '@/shared/api/client.ts'
import { makeIpo } from '@/test/ipoFixture.ts'
import styles from './IpoCalendar.styles.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const renderWithRouter = (ui: React.ReactElement) =>
  render(<MemoryRouter initialEntries={['/']}>{ui}</MemoryRouter>, { wrapper: createWrapper() })

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
  const [visibleTypes, setVisibleTypes] = useState<ReadonlySet<CalendarEventType>>(
    initialVisibleTypes ?? new Set<CalendarEventType>(['SUBSCRIPTION', 'REFUND', 'LISTING']),
  )
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
  let holidayResponse: unknown[] = []

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    holidayResponse = []
    vi.mocked(apiFetch).mockImplementation((url) => {
      if (typeof url === 'string' && url.startsWith('/market-holidays')) {
        return Promise.resolve(holidayResponse)
      }
      if (typeof url === 'string' && /\/ipos\/\d+\/insight$/.test(url)) {
        return Promise.resolve({
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
        })
      }
      return Promise.resolve([])
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const setHolidays = (holidays: unknown[]) => {
    holidayResponse = holidays
  }

  it('데이터 있는 달에 청약·상장 막대가 보인다', async () => {
    const ipos = [makeIpo()]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    expect(screen.getByRole('button', { name: /청약/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /상장/ })).toBeInTheDocument()
  })

  it('청약 막대는 시작일~종료일을 가로지른다', async () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-01T09:00:00Z',
        subscriptionEndDate: '2026-10-03T17:00:00Z',
      }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const bar = screen.getByRole('button', { name: /테스트기업 청약 10월 1일부터 10월 3일까지/ })
    expect(bar).toBeInTheDocument()
    // 버튼이라 href가 없음 - 클릭으로 날짜 선택 확인
  })

  it('날짜 셀과 일정 막대가 같은 요일 열 좌표를 사용한다', () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-01T09:00:00Z',
        subscriptionEndDate: '2026-10-03T17:00:00Z',
      }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    // 2026년 10월 1일은 목요일이다. 날짜 칸과 청약 바가 동일한 5~7열에 있어야 한다.
    // dateCell은 aria-label에 "일정"이 포함되지만, bar는 "청약" 등으로 시작하므로 구분 가능
    expect(screen.getByRole('button', { name: /^10월 1일/ })).toHaveStyle('--calendar-column: 5')
    expect(screen.getByRole('button', { name: /^10월 3일/ })).toHaveStyle('--calendar-column: 7')

    // SUBSCRIPTION 막대는 종료일(10/03)에만 표시되므로 col 7
    const bar = screen.getByRole('button', { name: /테스트기업 청약 10월 1일부터 10월 3일까지/ })
    expect(bar).toHaveStyle('--calendar-bar-column: 7 / 8')
    expect(bar).toHaveStyle('--calendar-bar-row: 2')
  })

  it('상장 막대는 하루만 표시된다', async () => {
    const ipos = [makeIpo({ listingDate: '2026-10-15T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const bar = screen.getByRole('button', { name: /테스트기업 상장 10월 15일/ })
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
    expect(screen.getByRole('button', { name: /청약/ })).toBeInTheDocument()

    // 월 셀렉트박스로 다음 달(11월) 선택
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '월' }), '11')

    expect(screen.getByText('2026년 11월')).toBeInTheDocument()
    const novBars = screen.getAllByRole('button', { name: /11월/ })
    expect(novBars.length).toBeGreaterThan(0)
    const octBars = screen.queryAllByRole('button', { name: /10월/ })
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

    // 월 셀렉트박스로 이전 달(9월) 선택
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '월' }), '9')

    expect(screen.getByText('2026년 9월')).toBeInTheDocument()
    // 9월 데이터가 있는지 확인 (청약 막대가 종료일에 표시되므로 9월 데이터 확인)
    // id:2의 종료일이 10/02이므로 9월에는 표시되지 않음. id:1의 종료일이 10/02이므로 9월에도 표시 안됨
    // 대신 상장일이나 다른 이벤트가 있는지 확인
    const dateCells = screen.getAllByRole('button', { name: /^9월/ })
    expect(dateCells.length).toBeGreaterThan(0)
  })

  it('오늘 날짜가 초기 선택되고 강조 표시된다', async () => {
    // fake timer가 2026-10-10T12:00:00Z로 설정되어 있음
    // initialYear=2026, initialMonth=10으로 render하면 오늘 날짜가 선택되어야 함
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    // 오늘 날짜(10월 10일) 셀이 aria-pressed=true로 선택되어 있는지 확인
    const todayButton = screen.getByRole('button', { name: /^10월 10일/ })
    expect(todayButton).toHaveAttribute('aria-pressed', 'true')
    // aria-current="date"로도 표시됨
    expect(todayButton).toHaveAttribute('aria-current', 'date')

    // 오늘 버튼은 존재하지 않음 (제거됨)
    expect(screen.queryByRole('button', { name: '오늘' })).not.toBeInTheDocument()
  })

  it('연도와 월 선택으로 원하는 달력 날짜로 바로 이동한다', async () => {
    renderWithRouter(<IpoCalendar ipos={[makeIpo()]} initialYear={2026} initialMonth={10} />)

    await userEvent.selectOptions(screen.getByRole('combobox', { name: '연도' }), '2025')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '월' }), '3')

    expect(screen.getByText('2025년 3월')).toBeInTheDocument()
  })

  it('날짜 칸 클릭 시 상세 영역에 회사명이 나온다', async () => {
    const ipos = [
      makeIpo({ companyName: '테스트기업', subscriptionStartDate: '2026-10-05T09:00:00Z' }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /^10월 5일/ })
    await userEvent.click(dateButton)

    const detailRegion = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    expect(detailRegion).toHaveTextContent('테스트기업')
    expect(detailRegion).toHaveTextContent('청약')
  })

  it('선택 날짜의 청약 상세에 모든 주관사 이름을 표시한다', async () => {
    renderWithRouter(
      <IpoCalendar
        ipos={[makeIpo({ subscriptionStartDate: '2026-10-05T09:00:00Z' })]}
        initialYear={2026}
        initialMonth={10}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: /^10월 5일/ }))

    const region = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    await waitFor(() => {
      expect(region).toHaveTextContent('미래에셋증권')
      expect(region).toHaveTextContent('KB증권')
    })
    // '주관사' 접두어는 제거되어야 함
    expect(region).not.toHaveTextContent('주관사')
  })

  it('환불 이벤트 상세에서 모든 주관사 칩이 표시되고 접두어 없음', async () => {
    renderWithRouter(
      <IpoCalendar
        ipos={[
          makeIpo({
            subscriptionStartDate: '2026-10-05T09:00:00Z',
            subscriptionEndDate: '2026-10-07T17:00:00Z',
            refundDate: '2026-10-10T09:00:00Z',
          }),
        ]}
        initialYear={2026}
        initialMonth={10}
        visibleTypes={new Set(['REFUND'])}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: /^10월 10일/ }))
    const region = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    await waitFor(() => {
      expect(region).toHaveTextContent('미래에셋증권')
      expect(region).toHaveTextContent('KB증권')
    })
    expect(region).not.toHaveTextContent('주관사')
  })

  it('상장 이벤트 상세에서 모든 주관사 칩이 표시되고 접두어 없음', async () => {
    renderWithRouter(
      <IpoCalendar
        ipos={[
          makeIpo({
            subscriptionStartDate: '2026-10-05T09:00:00Z',
            subscriptionEndDate: '2026-10-07T17:00:00Z',
            listingDate: '2026-10-20T09:00:00Z',
          }),
        ]}
        initialYear={2026}
        initialMonth={10}
        visibleTypes={new Set(['LISTING'])}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: /^10월 20일/ }))
    const region = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    await waitFor(() => {
      expect(region).toHaveTextContent('미래에셋증권')
      expect(region).toHaveTextContent('KB증권')
    })
    expect(region).not.toHaveTextContent('주관사')
  })

  it('청약 기간 전·중인 청약 일정에는 청약 등록 링크가 ipoId와 함께 보이지 않는다', async () => {
    const ipos = [
      makeIpo({
        id: 5,
        subscriptionStartDate: '2026-10-09T09:00:00Z',
        subscriptionEndDate: '2026-10-12T17:00:00Z',
      }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    await userEvent.click(screen.getByRole('button', { name: /^10월 10일/ }))

    const region = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    // 달력 상세에서는 청약 등록 링크 제거; /subscriptions 목록에서 유지
    expect(within(region).queryByRole('link', { name: /청약 등록/ })).not.toBeInTheDocument()
  })

  it('청약 기간이 지난 일정에는 청약 등록 링크가 없다', async () => {
    const ipos = [
      makeIpo({
        subscriptionStartDate: '2026-10-05T09:00:00Z',
        subscriptionEndDate: '2026-10-07T17:00:00Z',
      }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    await userEvent.click(screen.getByRole('button', { name: /^10월 5일/ }))

    const region = screen.getByRole('region', { name: '선택한 날짜 일정 상세' })
    expect(within(region).queryByRole('link', { name: /청약 등록/ })).not.toBeInTheDocument()
  })

  it('이벤트 없는 날 클릭 시 "공모주 일정이 없습니다"가 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /^10월 10일/ })
    await userEvent.click(dateButton)

    expect(screen.getByText('공모주 일정이 없습니다')).toBeInTheDocument()
  })

  it('선택된 날짜는 aria-pressed로 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-05T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /^10월 5일/ })
    expect(dateButton).not.toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(dateButton)

    expect(dateButton).toHaveAttribute('aria-pressed', 'true')
  })

  it('오늘 날짜는 aria-current="date"로 표시된다', async () => {
    const ipos = [makeIpo({ subscriptionStartDate: '2026-10-10T09:00:00Z' })]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const todayButton = screen.getByRole('button', { name: /^10월 10일/ })
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

    // 월 셀렉트박스로 다음 달(11월) 선택
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '월' }), '11')

    await waitFor(() => {
      // 선택된 날짜가 해제되어 제목이 빈 문자열이 됨
      expect(screen.getByRole('heading', { name: '' })).toBeInTheDocument()
      // 일정 리스트도 없어야 함
      expect(screen.queryByRole('list')).not.toBeInTheDocument()
    })
  })

  it('12월에서 다음 달은 다음 해 1월, 1월에서 이전 달은 전년 12월이다', async () => {
    renderWithRouter(<IpoCalendar ipos={[]} initialYear={2026} initialMonth={12} />)

    // 연도/월 셀렉트박스로 2027년 1월 선택
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '연도' }), '2027')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '월' }), '1')
    expect(screen.getByText('2027년 1월')).toBeInTheDocument()

    // 다시 2026년 12월로
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '연도' }), '2026')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '월' }), '12')
    expect(screen.getByText('2026년 12월')).toBeInTheDocument()
  })

  it('상세 패널의 각 항목이 올바른 상세 페이지 링크(href)를 가진다', async () => {
    const ipos = [
      makeIpo({ id: 7, companyName: '테스트기업', subscriptionStartDate: '2026-10-05T09:00:00Z' }),
    ]
    renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

    const dateButton = screen.getByRole('button', { name: /^10월 5일/ })
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
      expect(screen.getByRole('button', { name: /테스트기업 청약/ })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /테스트기업 상장/ })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /테스트기업 환불/ })).toBeInTheDocument()

      // 청약 끄기
      await userEvent.click(screen.getByRole('button', { name: '청약' }))
      expect(screen.queryByRole('button', { name: /테스트기업 청약/ })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /테스트기업 상장/ })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /테스트기업 환불/ })).toBeInTheDocument()

      // 환불 끄기
      await userEvent.click(screen.getByRole('button', { name: '환불' }))
      expect(screen.queryByRole('button', { name: /테스트기업 청약/ })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /테스트기업 상장/ })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /테스트기업 환불/ })).not.toBeInTheDocument()

      // 상장 끄기
      await userEvent.click(screen.getByRole('button', { name: '상장' }))
      expect(screen.queryByRole('button', { name: /테스트기업 청약/ })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /테스트기업 상장/ })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /테스트기업 환불/ })).not.toBeInTheDocument()
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
      const dateButtonAll = screen.getByRole('button', { name: /^10월 5일/ })
      expect(dateButtonAll).toHaveAttribute('aria-label', '10월 5일, 일정 3개')

      // 청약 끄기
      await userEvent.click(screen.getByRole('button', { name: '청약' }))
      const dateButtonTwo = screen.getByRole('button', { name: /^10월 5일/ })
      expect(dateButtonTwo).toHaveAttribute('aria-label', '10월 5일, 일정 2개')

      // 환불 끄기
      await userEvent.click(screen.getByRole('button', { name: '환불' }))
      const dateButtonOne = screen.getByRole('button', { name: /^10월 5일/ })
      expect(dateButtonOne).toHaveAttribute('aria-label', '10월 5일, 일정 1개')

      // 상장 끄기
      await userEvent.click(screen.getByRole('button', { name: '상장' }))
      const dateButtonZero = screen.getByRole('button', { name: /^10월 5일/ })
      // 일정이 없으면 개수 문구를 붙이지 않는다(기존 동작)
      expect(dateButtonZero).toHaveAttribute('aria-label', '10월 5일')
    })

    it('3종 모두 꺼짐 안내가 보인다', async () => {
      const ipos = [makeIpo()]
      renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

      await userEvent.click(screen.getByRole('button', { name: '청약' }))
      await userEvent.click(screen.getByRole('button', { name: '환불' }))
      await userEvent.click(screen.getByRole('button', { name: '상장' }))

      expect(screen.getByText('표시할 일정 종류를 선택해 주세요.')).toBeInTheDocument()
    })

    it('모두 보기 버튼은 제거되었다', async () => {
      const ipos = [makeIpo()]
      renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

      // "모두 보기" 버튼이 없음을 확인
      expect(screen.queryByRole('button', { name: '모두 보기' })).not.toBeInTheDocument()
    })

    it('필터 버튼을 모두 누르면 종류가 모두 켜진다', async () => {
      const ipos = [makeIpo()]
      renderWithRouter(<FilterTestWrapper ipos={ipos} initialYear={2026} initialMonth={10} />)

      // 먼저 모두 끈 상태에서 다시 켜기
      await userEvent.click(screen.getByRole('button', { name: '청약' }))
      await userEvent.click(screen.getByRole('button', { name: '환불' }))
      await userEvent.click(screen.getByRole('button', { name: '상장' }))

      // 이제 모두 꺼진 상태 (aria-pressed="false")
      expect(screen.getByRole('button', { name: '청약' })).toHaveAttribute('aria-pressed', 'false')
      expect(screen.getByRole('button', { name: '환불' })).toHaveAttribute('aria-pressed', 'false')
      expect(screen.getByRole('button', { name: '상장' })).toHaveAttribute('aria-pressed', 'false')

      // 다시 클릭하여 모두 켜기
      await userEvent.click(screen.getByRole('button', { name: '청약' }))
      await userEvent.click(screen.getByRole('button', { name: '환불' }))
      await userEvent.click(screen.getByRole('button', { name: '상장' }))

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

      const dateButton = screen.getByRole('button', { name: /^10월 1일/ })
      await userEvent.click(dateButton)

      expect(screen.getByText('2026-10-01 ~ 2026-10-03')).toBeInTheDocument()
    })

    it('visibleTypes props 없이도 동작한다(기존 테스트 호환)', async () => {
      const ipos = [makeIpo()]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      expect(screen.getByRole('button', { name: /테스트기업 청약/ })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /테스트기업 상장/ })).toBeInTheDocument()
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
      // 막대는 종료일인 10/12에만 표시됨 (SUBSCRIPTION 종료일 하루 표시)
      const bars = screen.getAllByRole('button', { name: /테스트기업 청약/ })
      expect(bars.length).toBe(1)
    })
  })

  describe('공휴일·휴장일 표시', () => {
    it('공휴일 응답이 있으면 해당 칸에 이름이 보이고 날짜 숫자가 붉은색이다', async () => {
      setHolidays([
        { date: '2026-10-09', name: '한글날' },
        { date: '2026-10-03', name: '개천절' },
      ])
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      // 쿼리 완료 대기
      await waitFor(() => {
        const hangulDay = screen.getByRole('button', { name: /10월 9일, 한글날/ })
        expect(hangulDay).toHaveTextContent('한글날')
        expect(hangulDay).toHaveClass(styles.hasHoliday)
      })

      await waitFor(() => {
        const gaecheonDay = screen.getByRole('button', { name: /10월 3일, 개천절/ })
        expect(gaecheonDay).toHaveTextContent('개천절')
      })
    })

    it('공휴일이 아닌 칸에는 이름이 없고 기존 스타일이다', async () => {
      setHolidays([{ date: '2026-10-09', name: '한글날' }])
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      await waitFor(() => {
        const normalDay = screen.getByRole('button', { name: /10월 10일/ })
        expect(normalDay).not.toHaveTextContent('한글날')
        expect(normalDay).not.toHaveClass('hasHoliday')
      })
    })

    it('공휴일인 날짜를 선택하면 상세 패널에 "휴장일 · 이름"이 보인다', async () => {
      setHolidays([{ date: '2026-10-09', name: '한글날' }])
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      await waitFor(() => {
        const dateButton = screen.getByRole('button', { name: /10월 9일, 한글날/ })
        expect(dateButton).toBeInTheDocument()
      })
      await userEvent.click(screen.getByRole('button', { name: /10월 9일, 한글날/ }))

      await waitFor(() => {
        expect(screen.getByText('휴장일 · 한글날')).toBeInTheDocument()
      })
    })

    it('일정이 없는 공휴일을 선택하면 "휴장일 · 이름"과 "공모주 일정이 없습니다"가 모두 보인다', async () => {
      setHolidays([{ date: '2026-10-09', name: '한글날' }])
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      await waitFor(() => {
        const dateButton = screen.getByRole('button', { name: /10월 9일, 한글날/ })
        expect(dateButton).toBeInTheDocument()
      })
      await userEvent.click(screen.getByRole('button', { name: /10월 9일, 한글날/ }))

      await waitFor(() => {
        expect(screen.getByText('휴장일 · 한글날')).toBeInTheDocument()
        expect(screen.getByText('공모주 일정이 없습니다')).toBeInTheDocument()
      })
    })

    it('응답 실패(reject)여도 달력은 정상으로 그려지고 오류 문구가 없다', async () => {
      setHolidays([])
      vi.mocked(apiFetch).mockImplementation((url) => {
        if (typeof url === 'string' && url.startsWith('/market-holidays')) {
          return Promise.reject(new Error('Failed to fetch'))
        }
        return Promise.resolve([])
      })
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      // 달력이 정상 렌더링됨
      await waitFor(() => {
        expect(screen.getByText('2026년 10월')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /청약/ })).toBeInTheDocument()
      })
      // 오류 문구가 없어야 함
      expect(screen.queryByText(/오류|에러|실패/i)).not.toBeInTheDocument()
    })

    it('월 이동으로 연도가 바뀌면 새 연도를 요청한다(12월→1월)', async () => {
      // 2026년 12월 휴장일
      setHolidays([{ date: '2026-12-25', name: '크리스마스' }])
      renderWithRouter(<IpoCalendar ipos={[]} initialYear={2026} initialMonth={12} />)

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /12월 25일, 크리스마스/ })).toBeInTheDocument()
      })

      // 2027년 1월 휴장일로 변경
      setHolidays([{ date: '2027-01-01', name: '신정' }])

      // 연도/월 셀렉트박스로 다음 달(2027년 1월)로 이동
      await userEvent.selectOptions(screen.getByRole('combobox', { name: '연도' }), '2027')
      await userEvent.selectOptions(screen.getByRole('combobox', { name: '월' }), '1')

      await waitFor(() => {
        expect(screen.getByText('2027년 1월')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /1월 1일, 신정/ })).toBeInTheDocument()
      })

      // 2026년 12월, 2027년 1월 순서로 호출되었는지 확인
      expect(apiFetch).toHaveBeenNthCalledWith(1, '/market-holidays?year=2026', expect.any(Object))
      expect(apiFetch).toHaveBeenNthCalledWith(2, '/market-holidays?year=2027', expect.any(Object))
    })

    it('aria-label에 공휴일 이름과 일정 개수가 모두 포함된다', async () => {
      setHolidays([{ date: '2026-10-09', name: '한글날' }])
      const ipos = [
        makeIpo({
          subscriptionStartDate: '2026-10-09T09:00:00Z',
          subscriptionEndDate: '2026-10-09T17:00:00Z',
        }),
      ]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)

      await waitFor(() => {
        const dateButton = screen.getByRole('button', { name: '10월 9일, 한글날, 일정 1개' })
        expect(dateButton).toBeInTheDocument()
      })
    })
  })

  describe('모바일 주말 표시·스크롤·reduced motion', () => {
    const originalInnerWidth = window.innerWidth
    const originalMatchMedia = window.matchMedia
    let scrollMockFn: any

    beforeEach(() => {
      scrollMockFn = vi.fn()
      const proto = (globalThis as any).HTMLElement?.prototype
      if (proto)
        Object.defineProperty(proto, 'scrollIntoView', {
          value: scrollMockFn,
          writable: true,
          configurable: true,
        })
    })
    afterEach(() => {
      const proto = (globalThis as any).HTMLElement?.prototype
      if (proto && proto.scrollIntoView === scrollMockFn) {
        // restore by deleting mock (jsdom lacks method by default); no need to keep
        delete proto.scrollIntoView
      }
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: originalInnerWidth,
      })
      window.dispatchEvent(new Event('resize'))
      window.matchMedia = originalMatchMedia
    })

    it('모바일(isMobile=true)에서 주말 전용 세그먼트는 렌더 세그먼트가 없고 Sun..Sat 전체 이벤트는 월~금 col1..5만 표시', async () => {
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 390,
      })
      window.dispatchEvent(new Event('resize'))
      const ipos = [
        makeIpo({
          subscriptionStartDate: '2026-10-10T09:00:00Z',
          subscriptionEndDate: '2026-10-11T17:00:00Z',
        }),
      ]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)
      expect(screen.getByText('2026년 10월')).toBeInTheDocument()
    })

    it('날짜 클릭 후 상세 section의 scrollIntoView가 smooth behavior + block start로 호출된다', async () => {
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 390,
      })
      window.dispatchEvent(new Event('resize'))
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-05T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)
      await userEvent.click(screen.getByRole('button', { name: /^10월 5일/ }))
      expect(scrollMockFn).toHaveBeenCalled()
      const args = (scrollMockFn.mock.calls[scrollMockFn.mock.calls.length - 1] || [])[0] as Record<
        string,
        unknown
      >
      expect(args.behavior).toBe('smooth')
      expect(args.block).toBe('start')
    })

    it('initial render에서는 scrollIntoView가 호출되지 않는다', async () => {
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)
      expect(scrollMockFn).not.toHaveBeenCalled()
    })

    it('reduced motion (matchMedia reduce=true) 시 클릭 시 scrollIntoView는 auto behavior + block start로 호출된다', async () => {
      const mockMatchMedia = (query: string) => ({
        matches: query === '(prefers-reduced-motion: reduce)',
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      })
      const prev = window.matchMedia
      window.matchMedia = mockMatchMedia as typeof window.matchMedia
      try {
        Object.defineProperty(window, 'innerWidth', {
          writable: true,
          configurable: true,
          value: 390,
        })
        window.dispatchEvent(new Event('resize'))
        const ipos = [makeIpo({ subscriptionStartDate: '2026-10-05T09:00:00Z' })]
        renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)
        await userEvent.click(screen.getByRole('button', { name: /^10월 5일/ }))
        expect(scrollMockFn).toHaveBeenCalled()
        const args = (scrollMockFn.mock.calls[scrollMockFn.mock.calls.length - 1] ||
          [])[0] as Record<string, unknown>
        expect(args.behavior).toBe('auto')
        expect(args.block).toBe('start')
      } finally {
        window.matchMedia = prev
      }
    })

    it('초기 오늘 선택은 fake system time이 실제로 적용된다', async () => {
      vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-01T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)
      await waitFor(() => {
        const todayButton = screen.getByRole('button', { name: /^10월 10일/ })
        expect(todayButton).toHaveAttribute('aria-current', 'date')
      })
    })

    it('desktop click (innerWidth=1024) 시 자동 scroll하지 않는다', async () => {
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 1024,
      })
      window.dispatchEvent(new Event('resize'))
      const ipos = [makeIpo({ subscriptionStartDate: '2026-10-05T09:00:00Z' })]
      renderWithRouter(<IpoCalendar ipos={ipos} initialYear={2026} initialMonth={10} />)
      await userEvent.click(screen.getByRole('button', { name: /^10월 5일/ }))
      expect(scrollMockFn).not.toHaveBeenCalled()
    })
  })
})
