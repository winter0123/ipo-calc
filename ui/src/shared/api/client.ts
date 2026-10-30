/**
 * fetch 래퍼 — 모든 요청에 credentials: 'include'를 기본으로 포함합니다.
 * 401 응답은 ApiError(401)를 그대로 throw하며, 라우터를 직접 참조하지 않습니다.
 * (FSD 순환 참조 방지 — 로그인 리다이렉트는 app/AppProviders의 쿼리 캐시 onError에서 처리)
 *
 * CSRF(더블 서브미트 쿠키):
 *  - Spring Security CookieCsrfTokenRepository가 응답에 XSRF-TOKEN 쿠키를 설정한다.
 *  - 읽기 전용 메서드에는 토큰 헤더를 붙이지 않는다.
 *  - 안전하지 않은 메서드에는 문서 쿠키의 XSRF-TOKEN 값을 X-XSRF-TOKEN 헤더로 보낸다.
 *  - 토큰이 없으면 unsafe 요청 직전에 public GET /auth/csrf로 먼저 부트스트랩하고,
 *    동시 요청 중복 방지를 위해 부트스트랩 프로미스를 재사용한다.
 *  - 403은 자동으로 변경을 재시도하지 않는다(서버가 실제 차단한다).
 */

import { readCookie } from '@/shared/lib/cookie.ts'
import { ApiError } from './apiError.ts'

const DEFAULT_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || (import.meta.env.PROD ? '/api' : 'http://localhost:8080')

const XSRF_TOKEN_NAME = 'XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

let csrfBootstrapPromise: Promise<void> | null = null

function resolveBase(path: string): string {
  return path.startsWith('http') ? path : `${DEFAULT_BASE_URL}${path}`
}

function hasCsrfToken(): boolean {
  return readCookie(XSRF_TOKEN_NAME).length > 0
}

/**
 * CSRF 토큰 쿠키를 갱신하려 서버에 부트스트랩 요청을 보낸다(apiFetch를 타지 않아 재호출 재귀를 막는다).
 * 동시 호출은 같은 프로미스를 재사용해 중복 부트스트랩을 방지한다.
 */
export async function csrfBootstrap(): Promise<void> {
  if (csrfBootstrapPromise) return csrfBootstrapPromise
  csrfBootstrapPromise = (async () => {
    try {
      await fetch(resolveBase('/auth/csrf'), { credentials: 'include', method: 'GET' })
    } finally {
      csrfBootstrapPromise = null
    }
  })()
  return csrfBootstrapPromise
}

export function buildRequestHeaders(
  options: RequestInit,
  isUnsafe: boolean,
): Record<string, string> {
  const headers: Record<string, string> = {}
  if (options.headers) {
    if (options.headers instanceof Headers) {
      options.headers.forEach((value, key) => {
        headers[key] = value
      })
    } else {
      for (const [key, value] of Object.entries(options.headers as Record<string, string>)) {
        headers[key] = String(value)
      }
    }
  }
  headers['Content-Type'] = 'application/json'
  if (isUnsafe) {
    const token = readCookie(XSRF_TOKEN_NAME)
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  return headers
}

export async function apiFetch<T = unknown>(path: string, options: RequestInit = {}): Promise<T> {
  const url = resolveBase(path)
  const method = (options.method ?? 'GET').toUpperCase()
  const isUnsafe = !SAFE_METHODS.has(method)

  // 안전하지 않은 메서드에 CSRF 토큰 쿠키가 없으면 먼저 부트스트랩한다.
  // 부트스트랩 응답에서 설정된 쿠키는 바로 다음 readCookie로 읽어 헤더에 붙인다.
  if (isUnsafe && !hasCsrfToken()) {
    await csrfBootstrap()
  }

  const config: RequestInit = {
    ...options,
    credentials: 'include',
    headers: buildRequestHeaders(options, isUnsafe),
  }

  const response = await fetch(url, config)

  if (response.status === 401) {
    throw new ApiError(401, `Unauthorized: ${response.status}`)
  }

  if (!response.ok) {
    let serverMessage: string | undefined
    try {
      const errorBody = await response.json()
      if (errorBody && typeof errorBody === 'object' && 'message' in errorBody) {
        serverMessage = String(errorBody.message)
      }
    } catch {
      // 응답이 JSON이 아니거나 파싱 실패 시 기본 메시지 유지
    }
    throw new ApiError(
      response.status,
      serverMessage ?? `Request failed: ${response.status}`,
      serverMessage,
    )
  }

  // 204 또는 본문 없는 200(예: POST /auth/login)은 undefined로 처리
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
