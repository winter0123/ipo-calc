/** 이 브라우저가 웹 푸시를 쓸 수 있는지와 그 이유를 판단한다. 화면 안내 문구를 고르는 기준이다. */
export type PushSupport = 'supported' | 'ios-needs-install' | 'unsupported'

interface Env {
  userAgent: string
  maxTouchPoints: number
  standalone: boolean
  hasServiceWorker: boolean
  hasPushManager: boolean
  hasNotification: boolean
}

export function readEnv(): Env {
  const nav = typeof navigator === 'undefined' ? undefined : navigator
  return {
    userAgent: nav?.userAgent ?? '',
    maxTouchPoints: nav?.maxTouchPoints ?? 0,
    standalone:
      (typeof window !== 'undefined' &&
        window.matchMedia?.('(display-mode: standalone)').matches) ||
      (nav as unknown as { standalone?: boolean } | undefined)?.standalone === true,
    hasServiceWorker: !!nav && 'serviceWorker' in nav,
    hasPushManager: typeof window !== 'undefined' && 'PushManager' in window,
    hasNotification: typeof window !== 'undefined' && 'Notification' in window,
  }
}

export function detectPushSupport(env: Env = readEnv()): PushSupport {
  if (env.hasServiceWorker && env.hasPushManager && env.hasNotification) return 'supported'
  // 아이폰·아이패드(iPadOS는 Mac처럼 보임)는 홈 화면에 추가한 앱에서만 푸시가 된다(iOS 16.4+)
  const isIos =
    /iPhone|iPad|iPod/.test(env.userAgent) ||
    (/Macintosh/.test(env.userAgent) && env.maxTouchPoints > 1)
  return isIos && !env.standalone ? 'ios-needs-install' : 'unsupported'
}

/** VAPID 공개키(Base64URL)를 pushManager.subscribe 가 받는 바이트로 바꾼다. */
export function urlBase64ToUint8Array(base64Url: string): Uint8Array<ArrayBuffer> {
  const padded = base64Url + '='.repeat((4 - (base64Url.length % 4)) % 4)
  const raw = atob(padded.replace(/-/g, '+').replace(/_/g, '/'))
  const out = new Uint8Array(new ArrayBuffer(raw.length))
  for (let i = 0; i < raw.length; i += 1) out[i] = raw.charCodeAt(i)
  return out
}
