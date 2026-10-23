import { z } from 'zod'

export const announcementSchema = z.object({
  id: z.number(),
  title: z.string(),
  content: z.string(),
  startAt: z.string(),
  endAt: z.string(),
  status: z.enum(['ACTIVE', 'INACTIVE', 'CANCELLED']),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const announcementPageSchema = z.object({
  content: z.array(announcementSchema),
  totalPages: z.number(),
  number: z.number(),
})

export type Announcement = z.infer<typeof announcementSchema>
export type AnnouncementInput = Pick<
  Announcement,
  'title' | 'content' | 'startAt' | 'endAt' | 'status'
>
export type AnnouncementPage = z.infer<typeof announcementPageSchema>
