import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fill } from '@/test/fill'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
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

function renderForm(target: AccountFormTarget, overrides?: { onDone?: () => void }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AccountForm target={target} onDone={overrides?.onDone ?? vi.fn()} onCancel={vi.fn()} />
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
    // 첫 번호 입력에도 확인 다이얼로그를 거친다
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '변경' }),
    )
    await waitFor(() => expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(1))
    const body = JSON.parse(calls('PATCH', '/securities-accounts/10')[0][1]!.body as string)
    expect(body.accountNumber).toBe('1234-5678-90')
  })

  it('수정 모드에서 기존 번호가 있으면 마스킹 안내와 빈 입력칸을 보여 주고, 비워두면 번호 없이 PATCH한다', async () => {
    renderForm({
      kind: 'edit',
      accountId: 10,
      firmName: '가증권',
      accountName: '주력',
      accountNumber: '1234-****-90',
    })
    // 마스킹 값은 입력칸의 기본값이 아니다
    const input = screen.getByLabelText('계좌번호')
    expect(input).toHaveValue('')
    expect(screen.getByText(/1234-\*\*\*\*-90/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    // 번호 변경 입력이 없으므로 확인 다이얼로그 없이 바로 저장된다
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    await waitFor(() => expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(1))
    const body = JSON.parse(calls('PATCH', '/securities-accounts/10')[0][1]!.body as string)
    expect(body).not.toHaveProperty('accountNumber')
  })

  it('수정 모드에서 새 번호를 입력하면 확인 다이얼로그를 거친다: 확인 전 PATCH 0회, 취소 시 입력 유지, 확인 후 1회', async () => {
    renderForm({
      kind: 'edit',
      accountId: 10,
      firmName: '가증권',
      accountName: '주력',
      accountNumber: '1234-****-90',
    })
    await fill(screen.getByLabelText('계좌번호'), '999988887777')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    // 확인 다이얼로그: 이력 유지 경고 문구
    const dialog = screen.getByRole('alertdialog')
    expect(dialog).toHaveTextContent(
      '계좌번호가 바뀌어도 기존 청약·매도 이력은 같은 계좌에 남습니다',
    )
    expect(dialog).toHaveTextContent('실제 계좌와 맞는지 확인')
    // 확인 전 PATCH 0회
    expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(0)
    // 취소: PATCH 0회 유지, 입력값 유지
    await userEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(0)
    expect(screen.getByLabelText('계좌번호')).toHaveValue('9999-8888-7777')
    // 다시 저장 → 확인 → PATCH 정확히 1회
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '변경' }),
    )
    await waitFor(() => expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(1))
    const body = JSON.parse(calls('PATCH', '/securities-accounts/10')[0][1]!.body as string)
    expect(body.accountNumber).toBe('9999-8888-7777')
    expect(body.accountName).toBe('주력')
  })

  it('수정 폼에는 삭제 버튼이 있고, 삭제 확인 후 DELETE하고 onDone을 호출한다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (_path: string, init?: RequestInit) => {
      if (init?.method === 'DELETE') return undefined
      if (init?.method === 'PATCH') return UPDATED_ACCOUNT
      return undefined
    })
    const onDone = vi.fn()
    renderForm(
      {
        kind: 'edit',
        accountId: 10,
        firmName: '가증권',
        accountName: '주력',
        accountNumber: '1234-****-90',
      },
      { onDone },
    )
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    // 확인 전 DELETE 0회, 취소 시에도 DELETE 0회
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '취소' }),
    )
    expect(calls('DELETE', '/securities-accounts/10')).toHaveLength(0)
    // 다시 열어서 확인하면 DELETE 1회 + onDone
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )
    await waitFor(() => expect(calls('DELETE', '/securities-accounts/10')).toHaveLength(1))
    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1))
  })

  it('삭제가 409(청약 이력)이면 서버 메시지를 보여 주고 onDone을 호출하지 않는다', async () => {
    vi.mocked(apiFetch).mockImplementation(async (_path: string, init?: RequestInit) => {
      if (init?.method === 'DELETE') {
        throw new ApiError(409, '연결된 청약 이력이 있어 비활성화할 수 없습니다')
      }
      return undefined
    })
    const onDone = vi.fn()
    renderForm(
      {
        kind: 'edit',
        accountId: 10,
        firmName: '가증권',
        accountName: '주력',
        accountNumber: '1234-****-90',
      },
      { onDone },
    )
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '연결된 청약 이력이 있어 비활성화할 수 없습니다',
    )
    expect(onDone).not.toHaveBeenCalled()
  })

  it('미입력 채우기 폼(기존 id 편집)에도 삭제 버튼이 있다', async () => {
    renderForm({
      kind: 'edit',
      accountId: 10,
      firmName: '가증권',
      accountName: '',
      accountNumber: null,
    })
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument()
  })

  it('생성 폼에는 삭제 버튼이 없다', async () => {
    renderForm({ kind: 'create', investorId: 1, firmId: 2, firmName: '가증권' })
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })
})
