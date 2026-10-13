import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { SaleProfitRow } from '../useAllInvestorProfits.ts'
import ProfitTable from './ProfitTable.tsx'

const sale = (over: Partial<SaleProfitRow> = {}): SaleProfitRow => ({
  saleId: 1,
  saleDate: '2026-10-12',
  allocationId: 1,
  quantity: 10,
  saleAmount: 100000,
  matchedAcquisitionAmount: 80000,
  saleFee: 500,
  saleTax: 300,
  saleOtherCost: 0,
  preTaxProfit: 19200,
  afterTaxProfit: 19200,
  realizedProfit: 20000,
  listingDate: null,
  ipoName: null,
  investorName: '홍길동',
  ...over,
})

const renderTable = (sales: SaleProfitRow[]) =>
  render(
    <MemoryRouter>
      <ProfitTable sales={sales} />
    </MemoryRouter>,
  )

describe('ProfitTable', () => {
  it('매도가 없으면 빈 안내와 매도 등록 링크만 보인다', () => {
    renderTable([])
    expect(screen.getByText('이 기간에 매도 기록이 없습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '매도 등록' })).toHaveAttribute(
      'href',
      '/sales/register',
    )
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('합계 행과 수익률(최종수익금/취득금액)을 소수 둘째 자리까지 계산한다', () => {
    renderTable([
      sale({ saleId: 1 }),
      sale({
        saleId: 2,
        saleAmount: 50000,
        matchedAcquisitionAmount: 40000,
        saleFee: 100,
        saleTax: 50,
        realizedProfit: 10000,
        afterTaxProfit: 9850,
      }),
    ])
    const summary = screen.getByText('합계').closest('tr')!
    const cells = within(summary)
      .getAllByRole('cell')
      .map((c) => c.textContent)
    expect(cells).toEqual(['합계', '150,000', '120,000', '600', '350', '30,000', '29,050'])
    // 29050 / 120000 = 24.2083...%
    expect(screen.getByText('24.21%')).toBeInTheDocument()
    expect(screen.getByText('29,050원')).toBeInTheDocument()
  })

  it('매도금액이 null이면 행에는 -, 합계에는 0으로 센다', () => {
    renderTable([sale({ saleAmount: null })])
    const row = screen.getByText('홍길동').closest('tr')!
    expect(within(row).getAllByRole('cell')[3]).toHaveTextContent('-')
    const summary = screen.getByText('합계').closest('tr')!
    expect(within(summary).getAllByRole('cell')[1]).toHaveTextContent('0')
  })

  it('취득금액 합이 0이면 수익률은 -', () => {
    renderTable([sale({ matchedAcquisitionAmount: 0 })])
    expect(screen.getByText('수익률').nextElementSibling).toHaveTextContent('-')
  })

  it('손실이면 음수 값이 그대로 표시된다', () => {
    renderTable([sale({ realizedProfit: -5000, afterTaxProfit: -5800 })])
    const summary = screen.getByText('합계').closest('tr')!
    const cells = within(summary).getAllByRole('cell')
    expect(cells[5]).toHaveTextContent('-5,000')
    expect(cells[6]).toHaveTextContent('-5,800')
    expect(screen.getByText('-5,800원')).toBeInTheDocument()
    // -5800 / 80000 = -7.25%
    expect(screen.getByText('-7.25%')).toBeInTheDocument()
  })
})
