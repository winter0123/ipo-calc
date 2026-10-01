import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fill } from '@/test/fill'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import IpoRegisterForm from './IpoRegisterForm.tsx'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/shared/api/apiError.ts'

let mutationFnMock: (data: unknown) => Promise<unknown> = () => Promise.resolve({})

vi.mock('../useIpoRegister.ts', () => ({
  useIpoRegister: () => {
    const queryClient = useQueryClient()
    return useMutation({
      mutationFn: mutationFnMock,
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['ipos'] })
        mockNavigate('/ipo-calendar')
      },
    })
  },
}))

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/ipos']}>
        <IpoRegisterForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function setupForm() {
  renderForm()
  await waitFor(() => {
    expect(screen.getByLabelText(/회사명/)).not.toBeDisabled()
  })
  await fill(screen.getByLabelText(/회사명/), '테스트 회사')
  await fill(screen.getByLabelText(/공모주명/), '테스트 IPO')
  await waitFor(() => {
    expect(screen.getByLabelText(/청약 시작일/)).not.toBeDisabled()
  })
  await fill(screen.getByLabelText(/청약 시작일/), '2026-02-01')
  await fill(screen.getByLabelText(/청약 종료일/), '2026-02-02')
}

describe('IpoRegisterForm', () => {
  beforeEach(() => {
    mockNavigate.mockClear()
    mutationFnMock = () => Promise.resolve({})
    vi.clearAllMocks()
  })

  it('회사명 필수값 검증', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/회사명/), '')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('회사명을 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('공모주명 필수값 검증', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/공모주명/), '')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('공모주명을 입력해 주세요.')).toBeInTheDocument()
    })
  })

  it('청약 시작일 필수값 검증', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/청약 시작일/), '')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('청약 시작일을 선택해 주세요.')).toBeInTheDocument()
    })
  })

  it('청약 종료일 필수값 검증', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/청약 종료일/), '')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('청약 종료일을 선택해 주세요.')).toBeInTheDocument()
    })
  })

  it('청약 종료일이 시작일보다 빠르면 에러 메시지 표시', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/청약 종료일/), '2026-01-01')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('청약 종료일은 시작일보다 빠를 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('희망 공모가 하단이 상단보다 크면 에러 메시지 표시', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/희망 공모가 하단/), '50000')
    await fill(screen.getByLabelText(/희망 공모가 상단/), '40000')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('희망 공모가 하단은 상단보다 클 수 없습니다.')).toBeInTheDocument()
    })
  })

  it('희망 공모가 하단 음수 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/희망 공모가 하단/), '-1')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 이상이어야 합니다.')).toBeInTheDocument()
    })
  })

  it('희망 공모가 상단 음수 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/희망 공모가 상단/), '-1')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 이상이어야 합니다.')).toBeInTheDocument()
    })
  })

  it('확정 공모가 음수 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/확정 공모가/), '-1')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 이상이어야 합니다.')).toBeInTheDocument()
    })
  })

  it('공모 주식 수 음수 입력 시 에러 메시지 표시', async () => {
    await setupForm()
    await fill(screen.getByLabelText(/공모 주식 수/), '-1')
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('0 이상이어야 합니다.')).toBeInTheDocument()
    })
  })

  it('정상 제출 시 올바른 body로 POST (빈 선택값은 body에 없음)', async () => {
    mutationFnMock = vi.fn().mockResolvedValue({ id: 1 })
    await setupForm()
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(mutationFnMock).toHaveBeenCalledWith(
        expect.objectContaining({
          companyName: '테스트 회사',
          ipoName: '테스트 IPO',
          subscriptionStartDate: '2026-02-01',
          subscriptionEndDate: '2026-02-02',
          stockCode: undefined,
          roundLabel: undefined,
          lowerBandPrice: undefined,
          upperBandPrice: undefined,
          fixedPrice: undefined,
          listingDate: undefined,
          offeringShareCount: undefined,
        }),
        expect.any(Object),
      )
    })
  })

  it('403 에러 시 "관리자만 공모주를 등록할 수 있습니다." 표시', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new ApiError(403, 'Forbidden'))
    await setupForm()
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('관리자만 공모주를 등록할 수 있습니다.')).toBeInTheDocument()
    })
  })

  it('409 에러 시 서버 메시지 그대로 표시', async () => {
    mutationFnMock = vi
      .fn()
      .mockRejectedValue(new ApiError(409, '이미 등록된 IPO입니다.', '이미 등록된 IPO입니다.'))
    await setupForm()
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('이미 등록된 IPO입니다.')).toBeInTheDocument()
    })
  })

  it('400 에러 시 기본 메시지 표시', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new ApiError(400, 'Request failed: 400'))
    await setupForm()
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(
        screen.getByText('공모주를 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.'),
      ).toBeInTheDocument()
    })
  })

  it('네트워크 오류 시 "네트워크 연결을 확인해 주세요." 표시', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new Error('Failed to fetch'))
    await setupForm()
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(screen.getByText('네트워크 연결을 확인해 주세요.')).toBeInTheDocument()
    })
  })

  it('등록 성공 시 /ipo-calendar로 이동', async () => {
    mutationFnMock = vi.fn().mockResolvedValue({ id: 1 })
    await setupForm()
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/ipo-calendar')
    })
  })

  it('등록 실패 시 이동하지 않음', async () => {
    mutationFnMock = vi.fn().mockRejectedValue(new ApiError(400, 'Request failed: 400'))
    await setupForm()
    await userEvent.click(screen.getByRole('button', { name: /공모주 등록/i }))
    await waitFor(() => {
      expect(
        screen.getByText('공모주를 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.'),
      ).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
