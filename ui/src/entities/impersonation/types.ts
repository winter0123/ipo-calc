import { z } from 'zod'

/** 임퍼소네이션 시작 응답. */
export const impersonationStartSchema = z.object({
  sessionId: z.string(),
  expiresAt: z.string().datetime(),
  readOnly: z.boolean(),
})
export type ImpersonationStart = z.infer<typeof impersonationStartSchema>

/** 임퍼소네이션 상태 응답. targetAccountId/expiresAt는 임퍼소네이션 중일 때만 존재. */
export const impersonationStatusSchema = z.object({
  impersonating: z.boolean(),
  targetAccountId: z.number().nullable(),
  expiresAt: z.string().datetime().nullable(),
})
export type ImpersonationStatus = z.infer<typeof impersonationStatusSchema>
