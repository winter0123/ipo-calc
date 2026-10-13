import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoInsightSections from './IpoInsightSections.tsx'
import type { IpoInsight } from '@/entities/ipo/index.ts'

const EMPTY: IpoInsight = {
  description: null,
  keywords: [],
  ceoName: null,
  establishedOn: null,
  address: null,
  demandForecastStartDate: null,
  demandForecastEndDate: null,
  institutionalCompetitionRate: null,
  lockUpCommitmentRatio: null,
  protectedShareRatio: null,
  floatShareRatio: null,
  listedShares: null,
  offeringShares: null,
  marketCapAtOfferPrice: null,
  recentSales: null,
  recentNetProfit: null,
  registrationStatementUrl: null,
  brokers: [],
}

function renderWith(body: unknown, status = 200) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
  render(
    <QueryClientProvider client={new QueryClient()}>
      <IpoInsightSections ipoId={7} />
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('IpoInsightSections', () => {
  it('수요예측·증권사별 청약·기업정보·실적을 단위와 함께 보여 준다', async () => {
    renderWith({
      ...EMPTY,
      description: '생체 소재 의료기기',
      keywords: ['의료기기'],
      ceoName: '김성포',
      establishedOn: '2015-10-27',
      demandForecastStartDate: '2026-09-16',
      demandForecastEndDate: '2026-09-22',
      institutionalCompetitionRate: 1096.56,
      lockUpCommitmentRatio: 30.62,
      protectedShareRatio: 76.67,
      floatShareRatio: 23.33,
      recentSales: 23786847000,
      recentNetProfit: 7287888000,
      registrationStatementUrl: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=1',
      brokers: [
        {
          name: '하나증권',
          shares: 213000,
          equalSharesPerPerson: null,
          proportionalCompetitionRate: null,
        },
      ],
    })

    expect(await screen.findByText('1,096.56:1')).toBeInTheDocument()
    expect(screen.getByText('30.62%')).toBeInTheDocument()
    expect(screen.getByText('76.67% / 23.33%')).toBeInTheDocument()
    expect(screen.getByText('2026-09-16 ~ 2026-09-22')).toBeInTheDocument()
    expect(screen.getByText('237.9억원')).toBeInTheDocument()
    expect(screen.getByText('김성포')).toBeInTheDocument()
    const brokers = screen.getByRole('region', { name: '증권사별 청약' })
    expect(within(brokers).getByText('213,000주')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '증권신고서 보기' })).toHaveAttribute(
      'rel',
      'noopener noreferrer',
    )
  })

  it('값이 하나도 없는 구역은 숨긴다', async () => {
    renderWith({ ...EMPTY, ceoName: '홍길동' })

    expect(await screen.findByText('홍길동')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '수요예측·공모' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '증권사별 청약' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '증권신고서 보기' })).not.toBeInTheDocument()
  })

  it('조회 실패 시 안내만 보인다', async () => {
    renderWith({ message: 'x' }, 500)

    expect(await screen.findByText(/지금 불러올 수 없습니다/)).toBeInTheDocument()
  })
})
