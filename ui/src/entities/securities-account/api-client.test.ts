import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchSecuritiesAccounts } from './api-client.ts'

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
})
