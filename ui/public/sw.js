// 웹 푸시 서비스 워커. 서버가 보낸 JSON {title, body, url, tag}을 알림으로 보여 주고, 누르면 해당 화면을 연다.
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { body: event.data ? event.data.text() : '' }
  }
  const title = data.title || '투자 관리 시스템'
  event.waitUntil(
    self.registration
      .showNotification(title, {
        body: data.body || '',
        icon: '/icon-192.png',
        badge: '/icon-192.png',
        tag: data.tag || undefined,
        data: { url: data.url || '/dashboard' },
      })
      // 열려 있는 화면에도 알려 준다(수신 확인·화면 갱신용)
      .then(() => self.clients.matchAll({ type: 'window', includeUncontrolled: true }))
      .then((clients) => clients.forEach((c) => c.postMessage({ type: 'push', title }))),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const raw = (event.notification.data && event.notification.data.url) || '/dashboard'
  // 같은 사이트 안 경로만 연다
  const target = new URL(raw.startsWith('/') ? raw : '/dashboard', self.location.origin).href
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (clients) => {
      const open = clients.find((c) => new URL(c.url).origin === self.location.origin)
      if (open) {
        await open.focus()
        if ('navigate' in open) await open.navigate(target).catch(() => undefined)
        return undefined
      }
      return self.clients.openWindow(target)
    }),
  )
})
