import { describe, expect, it } from 'vitest'
import { ipoKeys } from './queryKeys.ts'

describe('ipoKeys', () => {
  it('유효하지 않은 상세 조회 key를 기존 namespace로 생성한다', () => {
    expect(ipoKeys.invalidDetail()).toEqual(['ipos', 'detail', 'invalid'])
  })

  it('동기화 상태 key를 기존 namespace로 생성한다', () => {
    expect(ipoKeys.syncStatus()).toEqual(['ipoSyncStatus'])
  })
})
