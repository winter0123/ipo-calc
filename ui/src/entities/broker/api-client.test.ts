import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { registerBrokerCredential, replaceBrokerCredential } from './api-client.ts'

const CREDENTIAL = {
  id: 3,
  securitiesAccountId: 7,
  apiProvider: 'KIS',
  connectionStatus: 'CONNECTED',
  accountNumber: '****1234',
  lastSyncedAt: null,
  supportedProvider: 'KIS',
  keyIssuedOn: '2026-01-01',
  keyExpiresOn: '2027-01-01',
}

describe('broker credential mutation 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('POST/PUT 응답을 credential schema로 검증하고 기존 요청을 보낸다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(CREDENTIAL))))
    vi.stubGlobal('fetch', fetchMock)

    const registration = { apiProvider: 'KIS', appKey: 'test-key', appSecret: 'test-secret' }
    await expect(registerBrokerCredential(7, registration)).resolves.toEqual(CREDENTIAL)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/securities-accounts\/7\/api-credential$/),
      expect.objectContaining({ method: 'POST', body: JSON.stringify(registration) }),
    )

    const replacement = { appKey: 'replacement-key', appSecret: 'replacement-secret' }
    await expect(replaceBrokerCredential(7, replacement)).resolves.toEqual(CREDENTIAL)
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/securities-accounts\/7\/api-credential$/),
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(replacement) }),
    )
  })

  it('불완전한 등록·교체 응답은 ZodError로 거부한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(new Response(JSON.stringify({ id: 3 })))),
    )

    await expect(
      registerBrokerCredential(7, { apiProvider: 'KIS', appKey: 'key', appSecret: 'secret' }),
    ).rejects.toBeInstanceOf(ZodError)
    await expect(
      replaceBrokerCredential(7, { appKey: 'key', appSecret: 'secret' }),
    ).rejects.toBeInstanceOf(ZodError)
  })
})
