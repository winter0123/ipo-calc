import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import InvestorRegisterPage from './InvestorRegisterPage.tsx'
import { apiFetch } from '@/shared/api/client.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const account = (id: number, investorId: number) => ({
  id,
  investorId,
  securitiesFirmId: 1,
  securitiesFirmName: '증권사',
  accountNumber: '1234',
  accountName: null,
  isDefault: true,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '2026-10-22T00:00:00',
  updatedAt: '2026-10-22T00:00:00',
})

describe('InvestorRegisterPage', () => {
  it('투자자 수와 각 투자자의 이름·관계·증권계좌 수를 표시한다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
      if (path === '/investors') {
        return [
          {
            id: 1,
            displayName: '조이',
            relationship: 'SELF',
            accountId: 1,
            status: 'ACTIVE',
            deletedAt: null,
            createdAt: '2026-10-22T00:00:00',
            updatedAt: '2026-10-22T00:00:00',
          },
          {
            id: 2,
            displayName: '가족',
            relationship: 'SPOUSE',
            accountId: 1,
            status: 'ACTIVE',
            deletedAt: null,
            createdAt: '2026-10-22T00:00:00',
            updatedAt: '2026-10-22T00:00:00',
          },
        ]
      }
      if (typeof path === 'string' && path.includes('investorId=1')) {
        return [account(10, 1), account(11, 1)]
      }
      if (typeof path === 'string' && path.includes('investorId=2')) return []
      throw new Error(`Unexpected API path: ${String(path)}`)
    })

    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter>
          <InvestorRegisterPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('현재 2명의 투자자가 등록되어 있습니다.')).toBeInTheDocument()
    expect(screen.getByText('조이')).toBeInTheDocument()
    expect(
      within(screen.getByRole('list', { name: '투자자별 증권계좌 현황' })).getByText('본인'),
    ).toBeInTheDocument()
    expect(await screen.findByText('증권계좌 2개')).toBeInTheDocument()
    const investorList = within(screen.getByRole('list', { name: '투자자별 증권계좌 현황' }))
    expect(investorList.getByText('가족')).toBeInTheDocument()
    expect(investorList.getByText('연인')).toBeInTheDocument()
    expect(await screen.findByText('증권계좌 0개')).toBeInTheDocument()
  })
})
