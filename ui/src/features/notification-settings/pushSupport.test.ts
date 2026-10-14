import { describe, expect, it } from 'vitest'
import { detectPushSupport, urlBase64ToUint8Array } from './pushSupport.ts'

const base = {
  userAgent: 'Mozilla/5.0 Chrome',
  maxTouchPoints: 0,
  standalone: false,
  hasServiceWorker: true,
  hasPushManager: true,
  hasNotification: true,
}

describe('detectPushSupport', () => {
  it('필요한 API가 모두 있으면 supported', () => {
    expect(detectPushSupport(base)).toBe('supported')
  })

  it('API가 없는 일반 브라우저는 unsupported', () => {
    expect(detectPushSupport({ ...base, hasPushManager: false })).toBe('unsupported')
  })

  it('홈 화면에 추가하지 않은 아이폰 사파리는 ios-needs-install', () => {
    const iphone = {
      ...base,
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)',
      hasPushManager: false,
    }
    expect(detectPushSupport(iphone)).toBe('ios-needs-install')
  })

  it('아이패드(Mac으로 보이는 UA)도 터치가 있으면 아이폰과 같게 본다', () => {
    const ipad = {
      ...base,
      userAgent: 'Mozilla/5.0 (Macintosh)',
      maxTouchPoints: 5,
      hasPushManager: false,
    }
    expect(detectPushSupport(ipad)).toBe('ios-needs-install')
  })
})

describe('urlBase64ToUint8Array', () => {
  it('Base64URL을 바이트로 바꾼다', () => {
    expect(Array.from(urlBase64ToUint8Array('-_8'))).toEqual([251, 255])
  })
})
