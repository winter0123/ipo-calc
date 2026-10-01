import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InvestmentHistoryTable from './InvestmentHistoryTable.tsx'
import InvestmentHistoryDetail from './InvestmentHistoryDetail.tsx'
import type { InvestmentHistoryItem } from '@/entities/subscription/types.ts'

function renderTable(items: InvestmentHistoryItem[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <InvestmentHistoryTable items={items} />
    </QueryClientProvider>,
  )
}

const ITEM_WITH_NAMES: InvestmentHistoryItem = {
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
}

const ITEM_WITHOUT_NAMES: InvestmentHistoryItem = {
  id: 2,
  subscribedAt: '2026-09-02T09:00:00Z',
  ipoId: 11,
  ipoName: '삼성바이오로직스',
  ipoFixedPrice: 55000,
  subscriptionQuantity: 50,
  subscriptionStatus: 'ACTIVE',
  allocation: null,
  sales: [],
  remainingQuantity: 20,
  investorName: null,
  securitiesFirmName: null,
}

describe('InvestmentHistoryTable — 투자자 컬럼', () => {
  it('thead에 "투자자" 컬럼 헤더가 표시됨', () => {
    renderTable([ITEM_WITH_NAMES])

    const header = screen.getByRole('columnheader', { name: '투자자' })
    expect(header).toBeInTheDocument()
  })

  it('헤더와 본문의 컬럼 순서가 일치함 (공모주명 다음이 투자자)', () => {
    renderTable([ITEM_WITH_NAMES])

    const headers = screen.getAllByRole('columnheader').map((th) => th.textContent)
    expect(headers).toEqual([
      '구독 ID',
      '구독일',
      '공모주명',
      '투자자',
      '청약수량',
      '상태',
      '잔여수량',
    ])
  })

  it('행에 투자자명이 표시됨', () => {
    renderTable([ITEM_WITH_NAMES])

    const row = screen.getAllByRole('row')[1]
    const cells = within(row)
      .getAllByRole('cell')
      .map((td) => td.textContent)
    // 투자자 컬럼은 공모주명(인덱스 2) 바로 다음
    expect(cells[2]).toBe('카카오뱅크')
    expect(cells[3]).toBe('김철수')
  })

  it('investorName이 null이면 "-"로 표시됨', () => {
    renderTable([ITEM_WITHOUT_NAMES])

    const row = screen.getAllByRole('row')[1]
    const cells = within(row)
      .getAllByRole('cell')
      .map((td) => td.textContent)
    expect(cells[3]).toBe('-')
  })

  it('헤더 셀 수와 데이터 셀 수가 일치함', () => {
    renderTable([ITEM_WITH_NAMES])

    const headerCount = screen.getAllByRole('columnheader').length
    const cellsInRow = within(screen.getAllByRole('row')[1]).getAllByRole('cell').length
    expect(cellsInRow).toBe(headerCount)
  })
})

describe('InvestmentHistoryDetail — 투자자명/증권사명', () => {
  it('투자자명과 증권사명이 상세 필드로 표시됨', () => {
    render(<InvestmentHistoryDetail detail={ITEM_WITH_NAMES} onClose={() => {}} />)

    expect(screen.getByText('투자자명')).toBeInTheDocument()
    expect(screen.getByText('김철수')).toBeInTheDocument()
    expect(screen.getByText('증권사명')).toBeInTheDocument()
    expect(screen.getByText('한국투자증권')).toBeInTheDocument()
  })

  it('investorName/securitiesFirmName이 null이면 "-"로 표시됨', () => {
    render(<InvestmentHistoryDetail detail={ITEM_WITHOUT_NAMES} onClose={() => {}} />)

    const investorValue = screen.getByText('투자자명').nextElementSibling
    const firmValue = screen.getByText('증권사명').nextElementSibling
    expect(investorValue).toHaveTextContent('-')
    expect(firmValue).toHaveTextContent('-')
  })

  it('기존 상세 필드가 함께 유지됨', () => {
    render(<InvestmentHistoryDetail detail={ITEM_WITH_NAMES} onClose={() => {}} />)

    expect(screen.getByText('공모주명')).toBeInTheDocument()
    expect(screen.getByText('카카오뱅크')).toBeInTheDocument()
    expect(screen.getByText('IPO 고정가')).toBeInTheDocument()
  })
})
