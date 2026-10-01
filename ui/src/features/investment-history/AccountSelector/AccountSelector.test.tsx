import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import AccountSelector from './AccountSelector.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import type { InvestorResponse } from '@/entities/investor/types.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/types.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

const MOCK_INVESTORS: InvestorResponse[] = [
  {
    id: 1,
    displayName: '김철수',
    accountId: 100,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    displayName: '이영희',
    accountId: 200,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const MOCK_SECURITIES_ACCOUNTS: SecuritiesAccount[] = [
  {
    id: 10,
    investorId: 1,
    securitiesFirmId: 1,
    securitiesFirmName: '한국투자증권',
    accountNumber: '123-456-789',
    accountName: '주계좌',
    isDefault: true,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 11,
    investorId: 1,
    securitiesFirmId: 2,
    securitiesFirmName: '미래에셋증권',
    accountNumber: '987-654-321',
    accountName: '부계좌',
    isDefault: false,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const MOCK_SECURITIES_ACCOUNTS_INVESTOR_2: SecuritiesAccount[] = [
  {
    id: 20,
    investorId: 2,
    securitiesFirmId: 3,
    securitiesFirmName: '삼성증권',
    accountNumber: '111-222-333',
    accountName: '주계좌',
    isDefault: true,
    status: 'ACTIVE',
    deletedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

function renderComponent(
  props: {
    investorId?: string
    securitiesAccountId?: string
    onInvestorChange?: (investorId: string) => void
    onSecuritiesAccountChange?: (securitiesAccountId: string) => void
  } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AccountSelector
          investorId={props.investorId ?? ''}
          securitiesAccountId={props.securitiesAccountId ?? ''}
          onInvestorChange={props.onInvestorChange ?? vi.fn()}
          onSecuritiesAccountChange={props.onSecuritiesAccountChange ?? vi.fn()}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function waitForInvestorsLoaded() {
  await waitFor(() => {
    expect(screen.getByLabelText('투자자')).toBeInTheDocument()
  })
  // Wait for the investor "전체" option to appear (data loaded)
  await waitFor(() => {
    const investorSelect = screen.getByLabelText('투자자')
    expect(investorSelect.querySelector('option[value=""]')).toHaveTextContent('전체')
  })
}

function setupInvestorMock(investors: InvestorResponse[] = MOCK_INVESTORS) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return investors
    if (pathStr.startsWith('/securities-accounts')) return []
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

function setupInvestorAndAccountMock(accounts: SecuritiesAccount[] = MOCK_SECURITIES_ACCOUNTS) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    const pathStr = String(path)
    if (pathStr === '/investors') return MOCK_INVESTORS
    if (pathStr.startsWith('/securities-accounts')) {
      // Check if investorId=1 is in the query
      if (pathStr.includes('investorId=1')) return accounts
      if (pathStr.includes('investorId=2')) return MOCK_SECURITIES_ACCOUNTS_INVESTOR_2
      return []
    }
    throw new Error(`unexpected path: ${pathStr}`)
  })
}

describe('AccountSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('AC-1901: 투자자 옵션이 드롭다운에 표시된다 (표시명 + ID)', async () => {
    setupInvestorMock()

    renderComponent()
    await waitForInvestorsLoaded()

    expect(screen.getByLabelText('투자자')).toBeInTheDocument()
    const investorSelect = screen.getByLabelText('투자자')
    expect(investorSelect.querySelector('option[value=""]')).toHaveTextContent('전체')

    await waitFor(() => {
      expect(screen.getByRole('option', { name: '김철수 (ID: 1)' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: '이영희 (ID: 2)' })).toBeInTheDocument()
    })
  })

  it('AC-1902: 투자자 변경 시 onInvestorChange 호출됨', async () => {
    setupInvestorAndAccountMock()

    const onInvestorChange = vi.fn()
    const onSecuritiesAccountChange = vi.fn()

    renderComponent({
      investorId: '',
      securitiesAccountId: '',
      onInvestorChange,
      onSecuritiesAccountChange,
    })

    await waitForInvestorsLoaded()

    await waitFor(() => {
      expect(screen.getByRole('option', { name: '김철수 (ID: 1)' })).toBeInTheDocument()
    })

    // 첫 번째 투자자 선택
    const investorSelect = screen.getByLabelText('투자자')
    fireEvent.change(investorSelect, { target: { value: '1' } })

    await waitFor(() => {
      expect(onInvestorChange).toHaveBeenCalledWith('1')
    })

    // 두 번째 투자자 선택
    fireEvent.change(investorSelect, { target: { value: '2' } })

    await waitFor(() => {
      expect(onInvestorChange).toHaveBeenCalledWith('2')
    })
  })

  it('AC-1903: 투자자만 선택해도 investorId만으로 조회 가능 (증권계좌 미선택)', async () => {
    setupInvestorAndAccountMock()

    const onInvestorChange = vi.fn()
    const onSecuritiesAccountChange = vi.fn()

    renderComponent({
      investorId: '',
      securitiesAccountId: '',
      onInvestorChange,
      onSecuritiesAccountChange,
    })

    await waitForInvestorsLoaded()

    await waitFor(() => {
      expect(screen.getByRole('option', { name: '김철수 (ID: 1)' })).toBeInTheDocument()
    })

    const investorSelect = screen.getByLabelText('투자자')
    fireEvent.change(investorSelect, { target: { value: '1' } })

    await waitFor(() => {
      expect(onInvestorChange).toHaveBeenCalledWith('1')
    })

    // 증권계좌 드롭다운이 존재해야 함
    const accountSelect = screen.getByLabelText('증권계좌')
    expect(accountSelect).toBeInTheDocument()
  })

  it('AC-1904: 등록된 투자자가 0명일 때 빈 상태 안내 표시', async () => {
    setupInvestorMock([])

    renderComponent()

    await waitForInvestorsLoaded()

    const investorSelect = screen.getByLabelText('투자자')
    expect(investorSelect).toHaveValue('')
    // 옵션은 "전체"만 있어야 함
    const options = investorSelect.querySelectorAll('option')
    expect(options).toHaveLength(1)
    expect(options[0]).toHaveTextContent('전체')

    // 빈 상태 힌트 표시 확인
    expect(screen.getByText('등록된 투자자가 없습니다')).toBeInTheDocument()
  })

  it('투자자 선택 전에는 증권계좌 드롭다운이 비활성화됨', async () => {
    setupInvestorMock()

    renderComponent()

    await waitForInvestorsLoaded()

    const accountSelect = screen.getByLabelText('증권계좌')
    expect(accountSelect).toBeDisabled()
    expect(screen.getByText('투자자를 먼저 선택해 주세요')).toBeInTheDocument()
  })

  it('접근성: 모든 select에 label 연결됨', async () => {
    setupInvestorMock()

    renderComponent()

    await waitForInvestorsLoaded()

    // 각 select에 label이 연결되어 있는지 확인 (getByLabelText로 접근 가능)
    expect(screen.getByLabelText('투자자')).toBeInTheDocument()
    expect(screen.getByLabelText('증권계좌')).toBeInTheDocument()
  })

  it('증권계좌 표시명에 증권사명과 계좌번호가 포함됨 (투자자 사전 선택)', async () => {
    // 투자자가 이미 선택된 상태에서 렌더링
    setupInvestorAndAccountMock()

    renderComponent({ investorId: '1', securitiesAccountId: '' })

    await waitForInvestorsLoaded()

    // 증권계좌 옵션이 로드되어 있어야 함
    await waitFor(() => {
      expect(screen.getByRole('option', { name: '한국투자증권 - 123-456-789' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: '미래에셋증권 - 987-654-321' })).toBeInTheDocument()
    })
  })
})
