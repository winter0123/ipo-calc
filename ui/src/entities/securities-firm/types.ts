import { z } from 'zod'

export const securitiesFirmResponseSchema = z.object({
  id: z.number(),
  name: z.string(),
  externalCode: z.string().nullable(),
  businessRegistrationNumber: z.string().nullable(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type SecuritiesFirmResponse = z.infer<typeof securitiesFirmResponseSchema>
