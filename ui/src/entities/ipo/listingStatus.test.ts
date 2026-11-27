import { describe, expect, it } from 'vitest'
import type { Ipo } from './types.ts'
import { getListingDisplay } from './listingStatus.ts'

const ipo = (
  listingDate: string | null,
  listingDateEvidenceType: Ipo['listingDateEvidenceType'],
): Pick<Ipo, 'listingDate' | 'listingDateEvidenceType'> => ({
  listingDate,
  listingDateEvidenceType,
})

describe('getListingDisplay', () => {
  it('출처가 없는 기존 데이터는 null을 돌려주어 날짜 기반 단계를 유지한다', () => {
    expect(getListingDisplay(ipo('2026-10-15T09:00:00Z', null), new Date('2026-10-10'))).toBeNull()
    expect(
      getListingDisplay(ipo('2026-10-15T09:00:00Z', undefined), new Date('2026-10-10')),
    ).toBeNull()
  })

  it('증거가 있어도 상장일이 없으면 상장일 미정이다', () => {
    expect(getListingDisplay(ipo(null, 'ESTIMATED_DART'), new Date('2026-10-10'))).toEqual({
      kind: 'unknown',
      label: '상장일 미정',
      dashed: false,
      confirmed: false,
    })
  })

  it('미래 DART 예정일은 상장 예정과 점선이다', () => {
    expect(
      getListingDisplay(ipo('2026-10-15T09:00:00Z', 'ESTIMATED_DART'), new Date('2026-10-10')),
    ).toEqual({
      kind: 'estimated',
      label: '상장 예정',
      dashed: true,
      confirmed: false,
    })
  })

  it('오늘 DART 예정일은 예정일 상태를 유지한다', () => {
    expect(
      getListingDisplay(ipo('2026-10-10T09:00:00Z', 'ESTIMATED_DART'), new Date('2026-10-10')),
    ).toEqual({
      kind: 'estimated',
      label: '상장 예정',
      dashed: true,
      confirmed: false,
    })
  })

  it('과거 DART 예정일은 상장 완료가 아니라 거래소 미확인 상태다', () => {
    expect(
      getListingDisplay(ipo('2026-10-05T09:00:00Z', 'ESTIMATED_DART'), new Date('2026-10-10')),
    ).toEqual({
      kind: 'estimated-overdue',
      label: '상장예정일 경과 · 거래소 미확인',
      dashed: true,
      confirmed: false,
    })
  })

  it('미래 공식 공고는 거래소 상장 예정이고 지난 날짜는 확정 상태다', () => {
    expect(
      getListingDisplay(ipo('2026-10-15T09:00:00Z', 'CONFIRMED_KRX'), new Date('2026-10-10')),
    ).toEqual({
      kind: 'confirmed-upcoming',
      label: '상장 예정 · 거래소 공고',
      dashed: false,
      confirmed: true,
    })
    expect(
      getListingDisplay(ipo('2026-10-05T09:00:00Z', 'CONFIRMED_KRX'), new Date('2026-10-10')),
    ).toMatchObject({ kind: 'confirmed', label: '상장 확정', dashed: false, confirmed: true })
  })

  it('날짜 판정은 기기 시간대와 무관하게 한국 시간 기준이다', () => {
    const target = ipo('2026-11-10', 'ESTIMATED_DART')
    // 한국 2026-11-09 23:59:59 → 아직 예정
    expect(getListingDisplay(target, new Date('2026-11-09T14:59:59Z'))?.kind).toBe('estimated')
    // 한국 2026-11-10 00:00:00 → 예정일 당일
    expect(getListingDisplay(target, new Date('2026-11-09T15:00:00Z'))?.kind).toBe('estimated')
    // 한국 2026-11-11 00:00:00 → 예정일 경과
    expect(getListingDisplay(target, new Date('2026-11-10T15:00:00Z'))?.kind).toBe(
      'estimated-overdue',
    )
  })
})
