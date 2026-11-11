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
    if (key === 'PATCH /securities-accounts/50') {
      return account(50, 7, 4, { accountName: '월급 계좌' })
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
    await screen.findByTitle('주력')
    rerender(ui(8))
    const heads = await screen.findAllByRole('heading', { level: 3 })
    expect(heads[0]).toHaveTextContent('가증권')
    expect(screen.queryByTitle('주력')).not.toBeInTheDocument()
  })

  it('번호 자체를 눌러 그 행에만 평문을 보이고 다시 누르면 마스킹으로 되돌린다', async () => {
    render(ui(7))
    // 별도 보기/눈 아이콘 버튼은 없고 번호 자체가 유일한 '보기' 버튼이다
    const toggles = await screen.findAllByRole('button', { name: /계좌번호 보기/ })
    // 다증권의 번호 있는 계좌 2개(주력·기본) 각각이 토글이다
    expect(toggles).toHaveLength(2)
    await userEvent.click(screen.getByRole('button', { name: '주력 계좌번호 보기' }))
    expect(await screen.findByText('1234-5678-90')).toBeInTheDocument()
    expect(screen.getAllByText('1234-****-90')).toHaveLength(1)
    // 토글의 접근가능 이름이 '숨기기'로 바뀐다
    await userEvent.click(screen.getByRole('button', { name: '주력 계좌번호 숨기기' }))
    expect(screen.queryByText('1234-5678-90')).not.toBeInTheDocument()
  })

  it('번호 보기 응답을 기다리는 동안 토글이 비활성화된다', async () => {
    let resolveReveal: ((v: unknown) => void) | null = null
    const revealPromise = new Promise<unknown>((res) => {
      resolveReveal = res
    })
    vi.mocked(apiFetch).mockImplementation(async (path: string, init?: RequestInit) => {
      const key = `${init?.method ?? 'GET'} ${path}`
      if (key === 'GET /securities-accounts/10/account-number') return revealPromise
      if (key === 'GET /securities-firms') return FIRMS
      const list = /^GET \/securities-accounts\?investorId=(\d+)$/.exec(key)
      if (list) return accounts[Number(list[1])] ?? []
      return undefined
    })
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '주력 계좌번호 보기' }))
    expect(screen.getByRole('button', { name: '주력 계좌번호 보기' })).toBeDisabled()
    resolveReveal!({
      id: 10,
      accountNumber: '1234-5678-90',
      exposedAt: '2026-10-26T00:00:00Z',
    })
    expect(await screen.findByRole('button', { name: '주력 계좌번호 숨기기' })).toBeEnabled()
  })

  it('번호 없는 계좌는 평소 미입력 텍스트만 보이고, 펼친 계좌관리의 해당 계좌 수정이 번호 입력 폼을 연다', async () => {
    accounts[7] = [account(10, 7, 3, { accountNumber: null, accountName: null })]
    render(ui(7)) // 다증권 단일 계좌(번호 미입력)
    // 미입력은 버튼이 아니라 단순 텍스트다
    const none = (await screen.findAllByText('미입력')).find((el) => el.tagName === 'SPAN')
    expect(none).toBeTruthy()
    // 접힌 상태에서는 수정/삭제/계좌추가 버튼이 없다
    expect(screen.queryByRole('button', { name: '미입력 수정' })).not.toBeInTheDocument()
    // 계좌관리를 펼쳐 수정을 누르면 번호 입력 폼이 열린다
    await userEvent.click(screen.getByRole('button', { name: '다증권 계좌관리' }))
    await userEvent.click(screen.getByRole('button', { name: '미입력 수정' }))
    expect(await screen.findByRole('form', { name: '다증권 계좌 수정' })).toBeInTheDocument()
    expect(calls('GET', '/securities-accounts/10/account-number')).toHaveLength(0)
  })

  it('계좌 등록은 펼친 그룹의 계좌추가로 등록 요청을 보낸다', async () => {
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '나증권 계좌관리' }))
    await userEvent.click(screen.getByRole('button', { name: '나증권 계좌추가' }))
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

  it('수정은 펼친 그룹의 수정 버튼으로 PATCH로 저장한다', async () => {
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '다증권 계좌관리' }))
    await userEvent.click(screen.getByRole('button', { name: '주력 수정' }))
    await fill(screen.getByLabelText('계좌명 (선택)'), '월급 계좌')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await waitFor(() => expect(calls('PATCH', '/securities-accounts/10')).toHaveLength(1))
    expect(calls('PATCH', '/securities-accounts/10')[0][1]!.body).toBe(
      JSON.stringify({ accountName: '월급 계좌' }),
    )
  })

  it('삭제는 확인 후에만 요청하고 취소하면 요청하지 않는다', async () => {
    render(ui(7))
    await userEvent.click(await screen.findByRole('button', { name: '다증권 계좌관리' }))
    await userEvent.click(screen.getByRole('button', { name: '주력 삭제' }))
    expect(screen.getByText(/청약 이력이 없는 계좌만 해제할 수 있습니다/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(calls('DELETE', '/securities-accounts/10')).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: '다증권 계좌관리' }))
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
    await userEvent.click(await screen.findByRole('button', { name: '다증권 계좌관리' }))
    await userEvent.click(screen.getByRole('button', { name: '주력 삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('삭제할 수 없는 계좌입니다.')
  })

  describe('행 구조(모두 한 줄 순서)', () => {
    const domOrder = (firmName: string) =>
      // 체크박스 → 로고 → 이름 → 계좌번호 → 액션버튼 순서를 DOM 순서로 확인한다
      Array.from(document.querySelectorAll('.s-ed0b413-firmHead'))
        .map((head) => {
          const name = head.querySelector('.s-ed0b413-firmName')?.textContent
          if (name !== firmName) return null
          return Array.from(
            head.querySelectorAll(
              'input, img, svg, h3, .s-ed0b413-numberButton, .s-ed0b413-numberNoneText, button',
            ),
          ).map((el) => {
            if (el.tagName === 'INPUT') return 'checkbox'
            if (el.tagName === 'IMG' || el.tagName === 'svg') return 'logo'
            if (el.tagName === 'H3') return 'name'
            if (
              el.classList.contains('s-ed0b413-numberButton') ||
              el.classList.contains('s-ed0b413-numberNoneText')
            )
              return 'number'
            return el.textContent?.trim()
          })
        })
        .find((order) => order !== null && order !== undefined)

    it('등록된 증권사(단일 계좌)는 체크박스→로고→이름→번호→버튼 순서이고 모든 요소가 같은 행에 있다', async () => {
      render(ui(8)) // 가증권에 단일 계좌 등록
      await screen.findByRole('checkbox', { name: '가증권 계좌 사용 해제' })
      const order = domOrder('가증권')
      expect(order).toBeTruthy()
      expect(order![0]).toBe('checkbox')
      expect(order![2]).toBe('name')
      const numberIndex = order!.indexOf('number')
      const buttonIndex = order!.findIndex((v, i) => i > numberIndex && v && v !== 'number')
      expect(numberIndex).toBeGreaterThan(2)
      expect(buttonIndex).toBeGreaterThan(numberIndex)
      // 계좌 번호(1234-****-90)·계좌관리 토글이 모두 헤더 행 안에 있다
      const head = Array.from(document.querySelectorAll('.s-ed0b413-firmHead')).find(
        (h) => h.querySelector('.s-ed0b413-firmName')?.textContent === '가증권',
      )!
      expect(head.textContent).toContain('1234-****-90')
      expect(
        within(head as HTMLElement).getByRole('button', { name: '계좌 계좌번호 보기' }),
      ).toBeInTheDocument()
      // 평소엔 계좌관리 하나만 보이고 수정/삭제/계좌추가는 펼칠 때만 보인다
      expect(
        within(head as HTMLElement).getByRole('button', { name: '가증권 계좌관리' }),
      ).toBeInTheDocument()
      expect(
        within(head as HTMLElement).queryByRole('button', { name: /수정/ }),
      ).not.toBeInTheDocument()
      await userEvent.click(
        within(head as HTMLElement).getByRole('button', { name: '가증권 계좌관리' }),
      )
      const group = screen.getByRole('group', { name: '가증권 계좌 작업' })
      expect(within(group).getByRole('button', { name: '1234-****-90 수정' })).toBeInTheDocument()
      expect(within(group).getByRole('button', { name: '1234-****-90 삭제' })).toBeInTheDocument()
      expect(within(group).getByRole('button', { name: '가증권 계좌추가' })).toBeInTheDocument()
      // 다시 누르면 닫힌다
      await userEvent.click(
        within(head as HTMLElement).getByRole('button', { name: '가증권 계좌관리' }),
      )
      expect(screen.queryByRole('group', { name: '가증권 계좌 작업' })).not.toBeInTheDocument()
    })

    it('미등록 증권사는 같은 순서로 미입력 텍스트와 계좌관리 토글을 같은 행에 보여 준다', async () => {
      render(ui(8))
      await screen.findByRole('checkbox', { name: '나증권 계좌 사용 등록' })
      const head = Array.from(document.querySelectorAll('.s-ed0b413-firmHead')).find(
        (h) => h.querySelector('.s-ed0b413-firmName')?.textContent === '나증권',
      )!
      expect(within(head as HTMLElement).getByText('미입력')).toBeInTheDocument()
      expect(
        within(head as HTMLElement).getByRole('button', { name: '나증권 계좌관리' }),
      ).toBeInTheDocument()
      // 미등록 행은 수정/삭제 버튼이 없고, 펼치면 계좌추가만 보인다
      expect(
        within(head as HTMLElement).queryByRole('button', { name: /수정|삭제/ }),
      ).not.toBeInTheDocument()
      await userEvent.click(
        within(head as HTMLElement).getByRole('button', { name: '나증권 계좌관리' }),
      )
      const group = screen.getByRole('group', { name: '나증권 계좌 작업' })
      expect(within(group).getByRole('button', { name: '나증권 계좌추가' })).toBeInTheDocument()
      expect(within(group).queryByRole('button', { name: /수정/ })).not.toBeInTheDocument()
      expect(within(group).queryByRole('button', { name: /삭제/ })).not.toBeInTheDocument()
      // 체크박스가 헤더 행의 첫 번째 focusable 요소다
      const first = head.querySelector('input')
      expect(first?.getAttribute('aria-label')).toBe('나증권 계좌 사용 등록')
    })

    it('복수 계좌는 번호들이 헤더 행 안에서 필요한 만큼 작은 줄로 접히고 계좌별 관리 토글은 없다', async () => {
      render(ui(7)) // 다증권에 계좌 2개(주력, 그리고 기본값)
      await screen.findByRole('checkbox', { name: '다증권 계좌 사용 해제' })
      const heads = Array.from(document.querySelectorAll('.s-ed0b413-firmHead')).filter(
        (h) => h.querySelector('.s-ed0b413-firmName')?.textContent === '다증권',
      )
      expect(heads).toHaveLength(1) // 체크박스는 한 번만 렌더된다
      const head = heads[0] as HTMLElement
      // 두 계좌의 번호 토글이 모두 헤더 행 안에 있다
      expect(within(head).getAllByRole('button', { name: /계좌번호 보기/ })).toHaveLength(2)
      // 첫 계좌(주력)의 별칭은 title로 안내된다
      expect(head.querySelector('.s-ed0b413-numberButton')?.getAttribute('title')).toBe('주력')
      // 계좌관리 토글은 회사당 정확히 1개뿐이고 계좌별 토글이 없다
      expect(within(head).getAllByRole('button', { name: '다증권 계좌관리' })).toHaveLength(1)
      expect(within(head).queryByRole('button', { name: /주력 계좌관리|계좌 계좌관리/ })).toBeNull()
      // 헤더 밖 별도 작은 계좌 행은 없다
      const firmCard = head.closest('.s-ed0b413-firm')!
      expect(firmCard.querySelector('.s-ed0b413-accounts')).toBeNull()
      expect(firmCard.querySelectorAll('h3')).toHaveLength(1) // 회사명 반복 없음
      expect(firmCard.querySelectorAll('input[type="checkbox"]')).toHaveLength(1)
    })

    it('계좌관리를 다시 누르면 닫히고, ESC를 누르면 닫히며 포커스가 토글로 돌아온다', async () => {
      render(ui(8))
      const toggle = await screen.findByRole('button', { name: '가증권 계좌관리' })
      await userEvent.click(toggle)
      expect(screen.getByRole('group', { name: '가증권 계좌 작업' })).toBeInTheDocument()
      // 그룹 안의 첫 버튼으로 포커스를 옮긴 뒤 ESC → 닫히고 토글로 포커스 복귀
      await userEvent.tab()
      expect(screen.getByRole('button', { name: '1234-****-90 수정' })).toHaveFocus()
      await userEvent.keyboard('{Escape}')
      expect(screen.queryByRole('group', { name: '가증권 계좌 작업' })).not.toBeInTheDocument()
      expect(toggle).toHaveFocus()
      // 다시 열고 토글에서 ESC
      await userEvent.click(toggle)
      expect(screen.getByRole('group', { name: '가증권 계좌 작업' })).toBeInTheDocument()
      await userEvent.keyboard('{Escape}')
      expect(screen.queryByRole('group', { name: '가증권 계좌 작업' })).not.toBeInTheDocument()
      expect(toggle).toHaveFocus()
    })

    it('번호가 없는 계좌는 미입력 칩을 보여 준다', async () => {
      accounts[7] = [account(40, 7, 2, { accountNumber: null })]
      render(ui(7))
      expect(await screen.findAllByText('미입력')).not.toHaveLength(0)
    })

    it('한국투자증권에 계좌 2개일 때 계좌관리 토글은 정확히 1개이고 펼치면 계좌별 수정·삭제와 계좌추가 1개가 보인다', async () => {
      // 모의 API에 한국투자증권을 등록하고 투자자 7의 다증권 대신 한국투자증권에 계좌 2개를 둔다
      overrides['GET /securities-firms'] = () => [...FIRMS, firm(4, '한국투자증권')]
      accounts[7] = [
        account(50, 7, 4, { accountName: '주력' }),
        account(51, 7, 4, { accountNumber: '9999-****-88' }),
      ]
      render(ui(7))
      await screen.findByRole('checkbox', { name: '한국투자증권 계좌 사용 해제' })
      const head = Array.from(document.querySelectorAll('.s-ed0b413-firmHead')).find(
        (h) => h.querySelector('.s-ed0b413-firmName')?.textContent === '한국투자증권',
      ) as HTMLElement
      // 평소엔 계좌관리 토글이 회사당 정확히 1개
      expect(within(head).getAllByRole('button', { name: '한국투자증권 계좌관리' })).toHaveLength(1)
      expect(within(head).queryByRole('button', { name: /수정|삭제|계좌추가/ })).toBeNull()
      // 체크→로고→회사명→번호들→계좌관리 순서
      const order = Array.from(
        head.querySelectorAll(
          'input, img, svg, h3, .s-ed0b413-numberButton, .s-ed0b413-numberNoneText, button',
        ),
      ).map((el) => {
        if (el.tagName === 'INPUT') return 'checkbox'
        if (el.tagName === 'IMG' || el.tagName === 'svg') return 'logo'
        if (el.tagName === 'H3') return 'name'
        if (el.classList.contains('s-ed0b413-numberButton')) return 'number'
        return el.textContent?.trim()
      })
      expect(order).toEqual(['checkbox', 'logo', 'name', 'number', 'number', '계좌관리'])
      // 펼친 영역: 각 계좌 대상(별칭/번호)을 명확히 한 수정·삭제 + 회사 단위 계좌추가 정확히 1개
      await userEvent.click(within(head).getByRole('button', { name: '한국투자증권 계좌관리' }))
      const group = screen.getByRole('group', { name: '한국투자증권 계좌 작업' })
      expect(within(group).getByText('주력')).toBeInTheDocument()
      expect(within(group).getByText('9999-****-88')).toBeInTheDocument()
      expect(within(group).getByRole('button', { name: '주력 수정' })).toBeInTheDocument()
      expect(within(group).getByRole('button', { name: '주력 삭제' })).toBeInTheDocument()
      expect(within(group).getByRole('button', { name: '9999-****-88 수정' })).toBeInTheDocument()
      expect(within(group).getByRole('button', { name: '9999-****-88 삭제' })).toBeInTheDocument()
      expect(within(group).getAllByRole('button', { name: '한국투자증권 계좌추가' })).toHaveLength(
        1,
      )
      // 수정 버튼은 각 계좌 id로 PATCH를 보낸다(주력=50)
      await userEvent.click(within(group).getByRole('button', { name: '주력 수정' }))
      await fill(screen.getByLabelText('계좌명 (선택)'), '월급 계좌')
      await userEvent.click(screen.getByRole('button', { name: '저장' }))
      await waitFor(() => expect(calls('PATCH', '/securities-accounts/50')).toHaveLength(1))
      expect(calls('PATCH', '/securities-accounts/50')[0][1]!.body).toBe(
        JSON.stringify({ accountName: '월급 계좌' }),
      )
      // 다시 펼쳐 번호 계좌의 삭제 버튼은 그 계좌(51) id로 DELETE를 보낸다
      await userEvent.click(screen.getByRole('button', { name: '한국투자증권 계좌관리' }))
      await userEvent.click(screen.getByRole('button', { name: '9999-****-88 삭제' }))
      await userEvent.click(
        within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
      )
      await waitFor(() => expect(calls('DELETE', '/securities-accounts/51')).toHaveLength(1))
    })
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
