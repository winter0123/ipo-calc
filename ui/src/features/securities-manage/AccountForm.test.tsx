import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fill } from '@/test/fill'
import { apiFetch } from '@/shared/api/client.ts'
import AccountForm, { type AccountFormTarget } from './AccountForm.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const TS = '2026-01-01T00:00:00'

const CREATED_ACCOUNT = {
  id: 30,
  investorId: 1,
  securitiesFirmId: 2,
  securitiesFirmName: null,
  accountNumber: null,
  accountName: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
}

const UPDATED_ACCOUNT = {
  id: 10,
  investorId: 1,
  securitiesFirmId: 3,
  securitiesFirmName: null,
  accountNumber: '1234-****-90',
  accountName: null,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
}

function renderForm(target: AccountFormTarget) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AccountForm target={target} onDone={vi.fn()} onCancel={vi.fn()} />
    </QueryClientProvider>,
  )
}

const calls = (method: string, path: string) =>
  vi.mocked(apiFetch).mock.calls.filter(([p, i]) => p === path && (i?.method ?? 'GET') === method)

describe('AccountForm', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
    vi.mocked(apiFetch).mockImplementation(async (_path: string, init?: RequestInit) => {
      if (init?.method === 'POST' && _path === '/securities-accounts') return CREATED_ACCOUNT
      if (init?.method === 'PATCH' && String(_path).startsWith('/securities-accounts/'))
        return UPDATED_ACCOUNT
      return undefined
    })
  })

  it('생성 시 계좌번호 미입력으로도 등록 요청을 보낸다(번호 미입력 생성)', async () => {
    renderForm({ kind: 'create', investorId: 1, firmId: 2, firmName: '가증권' })
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    await waitFor(() => expect(calls('POST', '/securities-accounts')).toHaveLength(1))
    const body = JSON.parse(calls('POST', '/securities-accounts')[0][1]!.body as string)
    expect(body).toMatchObject({ investorId: 1, securitiesFirmId: 2 })
    expect(body).not.toHaveProperty('accountNumber')
  })

  it('생성 시 계좌번호를 입력하면 포맷팅해 전송한다', async () => {
    renderForm({ kind: 'create', investorId: 1, firmId: 2, firmName: '가증권' })
    await fill(screen.getByLabelText('계좌번호'), '1234567890')
    expect(screen.getByLabelText('계좌번호')).toHaveValue('1234-5678-90')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    await waitFor(() => expect(calls('POST', '/securities-accounts')).toHaveLength(1))
    const body = JSON.parse(calls('POST', '/securities-accounts')[0][1]!.body as string)
    expect(body.accountNumber).toBe('1234-5678-90')
  })

  it('생성 시 계좌번호가 10자 미만이면 인라인 오류를 보여 전송하지 않는다', async () => {
    renderForm({ kind: 'create', investorId: 1, firmId: 2, firmName: '가증권' })
    await fill(screen.getByLabelText('계좌번호'), '123')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(screen.getByText('계좌번호는 10~16자리 숫자여야 합니다.')).toBeInTheDocument()
    expect(calls('POST', '/securities-accounts')).toHaveLength(0)
  })

  it('수정 모드에서 번호가 없으면 입력칸이 활성화되고 사후 입력을 보낸다', async () => {
    renderForm({
      kind: 'edit',
      accountId: 10,
      firmName: '가증권',
      accountName: '주력',
      accountNumber: null,
    })
    await screen.findByLabelText('계좌번호')
    await fill(screen.getByLabelText('계좌번호'), '1234567890')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await waitFor(() => expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(1))
    const body = JSON.parse(calls('PATCH', '/securities-accounts/10')[0][1]!.body as string)
    expect(body.accountNumber).toBe('1234-5678-90')
  })

  it('수정 모드에서 기존 번호가 있으면 입력칸 대신 마스킹 번호와 교체 불가 안내를 보여 준다', async () => {
    renderForm({
      kind: 'edit',
      accountId: 10,
      firmName: '가증권',
      accountName: '주력',
      accountNumber: '1234-****-90',
    })
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument()
    expect(screen.getByText('1234-****-90')).toBeInTheDocument()
    expect(screen.getByText('이미 등록된 계좌번호입니다.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await waitFor(() => expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(1))
    const body = JSON.parse(calls('PATCH', '/securities-accounts/10')[0][1]!.body as string)
    expect(body).not.toHaveProperty('accountNumber')
  })
})
