import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchSecuritiesFirms } from './api-client.ts'

const stub = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))
const TS = '2026-01-01T00:00:00'

const FIRM = {
  id: 1,
  name: 'f',
  externalCode: null,
  businessRegistrationNumber: null,
  status: 'ACTIVE',
  createdAt: TS,
  updatedAt: TS,
}

describe('securities-firm api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('외부코드·사업자번호 null을 허용하고 필수 필드가 null이면 거부한다', async () => {
    stub([FIRM])
    await expect(fetchSecuritiesFirms()).resolves.toHaveLength(1)
    stub([{ ...FIRM, name: null }])
    await expect(fetchSecuritiesFirms()).rejects.toBeInstanceOf(ZodError)
  })
})
