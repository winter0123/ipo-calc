import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AllocationRegisterPage from './AllocationRegisterPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

function renderPage(initialEntries: string[] = ['/allocations/register']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <AllocationRegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AllocationRegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
  })

  it('subscriptionId 쿼리 파라미터가 없으면 /subscriptions로 리다이렉트됨', async () => {
    renderPage(['/allocations/register'])

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/subscriptions')
    })
  })

  it('subscriptionId 쿼리 파라미터가 있으면 AllocationRegisterForm이 정상 렌더링됨', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
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
      investorName: null,
      securitiesFirmName: null,
    })

    renderPage(['/allocations/register?subscriptionId=1'])

    await waitFor(() => {
      expect(screen.getByText('배정 등록')).toBeInTheDocument()
      expect(screen.getByText('청약수량 (참고)')).toBeInTheDocument()
      expect(screen.getByDisplayValue('100')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('빈 문자열 subscriptionId도 리다이렉트 처리함', async () => {
    renderPage(['/allocations/register?subscriptionId='])

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/subscriptions')
    })
  })
})
