import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './apiError'
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

  it('본문 message가 있는 404도 status를 보존한 ApiError로 throw한다', async () => {
    stub('{"message":"존재하지 않는 투자자입니다"}', 404)
    const error = await apiFetch('/x').catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 404,
      message: '존재하지 않는 투자자입니다',
      serverMessage: '존재하지 않는 투자자입니다',
    })
  })

  it('본문이 없는 오류는 기본 메시지이고 serverMessage는 비어 있다', async () => {
    stub('', 500)
    const error = (await apiFetch('/x').catch((e) => e)) as ApiError
    expect(error).toMatchObject({ status: 500, message: 'Request failed: 500' })
    expect(error.serverMessage).toBeUndefined()
  })

  it('401은 ApiError(401)', async () => {
    stub('', 401)
    await expect(apiFetch('/x')).rejects.toMatchObject({
      status: 401,
      message: 'Unauthorized: 401',
    })
  })
})
