import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import {
  fetchSecuritiesAccounts,
  registerSecuritiesAccount,
  revealSecuritiesAccountNumber,
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
  status: 'ACTIVE',
  deletedAt: null,
  createdAt: TS,
  updatedAt: TS,
}

const ACCOUNT_NO_NUMBER = {
  ...ACCOUNT,
  accountNumber: null,
}

describe('securities-account api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('증권사명·계좌명 null을 허용하고 타입이 어긋나면 거부한다', async () => {
    stub([ACCOUNT])
    await expect(fetchSecuritiesAccounts(1)).resolves.toHaveLength(1)
    stub([{ ...ACCOUNT, securitiesFirmId: 'invalid' }])
    await expect(fetchSecuritiesAccounts(1)).rejects.toBeInstanceOf(ZodError)
  })

  it('계좌번호 null을 키와 함께 허용한다', async () => {
    stub([ACCOUNT_NO_NUMBER])
    const result = await fetchSecuritiesAccounts(1)
    expect(result).toHaveLength(1)
    expect(result[0].accountNumber).toBeNull()
    expect(result[0]).toHaveProperty('accountNumber')
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

  it('계좌번호를 미제공해도 등록 요청을 보낸다(번호 미입력 생성)', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(ACCOUNT_NO_NUMBER))))
    vi.stubGlobal('fetch', fetchMock)
    const request = {
      investorId: 1,
      securitiesFirmId: 1,
    }

    await expect(registerSecuritiesAccount(request)).resolves.toEqual(ACCOUNT_NO_NUMBER)
    const calls = fetchMock.mock.calls as unknown as Array<[string, RequestInit | undefined]>
    const postCalls = calls.filter(([path]) => String(path).includes('/securities-accounts'))
    expect(postCalls.length).toBeGreaterThan(0)
    const init = postCalls[0]?.[1]
    const body =
      init && typeof init === 'object' && 'body' in init
        ? (init as { body?: string }).body
        : undefined
    expect(body).toBeDefined()
    const parsed = JSON.parse(body as string)
    expect(parsed).not.toHaveProperty('accountNumber')
    expect(parsed.investorId).toBe(1)
    expect(parsed.securitiesFirmId).toBe(1)
  })

  it('계좌번호가 undefined면 등록 요청에서 제외한다(폼에서 빈값을 undefined로 변환해 전달)', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(ACCOUNT_NO_NUMBER))))
    vi.stubGlobal('fetch', fetchMock)
    const request = {
      investorId: 1,
      securitiesFirmId: 1,
      accountNumber: undefined,
    }

    await expect(registerSecuritiesAccount(request)).resolves.toEqual(ACCOUNT_NO_NUMBER)
    const calls = fetchMock.mock.calls as unknown as Array<[string, RequestInit | undefined]>
    const postCalls = calls.filter(([path]) => String(path).includes('/securities-accounts'))
    expect(postCalls.length).toBeGreaterThan(0)
    const init = postCalls[0]?.[1]
    const body =
      init && typeof init === 'object' && 'body' in init
        ? (init as { body?: string }).body
        : undefined
    expect(body).toBeDefined()
    const parsed = JSON.parse(body as string)
    expect(parsed).not.toHaveProperty('accountNumber')
  })

  it('계좌 수정 응답을 schema로 검증한다', async () => {
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
  })

  it('계좌번호만 전달해 수정 요청을 보낸다(사후 입력)', async () => {
    const updated = { ...ACCOUNT_NO_NUMBER, accountNumber: '1234567890' }
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(updated))))
    vi.stubGlobal('fetch', fetchMock)

    await expect(updateSecuritiesAccount(1, { accountNumber: '1234567890' })).resolves.toEqual(
      updated,
    )
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/securities-accounts\/1$/),
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ accountNumber: '1234567890' }),
      }),
    )
  })

  it('계좌번호·계좌명을 모두 전달해 수정 요청을 보낸다', async () => {
    const updated = { ...ACCOUNT, accountName: '새 계좌명', accountNumber: '1234567890' }
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(updated))))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      updateSecuritiesAccount(1, { accountName: '새 계좌명', accountNumber: '1234567890' }),
    ).resolves.toEqual(updated)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/securities-accounts\/1$/),
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ accountName: '새 계좌명', accountNumber: '1234567890' }),
      }),
    )
  })
})
