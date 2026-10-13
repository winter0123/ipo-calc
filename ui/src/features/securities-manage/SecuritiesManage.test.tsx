import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fill } from '@/test/fill'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import SecuritiesManage from './SecuritiesManage.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const TS = '2026-01-01T00:00:00'
const firm = (id: number, name: string) => ({
  id,
  name,
  externalCode: null,
  businessRegistrationNumber: null,
  status: 'ACTIVE',
  createdAt: TS,
  updatedAt: TS,
})
const account = (id: number, investorId: number, firmId: number, over = {}) => ({
  id,
  investorId,
  securitiesFirmId: firmId,
  securitiesFirmName: null,
  accountNumber: '1234-****-90',
  accountName: null,
  isDefault: false,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
  ...over,
})

const FIRMS = [firm(1, '가증권'), firm(2, '나증권'), firm(3, '다증권')]
let client: QueryClient
let accounts: Record<number, unknown[]>
let overrides: Record<string, () => unknown>

beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  accounts = {
    7: [account(10, 7, 3, { accountName: '주력', isDefault: true }), account(11, 7, 3)],
    8: [account(20, 8, 1)],
  }
  overrides = {}
  vi.mocked(apiFetch).mockReset()
  vi.mocked(apiFetch).mockImplementation(async (path: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${path}`
    if (overrides[key]) return overrides[key]()
    if (key === 'GET /securities-firms') return FIRMS
    const list = /^GET \/securities-accounts\?investorId=(\d+)$/.exec(key)
    if (list) return accounts[Number(list[1])] ?? []
    if (key.endsWith('/api-credential')) {
      return {
        id: null,
        securitiesAccountId: 1,
        apiProvider: null,
        connectionStatus: 'DISCONNECTED',
        accountNumber: null,
        lastSyncedAt: null,
        supportedProvider: null,
        keyIssuedOn: null,
        keyExpiresOn: null,
      }
    }
    if (key === 'GET /securities-accounts/10/account-number')
      return { accountNumber: '1234-5678-90' }
    return undefined
  })
})

function ui(investorId: number) {
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SecuritiesManage investorId={investorId} />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const calls = (method: string, path: string) =>
  vi.mocked(apiFetch).mock.calls.filter(([p, i]) => p === path && (i?.method ?? 'GET') === method)

describe('SecuritiesManage', () => {
  it('등록된 증권사를 위로 올리고 마스킹된 번호·기본 계좌를 보여 준다', async () => {
    render(ui(7))
    const items = await screen.findAllByRole('heading', { level: 3 })
    expect(items.map((h) => h.textContent)).toEqual(['다증권', '가증권', '나증권'])
    expect(screen.getByRole('heading', { level: 2, name: '등록한 증권사' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: '다른 증권사' })).toBeInTheDocument()
    expect(screen.getAllByText('1234-****-90')).toHaveLength(2)
    expect(screen.getByText('등록됨')).toBeInTheDocument()
    expect(screen.getByText('기본 계좌')).toBeInTheDocument()
  })

  it('투자자가 바뀌면 그 투자자의 계좌로 바뀐다', async () => {
    const { rerender } = render(ui(7))
    await screen.findByText('주력')
    rerender(ui(8))
    const heads = await screen.findAllByRole('heading', { level: 3 })
    expect(heads[0]).toHaveTextContent('가증권')
    expect(screen.queryByText('주력')).not.toBeInTheDocument()
  })

  it('보기로 전체 번호를 그 행에만 보이고 숨기기로 되돌린다', async () => {
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '주력 계좌번호 보기' }))
    expect(await screen.findByText('1234-5678-90')).toBeInTheDocument()
    expect(screen.getAllByText('1234-****-90')).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: '숨기기' }))
    expect(screen.queryByText('1234-5678-90')).not.toBeInTheDocument()
  })

  it('계좌 등록은 그 증권사로 등록 요청을 보낸다', async () => {
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '나증권 계좌 등록' }))
    const form = screen.getByRole('form', { name: '나증권 계좌 등록' })
    await fill(within(form).getByLabelText('계좌번호'), '1234567890')
    expect(within(form).getByLabelText('계좌번호')).toHaveValue('1234-5678-90')
    await userEvent.click(within(form).getByRole('button', { name: '등록' }))
    await waitFor(() => expect(calls('POST', '/securities-accounts')).toHaveLength(1))
    expect(JSON.parse(calls('POST', '/securities-accounts')[0][1]!.body as string)).toMatchObject({
      investorId: 7,
      securitiesFirmId: 2,
      accountNumber: '1234-5678-90',
    })
  })

  it('수정은 계좌명을 PATCH로 저장한다', async () => {
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '주력 수정' }))
    await fill(screen.getByLabelText('계좌명 (선택)'), '월급 계좌')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await waitFor(() => expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(1))
    expect(calls('PATCH', '/securities-accounts/10')[0][1]!.body).toBe(
      JSON.stringify({ accountName: '월급 계좌' }),
    )
  })

  it('삭제는 확인 후에만 요청하고 취소하면 요청하지 않는다', async () => {
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '주력 삭제' }))
    expect(screen.getByText('이 계좌로 기록한 청약은 그대로 남습니다.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(calls('DELETE', '/securities-accounts/10')).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: '주력 삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )
    await waitFor(() => expect(calls('DELETE', '/securities-accounts/10')).toHaveLength(1))
  })

  it('기본으로를 누르면 기본 지정을 요청하고 기본 계좌에는 버튼이 없다', async () => {
    render(ui(7))
    await screen.findByText('주력')
    expect(screen.queryByRole('button', { name: '주력 기본으로 지정' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '계좌 기본으로 지정' }))
    await waitFor(() =>
      expect(calls('PATCH', '/securities-accounts/11/set-default')).toHaveLength(1),
    )
  })

  it('API 오류 메시지를 그대로 보여 준다', async () => {
    overrides['DELETE /securities-accounts/10'] = () => {
      throw new ApiError(400, 'Bad', '삭제할 수 없는 계좌입니다.')
    }
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '주력 삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('삭제할 수 없는 계좌입니다.')
  })
})
