import type { z } from 'zod'
import { apiFetch } from './client.ts'

// 응답이 계약과 다르면 ZodError로 실패해 TanStack Query의 error 상태로 흐른다(UI는 일반 오류 문구).
export async function apiFetchParsed<T>(
  path: string,
  schema: z.ZodType<T>,
  options: RequestInit = {},
): Promise<T> {
  return schema.parse(await apiFetch<unknown>(path, options))
}

export async function apiGetParsed<T>(
  path: string,
  schema: z.ZodType<T>,
  signal?: AbortSignal,
): Promise<T> {
  return apiFetchParsed(path, schema, { method: 'GET', signal })
}
