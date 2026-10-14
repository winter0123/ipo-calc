import { useCallback, useEffect, useState } from 'react'
import { registerPushSubscription, removePushSubscription } from '@/entities/notification/index.ts'
import { detectPushSupport, urlBase64ToUint8Array } from './pushSupport.ts'

const SW_URL = '/sw.js'

function toPayload(sub: PushSubscription) {
  const json = sub.toJSON()
  return {
    endpoint: sub.endpoint,
    keys: { p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '' },
  }
}

/** 이 기기(브라우저)의 푸시 구독 상태와 켜기/끄기. publicKey 가 없으면(서버 비활성) 켤 수 없다. */
export function usePushDevice(publicKey: string | null | undefined) {
  const support = detectPushSupport()
  const [permission, setPermission] = useState<NotificationPermission>(
    support === 'supported' ? Notification.permission : 'default',
  )
  const [subscribed, setSubscribed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 이미 구독된 기기면 서버에 다시 알려 서버 쪽 기록과 맞춘다(만료 정리·계정 교체 복구)
  useEffect(() => {
    if (support !== 'supported' || !publicKey || permission !== 'granted') return
    let cancelled = false
    void navigator.serviceWorker
      .getRegistration(SW_URL)
      .then((reg) => reg?.pushManager.getSubscription())
      .then(async (sub) => {
        if (cancelled || !sub) return
        setSubscribed(true)
        await registerPushSubscription(toPayload(sub)).catch(() => undefined)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [support, publicKey, permission])

  const enable = useCallback(async () => {
    if (!publicKey) return
    setBusy(true)
    setError(null)
    try {
      const result = await Notification.requestPermission()
      setPermission(result)
      if (result !== 'granted') return
      const reg = await navigator.serviceWorker.register(SW_URL)
      await navigator.serviceWorker.ready
      const sub =
        (await reg.pushManager.getSubscription()) ??
        (await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(publicKey),
        }))
      await registerPushSubscription(toPayload(sub))
      setSubscribed(true)
    } catch {
      setError('알림을 켜지 못했습니다. 잠시 뒤 다시 시도해 주세요.')
    } finally {
      setBusy(false)
    }
  }, [publicKey])

  const disable = useCallback(async () => {
    setBusy(true)
    setError(null)
    try {
      const reg = await navigator.serviceWorker.getRegistration(SW_URL)
      const sub = await reg?.pushManager.getSubscription()
      if (sub) {
        await removePushSubscription(sub.endpoint).catch(() => undefined)
        await sub.unsubscribe()
      }
      setSubscribed(false)
    } catch {
      setError('알림을 끄지 못했습니다. 잠시 뒤 다시 시도해 주세요.')
    } finally {
      setBusy(false)
    }
  }, [])

  return { support, permission, subscribed, busy, error, enable, disable }
}
