import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoCalendarPage from './IpoCalendarPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import type { Ipo } from '@/entities/ipo/index.ts'
import { ApiError } from '@/shared/api/apiError.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

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

function LocationSearch() {
  return <div data-testid="search">{useLocation().search}</div>
}

function renderPage(initialEntries = ['/ipo-calendar']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <IpoCalendarPage />
        <LocationSearch />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('IpoCalendarPage', () => {
  let ipoResponse: unknown[] = []
  let holidayResponse: unknown[] = []

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    ipoResponse = []
    holidayResponse = []
    vi.mocked(apiFetch).mockImplementation((url) => {
      if (typeof url === 'string' && url.startsWith('/market-holidays')) {
        return Promise.resolve(holidayResponse)
      }
      if (typeof url === 'string' && url === '/ipos') {
        return Promise.resolve(ipoResponse)
      }
      return Promise.resolve([])
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const setIpos = (ipos: unknown[]) => {
    ipoResponse = ipos
  }

  const setHolidays = (holidays: unknown[]) => {
    holidayResponse = holidays
  }

  it('로딩 상태를 표시한 뒤 응답 수신 후 달력을 표시한다', async () => {
    let resolveIpos: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementation((url) => {
      if (typeof url === 'string' && url.startsWith('/market-holidays')) {
        return Promise.resolve([])
      }
      if (typeof url === 'string' && url === '/ipos') {
        return new Promise((resolve) => {
          resolveIpos = resolve
        })
      }
      return Promise.resolve([])
    })
    renderPage()

    expect(screen.getByText('일정을 불러오는 중...')).toBeInTheDocument()

    resolveIpos([makeIpo()])

    await waitFor(() => {
      expect(screen.getByText('청약')).toBeInTheDocument()
    })
    expect(screen.queryByText('일정을 불러오는 중...')).not.toBeInTheDocument()
  })

  it('데이터 있는 달에 청약·상장 막대가 보인다', async () => {
    setIpos([makeIpo()])
    setHolidays([])
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('청약')).toBeInTheDocument()
    })
    expect(screen.getByText('상장')).toBeInTheDocument()
  })

  it('빈 목록일 때 안내 문구를 표시한다', async () => {
    setIpos([])
    setHolidays([])
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('표시할 공모주 일정이 없습니다.')).toBeInTheDocument()
    })
  })

  it('네트워크 오류 시 에러 메시지를 표시한다', async () => {
    vi.mocked(apiFetch).mockImplementation((url) => {
      if (typeof url === 'string' && url.startsWith('/market-holidays')) {
        return Promise.resolve([])
      }
      if (typeof url === 'string' && url === '/ipos') {
        return Promise.reject(new Error('Failed to fetch'))
      }
      return Promise.resolve([])
    })
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('기타 API 에러 시 공용 에러 메시지를 표시한다', async () => {
    vi.mocked(apiFetch).mockImplementation((url) => {
      if (typeof url === 'string' && url.startsWith('/market-holidays')) {
        return Promise.resolve([])
      }
      if (typeof url === 'string' && url === '/ipos') {
        return Promise.reject(new ApiError(500, 'Request failed: 500'))
      }
      return Promise.resolve([])
    })
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('공모주 일정을 불러오는 데 실패했습니다.')).toBeInTheDocument()
    })
  })

  it('h1 제목을 정확히 1개 렌더링한다', async () => {
    setIpos([makeIpo()])
    setHolidays([])
    renderPage()

    await waitFor(() => {
      const headings = screen.getAllByRole('heading', { level: 1 })
      expect(headings).toHaveLength(1)
      expect(headings[0]).toHaveTextContent('공모주 일정')
    })
  })

  describe('URL 쿼리 파라미터 필터', () => {
    it('초기 엔트리에 types=LISTING이면 상장 막대만 보인다', async () => {
      setIpos([makeIpo()])
      setHolidays([])
      renderPage(['/ipo-calendar?types=LISTING'])

      await waitFor(() => {
        expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
      })
      await waitFor(() => {
        expect(screen.queryByRole('link', { name: /청약/ })).not.toBeInTheDocument()
      })
    })

    it('필터 버튼을 누르면 해당 종류의 막대가 사라진다', async () => {
      setIpos([makeIpo()])
      setHolidays([])
      renderPage(['/ipo-calendar'])

      await waitFor(() => {
        expect(screen.getByRole('link', { name: /청약/ })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
      })

      // 청약 끄기
      await waitFor(() => {
        const subButton = screen.getByRole('button', { name: '청약' })
        expect(subButton).not.toBeDisabled()
      })
      const subButton = screen.getByRole('button', { name: '청약' })
      await userEvent.click(subButton)

      // 청약 막대가 사라지고 상장만 남음
      await waitFor(() => {
        expect(screen.queryByRole('link', { name: /청약/ })).not.toBeInTheDocument()
      })
      expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
    })

    it('모두 보기 버튼을 누르면 모든 종류가 켜진다', async () => {
      setIpos([makeIpo()])
      setHolidays([])
      renderPage(['/ipo-calendar?types=LISTING'])

      await waitFor(() => {
        expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
      })

      // 모두 보기 버튼으로 청약 켜기
      const showAllButton = screen.getByRole('button', { name: '모두 보기' })
      await waitFor(() => {
        expect(showAllButton).not.toBeDisabled()
      })
      await userEvent.click(showAllButton)

      // 청약과 상장 모두 보임
      await waitFor(() => {
        expect(screen.getByRole('link', { name: /청약/ })).toBeInTheDocument()
      })
      expect(screen.getByRole('link', { name: /상장/ })).toBeInTheDocument()
    })

    it('세 종류를 모두 끄면 주소가 types= 가 되고 안내 문구가 보이며, 다시 켜면 값이 생기고 모두 켜면 사라진다', async () => {
      setIpos([makeIpo()])
      setHolidays([])
      renderPage(['/ipo-calendar'])
      await waitFor(() => {
        expect(screen.getByRole('button', { name: '청약' })).toBeInTheDocument()
      })
      expect(screen.getByTestId('search').textContent).toBe('')

      // 청약을 끄면 남은 종류가 환불·상장 순서로 저장된다
      await userEvent.click(screen.getByRole('button', { name: '청약' }))
      expect(decodeURIComponent(screen.getByTestId('search').textContent ?? '')).toBe(
        '?types=REFUND,LISTING',
      )

      await userEvent.click(screen.getByRole('button', { name: '환불' }))
      expect(screen.getByTestId('search').textContent).toBe('?types=LISTING')

      // 모두 끄면 빈 값(types=)이며 모두 켜짐(쿼리 없음)과 구분된다
      await userEvent.click(screen.getByRole('button', { name: '상장' }))
      expect(screen.getByTestId('search').textContent).toBe('?types=')
      expect(screen.getByText('표시할 일정 종류를 선택해 주세요.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '모두 보기' })).not.toBeDisabled()

      await userEvent.click(screen.getByRole('button', { name: '청약' }))
      expect(screen.getByTestId('search').textContent).toBe('?types=SUBSCRIPTION')

      // 모두 보기로 3종이 켜지면 쿼리가 사라진다
      await userEvent.click(screen.getByRole('button', { name: '모두 보기' }))
      expect(screen.getByTestId('search').textContent).toBe('')
    })
  })
})
