import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SaleRegisterPage from './SaleRegisterPage.tsx'
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

function renderPage() {
  vi.mocked(apiFetch).mockImplementation(async () => [])
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/sales/register']}>
        <SaleRegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SaleRegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
  })

  it('매도 등록 페이지가 렌더링됨', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('매도 등록')).toBeInTheDocument()
    })
  })

  it('SaleRegisterForm이 렌더링됨', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByLabelText('매도수량')).toBeInTheDocument()
    })
  })
})
