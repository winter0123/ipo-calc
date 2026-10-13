import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InvestmentHistoryTable from './InvestmentHistoryTable.tsx'
import InvestmentHistoryDetail from './InvestmentHistoryDetail.tsx'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'

function renderTable(items: InvestmentHistoryItem[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <InvestmentHistoryTable items={items} />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

function renderDetail(detail: InvestmentHistoryItem, onClose: () => void = () => {}) {
  return render(
    <MemoryRouter>
      <InvestmentHistoryDetail detail={detail} onClose={onClose} />
    </MemoryRouter>,
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
    expect(headers).toEqual(['청약일', '공모주명', '투자자', '청약수량', '상태', '잔여수량'])
  })

  it('행에 투자자명이 표시됨', () => {
    renderTable([ITEM_WITH_NAMES])

    const row = screen.getAllByRole('row')[1]
    const cells = within(row)
      .getAllByRole('cell')
      .map((td) => td.textContent)
    // 투자자 컬럼은 공모주명(인덱스 1) 바로 다음
    expect(cells[1]).toBe('카카오뱅크')
    expect(cells[2]).toBe('김철수')
  })

  it('investorName이 null이면 "-"로 표시됨', () => {
    renderTable([ITEM_WITHOUT_NAMES])

    const row = screen.getAllByRole('row')[1]
    const cells = within(row)
      .getAllByRole('cell')
      .map((td) => td.textContent)
    expect(cells[2]).toBe('-')
  })

  it('헤더 셀 수와 데이터 셀 수가 일치함', () => {
    renderTable([ITEM_WITH_NAMES])

    const headerCount = screen.getAllByRole('columnheader').length
    const cellsInRow = within(screen.getAllByRole('row')[1]).getAllByRole('cell').length
    expect(cellsInRow).toBe(headerCount)
  })
})

describe('InvestmentHistoryTable — 모바일 라벨', () => {
  it('각 셀에 데스크톱 헤더와 같은 data-label이 있다', () => {
    renderTable([ITEM_WITH_NAMES])

    const labels = within(screen.getAllByRole('row')[1])
      .getAllByRole('cell')
      .map((td) => td.getAttribute('data-label'))
    expect(labels).toEqual(['청약일', '공모주명', '투자자', '청약수량', '상태', '잔여수량'])
  })
})

describe('InvestmentHistoryDetail — 투자자명/증권사명', () => {
  it('투자자명과 증권사명이 상세 필드로 표시됨', () => {
    renderDetail(ITEM_WITH_NAMES)

    expect(screen.getByText('투자자명')).toBeInTheDocument()
    expect(screen.getByText('김철수')).toBeInTheDocument()
    expect(screen.getByText('증권사명')).toBeInTheDocument()
    expect(screen.getByText('한국투자증권')).toBeInTheDocument()
  })

  it('investorName/securitiesFirmName이 null이면 "-"로 표시됨', () => {
    renderDetail(ITEM_WITHOUT_NAMES)

    const investorValue = screen.getByText('투자자명').nextElementSibling
    const firmValue = screen.getByText('증권사명').nextElementSibling
    expect(investorValue).toHaveTextContent('-')
    expect(firmValue).toHaveTextContent('-')
  })

  it('기존 상세 필드가 함께 유지됨', () => {
    renderDetail(ITEM_WITH_NAMES)

    expect(screen.getByText('공모주명')).toBeInTheDocument()
    expect(screen.getByText('카카오뱅크')).toBeInTheDocument()
    expect(screen.getByText('공모가')).toBeInTheDocument()
  })

  it('내부 ID 항목(청약 ID, IPO ID)이 상세에 표시되지 않음', () => {
    renderDetail(ITEM_WITH_NAMES)

    expect(screen.queryByText('청약 ID')).not.toBeInTheDocument()
    expect(screen.queryByText('IPO ID')).not.toBeInTheDocument()
  })

  it('제목과 닫기 버튼이 있고 닫기 버튼을 누르면 onClose가 호출됨', async () => {
    const onClose = vi.fn()
    renderDetail(ITEM_WITH_NAMES, onClose)

    expect(screen.getByRole('heading', { name: '청약 상세' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '상세 정보 닫기' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})

const ALLOCATED: InvestmentHistoryItem = {
  ...ITEM_WITH_NAMES,
  id: 7,
  allocation: { id: 3, allocatedQuantity: 10, allocationPrice: 39000, allocationStatus: 'ACTIVE' },
  remainingQuantity: 4,
}

describe('InvestmentHistoryDetail — 상태 문구와 다음 행동', () => {
  it('상태를 한글로, 미배정 잔여수량을 "배정 전"으로 표시한다', () => {
    renderDetail(ITEM_WITH_NAMES)

    expect(screen.queryByText('ACTIVE')).not.toBeInTheDocument()
    expect(screen.getByText('진행 중')).toBeInTheDocument()
    expect(screen.getByText('배정 전')).toBeInTheDocument()
  })

  it('미배정이면 배정 입력 링크가 청약 ID로 이어진다', () => {
    renderDetail(ITEM_WITH_NAMES)

    expect(screen.getByRole('link', { name: '배정 입력' })).toHaveAttribute(
      'href',
      '/allocations/register?subscriptionId=1',
    )
    expect(screen.queryByRole('link', { name: '매도 등록' })).not.toBeInTheDocument()
  })

  it('남은 수량이 있으면 매도 등록 링크가 보인다', () => {
    renderDetail(ALLOCATED)

    expect(screen.getByRole('link', { name: '매도 등록' })).toHaveAttribute(
      'href',
      '/sales/register?subscriptionId=7',
    )
    expect(screen.queryByRole('link', { name: '배정 입력' })).not.toBeInTheDocument()
  })

  it('모두 팔았으면 이동 버튼이 없다', () => {
    renderDetail({ ...ALLOCATED, remainingQuantity: 0 })

    expect(screen.queryByRole('link', { name: '매도 등록' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '배정 입력' })).not.toBeInTheDocument()
  })
})
