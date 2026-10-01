import { apiGetParsed } from '@/shared/api/parsed.ts'
import { currentAccountSchema, type CurrentAccount } from './types.ts'

export function fetchCurrentAccount(signal?: AbortSignal): Promise<CurrentAccount> {
  return apiGetParsed('/auth/me', currentAccountSchema, signal)
}
