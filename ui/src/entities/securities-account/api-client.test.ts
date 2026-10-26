import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import {
  fetchSecuritiesAccounts,
  registerSecuritiesAccount,
  revealSecuritiesAccountNumber,
  setDefaultSecuritiesAccount,
  updateSecuritiesAccount,
} from './api-client.ts'

const stub = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))
const TS = '2026-01-01T00:00:00'

const ACCOUNT = {
  id: 1,
  investorId: 1,
  securitiesFirmId: 1,
  securitiesFirmName: null,
  accountNumber: '***',
  accountName: null,
  isDefault: false,
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
}

describe('securities-account api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('증권사명·계좌명 null을 허용하고 타입이 어긋나면 거부한다', async () => {
    stub([ACCOUNT])
    await expect(fetchSecuritiesAccounts(1)).resolves.toHaveLength(1)
    stub([{ ...ACCOUNT, isDefault: 'no' }])
    await expect(fetchSecuritiesAccounts(1)).rejects.toBeInstanceOf(ZodError)
  })

  it('평문 계좌번호 공개 응답의 전체 DTO를 검증한다', async () => {
    const revealed = {
      id: 1,
      accountNumber: '1234567890',
      exposedAt: '2026-10-26T00:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(revealed)))
    vi.stubGlobal('fetch', fetchMock)

    await expect(revealSecuritiesAccountNumber(1)).resolves.toEqual(revealed)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/securities-accounts\/1\/account-number$/),
      expect.objectContaining({ method: 'GET' }),
    )

    stub({ accountNumber: '1234567890' })
    await expect(revealSecuritiesAccountNumber(1)).rejects.toBeInstanceOf(ZodError)
  })

  it('등록 응답을 securities-account schema로 검증한다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(ACCOUNT))))
    vi.stubGlobal('fetch', fetchMock)
    const request = {
      investorId: 1,
      securitiesFirmId: 1,
      accountNumber: '1234567890',
    }

    await expect(registerSecuritiesAccount(request)).resolves.toEqual(ACCOUNT)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/securities-accounts$/),
      expect.objectContaining({ method: 'POST', body: JSON.stringify(request) }),
    )

    stub({ ...ACCOUNT, securitiesFirmId: 'invalid' })
    await expect(registerSecuritiesAccount(request)).rejects.toBeInstanceOf(ZodError)
  })

  it('계좌 수정과 기본 계좌 지정 응답을 schema로 검증한다', async () => {
    const updated = { ...ACCOUNT, accountName: '새 계좌명' }
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(updated))))
    vi.stubGlobal('fetch', fetchMock)

    await expect(updateSecuritiesAccount(1, { accountName: '새 계좌명' })).resolves.toEqual(updated)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/securities-accounts\/1$/),
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ accountName: '새 계좌명' }),
      }),
    )

    await expect(setDefaultSecuritiesAccount(1)).resolves.toEqual(updated)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/securities-accounts\/1\/set-default$/),
      expect.objectContaining({ method: 'PATCH' }),
    )

    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(new Response(JSON.stringify({ id: 1 })))),
    )
    await expect(setDefaultSecuritiesAccount(1)).rejects.toBeInstanceOf(ZodError)
  })
})
