import { apiFetchParsed } from '@/shared/api/parsed.ts'
import { signupResponseSchema } from './types.ts'
import type { SignupRequest, SignupResponse } from './types.ts'

export function registerAccount(data: SignupRequest): Promise<SignupResponse> {
  return apiFetchParsed('/accounts', signupResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
