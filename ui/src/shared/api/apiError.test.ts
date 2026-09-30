import { describe, expect, it } from 'vitest'
import { ApiError, NETWORK_ERROR_MESSAGE, toErrorMessage } from './apiError'

describe('toErrorMessage', () => {
  it('에러가 없으면 null', () => {
    expect(toErrorMessage(null)).toBeNull()
  })

  it('ApiError가 아니면(fetch 실패 등) 네트워크 메시지', () => {
    expect(toErrorMessage(new TypeError('Failed to fetch'))).toBe(NETWORK_ERROR_MESSAGE)
    expect(toErrorMessage(new TypeError('x'), { fallback: '실패' })).toBe(NETWORK_ERROR_MESSAGE)
  })

  it('상태 코드로 매핑하며 서버 메시지 문구와 무관하다', () => {
    const error = new ApiError(404, '존재하지 않는 대상입니다', '존재하지 않는 대상입니다')
    expect(toErrorMessage(error, { byStatus: { 404: '찾을 수 없습니다.' } })).toBe(
      '찾을 수 없습니다.',
    )
  })

  it('byStatus 함수는 ApiError를 받고 undefined면 기본 규칙으로 넘어간다', () => {
    const rule = (e: ApiError) => (e.serverMessage?.includes('투자자') ? '투자자 없음' : undefined)
    expect(
      toErrorMessage(new ApiError(400, '투자자 x', '투자자 x'), { byStatus: { 400: rule } }),
    ).toBe('투자자 없음')
    expect(toErrorMessage(new ApiError(400, '그 외', '그 외'), { byStatus: { 400: rule } })).toBe(
      '그 외',
    )
  })

  it('400은 서버 메시지가 있으면 그것, 없으면 기본 문구', () => {
    expect(toErrorMessage(new ApiError(400, '이름이 짧습니다', '이름이 짧습니다'))).toBe(
      '이름이 짧습니다',
    )
    expect(toErrorMessage(new ApiError(400, 'Request failed: 400'))).toBe('입력값을 확인해 주세요.')
  })

  it('fallback이 있으면 매핑되지 않은 상태는 fallback', () => {
    expect(toErrorMessage(new ApiError(500, 'Request failed: 500'), { fallback: '실패' })).toBe(
      '실패',
    )
  })

  it('fallback이 없으면 매핑되지 않은 상태는 error.message', () => {
    expect(toErrorMessage(new ApiError(500, 'Request failed: 500'))).toBe('Request failed: 500')
  })
})
