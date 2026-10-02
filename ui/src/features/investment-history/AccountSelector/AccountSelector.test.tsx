import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import AccountSelector from './AccountSelector.tsx'
import { apiFetch } from '@/shared/api/client.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'

vi.mock('@/shared/api/client.ts', () => ({
  apiFetch: vi.fn(),
}))

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

function renderComponent(props: { investorId?: string; onChange?: (id: string) => void } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AccountSelector
          investorId={props.investorId ?? ''}
          securitiesAccountId=""
          onSecuritiesAccountChange={props.onChange ?? vi.fn()}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AccountSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      const pathStr = String(path)
      if (pathStr.startsWith('/securities-accounts')) {
        return pathStr.includes('investorId=1') ? MOCK_SECURITIES_ACCOUNTS : []
      }
      throw new Error(`unexpected path: ${pathStr}`)
    })
  })

  it('투자자 선택은 여기서 하지 않는다(상단 전환기가 담당)', () => {
    renderComponent({ investorId: '1' })
    expect(screen.queryByLabelText('투자자')).not.toBeInTheDocument()
    expect(screen.getByLabelText('증권계좌')).toBeInTheDocument()
  })

  it('투자자가 없으면 증권계좌 드롭다운이 비활성화된다', () => {
    renderComponent()
    expect(screen.getByLabelText('증권계좌')).toBeDisabled()
  })

  it('증권계좌 표시명에 증권사명과 계좌번호가 포함된다', async () => {
    renderComponent({ investorId: '1' })
    await waitFor(() => {
      expect(screen.getByRole('option', { name: '한국투자증권 - 123-456-789' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: '미래에셋증권 - 987-654-321' })).toBeInTheDocument()
    })
  })

  it('증권계좌를 고르면 onSecuritiesAccountChange가 호출된다', async () => {
    const onChange = vi.fn()
    renderComponent({ investorId: '1', onChange })
    await screen.findByRole('option', { name: '한국투자증권 - 123-456-789' })
    await userEvent.selectOptions(screen.getByLabelText('증권계좌'), '10')
    expect(onChange).toHaveBeenCalledWith('10')
  })

  it('증권계좌가 없는 투자자에게는 안내가 보인다', async () => {
    renderComponent({ investorId: '2' })
    expect(await screen.findByText('해당 투자자의 증권계좌가 없습니다')).toBeInTheDocument()
  })
})
