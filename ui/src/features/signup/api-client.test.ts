import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { registerAccount } from './api-client.ts'

const RESPONSE = {
  accountId: 1,
  email: 'new@example.com',
  name: '새 사용자',
  nickname: '새 별명',
  approved: false,
}

describe('signup api-client 응답 검증', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('회원가입 응답을 DTO schema로 검증한다', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(RESPONSE))))
    vi.stubGlobal('fetch', fetchMock)
    const request = {
      email: RESPONSE.email,
      password: 'test-password',
      name: RESPONSE.name,
      nickname: RESPONSE.nickname,
    }

    await expect(registerAccount(request)).resolves.toEqual(RESPONSE)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/accounts$/),
      expect.objectContaining({ method: 'POST', body: JSON.stringify(request) }),
    )

    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(new Response(JSON.stringify({ ...RESPONSE, approved: 'no' })))),
    )
    await expect(registerAccount(request)).rejects.toBeInstanceOf(ZodError)
  })
})
