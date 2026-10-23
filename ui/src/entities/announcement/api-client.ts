import { z } from 'zod'
import { apiFetch } from '@/shared/api/client.ts'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import { announcementPageSchema, announcementSchema, type AnnouncementInput } from './types.ts'

const listSchema = z.array(announcementSchema)

export const fetchAnnouncements = (page: number, signal?: AbortSignal) =>
  apiGetParsed(`/announcements?page=${page}&size=20`, announcementPageSchema, signal)

export async function fetchPopupAnnouncements(signal?: AbortSignal) {
  const result = await apiFetch<unknown>('/announcements/popup', { method: 'GET', signal })
  return result === undefined ? [] : listSchema.parse(result)
}

export const fetchAnnouncement = (id: number, signal?: AbortSignal) =>
  apiGetParsed(`/announcements/${id}`, announcementSchema, signal)

export const fetchAdminAnnouncements = (
  page: number,
  status?: 'ACTIVE' | 'CANCELLED',
  signal?: AbortSignal,
) => {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (status) params.set('status', status)
  return apiGetParsed(`/admin/announcements?${params.toString()}`, announcementPageSchema, signal)
}

export const createAnnouncement = (input: AnnouncementInput) =>
  apiFetch('/admin/announcements', { method: 'POST', body: JSON.stringify(input) })

export const updateAnnouncement = (id: number, input: AnnouncementInput) =>
  apiFetch(`/admin/announcements/${id}`, { method: 'PATCH', body: JSON.stringify(input) })

export const deleteAnnouncement = (id: number) =>
  apiFetch<void>(`/admin/announcements/${id}`, { method: 'DELETE' })

export const dismissAnnouncement = (id: number) =>
  apiFetch<void>(`/announcements/${id}/dismiss`, { method: 'POST' })
