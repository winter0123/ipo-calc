import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InvestmentHistoryPage from './InvestmentHistoryPage.tsx'
import { apiFetch } from '../../shared/api/client.ts'
import type { InvestmentHistoryResponse } from '../../entities/subscription/types.ts'

vi.mock('../../shared/api/client.ts', () => ({
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

const EMPTY_HISTORY_RESPONSE: InvestmentHistoryResponse = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/investments']}>
        <InvestmentHistoryPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForLoaded() {
  await waitFor(() => {
    expect(screen.getByText('투자 내역')).toBeInTheDocument()
  })
}

async function waitForResultsLoaded() {
  await waitFor(() => {
    expect(screen.getByText('카카오뱅크')).toBeInTheDocument()
  })
}

describe('InvestmentHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('로딩 상태를 표시한 뒤 응답 수신 후 결과를 표시함', async () => {
    let resolveHistory: (value: unknown) => void = () => {}
    vi.mocked(apiFetch).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveHistory = resolve
        }),
    )
    renderPage()

    await waitForLoaded()
    expect(screen.getByText('투자 내역을 불러오는 중...')).toBeInTheDocument()

    resolveHistory(HISTORY_RESPONSE)

    await waitForResultsLoaded()
  })

  it('필터 입력 시 입력값이 UI에 반영되고 재조회가 트리거됨 (투자자 ID)', async () => {
    vi.mocked(apiFetch).mockResolvedValue(HISTORY_RESPONSE)
    renderPage()
    await waitForResultsLoaded()

    const investorInput = screen.getByLabelText('투자자 ID')
    fireEvent.change(investorInput, { target: { value: '1' } })

    await waitFor(() => {
      expect(investorInput).toHaveValue('1')
    })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(2)
    })
  })

  it('필터 입력 시 입력값이 UI에 반영되고 재조회가 트리거됨 (증권계좌 ID)', async () => {
    vi.mocked(apiFetch).mockResolvedValue(HISTORY_RESPONSE)
    renderPage()
    await waitForResultsLoaded()

    const securitiesInput = screen.getByLabelText('증권계좌 ID')
    fireEvent.change(securitiesInput, { target: { value: '2' } })

    await waitFor(() => {
      expect(securitiesInput).toHaveValue('2')
    })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(2)
    })
  })

  it('필터 입력 시 입력값이 UI에 반영되고 재조회가 트리거됨 (IPO ID)', async () => {
    vi.mocked(apiFetch).mockResolvedValue(HISTORY_RESPONSE)
    renderPage()
    await waitForResultsLoaded()

    const ipoInput = screen.getByLabelText('IPO ID')
    fireEvent.change(ipoInput, { target: { value: '3' } })

    await waitFor(() => {
      expect(ipoInput).toHaveValue('3')
    })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(2)
    })
  })

  it('초기화 버튼 클릭 시 필터가 비워지고 재조회됨', async () => {
    vi.mocked(apiFetch).mockResolvedValue(HISTORY_RESPONSE)
    renderPage()
    await waitForResultsLoaded()

    const investorInput = screen.getByLabelText('투자자 ID')
    fireEvent.change(investorInput, { target: { value: '1' } })
    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(2)
    })
    await waitFor(() => {
      expect(investorInput).toHaveValue('1')
    })

    fireEvent.click(screen.getByRole('button', { name: /초기화/i }))

    await waitFor(() => {
      expect(investorInput).toHaveValue('')
    })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(4)
    })

    // 초기화 후 필터가 비워졌는지 확인
    await waitFor(() => {
      expect(investorInput).toHaveValue('')
    })
  })

  it('빈 결과 시 EmptyState가 표시되고 초기화 버튼이 동작함', async () => {
    vi.mocked(apiFetch).mockResolvedValue(EMPTY_HISTORY_RESPONSE)
    renderPage()
    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByText('해당 조건에 해당하는 기록이 없습니다.')).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: '전체 필터 초기화' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '전체 필터 초기화' }))

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(2)
    })
  })

  it('네트워크 오류 시 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Failed to fetch'))
    renderPage()
    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('404 에러 시 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Request failed: 404 Not Found'))
    renderPage()
    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByText('투자 내역을 찾을 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('기타 API 에러 시 공용 에러 메시지 표시', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('Request failed: 500'))
    renderPage()
    await waitForLoaded()

    await waitFor(() => {
      expect(screen.getByText('투자 내역을 불러오는 데 실패했습니다.')).toBeInTheDocument()
    })
  })

  it('FilterBar에 접근성 속성이 올바르게 설정됨', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(HISTORY_RESPONSE)
    renderPage()
    await waitForLoaded()

    expect(screen.getByRole('search')).toBeInTheDocument()
    expect(screen.getByLabelText('투자자 ID')).toBeInTheDocument()
    expect(screen.getByLabelText('증권계좌 ID')).toBeInTheDocument()
    expect(screen.getByLabelText('IPO ID')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: /초기화/i })).toHaveAttribute(
      'aria-label',
      '필터 초기화',
    )
  })

  it('EmptyState에 접근성 속성이 올바르게 설정됨', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(EMPTY_HISTORY_RESPONSE)
    renderPage()
    await waitForLoaded()

    await waitFor(() => {
      const emptyState = screen
        .getByText('해당 조건에 해당하는 기록이 없습니다.')
        .closest('[role="status"]')
      expect(emptyState).toBeInTheDocument()
      expect(emptyState).toHaveAttribute('aria-live', 'polite')
    })
  })
})
