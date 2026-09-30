import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from './client'

describe('apiFetch', () => {
  afterEach(() => vi.unstubAllGlobals())

  const stub = (body: string, status = 200) =>
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body || null, { status })))

  it('본문 없는 200은 undefined를 반환한다', async () => {
    stub('')
    await expect(apiFetch('/auth/login')).resolves.toBeUndefined()
  })

  it('204는 undefined를 반환한다', async () => {
    stub('', 204)
    await expect(apiFetch('/x')).resolves.toBeUndefined()
  })

  it('JSON 본문은 파싱해 반환한다', async () => {
    stub('{"a":1}')
    await expect(apiFetch('/x')).resolves.toEqual({ a: 1 })
  })
})
