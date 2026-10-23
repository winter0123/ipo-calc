import { z } from 'zod'

export const adminAccountSchema = z.object({
  id: z.number(),
  email: z.string(),
  name: z.string().nullable(),
  nickname: z.string(),
  role: z.string(),
  approved: z.boolean(),
  createdAt: z.string(),
  locked: z.boolean().default(false),
})

export type AdminAccount = z.infer<typeof adminAccountSchema>
