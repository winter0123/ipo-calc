import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProfitViewPage from './ProfitViewPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

vi.mock('@/entities/investor/api-client.ts', async () => {
  const actual = await vi.importActual('@/entities/investor/api-client.ts')
  return {
    ...actual,
    fetchInvestors: vi.fn(),
  }
})

import { fetchInvestors } from '@/entities/investor/api-client.ts'
import { ApiError } from '@/shared/api/apiError.ts'

const INVESTORS = [
  {
    id: 1,
    displayName: '홍길동',
    accountId: 1,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
  {
    id: 2,
    displayName: '김철수',
    accountId: 1,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
]

const SALE_PROFIT = {
  saleId: 1,
  saleDate: '2026-09-21T10:00:00Z',
  allocationId: 10,
  quantity: 50,
  saleAmount: 2000000,
  matchedAcquisitionAmount: 1500000,
  matchedCostAmount: 100000,
  saleFee: 5000,
  saleTax: 30000,
  saleOtherCost: 1000,
  preTaxProfit: 364000,
  afterTaxProfit: 361000,
}

const INVESTOR_PROFITS = {
  investorId: 1,
  sales: [
    {
      saleId: 1,
      saleDate: '2026-09-21T10:00:00Z',
      allocationId: 10,
      quantity: 50,
      saleAmount: 2000000,
      matchedAcquisitionAmount: 1500000,
      matchedCostAmount: 100000,
      saleFee: 5000,
      saleTax: 30000,
      saleOtherCost: 1000,
      preTaxProfit: 364000,
      afterTaxProfit: 361000,
    },
    {
      saleId: 2,
      saleDate: '2026-09-22T10:00:00Z',
      allocationId: 11,
      quantity: 30,
      saleAmount: 1200000,
      matchedAcquisitionAmount: 900000,
      matchedCostAmount: 60000,
      saleFee: 3000,
      saleTax: 18000,
      saleOtherCost: 500,
      preTaxProfit: -5000,
      afterTaxProfit: -8000,
    },
  ],
  totalSaleAmount: 3200000,
  totalPreTaxProfit: 359000,
  totalAfterTaxProfit: 353000,
}

const EMPTY_INVESTOR_PROFITS = {
  investorId: 1,
  sales: [],
  totalSaleAmount: 0,
  totalPreTaxProfit: 0,
  totalAfterTaxProfit: 0,
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProfitViewPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForInvestorsLoaded() {
  await waitFor(() => {
    expect(screen.getByRole('radio', { name: '매도건별' })).toBeInTheDocument()
  })
}

async function waitForInvestorDropdown() {
  await waitFor(() => {
    expect(screen.getByLabelText('투자자')).toBeInTheDocument()
  })
}

function setupDefaultMocks() {
  vi.mocked(fetchInvestors).mockResolvedValue(INVESTORS)
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const p = String(path)
    if (p === '/sales/1/profit') return SALE_PROFIT
    if (p === '/investors/1/profits') return INVESTOR_PROFITS
    if (p === '/investors/2/profits') return EMPTY_INVESTOR_PROFITS
    throw new Error(`unexpected path: ${String(path)}`)
  })
}

describe('ProfitViewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupDefaultMocks()
  })

  it('기본적으로 매도건별 모드로 표시됨', async () => {
    renderPage()
    await waitForInvestorsLoaded()
    expect(screen.getByRole('radio', { name: '매도건별' })).toBeChecked()
    expect(screen.getByRole('radio', { name: '투자자별' })).not.toBeChecked()
  })

  it('모드 토글이 작동함', async () => {
    renderPage()
    await waitForInvestorsLoaded()

    await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
    expect(screen.getByRole('radio', { name: '투자자별' })).toBeChecked()

    await userEvent.click(screen.getByRole('radio', { name: '매도건별' }))
    expect(screen.getByRole('radio', { name: '매도건별' })).toBeChecked()
  })

  it('모드 전환 시 입력 필드가 리셋됨', async () => {
    renderPage()
    await waitForInvestorsLoaded()

    await fill(screen.getByLabelText('매도건 ID'), '1')
    await userEvent.click(screen.getByRole('button', { name: /조회/ }))
    await waitFor(() => {
      expect(screen.getByText('1')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
    await waitForInvestorDropdown()
    expect(screen.getByLabelText('투자자')).toHaveValue('')

    await userEvent.click(screen.getByRole('radio', { name: '매도건별' }))
    const saleIdInput = screen.getByLabelText('매도건 ID') as HTMLInputElement
    // React uncontrolled input may have null or '' value when empty
    expect(saleIdInput.value === '' || saleIdInput.value === null).toBe(true)
  })

  describe('매도건별 조회', () => {
    it('입력만으로는 조회하지 않고 조회 버튼을 눌러야 조회한다', async () => {
      renderPage()
      await waitForInvestorsLoaded()
      vi.mocked(apiFetch).mockClear()

      await fill(screen.getByLabelText('매도건 ID'), '123')

      expect(screen.getByLabelText('매도건 ID')).toHaveValue(123)
      expect(apiFetch).not.toHaveBeenCalled()
    })

    it('매도건별 조회 성공 시 수익 행이 표시됨 (AC-013)', async () => {
      renderPage()
      await waitForInvestorsLoaded()

      await fill(screen.getByLabelText('매도건 ID'), '1')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByText('1')).toBeInTheDocument()
      })
      expect(screen.getByText('2,000,000')).toBeInTheDocument()
      expect(screen.getByText('364,000')).toBeInTheDocument()
      expect(screen.getByText('361,000')).toBeInTheDocument()
    })

    it('존재하지 않는 매도건 404 시 에러 메시지 표시 (AC-015)', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const p = String(path)
        if (p === '/sales/999/profit') throw new ApiError(404, 'Request failed: 404')
        throw new Error(`unexpected path: ${String(path)}`)
      })

      renderPage()
      await waitForInvestorsLoaded()

      await fill(screen.getByLabelText('매도건 ID'), '999')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByText('매도건을 찾을 수 없습니다.')).toBeInTheDocument()
      })
    })

    it('네트워크 오류 시 에러 메시지 표시', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const p = String(path)
        if (p === '/sales/1/profit') throw new Error('Failed to fetch')
        throw new Error(`unexpected path: ${String(path)}`)
      })

      renderPage()
      await waitForInvestorsLoaded()

      await fill(screen.getByLabelText('매도건 ID'), '1')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
      })
    })
  })

  describe('투자자별 조회', () => {
    it('투자자별 조회 성공 시 테이블과 합계가 표시됨 (AC-014)', async () => {
      renderPage()
      await waitForInvestorsLoaded()

      await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
      await waitForInvestorDropdown()
      await fill(screen.getByLabelText('투자자'), '1')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByText('홍길동')).toBeInTheDocument()
      })
      await waitFor(() => {
        expect(screen.getByText('364,000')).toBeInTheDocument()
      })
      expect(screen.getByText('-5,000')).toBeInTheDocument()
      expect(screen.getByText('-8,000')).toBeInTheDocument()
      expect(screen.getByText('3,200,000')).toBeInTheDocument()
      expect(screen.getByText('359,000')).toBeInTheDocument()
      expect(screen.getByText('353,000')).toBeInTheDocument()
    })

    it('음수 수익(손실)이 빨간색으로 표시됨 (AC-017)', async () => {
      renderPage()
      await waitForInvestorsLoaded()

      await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
      await waitForInvestorDropdown()
      await fill(screen.getByLabelText('투자자'), '1')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        const negativeProfit = screen.getByText('-5,000')
        expect(negativeProfit.closest('td')).toHaveClass(/negative/)
      })
    })

    it('존재하지 않는 투자자 404 시 에러 메시지 표시 (AC-016)', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const p = String(path)
        if (p === '/investors/1/profits') throw new ApiError(404, 'Request failed: 404')
        throw new Error(`unexpected path: ${String(path)}`)
      })

      renderPage()
      await waitForInvestorsLoaded()

      await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
      await waitForInvestorDropdown()
      await fill(screen.getByLabelText('투자자'), '1')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByText('투자자를 찾을 수 없습니다.')).toBeInTheDocument()
      })
    })

    it('매도 내역이 없을 때 빈 상태 메시지 표시', async () => {
      renderPage()
      await waitForInvestorsLoaded()

      await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
      await waitForInvestorDropdown()
      await fill(screen.getByLabelText('투자자'), '2')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByText('매도 내역이 없습니다.')).toBeInTheDocument()
      })
    })

    it('네트워크 오류 시 에러 메시지 표시', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const p = String(path)
        if (p === '/investors/1/profits') throw new Error('Failed to fetch')
        throw new Error(`unexpected path: ${String(path)}`)
      })

      renderPage()
      await waitForInvestorsLoaded()

      await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
      await waitForInvestorDropdown()
      await fill(screen.getByLabelText('투자자'), '1')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
      })
    })
  })

  describe('로딩 상태', () => {
    it('조회 중 로딩 메시지 표시', async () => {
      let resolveSaleProfit: (value: unknown) => void = () => {}
      vi.mocked(apiFetch).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSaleProfit = resolve
          }),
      )

      renderPage()
      await waitForInvestorsLoaded()

      await fill(screen.getByLabelText('매도건 ID'), '1')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      expect(screen.getByText('수익 정보를 불러오는 중...')).toBeInTheDocument()

      resolveSaleProfit(SALE_PROFIT)

      await waitFor(() => {
        expect(screen.queryByText('수익 정보를 불러오는 중...')).not.toBeInTheDocument()
      })
    })
  })

  describe('접근성', () => {
    it('인풋에 label이 연결됨', async () => {
      renderPage()
      await waitForInvestorsLoaded()

      const saleIdInput = screen.getByLabelText('매도건 ID')
      expect(saleIdInput).toHaveAttribute('id', 'saleId')

      await userEvent.click(screen.getByRole('radio', { name: '투자자별' }))
      await waitForInvestorDropdown()
      const investorSelect = screen.getByLabelText('투자자')
      expect(investorSelect).toHaveAttribute('id', 'investorId')
    })

    it('에러 영역에 aria-live 속성이 있음', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
        const p = String(path)
        if (p === '/sales/999/profit') throw new ApiError(404, 'Request failed: 404')
        throw new Error(`unexpected path: ${String(path)}`)
      })

      renderPage()
      await waitForInvestorsLoaded()

      await fill(screen.getByLabelText('매도건 ID'), '999')
      await userEvent.click(screen.getByRole('button', { name: /조회/ }))

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument()
      })
    })
  })
})
