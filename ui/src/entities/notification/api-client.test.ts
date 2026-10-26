import { afterEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import {
  fetchPreferences,
  fetchVapidKey,
  registerPushSubscription,
  removePushSubscription,
} from './api-client.ts'

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

  it('구독 등록·해제 성공 응답을 검증하고 기존 void 반환을 유지한다', async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(new Response(JSON.stringify({ message: '완료' }))),
    )
    vi.stubGlobal('fetch', fetchMock)

    const subscription = {
      endpoint: 'https://fcm.googleapis.com/x',
      keys: { p256dh: 'public-key', auth: 'auth-key' },
    }
    await expect(registerPushSubscription(subscription)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringContaining('/notifications/subscriptions'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify(subscription) }),
    )

    await expect(removePushSubscription(subscription.endpoint)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/notifications/subscriptions'),
      expect.objectContaining({
        method: 'DELETE',
        body: JSON.stringify({ endpoint: 'https://fcm.googleapis.com/x' }),
      }),
    )
  })

  it('구독 mutation 응답에 message가 없으면 ZodError로 거부한다', async () => {
    stub({})
    await expect(removePushSubscription('https://fcm.googleapis.com/x')).rejects.toBeInstanceOf(
      ZodError,
    )
  })
})
