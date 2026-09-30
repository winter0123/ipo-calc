/**
 * fetch 래퍼 — 모든 요청에 credentials: 'include'를 기본으로 포함합니다.
 * 401 응답은 에러를 그대로 throw하며, 라우터를 직접 참조하지 않습니다.
 * (FSD 순환 참조 방지 — 로그인 리다이렉트는 pages/features 레벨에서 처리)
 */

import { ApiError } from './apiError.ts'

const DEFAULT_BASE_URL =
  typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL
    ? import.meta.env.VITE_API_BASE_URL
    : 'http://localhost:8080'

export async function apiFetch<T = unknown>(path: string, options: RequestInit = {}): Promise<T> {
  const url = path.startsWith('http') ? path : `${DEFAULT_BASE_URL}${path}`

  const config: RequestInit = {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string> | undefined),
    },
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
