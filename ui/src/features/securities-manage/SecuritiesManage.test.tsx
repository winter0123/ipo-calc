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
    7: [account(10, 7, 3, { accountName: '주력' }), account(11, 7, 3)],
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
    if (key === 'POST /securities-accounts') {
      return account(30, 7, 2, { accountNumber: '1234-****-3456' })
    }
    if (key === 'PATCH /securities-accounts/10') {
      return account(10, 7, 3, { accountName: '월급 계좌' })
    }
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
      return {
        id: 10,
        accountNumber: '1234-5678-90',
        exposedAt: '2026-10-26T00:00:00Z',
      }
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
  it('등록된 증권사를 위로 올리고 마스킹된 번호를 보여 준다', async () => {
    render(ui(7))
    const items = await screen.findAllByRole('heading', { level: 3 })
    expect(items.map((h) => h.textContent)).toEqual(['다증권', '가증권', '나증권'])
    expect(screen.getByRole('heading', { level: 2, name: '등록한 증권사' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: '다른 증권사' })).toBeInTheDocument()
    expect(screen.getAllByText('1234-****-90')).toHaveLength(2)
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
    expect(screen.getByText(/청약 이력이 없는 계좌만 해제할 수 있습니다/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(calls('DELETE', '/securities-accounts/10')).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: '주력 삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )
    await waitFor(() => expect(calls('DELETE', '/securities-accounts/10')).toHaveLength(1))
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

  describe('체크박스(계좌 사용 ON/OFF)', () => {
    it('체크 시 계좌번호 없이 등록 요청을 보낸다(번호 미입력 생성)', async () => {
      const errors: string[] = []
      const originalError = console.error
      console.error = (...args: unknown[]) => {
        errors.push(args.join(' '))
      }
      try {
        render(ui(7))
        await userEvent.click(
          await screen.findByRole('checkbox', { name: '나증권 계좌 사용 등록' }),
        )
        await waitFor(() => expect(calls('POST', '/securities-accounts')).toHaveLength(1))
        const body = JSON.parse(calls('POST', '/securities-accounts')[0][1]!.body as string)
        expect(body).toMatchObject({ investorId: 7, securitiesFirmId: 2 })
        expect(body).not.toHaveProperty('accountNumber')
        // 체크 후 React controlled→controlled 경고가 콘솔에 없어야 한다
        const controlledWarnings = errors.filter(
          (e) => e.includes('controlled') || e.includes('uncontrolled'),
        )
        expect(controlledWarnings).toHaveLength(0)
      } finally {
        console.error = originalError
      }
    })

    it('이미 활성 계좌가 있는 증권사는 처음부터 체크된 상태로 표시된다', async () => {
      render(ui(7))
      expect(await screen.findByRole('checkbox', { name: '다증권 계좌 사용 해제' })).toBeChecked()
    })

    it('복수 계좌가 있는 증권사는 해제를 안내하고 체크를 유지한다', async () => {
      render(ui(7))
      await userEvent.click(await screen.findByRole('checkbox', { name: '다증권 계좌 사용 해제' }))
      expect(
        await screen.findByText(/같은 증권사에 등록된 계좌가 여러 개 있어/),
      ).toBeInTheDocument()
      expect(calls('DELETE', '/securities-accounts')).toHaveLength(0)
      expect(await screen.findByRole('checkbox', { name: '다증권 계좌 사용 해제' })).toBeChecked()
    })

    it('단일 계좌 해제는 확인 후 DELETE를 보낸다', async () => {
      render(ui(8))
      await userEvent.click(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' }))
      await userEvent.click(
        within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
      )
      await waitFor(() => expect(calls('DELETE', '/securities-accounts/20')).toHaveLength(1))
    })

    it('체크 시 계좌번호 없이 등록 POST를 보낸다(번호 미입력 생성)', async () => {
      render(ui(7))
      await userEvent.click(await screen.findByRole('checkbox', { name: '나증권 계좌 사용 등록' }))
      await waitFor(() => expect(calls('POST', '/securities-accounts')).toHaveLength(1))
      const body = JSON.parse(calls('POST', '/securities-accounts')[0][1]!.body as string)
      expect(body).toMatchObject({ investorId: 7, securitiesFirmId: 2 })
      expect(body).not.toHaveProperty('accountNumber')
    })

    it('체크 POST 실패 시 체크를 해제하고 role=alert로 에러를 안내한다', async () => {
      vi.mocked(apiFetch).mockImplementation(async (path: string, init?: RequestInit) => {
        const key = `${init?.method ?? 'GET'} ${path}`
        if (key === 'POST /securities-accounts') throw new ApiError(400, 'Bad', '등록 실패')
        if (key === 'GET /securities-firms') return FIRMS
        const list = /^GET \/securities-accounts\?investorId=(\d+)$/.exec(key)
        if (list) return accounts[Number(list[1])] ?? []
        return undefined
      })
      render(ui(7))
      await userEvent.click(await screen.findByRole('checkbox', { name: '나증권 계좌 사용 등록' }))
      await waitFor(async () =>
        expect(await screen.findByRole('alert')).toHaveTextContent('등록 실패'),
      )
      expect(
        await screen.findByRole('checkbox', { name: '나증권 계좌 사용 등록' }),
      ).not.toBeChecked()
    })

    it('단일 계좌 해제 취소 시 DELETE를 보내지 않고 체크를 유지한다', async () => {
      render(ui(8))
      await userEvent.click(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' }))
      await userEvent.click(
        within(screen.getByRole('alertdialog')).getByRole('button', { name: '취소' }),
      )
      expect(calls('DELETE', '/securities-accounts')).toHaveLength(0)
      expect(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' })).toBeChecked()
    })

    it('단일 계좌 해제 시 DELETE가 409이면 체크를 유지한다', async () => {
      overrides['DELETE /securities-accounts/20'] = () => {
        throw new ApiError(409, '연결된 청약 이력이 있어 비활성화할 수 없습니다')
      }
      render(ui(8))
      await userEvent.click(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' }))
      await userEvent.click(
        within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
      )
      await waitFor(async () =>
        expect(await screen.findByRole('alert')).toHaveTextContent(
          '연결된 청약 이력이 있어 비활성화할 수 없습니다',
        ),
      )
      expect(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' })).toBeChecked()
    })

    it('서로 다른 2개 증권사를 실제 동시 등록하면 응답 순서대로 pending이 해제된다', async () => {
      let resolveFirst: ((v: unknown) => void) | null = null
      let resolveSecond: ((v: unknown) => void) | null = null
      let firstIssued = false
      const firstPromise = new Promise<unknown>((res) => {
        resolveFirst = res
      })
      const secondPromise = new Promise<unknown>((res) => {
        resolveSecond = res
      })

      render(ui(7))
      // 초기 쿼리 완료 대기
      await screen.findByRole('checkbox', {
        name: '가증권 계좌 사용 등록',
      })
      await screen.findByRole('checkbox', {
        name: '나증권 계좌 사용 등록',
      })

      // POST 두 개를 서로 다른 deferred Promise로 대기
      vi.mocked(apiFetch).mockImplementation(async (path: string, init?: RequestInit) => {
        const key = `${init?.method ?? 'GET'} ${path}`
        if (key === 'POST /securities-accounts') {
          if (!firstIssued) {
            firstIssued = true
            return firstPromise
          }
          return secondPromise
        }
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
        return undefined
      })

      // 가증권(firmId=1) 체크 → 첫째 POST 대기
      await userEvent.click(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 등록' }))
      // 나증권(firmId=2) 체크 → 둘째 POST 대기 (첫째 응답 전)
      await userEvent.click(await screen.findByRole('checkbox', { name: '나증권 계좌 사용 등록' }))

      // 두 개 모두 전송 후 둘 다 disabled(aria-label은 이미 "해제"로 변경됨)
      await waitFor(() => expect(calls('POST', '/securities-accounts')).toHaveLength(2))
      expect(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' })).toBeDisabled()
      expect(await screen.findByRole('checkbox', { name: '나증권 계좌 사용 해제' })).toBeDisabled()

      // 둘째 응답 먼저 완료(계좌 목록에 추가 후) → 나증권만 enabled·checked
      accounts[7]!.push(account(32, 7, 2, { accountNumber: null }))
      resolveSecond!(account(32, 7, 2, { accountNumber: null }))
      await waitFor(() =>
        expect(screen.getByRole('checkbox', { name: '나증권 계좌 사용 해제' })).toBeChecked(),
      )
      expect(
        await screen.findByRole('checkbox', { name: '나증권 계좌 사용 해제' }),
      ).not.toBeDisabled()
      // 첫째 응답 전까지 가증권은 여전히 "해제" 체크박스지만 disabled
      expect(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' })).toBeDisabled()

      // 첫째 응답 완료(계좌 목록에 추가 후) → 가증권도 enabled·checked
      accounts[7]!.push(account(31, 7, 1, { accountNumber: null }))
      resolveFirst!(account(31, 7, 1, { accountNumber: null }))
      await waitFor(() =>
        expect(screen.getByRole('checkbox', { name: '가증권 계좌 사용 해제' })).toBeChecked(),
      )
      expect(
        await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' }),
      ).not.toBeDisabled()
      expect(calls('POST', '/securities-accounts')).toHaveLength(2)
    })

    it('첫째 등록이 실패하면 해당만 unchecked·alert, 둘째는 정상 checked', async () => {
      let rejectFirst: ((err: unknown) => void) | null = null
      let resolveSecond: ((v: unknown) => void) | null = null
      let firstIssued = false
      const firstPromise = new Promise<never>((_res, rej) => {
        rejectFirst = rej
      })
      const secondPromise = new Promise<unknown>((res) => {
        resolveSecond = res
      })

      render(ui(7))
      // 초기 쿼리 완료 대기
      await screen.findByRole('checkbox', {
        name: '가증권 계좌 사용 등록',
      })
      await screen.findByRole('checkbox', {
        name: '나증권 계좌 사용 등록',
      })

      // POST만 오버라이드: 첫 번째는 지연 실패, 두 번째는 성공
      vi.mocked(apiFetch).mockImplementation(async (path: string, init?: RequestInit) => {
        const key = `${init?.method ?? 'GET'} ${path}`
        if (key === 'POST /securities-accounts') {
          if (!firstIssued) {
            firstIssued = true
            return firstPromise
          }
          return secondPromise
        }
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
        return undefined
      })

      // 가증권(firmId=1) 체크 → 첫째 POST 대기 (실패)
      await userEvent.click(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 등록' }))
      // 나증권(firmId=2) 체크 → 둘째 POST 대기 (성공)
      await userEvent.click(await screen.findByRole('checkbox', { name: '나증권 계좌 사용 등록' }))

      await waitFor(() => expect(calls('POST', '/securities-accounts')).toHaveLength(2))
      expect(await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' })).toBeDisabled()
      expect(await screen.findByRole('checkbox', { name: '나증권 계좌 사용 해제' })).toBeDisabled()

      // 첫째 실패 → 가증권 alert + unchecked + enabled
      rejectFirst!(new ApiError(400, 'Bad', '등록 실패'))
      await waitFor(async () =>
        expect(await screen.findByRole('alert')).toHaveTextContent('등록 실패'),
      )
      expect(screen.getByRole('checkbox', { name: '가증권 계좌 사용 등록' })).not.toBeChecked()
      expect(screen.getByRole('checkbox', { name: '가증권 계좌 사용 등록' })).not.toBeDisabled()

      // 둘째 성공(계좌 목록에 추가) → 나증권 checked + enabled
      accounts[7]!.push(account(32, 7, 2, { accountNumber: null }))
      resolveSecond!(account(32, 7, 2, { accountNumber: null }))
      await waitFor(() =>
        expect(screen.getByRole('checkbox', { name: '나증권 계좌 사용 해제' })).toBeChecked(),
      )
      expect(
        await screen.findByRole('checkbox', { name: '나증권 계좌 사용 해제' }),
      ).not.toBeDisabled()
      expect(calls('POST', '/securities-accounts')).toHaveLength(2)
    })

    it('투자자를 바꿔도 이전 투자자의 계좌 삭제 요청을 보내지 않는다', async () => {
      const { rerender } = render(ui(7))
      // 투자자 7의 다증권에 체크(등록됨) 상태
      expect(await screen.findByRole('checkbox', { name: '다증권 계좌 사용 해제' })).toBeChecked()
      // 투자자 8로 변경 → 투자자 7의 계좌에 대한 DELETE 없음
      rerender(ui(8))
      await screen.findAllByRole('checkbox', { name: /계좌 사용/ })
      expect(calls('DELETE', '/securities-accounts')).toHaveLength(0)
    })
  })
})
