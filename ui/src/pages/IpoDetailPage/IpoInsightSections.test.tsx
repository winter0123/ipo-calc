import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoInsightSections from './IpoInsightSections.tsx'
import type { IpoInsight } from '@/entities/ipo/index.ts'

const EMPTY: IpoInsight = {
  syncedAt: '2026-10-13T17:00:00',
  sourceReportName: '[기재정정]투자설명서',
  sourceUrl: null,
  ceoName: null,
  establishedOn: null,
  address: null,
  parValue: null,
  capitalStock: null,
  largestShareholderName: null,
  largestShareholderRatio: null,
  institutionalCompetitionRate: null,
  lockUpCommitmentRatio: null,
  financials: [],
  floatSchedule: [],
  underwriters: [],
}

function renderWith(body: unknown, status = 200) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
  render(
    <QueryClientProvider client={new QueryClient()}>
      <IpoInsightSections ipoId={7} listingDate="2026-10-22" />
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('IpoInsightSections', () => {
  it('수요예측·해제일·기업정보·손익계산서·인수단·출처를 보여 준다', async () => {
    renderWith({
      ...EMPTY,
      sourceUrl: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=1',
      ceoName: '김성포',
      parValue: 100,
      capitalStock: 2308265000,
      largestShareholderName: '김성포',
      largestShareholderRatio: 30.1,
      institutionalCompetitionRate: 1096.56,
      lockUpCommitmentRatio: 30.62,
      financials: [
        { period: '제11기', revenue: 23786846687, operatingProfit: -855758727, netIncome: null },
      ],
      floatSchedule: [{ label: '상장 후 3개월 뒤', shares: 6347310, ratio: 23.33 }],
      underwriters: [{ name: 'KB증권', role: '대표', shares: 4000000 }],
    })

    expect(await screen.findByText('1,096.56:1')).toBeInTheDocument()
    expect(screen.getByText('30.62%')).toBeInTheDocument()
    expect(screen.getByText('김성포 (30.1%)')).toBeInTheDocument()
    expect(screen.getByText('23.1억원')).toBeInTheDocument()
    expect(screen.getByText('100원')).toBeInTheDocument()
    const float = screen.getByRole('region', { name: '유통가능 물량 표' })
    expect(within(float).getByText('2027-01-22')).toBeInTheDocument()
    expect(within(float).getByText('6,347,310주')).toBeInTheDocument()
    const fin = screen.getByRole('region', { name: '손익계산서 표(억원)' })
    expect(within(fin).getByText('237.9')).toBeInTheDocument()
    expect(within(fin).getByText('-8.6').className).toMatch(/negative/)
    expect(within(fin).getByText('-')).toBeInTheDocument()
    expect(
      within(screen.getByRole('region', { name: '인수단 표' })).getByText('4,000,000주'),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/금융감독원 DART \[기재정정\]투자설명서 · 2026-10-13 기준/),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '공시 원문 보기' })).toHaveAttribute(
      'rel',
      'noopener noreferrer',
    )
  })

  it('값이 하나도 없는 구역은 숨긴다', async () => {
    renderWith({ ...EMPTY, ceoName: '홍길동' })

    expect(await screen.findByText('홍길동')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '수요예측' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '손익계산서' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '공시 원문 보기' })).not.toBeInTheDocument()
  })

  it('syncedAt이 null이면 안내만 보인다', async () => {
    renderWith({ ...EMPTY, syncedAt: null })

    expect(await screen.findByText('아직 공시 정보를 가져오지 않았습니다.')).toBeInTheDocument()
  })

  it('조회 실패 시 안내만 보인다', async () => {
    renderWith({ message: 'x' }, 500)

    expect(await screen.findByText(/지금 불러올 수 없습니다/)).toBeInTheDocument()
  })
})
