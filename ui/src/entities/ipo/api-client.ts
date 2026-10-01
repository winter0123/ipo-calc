import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { ipoSchema } from './types.ts'
import type { Ipo } from './types.ts'

export async function fetchIpos(signal?: AbortSignal): Promise<Ipo[]> {
  return apiGetParsed('/ipos', z.array(ipoSchema), signal)
}
