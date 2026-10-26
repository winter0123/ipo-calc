import { afterEach, describe, expect, it, vi } from 'vitest'
import { z, ZodError } from 'zod'
import { apiFetchParsed, apiGetParsed } from './parsed.ts'

describe('parsed API helpers', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('요청 옵션을 전달하고 응답을 schema로 파싱한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ enabled: true })))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      apiFetchParsed('/notifications/preferences', z.object({ enabled: z.boolean() }), {
        method: 'PUT',
      }),
    ).resolves.toEqual({ enabled: true })
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/notifications/preferences'),
      expect.objectContaining({ method: 'PUT' }),
    )
  })

  it('응답이 schema와 다르면 ZodError를 던진다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ enabled: 1 }))))

    await expect(
      apiFetchParsed('/notifications/preferences', z.object({ enabled: z.boolean() }), {
        method: 'PUT',
      }),
    ).rejects.toBeInstanceOf(ZodError)
  })

  it('GET helper가 GET 메서드와 AbortSignal을 전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ enabled: true })))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await apiGetParsed(
      '/notifications/preferences',
      z.object({ enabled: z.boolean() }),
      controller.signal,
    )

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/notifications/preferences'),
      expect.objectContaining({ method: 'GET', signal: controller.signal }),
    )
  })
})
