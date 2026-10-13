import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fill } from '@/test/fill'
import { apiFetch } from '@/shared/api/client.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import RecordActions, { type RecordKind } from './RecordActions.tsx'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))
const mockFetch = vi.mocked(apiFetch)

const RECORDS: Record<RecordKind, object> = {
  subscription: {
    id: 1,
    quantity: 10,
    depositAmount: 200000,
    subscribedAt: '2026-09-01T09:00:00',
    memo: '메모',
  },
  allocation: {
    id: 2,
    allocatedQuantity: 5,
    allocatedAt: '2026-09-05T09:00:00',
    allocationPrice: 10000,
    allocationMethod: 'LOTTERY',
    note: '',
  },
  sale: {
    id: 3,
    quantity: 4,
    price: 15000,
    amount: 60000,
    saleDate: '2026-09-10T09:00:00',
    fee: 2000,
    tax: 300,
  },
}
const ID = { subscription: 1, allocation: 2, sale: 3 }
const PATH = { subscription: '/subscriptions/1', allocation: '/allocations/2', sale: '/sales/3' }

function setup(kind: RecordKind) {
  mockFetch.mockImplementation((_path, options) =>
    Promise.resolve(options?.method ? undefined : RECORDS[kind]),
  )
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RecordActions kind={kind} id={ID[kind]} label="테스트 항목" />
    </QueryClientProvider>,
  )
}

const call = (method: string) => mockFetch.mock.calls.find(([, o]) => o?.method === method)

describe('RecordActions', () => {
  beforeEach(() => mockFetch.mockReset())

  it('청약 수정: 바뀐 값을 PATCH로 보낸다', async () => {
    setup('subscription')
    await userEvent.click(screen.getByRole('button', { name: '청약 수정' }))
    await fill(await screen.findByLabelText('청약수량'), '20')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(call('PATCH')).toBeDefined())
    const [path, options] = call('PATCH')!
    expect(path).toBe(PATH.subscription)
    expect(JSON.parse(options!.body as string)).toEqual({
      quantity: 20,
      depositAmount: 200000,
      subscribedAt: '2026-09-01T09:00',
      memo: '메모',
    })
  })

  it('배정 수정: 배정수량과 배정방식을 PATCH로 보낸다', async () => {
    setup('allocation')
    await userEvent.click(screen.getByRole('button', { name: '배정 수정' }))
    await fill(await screen.findByLabelText('배정수량'), '3')
    await fill(screen.getByLabelText('배정방식 (선택)'), 'PROPORTIONAL')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(call('PATCH')).toBeDefined())
    const [path, options] = call('PATCH')!
    expect(path).toBe(PATH.allocation)
    expect(JSON.parse(options!.body as string)).toEqual({
      allocatedQuantity: 3,
      allocatedAt: '2026-09-05T09:00',
      allocationPrice: 10000,
      allocationMethod: 'PROPORTIONAL',
      note: '',
    })
  })

  it('매도 수정: price=매도단가, amount=단가×수량, fee=공모주수수료, tax=세금 및 기타로 보낸다', async () => {
    setup('sale')
    await userEvent.click(screen.getByRole('button', { name: '매도 수정' }))
    await fill(await screen.findByLabelText('매도수량'), '2')
    await fill(screen.getByLabelText('매도단가'), '20000')
    await fill(screen.getByLabelText('세금 및 기타'), '500')
    await fill(screen.getByLabelText('공모주수수료'), '1000')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(call('PATCH')).toBeDefined())
    const [path, options] = call('PATCH')!
    expect(path).toBe(PATH.sale)
    expect(JSON.parse(options!.body as string)).toEqual({
      quantity: 2,
      price: 20000,
      amount: 40000,
      saleDate: '2026-09-10T09:00',
      fee: 1000,
      tax: 500,
    })
  })

  it.each(['subscription', 'allocation', 'sale'] as const)(
    '%s 삭제: 확인 후 DELETE를 호출한다',
    async (kind) => {
      setup(kind)
      await userEvent.click(screen.getByRole('button', { name: /삭제$/ }))
      await userEvent.click(
        within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
      )

      await waitFor(() => expect(call('DELETE')?.[0]).toBe(PATH[kind]))
    },
  )

  it('삭제 확인을 연달아 눌러도 DELETE는 한 번만 나간다', async () => {
    let release: () => void = () => {}
    mockFetch.mockImplementation((_path, options) =>
      options?.method === 'DELETE'
        ? new Promise((resolve) => {
            release = () => resolve(undefined)
          })
        : Promise.resolve(RECORDS.sale),
    )
    render(
      <QueryClientProvider client={new QueryClient()}>
        <RecordActions kind="sale" id={3} />
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: '매도 삭제' }))
    const confirm = within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' })
    await userEvent.dblClick(confirm)
    await userEvent.click(confirm)
    release()

    await waitFor(() =>
      expect(mockFetch.mock.calls.filter(([, o]) => o?.method === 'DELETE')).toHaveLength(1),
    )
  })

  it('배정 수정에서 있던 배정단가를 비우면 저장하지 않고 안내한다', async () => {
    setup('allocation')
    await userEvent.click(screen.getByRole('button', { name: /수정$/ }))
    const price = await screen.findByLabelText(/배정단가/)
    await userEvent.clear(price)
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(
      await screen.findByText('배정단가는 비울 수 없습니다. 값을 입력해 주세요.'),
    ).toBeInTheDocument()
    expect(call('PATCH')).toBeUndefined()
  })

  it('삭제가 409면 서버 메시지를 그대로 보여 준다', async () => {
    const message = '매도 기록이 있는 배정은 취소할 수 없습니다. 매도를 먼저 삭제해 주세요.'
    mockFetch.mockRejectedValueOnce(new ApiError(409, message, message))
    render(
      <QueryClientProvider client={new QueryClient()}>
        <RecordActions kind="allocation" id={2} />
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: '배정 삭제' }))
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })
})
