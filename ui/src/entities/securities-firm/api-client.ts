import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { securitiesFirmResponseSchema } from './types.ts'
import type { SecuritiesFirmResponse } from './types.ts'

export async function fetchSecuritiesFirms(
  signal?: AbortSignal,
): Promise<SecuritiesFirmResponse[]> {
  return apiGetParsed('/securities-firms', z.array(securitiesFirmResponseSchema), signal)
}
