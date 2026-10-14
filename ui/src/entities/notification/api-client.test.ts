import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { fetchPreferences, fetchVapidKey, removePushSubscription } from './api-client.ts'

const stub = (body: unknown) => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body)))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('notification api-client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('VAPID 키 응답을 파싱한다(비활성이면 publicKey null)', async () => {
    stub({ enabled: false, publicKey: null })
    await expect(fetchVapidKey()).resolves.toEqual({ enabled: false, publicKey: null })
  })

  it('설정 응답 형식이 다르면 ZodError', async () => {
    stub({ subscriptionDeadline: true })
    await expect(fetchPreferences()).rejects.toBeInstanceOf(ZodError)
  })

  it('구독 해제는 DELETE 본문에 endpoint를 담는다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(''))
    vi.stubGlobal('fetch', fetchMock)
    await removePushSubscription('https://fcm.googleapis.com/x')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/notifications/subscriptions'),
      expect.objectContaining({
        method: 'DELETE',
        body: JSON.stringify({ endpoint: 'https://fcm.googleapis.com/x' }),
      }),
    )
  })
})
