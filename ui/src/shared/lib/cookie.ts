/** 쿠키 값을 읽는다. 브라우저/jsdom에서만 동작하며, 그 외 환경에서는 빈 문자열을 반환한다. */
export function readCookie(name: string): string {
  if (typeof document === 'undefined' || !document.cookie) return ''
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`))
  return match ? match[1] : ''
}

/** 쿠키를 설정한다(경로 기본 /). */
export function setCookie(
  name: string,
  value: string,
  options: { path?: string; maxAge?: number } = {},
): void {
  if (typeof document === 'undefined') return
  const { path = '/', maxAge } = options
  let cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value)}; Path=${path}`
  if (maxAge != null) cookie += `; Max-Age=${maxAge}`
  document.cookie = cookie
}

/** 쿠키를 제거한다. */
export function removeCookie(name: string, options: { path?: string } = {}): void {
  if (typeof document === 'undefined') return
  const { path = '/' } = options
  document.cookie = `${encodeURIComponent(name)}=; Path=${path}; Expires=Thu, 01 Jan 1970 00:00:00 GMT`
}
