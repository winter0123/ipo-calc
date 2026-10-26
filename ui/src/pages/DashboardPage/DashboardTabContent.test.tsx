import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps } from 'react'
import { MemoryRouter } from 'react-router-dom'
import type { InvestorResponse } from '@/entities/investor/index.ts'
import type { DashboardByInvestor, DashboardSummary } from '@/features/dashboard/index.ts'
import DashboardTabContent from './DashboardTabContent.tsx'

const summary: DashboardSummary = {
  totalInvestmentAmount: 10000,
  totalPreTaxProfit: 2000,
  totalAfterTaxProfit: 1500,
  totalProfitRate: 15,
  participationCount: 2,
}

const investor = (id: number): InvestorResponse => ({
  id,
  displayName: `투자자 ${id}`,
  relationship: null,
  accountId: 1,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: '',
  updatedAt: '',
})

const investorSummary: DashboardByInvestor = {
  ...summary,
  investorId: 1,
  investorName: '투자자 1',
}

function renderContent(props: Partial<ComponentProps<typeof DashboardTabContent>> = {}) {
  return render(
    <MemoryRouter>
      <DashboardTabContent
        currentTab="all"
        summary={summary}
        investors={[investor(1), investor(2)]}
        onResetFilters={vi.fn()}
        {...props}
      />
    </MemoryRouter>,
  )
}

describe('DashboardTabContent', () => {
  it('shows total and all investor sections, including investors with no sales', () => {
    renderContent({ investorSummaries: [investorSummary] })

    expect(screen.getByRole('heading', { name: '종합' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '투자자 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '투자자 2' })).toBeInTheDocument()
    expect(screen.getByText('아직 매도 기록이 없습니다.')).toBeInTheDocument()
    expect(screen.getByText('투자자별 (2명)').closest('details')).toHaveAttribute('open')
  })

  it('starts the investor section collapsed for four or more investors', () => {
    renderContent({ investors: [investor(1), investor(2), investor(3), investor(4)] })

    expect(screen.getByText('투자자별 (4명)').closest('details')).not.toHaveAttribute('open')
  })

  it('shows empty state and forwards the reset action when the active tab has no rows', async () => {
    const onResetFilters = vi.fn()
    renderContent({ currentTab: 'securities-firm', securitiesFirmSummaries: [], onResetFilters })

    await userEvent.click(screen.getByRole('button', { name: '전체 필터 초기화' }))
    expect(onResetFilters).toHaveBeenCalledOnce()
  })
})
