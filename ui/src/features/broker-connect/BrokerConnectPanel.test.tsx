import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '@/shared/api/client.ts'
import { fill } from '@/test/fill'
import BrokerConnectPanel from './BrokerConnectPanel.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const credential = (connectionStatus: string) => ({
  id: connectionStatus === 'DISCONNECTED' ? null : 1,
  securitiesAccountId: 10,
  apiProvider: connectionStatus === 'DISCONNECTED' ? null : 'KIS',
  connectionStatus,
  accountNumber: '*******9012',
  lastSyncedAt: null,
})

function setup(status: string) {
  vi.mocked(apiFetch).mockImplementation(async (_path: unknown, init?: RequestInit) => {
    if (init?.method === 'POST') return credential('CONNECTED')
    return credential(status)
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
    setup('DISCONNECTED')

    expect(await screen.findByText(/조회 전용으로만 사용됩니다/)).toBeInTheDocument()
    expect(screen.getByLabelText('앱키')).toHaveAttribute('type', 'password')
    expect(screen.getByLabelText('앱시크릿')).toHaveAttribute('autocomplete', 'off')
    expect(screen.getByText('연결 안 됨')).toBeInTheDocument()
  })

  it('빈 값으로 제출하면 검증 오류를 보이고 요청하지 않는다', async () => {
    setup('DISCONNECTED')
    await userEvent.click(await screen.findByRole('button', { name: '연결' }))

    expect(await screen.findByText('앱키를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('앱시크릿을 입력해 주세요.')).toBeInTheDocument()
    expect(vi.mocked(apiFetch).mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('키를 입력해 제출하면 KIS로 등록 요청을 보낸다', async () => {
    setup('DISCONNECTED')
    await fill(await screen.findByLabelText('앱키'), 'dummy-key')
    await fill(screen.getByLabelText('앱시크릿'), 'dummy-secret')
    await userEvent.click(screen.getByRole('button', { name: '연결' }))

    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith('/securities-accounts/10/api-credential', {
        method: 'POST',
        body: JSON.stringify({
          apiProvider: 'KIS',
          appKey: 'dummy-key',
          appSecret: 'dummy-secret',
        }),
      }),
    )
  })

  it('연결된 계좌는 입력 폼 대신 연결 해제 버튼을 보여준다', async () => {
    setup('CONNECTED')

    expect(await screen.findByRole('button', { name: '연결 해제' })).toBeInTheDocument()
    expect(screen.queryByLabelText('앱키')).not.toBeInTheDocument()
    expect(screen.getByText(/\*{7}9012/)).toBeInTheDocument()
  })

  it('만료 상태도 안내하고 해제할 수 있다', async () => {
    setup('EXPIRED')

    expect(await screen.findByText('인증 만료')).toBeInTheDocument()
  })
})
