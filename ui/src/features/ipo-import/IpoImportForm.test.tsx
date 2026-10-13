import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoImportForm from './IpoImportForm.tsx'
import { buildTemplateBlob } from './csvTemplate.ts'
import { ApiError } from '@/shared/api/apiError.ts'
import { importIposCsv } from '@/entities/ipo/index.ts'

vi.mock('@/entities/ipo/index.ts', () => ({
  importIposCsv: vi.fn(),
  ipoKeys: { all: ['ipos'] },
}))

function setup() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const spy = vi.spyOn(client, 'invalidateQueries')
  render(
    <QueryClientProvider client={client}>
      <IpoImportForm />
    </QueryClientProvider>,
  )
  return { spy, user: userEvent.setup() }
}

const csv = new File(['a'], 'ipos.csv', { type: 'text/csv' })

describe('IpoImportForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('성공 결과를 보여 주고 공모주 쿼리를 무효화한다', async () => {
    vi.mocked(importIposCsv).mockResolvedValue({ successCount: 3, failureCount: 0, failures: [] })
    const { spy, user } = setup()
    await user.upload(screen.getByLabelText('CSV 파일'), csv)
    await user.click(screen.getByRole('button', { name: '가져오기' }))
    expect(await screen.findByText('성공 3건 · 실패 0건')).toBeInTheDocument()
    expect(spy).toHaveBeenCalledWith({ queryKey: ['ipos'] })
  })

  it('일부 실패 행을 표로 보여 준다', async () => {
    vi.mocked(importIposCsv).mockResolvedValue({
      successCount: 1,
      failureCount: 1,
      failures: [{ rowNumber: 3, reason: 'subscriptionEndDate 값이 YYYY-MM-DD 형식이 아닙니다.' }],
    })
    const { user } = setup()
    await user.upload(screen.getByLabelText('CSV 파일'), csv)
    await user.click(screen.getByRole('button', { name: '가져오기' }))
    expect(await screen.findByText('성공 1건 · 실패 1건')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '3' })).toBeInTheDocument()
    expect(screen.getByText(/YYYY-MM-DD 형식이 아닙니다/)).toBeInTheDocument()
  })

  it('5MB 초과 파일은 업로드 전에 막는다', async () => {
    const { user } = setup()
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.csv', { type: 'text/csv' })
    await user.upload(screen.getByLabelText('CSV 파일'), big)
    expect(screen.getByRole('alert')).toHaveTextContent('최대 5MB')
    expect(screen.getByRole('button', { name: '가져오기' })).toBeDisabled()
    expect(importIposCsv).not.toHaveBeenCalled()
  })

  it('400은 서버 메시지 그대로, 403은 관리자 안내를 보여 준다', async () => {
    vi.mocked(importIposCsv).mockRejectedValueOnce(
      new ApiError(400, 'x', '필수 헤더가 누락되었습니다: ipoName'),
    )
    const { user } = setup()
    await user.upload(screen.getByLabelText('CSV 파일'), csv)
    await user.click(screen.getByRole('button', { name: '가져오기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '필수 헤더가 누락되었습니다: ipoName',
    )

    vi.mocked(importIposCsv).mockRejectedValueOnce(new ApiError(403, 'Forbidden'))
    await user.click(screen.getByRole('button', { name: '가져오기' }))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('관리자만'))
  })

  it('템플릿은 UTF-8 BOM과 필수 헤더를 담는다', async () => {
    const buf = new Uint8Array(await buildTemplateBlob().arrayBuffer())
    expect([...buf.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    const text = new TextDecoder().decode(buf.slice(3))
    expect(text.split('\n')[0]).toContain(
      'companyName,ipoName,stockCode,roundLabel,lowerBandPrice,upperBandPrice,fixedPrice,subscriptionStartDate,subscriptionEndDate,listingDate,offeringShareCount',
    )
  })
})
