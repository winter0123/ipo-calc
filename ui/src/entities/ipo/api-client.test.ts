import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchIpos, fetchIpo, registerIpo } from './api-client.ts'

const stub = (body: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))
const TS = '2026-01-01T00:00:00'

const IPO_MIN = {
  id: 1,
  companyName: 'A',
  ipoName: 'A IPO',
  stockCode: null,
  roundLabel: null,
  lowerBandPrice: null,
  upperBandPrice: null,
  fixedPrice: null,
  subscriptionStartDate: '2026-01-01',
  subscriptionEndDate: '2026-01-02',
  listingDate: null,
  offeringShareCount: null,
  status: 'ACTIVE',
  createdAt: TS,
  updatedAt: TS,
}

describe('ipo api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('DB nullable 컬럼이 모두 null이어도 통과한다', async () => {
    stub([IPO_MIN])
    await expect(fetchIpos()).resolves.toHaveLength(1)
  })

  it('필수 컬럼이 없으면 ZodError로 거부한다', async () => {
    stub([{ ...IPO_MIN, companyName: undefined }])
    await expect(fetchIpos()).rejects.toBeInstanceOf(ZodError)
  })
})

describe('fetchIpo', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('GET /ipos/7을 호출하고 응답을 반환한다', async () => {
    const ipo = { ...IPO_MIN, id: 7 }
    stub(ipo)
    await expect(fetchIpo(7)).resolves.toEqual(ipo)
  })

  it('필수 컬럼이 없으면 ZodError로 거부한다', async () => {
    stub({ ...IPO_MIN, companyName: undefined })
    await expect(fetchIpo(7)).rejects.toBeInstanceOf(ZodError)
  })
})

describe('registerIpo', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('POST /ipos를 호출하고 응답을 반환한다', async () => {
    const createdIpo = { ...IPO_MIN, id: 2 }
    stub(createdIpo)
    const req = {
      companyName: '테스트',
      ipoName: '테스트 IPO',
      subscriptionStartDate: '2026-02-01',
      subscriptionEndDate: '2026-02-02',
    }
    await expect(registerIpo(req)).resolves.toEqual(createdIpo)
  })
})
