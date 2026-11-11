import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SaleRegisterPage from './SaleRegisterPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

vi.mock('@/shared/lib/ocr/recognizeImages.ts', () => ({
  recognizeImages: vi.fn(async () => [{ width: 1125, height: 2436, words: [] }]),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

const IPO = {
  id: 10,
  companyName: '카카오뱅크',
  ipoName: '카카오뱅크',
  stockCode: '323410',
  roundLabel: null,
  lowerBandPrice: null,
  upperBandPrice: null,
  fixedPrice: 10000,
  subscriptionStartDate: '2026-10-01',
  subscriptionEndDate: '2026-10-02',
  listingDate: '2026-10-23',
  offeringShareCount: 1000000,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

function mockApi() {
  vi.mocked(apiFetch).mockImplementation(async (path) => {
    const p = String(path)
    if (p === '/investors') return []
    if (p === '/ipos/10') return IPO
    if (p === '/ipos/10/insight') return { underwriters: [] }
    return []
  })
}

function renderPage(url = '/sales/register') {
  mockApi()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <SaleRegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SaleRegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
    localStorage.setItem('currentInvestorId', '1')
  })

  it('매도 등록 페이지가 렌더링됨', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('매도 등록')).toBeInTheDocument()
    })
  })

  it('문맥 없는 /sales/register는 공모주 청약 목록으로 가는 안내만 보여 준다', async () => {
    renderPage('/sales/register')
    await waitFor(() => {
      expect(screen.getByText(/공모주 청약 목록으로 이동/)).toBeInTheDocument()
    })
    expect(screen.queryByLabelText('매도수량')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('공모주')).not.toBeInTheDocument()
  })
})
