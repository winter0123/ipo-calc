import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoCalendarPage from './IpoCalendarPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import type { Ipo } from '@/entities/ipo/types.ts'
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

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <IpoCalendarPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('IpoCalendarPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('로딩 상태를 표시한 뒤 응답 수신 후 달력을 표시한다', async () => {
    let resolveIpos: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveIpos = resolve
        }),
    )
    renderPage()

    expect(screen.getByText('일정을 불러오는 중...')).toBeInTheDocument()

    resolveIpos([makeIpo()])

    expect(await screen.findByText('청약 시작')).toBeInTheDocument()
    expect(screen.queryByText('일정을 불러오는 중...')).not.toBeInTheDocument()
  })

  it('데이터 있는 달에 이벤트 라벨이 텍스트로 보인다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([makeIpo()])
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('청약 시작')).toBeInTheDocument()
    })
    expect(screen.getByText('청약 마감')).toBeInTheDocument()
    expect(screen.getByText('상장')).toBeInTheDocument()
  })

  it('빈 목록일 때 안내 문구를 표시한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('표시할 공모주 일정이 없습니다.')).toBeInTheDocument()
    })
  })

  it('네트워크 오류 시 에러 메시지를 표시한다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Failed to fetch'))
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('기타 API 에러 시 공용 에러 메시지를 표시한다', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(500, 'Request failed: 500'))
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('공모주 일정을 불러오는 데 실패했습니다.')).toBeInTheDocument()
    })
  })

  it('h1 제목을 정확히 1개 렌더링한다', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([makeIpo()])
    renderPage()

    await waitFor(() => {
      const headings = screen.getAllByRole('heading', { level: 1 })
      expect(headings).toHaveLength(1)
      expect(headings[0]).toHaveTextContent('공모주 일정')
    })
  })
})
