import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '@/shared/api/apiError.ts'
import IpoSyncNowPanel from './IpoSyncNowPanel.tsx'

const syncIposNow = vi.fn()
vi.mock('@/entities/ipo/index.ts', () => ({
  syncIposNow: () => syncIposNow(),
  ipoKeys: { all: ['ipos'] },
}))

let queryClient: QueryClient
function renderPanel() {
  return render(
    <QueryClientProvider client={queryClient}>
      <IpoSyncNowPanel />
    </QueryClientProvider>,
  )
}

describe('IpoSyncNowPanel', () => {
  beforeEach(() => {
    syncIposNow.mockReset()
    queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  })

  it('실행 중에는 버튼을 막고 끝나면 결과를 알리며 공모주 캐시를 무효화한다', async () => {
    let finish: (v: unknown) => void = () => {}
    syncIposNow.mockReturnValue(new Promise((r) => (finish = r)))
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'DART에서 지금 가져오기' }))
    expect(screen.getByRole('button', { name: '가져오는 중…' })).toBeDisabled()

    finish({
      created: 3,
      updated: 2,
      skipped: 1,
      failed: 1,
      scheduleMerged: 0,
      detailSaved: 4,
      aborted: false,
    })
    expect(
      await screen.findByText(/새로 등록 3건 · 갱신 2건 · 상세 4건 · 실패 1건/),
    ).toBeInTheDocument()
    expect(spy).toHaveBeenCalledWith({ queryKey: ['ipos'] })
  })

  it.each([
    [409, '이미 가져오기가 진행 중입니다'],
    [503, 'OpenDART 키가 설정되지 않았습니다.'],
    [500, '가져오기에 실패했습니다'],
  ])('%i 오류를 문구로 보여준다', async (status, text) => {
    syncIposNow.mockRejectedValue(new ApiError(status, 'x'))
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'DART에서 지금 가져오기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(text)
  })
})
