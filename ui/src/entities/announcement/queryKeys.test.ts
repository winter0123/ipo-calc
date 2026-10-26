import { describe, expect, it } from 'vitest'
import { announcementKeys } from './queryKeys.ts'

describe('announcementKeys', () => {
  it('기존 공지사항 캐시 key 구조와 관리자 prefix를 보존한다', () => {
    expect(announcementKeys.list(2)).toEqual(['announcements', 2])
    expect(announcementKeys.detail(7)).toEqual(['announcement', 7])
    expect(announcementKeys.popup(11)).toEqual(['announcement-popup', 11])
    expect(announcementKeys.popup(undefined)).toEqual(['announcement-popup', undefined])
    expect(announcementKeys.adminList(3, 'ACTIVE')).toEqual(['admin-announcements', 3, 'ACTIVE'])
    expect(announcementKeys.adminAll).toEqual(['admin-announcements'])
  })
})
