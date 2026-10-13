import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import { fill } from '@/test/fill'
import BrokerConnectPanel from './BrokerConnectPanel.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const toDate = (offsetDays: number) => {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return d.toLocaleDateString('en-CA')
}
const TODAY = toDate(0)

interface Over {
  connectionStatus?: string
  supportedProvider?: string | null
  keyIssuedOn?: string | null
  keyExpiresOn?: string | null
}

const credential = ({
  connectionStatus = 'DISCONNECTED',
  supportedProvider = 'KIS',
  keyIssuedOn = null,
  keyExpiresOn = null,
}: Over) => ({
  id: connectionStatus === 'DISCONNECTED' ? null : 1,
  securitiesAccountId: 10,
  apiProvider: connectionStatus === 'DISCONNECTED' ? null : 'KIS',
  connectionStatus,
  accountNumber: '*******9012',
  lastSyncedAt: null,
  supportedProvider,
  keyIssuedOn,
  keyExpiresOn,
})

const writes = () =>
  vi.mocked(apiFetch).mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')

function setup(over: Over = {}) {
  vi.mocked(apiFetch).mockImplementation(async (_path: unknown, init?: RequestInit) => {
    if (init?.method === 'POST' || init?.method === 'PUT') {
      return credential({ ...over, connectionStatus: 'CONNECTED' })
    }
    return credential(over)
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <BrokerConnectPanel securitiesAccountId={10} />
    </QueryClientProvider>,
  )
}

describe('BrokerConnectPanel', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('연결 전에는 조회 전용 안내와 마스킹된 키 입력칸을 보여준다', async () => {
    setup()

    expect(await screen.findByText(/조회 전용으로만 사용됩니다/)).toBeInTheDocument()
    expect(screen.getByLabelText('앱키')).toHaveAttribute('type', 'password')
    expect(screen.getByLabelText('앱시크릿')).toHaveAttribute('autocomplete', 'off')
    expect(screen.getByLabelText(/키 발급일/)).toHaveValue(TODAY)
    expect(screen.getByText('연결 안 됨')).toBeInTheDocument()
  })

  it('키움증권은 IP 등록 안내를 보이고 KIS 전용 사용기간·발급일 입력은 숨긴다', async () => {
    setup({ supportedProvider: 'KIWOOM' })
    expect(
      await screen.findByText('키움 REST API 사용 신청과 서버 IP 등록이 필요합니다.'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/신청일부터 1년/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/키 발급일/)).not.toBeInTheDocument()
  })

  it('빈 값으로 제출하면 검증 오류를 보이고 요청하지 않는다', async () => {
    setup()
    await userEvent.click(await screen.findByRole('button', { name: '연결' }))

    expect(await screen.findByText('앱키를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('앱시크릿을 입력해 주세요.')).toBeInTheDocument()
    expect(writes()).toHaveLength(0)
  })

  it('키와 발급일을 입력해 제출하면 증권사가 지원하는 어댑터로 등록 요청을 보낸다', async () => {
    setup()
    await fill(await screen.findByLabelText('앱키'), 'dummy-key')
    await fill(screen.getByLabelText('앱시크릿'), 'dummy-secret')
    await fill(screen.getByLabelText(/키 발급일/), toDate(-30))
    await userEvent.click(screen.getByRole('button', { name: '연결' }))

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith('/securities-accounts/10/api-credential', {
        method: 'POST',
        body: JSON.stringify({
          appKey: 'dummy-key',
          appSecret: 'dummy-secret',
          keyIssuedOn: toDate(-30),
          apiProvider: 'KIS',
        }),
      }),
    )
  })

  it('등록이 실패해도 앱시크릿 입력값은 비우고 앱키는 유지한다', async () => {
    setup()
    vi.mocked(apiFetch).mockImplementation(async (_path: unknown, init?: RequestInit) => {
      if (init?.method === 'POST') throw new ApiError(400, 'x', '증권사 인증에 실패했습니다.')
      return credential({})
    })
    await fill(await screen.findByLabelText('앱키'), 'dummy-key')
    await fill(screen.getByLabelText('앱시크릿'), 'dummy-secret')
    await userEvent.click(screen.getByRole('button', { name: '연결' }))

    expect(await screen.findByText('증권사 인증에 실패했습니다.')).toBeInTheDocument()
    expect(screen.getByLabelText('앱시크릿')).toHaveValue('')
    expect(screen.getByLabelText('앱키')).toHaveValue('dummy-key')
  })

  it('증권사가 API 연동을 지원하지 않으면 입력 폼 대신 안내를 보여준다', async () => {
    setup({ supportedProvider: null })

    expect(await screen.findByText(/아직 API 연동을 지원하지 않습니다/)).toBeInTheDocument()
    expect(screen.queryByLabelText('앱키')).not.toBeInTheDocument()
  })

  it('연결된 계좌는 입력 폼 대신 키 교체·연결 해제 버튼과 만료일을 보여준다', async () => {
    setup({
      connectionStatus: 'CONNECTED',
      keyIssuedOn: toDate(-10),
      keyExpiresOn: toDate(355),
    })

    expect(await screen.findByRole('button', { name: '연결 해제' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '키 교체' })).toBeInTheDocument()
    expect(screen.queryByLabelText('앱키')).not.toBeInTheDocument()
    expect(screen.getByText(/\*{7}9012/)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(`만료일 ${toDate(355)}`))).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('만료가 30일 이내면 갱신 안내를 알린다', async () => {
    setup({ connectionStatus: 'CONNECTED', keyExpiresOn: toDate(12) })

    expect(await screen.findByRole('alert')).toHaveTextContent('12일 남음')
  })

  it('사용기간이 지났으면 만료 상태와 교체 안내를 보여준다', async () => {
    setup({ connectionStatus: 'EXPIRED', keyExpiresOn: toDate(-3) })

    expect(await screen.findByText('사용기간 만료')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('끝났습니다')
  })

  it('키 교체 폼으로 새 키를 PUT하고 폼을 닫는다', async () => {
    setup({ connectionStatus: 'CONNECTED', keyExpiresOn: toDate(5) })
    await userEvent.click(await screen.findByRole('button', { name: '키 교체' }))
    await fill(screen.getByLabelText('앱키'), 'new-key')
    await fill(screen.getByLabelText('앱시크릿'), 'new-secret')
    await userEvent.click(screen.getByRole('button', { name: '새 키로 교체' }))

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith('/securities-accounts/10/api-credential', {
        method: 'PUT',
        body: JSON.stringify({ appKey: 'new-key', appSecret: 'new-secret', keyIssuedOn: TODAY }),
      }),
    )
    await waitFor(() => expect(screen.queryByLabelText('앱키')).not.toBeInTheDocument())
  })

  it('연결 해제는 확인 후에만 DELETE를 보낸다', async () => {
    setup({ connectionStatus: 'CONNECTED', keyExpiresOn: toDate(300) })
    await userEvent.click(await screen.findByRole('button', { name: '연결 해제' }))
    expect(writes()).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: '해제' }))

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith('/securities-accounts/10/api-credential', {
        method: 'DELETE',
      }),
    )
  })
})
