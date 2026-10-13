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
  allocationHasActiveSale: false,
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

const isPreview = (path: unknown) => String(path).includes('/broker-sync/preview')
const previewCalls = () =>
  vi
    .mocked(apiFetch)
    .mock.calls.filter(([path]) => isPreview(path))
    .map(([path]) => String(path))
const day = (offset: number) => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d.toLocaleDateString('en-CA')
}
const confirmCalls = () =>
  vi.mocked(apiFetch).mock.calls.filter(([path]) => String(path).endsWith('/broker-sync/confirm'))

function setup(preview: unknown = PREVIEW, provider = 'KIS') {
  vi.mocked(apiFetch).mockImplementation(async (path: unknown) => {
    if (String(path).endsWith('/api-credential')) {
      return {
        id: 1,
        securitiesAccountId: 10,
        apiProvider: provider,
        connectionStatus: 'CONNECTED',
        accountNumber: null,
        lastSyncedAt: null,
        supportedProvider: provider,
        keyIssuedOn: null,
        keyExpiresOn: null,
      }
    }
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

    expect(previewCalls()).toHaveLength(0)
  })

  it('기본 조회 기간은 오늘까지 최근 1년이고 기간 파라미터로 요청한다', async () => {
    setup()
    expect(screen.getByLabelText('조회 시작일')).toHaveValue(day(-365))
    expect(screen.getByLabelText('조회 종료일')).toHaveValue(day(0))
    await clickQuery()

    await screen.findByText('0123A4')
    expect(previewCalls()).toEqual([
      `/securities-accounts/10/broker-sync/preview?from=${day(-365)}&to=${day(0)}`,
    ])
    expect(screen.getByText(`조회 기간: ${day(-365)} ~ ${day(0)}`)).toBeInTheDocument()
  })

  it('기간을 입력만 해서는 조회하지 않고 조회를 눌러야 새 기간으로 요청한다', async () => {
    setup()
    await fill(screen.getByLabelText('조회 시작일'), day(-800))
    await fill(screen.getByLabelText('조회 종료일'), day(-30))
    expect(previewCalls()).toHaveLength(0)

    await clickQuery()

    await screen.findByText('0123A4')
    expect(previewCalls()).toEqual([
      `/securities-accounts/10/broker-sync/preview?from=${day(-800)}&to=${day(-30)}`,
    ])
  })

  it('기간이 잘못되면 안내하고 조회 버튼을 막는다', async () => {
    setup()
    await fill(screen.getByLabelText('조회 시작일'), day(-4000))

    expect(screen.getByRole('alert')).toHaveTextContent('최대 10년')
    expect(screen.getByRole('button', { name: '증권사에서 매도 내역 조회' })).toBeDisabled()

    await fill(screen.getByLabelText('조회 시작일'), day(-10))
    await fill(screen.getByLabelText('조회 종료일'), day(-20))
    expect(screen.getByRole('alert')).toHaveTextContent('시작일은 종료일보다')
  })

  it('요청이 너무 잦으면(429) 서버 안내를 그대로 보여준다', async () => {
    setup(
      new ApiError(429, 'Too Many Requests', '요청이 너무 잦습니다. 8초 뒤에 다시 시도해 주세요.'),
    )
    await clickQuery()

    expect(await screen.findByRole('alert')).toHaveTextContent('8초 뒤에 다시 시도')
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
    // 증권사 수수료 150 + 세금 230 = 380이 "세금 및 기타"의 처음 값이다
    expect(await screen.findByLabelText('2026-09-30 0123A4 세금 및 기타')).toHaveValue('380')
    expect(screen.getByLabelText('2026-09-30 0123A4 공모주수수료')).toHaveValue('2000')
    await fill(screen.getByLabelText('2026-09-30 0123A4 세금 및 기타'), '400')
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
          fee: 2000,
          tax: 400,
          saleDate: '2026-09-30T00:00:00',
        },
      ],
    })
    expect(await screen.findByText('1건을 매도로 등록했습니다.')).toBeInTheDocument()
  })

  it('확정이 409인데 서버 메시지가 없으면 한국어 일반 문구를 보여주고, 다시 조회하면 지운다', async () => {
    setup()
    const base = vi.mocked(apiFetch).getMockImplementation()!
    vi.mocked(apiFetch).mockImplementation(async (path: unknown, init?: RequestInit) => {
      if (String(path).endsWith('/broker-sync/confirm'))
        throw new ApiError(409, 'Request failed: 409')
      return base(path as string, init)
    })
    await clickQuery()
    await userEvent.click(await screen.findByRole('button', { name: '선택한 1건 매도로 등록' }))
    const alert = await screen.findByText(/매도 내역을 저장하지 못했습니다/)
    expect(alert).not.toHaveTextContent('Request failed')

    await clickQuery()
    await waitFor(() =>
      expect(screen.queryByText(/매도 내역을 저장하지 못했습니다/)).not.toBeInTheDocument(),
    )
  })

  it('같은 배정의 매도가 여러 건이면 공모주수수료 기본값은 첫 건에만 2000원이다', async () => {
    setup({
      matched: [
        item({ tradeDate: '2026-09-29' }),
        item({ tradeDate: '2026-09-30', quantity: 5, amount: 50000 }),
        item({ tradeDate: '2026-09-30', stockCode: 'ZZZ999', allocationId: 31 }),
      ],
      unmatched: [],
      allocationsWithoutStockCode: [],
    })
    await clickQuery()

    expect(await screen.findByLabelText('2026-09-29 0123A4 공모주수수료')).toHaveValue('2000')
    expect(screen.getByLabelText('2026-09-30 0123A4 공모주수수료')).toHaveValue('0')
    // 다른 배정의 첫 매도는 다시 2000원
    expect(screen.getByLabelText('2026-09-30 ZZZ999 공모주수수료')).toHaveValue('2000')
  })

  it('배정에 활성 매도가 이미 있으면 첫 행도 공모주수수료 0원으로 시작한다', async () => {
    setup({
      matched: [item({ allocationHasActiveSale: true })],
      unmatched: [],
      allocationsWithoutStockCode: [],
    })
    await clickQuery()

    expect(await screen.findByLabelText('2026-09-30 0123A4 공모주수수료')).toHaveValue('0')
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

  it('증권사 인증 실패(424)면 서버가 준 키 갱신 안내를 보여준다', async () => {
    setup(new ApiError(424, 'Failed Dependency', '증권사 인증에 실패했습니다. 키를 갱신해 주세요.'))
    await clickQuery()

    expect(await screen.findByRole('alert')).toHaveTextContent('키를 갱신해 주세요')
  })

  it('조회된 내역이 없으면 빈 상태를 보여준다', async () => {
    setup({ matched: [], unmatched: [], allocationsWithoutStockCode: [] })
    await clickQuery()

    expect(await screen.findByText('조회된 매도 내역이 없습니다.')).toBeInTheDocument()
  })

  it('LS증권은 한 번에 31일까지만 조회하도록 기본 기간과 검증을 바꾼다', async () => {
    setup(PREVIEW, 'LS')

    expect(await screen.findByText(/기본 최근 30일, 최대 31일/)).toBeInTheDocument()
    await fill(screen.getByLabelText('조회 시작일'), day(-40))

    expect(screen.getByRole('alert')).toHaveTextContent(
      'LS증권은 한 번에 31일까지 조회할 수 있습니다.',
    )
  })
})
