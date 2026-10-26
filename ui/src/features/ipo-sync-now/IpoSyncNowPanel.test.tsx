import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '@/shared/api/apiError.ts'
import { getIpoSyncStatus, syncIposNow } from '@/entities/ipo/index.ts'
import type { IpoSyncStatus } from '@/entities/ipo/index.ts'
import IpoSyncNowPanel from './IpoSyncNowPanel.tsx'

vi.mock('@/entities/ipo/index.ts', () => ({
  syncIposNow: vi.fn(),
  getIpoSyncStatus: vi.fn(),
  ipoKeys: { all: ['ipos'], syncStatus: () => ['ipoSyncStatus'] },
}))

const RESULT = {
  created: 0,
  updated: 0,
  skipped: 0,
  failed: 0,
  scheduleMerged: 0,
  detailSaved: 0,
  aborted: false,
}
const status = (over: Partial<IpoSyncStatus>): IpoSyncStatus => ({
  state: 'IDLE',
  fromMonth: null,
  toMonth: null,
  monthsDone: null,
  monthsTotal: null,
  startedAt: null,
  finishedAt: null,
  result: null,
  error: null,
  ...over,
})
const running = status({
  state: 'RUNNING',
  fromMonth: '2026-01',
  toMonth: '2026-10',
  monthsDone: 3,
  monthsTotal: 10,
  result: { ...RESULT, created: 2, detailSaved: 1 },
})

const now = new Date()
const thisMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

let queryClient: QueryClient
function renderPanel() {
  return render(
    <QueryClientProvider client={queryClient}>
      <IpoSyncNowPanel />
    </QueryClientProvider>,
  )
}
const runButton = () => screen.getByRole('button', { name: 'DART에서 가져오기' })

describe('IpoSyncNowPanel', () => {
  beforeEach(() => {
    vi.mocked(syncIposNow).mockReset()
    vi.mocked(getIpoSyncStatus).mockReset().mockResolvedValue(status({}))
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })
  afterEach(() => vi.useRealTimers())

  it('기본값은 올해 1월부터 이번 달까지다', () => {
    renderPanel()
    expect(screen.getByLabelText('시작 월')).toHaveValue(`${now.getFullYear()}-01`)
    expect(screen.getByLabelText('끝 월')).toHaveValue(thisMonth)
    expect(screen.getByLabelText('끝 월')).toHaveAttribute('max', thisMonth)
    expect(runButton()).toBeEnabled()
  })

  it('시작>끝이거나 24개월을 넘으면 버튼을 막고 이유를 알린다', () => {
    renderPanel()
    fireEvent.change(screen.getByLabelText('시작 월'), { target: { value: '2015-01' } })
    expect(runButton()).toBeDisabled()
    expect(runButton()).toHaveAccessibleDescription('기간은 24개월을 넘을 수 없습니다.')

    fireEvent.change(screen.getByLabelText('시작 월'), { target: { value: thisMonth } })
    fireEvent.change(screen.getByLabelText('끝 월'), { target: { value: '2015-01' } })
    expect(runButton()).toBeDisabled()
    expect(runButton()).toHaveAccessibleDescription('시작 월이 끝 월보다 늦습니다.')
  })

  it('실행하면 범위를 보내고, 진행 상황을 3초마다 갱신하다 완료되면 캐시를 무효화한다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    vi.mocked(syncIposNow).mockResolvedValue(status({ ...running, monthsDone: 0, result: RESULT }))
    renderPanel()
    fireEvent.change(screen.getByLabelText('시작 월'), { target: { value: '2025-06' } })
    fireEvent.change(screen.getByLabelText('끝 월'), { target: { value: '2026-05' } })

    fireEvent.click(runButton())
    await vi.waitFor(() =>
      expect(syncIposNow).toHaveBeenCalledWith({ fromMonth: '2025-06', toMonth: '2026-05' }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent(
      '진행 중: 0/10개월 · 새로 등록 0건 · 상세 0건',
    )
    expect(screen.getByRole('button', { name: '가져오는 중…' })).toBeDisabled()

    vi.mocked(getIpoSyncStatus).mockResolvedValue(running)
    await vi.advanceTimersByTimeAsync(3000)
    expect(screen.getByRole('status')).toHaveTextContent(
      '진행 중: 3/10개월 · 새로 등록 2건 · 상세 1건',
    )
    expect(invalidate).not.toHaveBeenCalled()

    vi.mocked(getIpoSyncStatus).mockResolvedValue(
      status({
        ...running,
        state: 'DONE',
        monthsDone: 10,
        result: { ...RESULT, created: 3, updated: 2, detailSaved: 4, failed: 1 },
      }),
    )
    await vi.advanceTimersByTimeAsync(3000)
    expect(screen.getByRole('status')).toHaveTextContent(
      '완료: 새로 등록 3건 · 갱신 2건 · 상세 4건 · 실패 1건',
    )
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['ipos'] })
    expect(runButton()).toBeEnabled()
  })

  it('화면을 열었을 때 이미 실행 중이면 진행 상황을 이어서 보여 준다', async () => {
    vi.mocked(getIpoSyncStatus).mockResolvedValue(running)
    renderPanel()
    expect(await screen.findByText(/진행 중: 3\/10개월/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가져오는 중…' })).toBeDisabled()
  })

  it('백그라운드 실행이 실패하면 error 문구를 보여 준다', async () => {
    vi.mocked(getIpoSyncStatus).mockResolvedValue(
      status({ state: 'FAILED', result: RESULT, error: 'DART 호출이 계속 실패했습니다.' }),
    )
    renderPanel()
    expect(await screen.findByText('DART 호출이 계속 실패했습니다.')).toBeInTheDocument()
  })

  it.each([
    [409, undefined, '이미 가져오기가 진행 중입니다'],
    [503, undefined, 'OpenDART 키가 설정되지 않았습니다.'],
    [400, '기간이 24개월을 넘습니다.', '기간이 24개월을 넘습니다.'],
    [500, undefined, '가져오기에 실패했습니다'],
  ])('%i 오류를 문구로 보여준다', async (code, serverMessage, text) => {
    vi.mocked(syncIposNow).mockRejectedValue(new ApiError(code, 'x', serverMessage))
    renderPanel()
    await userEvent.click(runButton())
    expect(await screen.findByRole('alert')).toHaveTextContent(text)
  })
})
