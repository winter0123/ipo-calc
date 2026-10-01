import { z } from 'zod'

export const currentAccountSchema = z.object({
  accountId: z.number(),
  email: z.string(),
  name: z.string().nullable(),
  role: z.enum(['USER', 'ADMIN']),
})

export type CurrentAccount = z.infer<typeof currentAccountSchema>
