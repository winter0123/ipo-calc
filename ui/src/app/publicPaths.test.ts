import { describe, expect, it } from 'vitest'
import { isPublicPath } from './publicPaths.ts'

describe('isPublicPath', () => {
  it.each(['/', '/about', '/login', '/signup', '/ipo-calendar', '/ipos/7', '/ipos/7/'])(
    '%s는 공개',
    (path) => {
      expect(isPublicPath(path)).toBe(true)
    },
  )

  it.each(['/dashboard', '/investments', '/ipos', '/ipo-calendarx', '/admin/ipos'])(
    '%s는 비공개',
    (path) => {
      expect(isPublicPath(path)).toBe(false)
    },
  )
})
