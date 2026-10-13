import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getInvestorProfits } from '@/entities/profit/index.ts'
import { useAllInvestorProfits } from './useAllInvestorProfits.ts'

vi.mock('@/entities/profit/index.ts', async (orig) => ({
  ...(await orig<object>()),
  getInvestorProfits: vi.fn(),
}))

const sale = (saleId: number, saleDate: string) => ({ saleId, saleDate })
const investors = [
  { id: 1, displayName: '가' },
  { id: 2, displayName: '나' },
]

function run(period?: { from: string; to: string }, list = investors) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return renderHook(() => useAllInvestorProfits(list, period), { wrapper })
}

describe('useAllInvestorProfits', () => {
  beforeEach(() => vi.mocked(getInvestorProfits).mockReset())

  it('투자자별 매도를 합쳐 매도일 최신순으로 정렬하고 투자자 이름을 붙인다', async () => {
    vi.mocked(getInvestorProfits).mockImplementation(async (id) => {
      const sales =
        id === 1 ? [sale(1, '2026-01-05'), sale(2, '2026-03-01')] : [sale(3, '2026-02-01')]
      return { sales } as never
    })
    const { result } = run({ from: '', to: '' })
    await waitFor(() => expect(result.current.rows).toHaveLength(3))
    expect(result.current.rows.map((r) => [r.saleId, r.investorName])).toEqual([
      [2, '가'],
      [3, '나'],
      [1, '가'],
    ])
    expect(result.current.error).toBeNull()
  })

  it.each([
    ['한쪽만 비어 있음(from)', { from: '2026-01-01', to: '' }],
    ['한쪽만 비어 있음(to)', { from: '', to: '2026-01-01' }],
    ['시작일이 종료일보다 뒤', { from: '2026-02-01', to: '2026-01-01' }],
  ])('조회하지 않는 기간(%s)이면 서버를 부르지 않는다', async (_n, period) => {
    const { result } = run(period)
    await new Promise((r) => setTimeout(r, 20))
    expect(getInvestorProfits).not.toHaveBeenCalled()
    expect(result.current.rows).toEqual([])
  })

  it('시작일과 종료일이 같은 하루 기간은 조회한다', async () => {
    vi.mocked(getInvestorProfits).mockResolvedValue({ sales: [] } as never)
    run({ from: '2026-01-01', to: '2026-01-01' }, [investors[0]])
    await waitFor(() => expect(getInvestorProfits).toHaveBeenCalledOnce())
    expect(vi.mocked(getInvestorProfits).mock.calls[0][1]).toEqual({
      from: '2026-01-01',
      to: '2026-01-01',
    })
  })

  it('한 투자자 조회가 실패하면 error를 노출하고 나머지 행은 유지한다', async () => {
    const boom = new Error('boom')
    vi.mocked(getInvestorProfits).mockImplementation(async (id) => {
      if (id === 1) throw boom
      return { sales: [sale(3, '2026-02-01')] } as never
    })
    const { result } = run({ from: '', to: '' })
    await waitFor(() => expect(result.current.error).toBe(boom))
    expect(result.current.rows.map((r) => r.saleId)).toEqual([3])
  })
})
