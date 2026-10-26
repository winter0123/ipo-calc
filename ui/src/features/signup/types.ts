import { z } from 'zod'

export interface SignupRequest {
  email: string
  password: string
  name: string
  nickname: string
  relationship?: string
}

export const signupResponseSchema = z.object({
  accountId: z.number(),
  email: z.string(),
  name: z.string(),
  nickname: z.string(),
  approved: z.boolean(),
})

export type SignupResponse = z.infer<typeof signupResponseSchema>
