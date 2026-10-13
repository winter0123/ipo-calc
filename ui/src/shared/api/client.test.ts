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

  it('JSON이 아닌 오류 본문이면 기본 메시지를 쓴다', async () => {
    stub('<html>Bad Gateway</html>', 502)
    const error = (await apiFetch('/x').catch((e) => e)) as ApiError
    expect(error).toMatchObject({ status: 502, message: 'Request failed: 502' })
    expect(error.serverMessage).toBeUndefined()
  })

  it('message 필드가 없는 JSON 오류 본문이면 기본 메시지를 쓴다', async () => {
    stub('{"error":"x"}', 400)
    const error = (await apiFetch('/x').catch((e) => e)) as ApiError
    expect(error).toMatchObject({ status: 400, message: 'Request failed: 400' })
    expect(error.serverMessage).toBeUndefined()
  })

  it('JSON null 오류 본문도 기본 메시지로 처리한다', async () => {
    stub('null', 500)
    await expect(apiFetch('/x')).rejects.toMatchObject({ message: 'Request failed: 500' })
  })

  it('절대 URL은 그대로 쓰고, 쿠키를 포함하며 헤더를 병합한다', async () => {
    stub('{}')
    await apiFetch('https://other.test/y', { method: 'POST', headers: { 'X-A': '1' } })
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('https://other.test/y')
    expect(init).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-A': '1' },
    })
  })

  it('상대 경로에는 기본 base URL을 붙인다', async () => {
    stub('{}')
    await apiFetch('/z')
    expect(String(vi.mocked(fetch).mock.calls[0][0])).toMatch(/^https?:\/\/.+\/z$/)
  })

  it('네트워크 실패(fetch reject)는 그대로 전파된다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(apiFetch('/x')).rejects.toBeInstanceOf(TypeError)
  })
})
