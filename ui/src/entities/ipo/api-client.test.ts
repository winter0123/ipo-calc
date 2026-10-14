import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchIpos, fetchIpo, registerIpo, syncIposNow, getIpoSyncStatus } from './api-client.ts'

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

describe('syncIposNow', () => {
  afterEach(() => vi.unstubAllGlobals())

  const STATUS = {
    state: 'RUNNING',
    fromMonth: '2026-01',
    toMonth: '2026-10',
    monthsDone: 3,
    monthsTotal: 10,
    startedAt: TS,
    finishedAt: null,
    result: {
      created: 1,
      updated: 2,
      skipped: 3,
      failed: 0,
      scheduleMerged: 4,
      detailSaved: 5,
      aborted: false,
    },
    error: null,
  }

  it('POST /ipos/sync를 호출하고 결과를 반환한다', async () => {
    stub(STATUS)
    await expect(syncIposNow()).resolves.toEqual(STATUS)
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(String(url)).toMatch(/\/ipos\/sync$/)
    expect(init?.method).toBe('POST')
  })

  it('범위를 지정하여 POST /ipos/sync를 호출한다', async () => {
    stub(STATUS)
    await expect(syncIposNow({ fromMonth: '2025-01', toMonth: '2026-12' })).resolves.toEqual(STATUS)
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(String(url)).toMatch(/\/ipos\/sync$/)
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe(JSON.stringify({ fromMonth: '2025-01', toMonth: '2026-12' }))
  })

  it('본문이 없으면 기존 동작(최근 days-back)과 동일하게 동작한다', async () => {
    stub(STATUS)
    await expect(syncIposNow(undefined)).resolves.toEqual(STATUS)
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(String(url)).toMatch(/\/ipos\/sync$/)
    expect(init?.method).toBe('POST')
    expect(init?.body).toBeUndefined()
  })

  it('필드가 빠지면 ZodError로 거부한다', async () => {
    stub({ state: 'RUNNING' })
    await expect(syncIposNow()).rejects.toBeInstanceOf(ZodError)
  })
})

describe('getIpoSyncStatus', () => {
  afterEach(() => vi.unstubAllGlobals())

  const STATUS = {
    state: 'IDLE',
    fromMonth: null,
    toMonth: null,
    monthsDone: 0,
    monthsTotal: 0,
    startedAt: null,
    finishedAt: null,
    result: {
      created: 0,
      updated: 0,
      skipped: 0,
      failed: 0,
      scheduleMerged: 0,
      detailSaved: 0,
      aborted: false,
    },
    error: null,
  }

  it('GET /ipos/sync/status를 호출하고 결과를 반환한다', async () => {
    stub(STATUS)
    await expect(getIpoSyncStatus()).resolves.toEqual(STATUS)
    const [url] = vi.mocked(fetch).mock.calls[0]
    expect(String(url)).toMatch(/\/ipos\/sync\/status$/)
  })

  it('필드가 빠지면 ZodError로 거부한다', async () => {
    stub({ state: 'IDLE' })
    await expect(getIpoSyncStatus()).rejects.toBeInstanceOf(ZodError)
  })
})
