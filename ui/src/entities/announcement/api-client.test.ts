import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ZodError } from 'zod'
import { apiFetch } from '@/shared/api/client.ts'
import { createAnnouncement, fetchPopupAnnouncements, updateAnnouncement } from './api-client.ts'
import type { Announcement } from './types.ts'

vi.mock('@/shared/api/client.ts', () => ({ apiFetch: vi.fn() }))

const mockApiFetch = vi.mocked(apiFetch)

const announcement: Announcement = {
  id: 1,
  title: '서비스 안내',
  content: '점검 안내입니다.',
  startAt: '2026-10-26T00:00:00',
  endAt: '2026-10-27T00:00:00',
  status: 'ACTIVE',
  createdAt: '2026-10-26T00:00:00',
  updatedAt: '2026-10-26T00:00:00',
}

describe('fetchPopupAnnouncements', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('204 응답(undefined)을 빈 목록으로 정규화하고 취소 신호를 전달한다', async () => {
    const controller = new AbortController()
    mockApiFetch.mockResolvedValue(undefined)

    await expect(fetchPopupAnnouncements(controller.signal)).resolves.toEqual([])

    expect(mockApiFetch).toHaveBeenCalledWith('/announcements/popup', {
      method: 'GET',
      signal: controller.signal,
    })
  })

  it('공지 목록 응답을 런타임 검증해 반환한다', async () => {
    mockApiFetch.mockResolvedValue([announcement])

    await expect(fetchPopupAnnouncements()).resolves.toEqual([announcement])
  })

  it('잘못된 공지 응답은 schema 오류로 거부한다', async () => {
    mockApiFetch.mockResolvedValue([{ id: 'invalid' }])

    await expect(fetchPopupAnnouncements()).rejects.toBeInstanceOf(ZodError)
  })
})

describe('announcement mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('POST/PATCH 응답을 announcement schema로 검증한다', async () => {
    const input = {
      title: announcement.title,
      content: announcement.content,
      startAt: announcement.startAt,
      endAt: announcement.endAt,
      status: announcement.status,
    }
    mockApiFetch.mockResolvedValue(announcement)

    await expect(createAnnouncement(input)).resolves.toEqual(announcement)
    expect(mockApiFetch).toHaveBeenLastCalledWith('/admin/announcements', {
      method: 'POST',
      body: JSON.stringify(input),
    })

    await expect(updateAnnouncement(announcement.id, input)).resolves.toEqual(announcement)
    expect(mockApiFetch).toHaveBeenLastCalledWith('/admin/announcements/1', {
      method: 'PATCH',
      body: JSON.stringify(input),
    })
  })

  it('불완전한 mutation 응답은 ZodError로 거부한다', async () => {
    mockApiFetch.mockResolvedValue({ id: announcement.id })

    await expect(
      createAnnouncement({
        title: announcement.title,
        content: announcement.content,
        startAt: announcement.startAt,
        endAt: announcement.endAt,
        status: announcement.status,
      }),
    ).rejects.toBeInstanceOf(ZodError)
  })
})
