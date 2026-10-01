import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import { fill } from '@/test/fill'
import BrokerSyncPanel from './BrokerSyncPanel.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const item = (over: Record<string, unknown>) => ({
  tradeDate: '2026-09-30',
  stockCode: '0123A4',
  quantity: 10,
  amount: 100000,
  price: 10000,
  fee: 150,
  tax: 230,
  allocationId: 30,
  reason: null,
  ...over,
})

const PREVIEW = {
  matched: [item({})],
  unmatched: [
    item({ stockCode: '999999', allocationId: null, reason: 'NO_ALLOCATION_FOR_STOCK' }),
    item({ stockCode: '005930', reason: 'ALREADY_REGISTERED' }),
  ],
  allocationsWithoutStockCode: [77],
}

const isPreview = (path: unknown) => String(path).endsWith('/broker-sync/preview')
const confirmCalls = () =>
  vi.mocked(apiFetch).mock.calls.filter(([path]) => String(path).endsWith('/broker-sync/confirm'))

function setup(preview: unknown = PREVIEW) {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    if (isPreview(path)) {
      if (preview instanceof Error) throw preview
      return preview
    }
    return undefined
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <BrokerSyncPanel securitiesAccountId={10} />
    </QueryClientProvider>,
  )
}

const clickQuery = () =>
  userEvent.click(screen.getByRole('button', { name: '증권사에서 매도 내역 조회' }))

describe('BrokerSyncPanel', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('버튼을 누르기 전에는 증권사를 조회하지 않는다', () => {
    setup()

    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('조회하면 매칭 성공 건과 실패 사유를 보여준다', async () => {
    setup()
    await clickQuery()

    expect(await screen.findByText('0123A4')).toBeInTheDocument()
    expect(screen.getByText(/이 종목의 배정 기록이 없습니다/)).toBeInTheDocument()
    expect(screen.getByText(/이미 같은 내용으로 등록돼 있습니다/)).toBeInTheDocument()
    expect(screen.getByText(/종목코드가 없는 IPO의 배정 1건/)).toBeInTheDocument()
    expect(confirmCalls()).toHaveLength(0)
  })

  it('고친 값으로 확정하면 그 값으로 저장 요청을 보낸다', async () => {
    setup()
    await clickQuery()
    await fill(await screen.findByLabelText('2026-09-30 0123A4 수수료'), '200')
    await userEvent.click(screen.getByRole('button', { name: '선택한 1건 매도로 등록' }))

    await waitFor(() => expect(confirmCalls()).toHaveLength(1))
    const [path, init] = confirmCalls()[0]
    expect(path).toBe('/securities-accounts/10/broker-sync/confirm')
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      sales: [
        {
          allocationId: 30,
          quantity: 10,
          price: 10000,
          amount: 100000,
          fee: 200,
          tax: 230,
          saleDate: '2026-09-30T00:00:00',
        },
      ],
    })
    expect(await screen.findByText('1건을 매도로 등록했습니다.')).toBeInTheDocument()
  })

  it('선택을 모두 해제하거나 숫자가 아니면 확정할 수 없다', async () => {
    setup()
    await clickQuery()
    const confirmButton = await screen.findByRole('button', { name: /매도로 등록/ })

    await fill(screen.getByLabelText('2026-09-30 0123A4 단가'), 'abc')
    expect(confirmButton).toBeDisabled()
    expect(screen.getByRole('alert')).toHaveTextContent('0 이상의 정수')

    await fill(screen.getByLabelText('2026-09-30 0123A4 단가'), '10000')
    await userEvent.click(screen.getByLabelText('2026-09-30 0123A4 선택'))
    expect(screen.getByRole('button', { name: /선택한 0건/ })).toBeDisabled()
  })

  it('연결이 없으면(404) 연결 안내를 보여준다', async () => {
    setup(new ApiError(404, 'Not Found'))
    await clickQuery()

    expect(await screen.findByRole('alert')).toHaveTextContent('먼저 연결해 주세요')
  })

  it('조회된 내역이 없으면 빈 상태를 보여준다', async () => {
    setup({ matched: [], unmatched: [], allocationsWithoutStockCode: [] })
    await clickQuery()

    expect(await screen.findByText('조회된 매도 내역이 없습니다.')).toBeInTheDocument()
  })
})
