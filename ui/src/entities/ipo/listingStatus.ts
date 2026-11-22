import type { Ipo } from './types.ts'
import { dateOnly, toSeoulYmd } from '@/shared/lib/date.ts'

export type ListingDisplayKind = 'unknown' | 'estimated' | 'estimated-overdue' | 'confirmed'

export interface ListingDisplay {
  kind: ListingDisplayKind
  label: string
  /** 달력에서 점선으로 그릴지 여부 */
  dashed: boolean
  /** 거래소 확정(실선) 여부 */
  confirmed: boolean
}

/**
 * DART 상장예정/거래소 확정 증거가 있을 때만 공통 표시 상태를 돌려준다.
 * 증거가 없는 기존 데이터(null)는 null을 돌려주어 화면별 기존 날짜 기반 단계·필터를 유지한다.
 * 날짜 비교는 기기 시간대와 무관하게 한국 시간 기준이다.
 */
export function getListingDisplay(
  ipo: Pick<Ipo, 'listingDate' | 'listingDateEvidenceType'>,
  today: Date = new Date(),
): ListingDisplay | null {
  const evidence = ipo.listingDateEvidenceType
  if (evidence !== 'ESTIMATED_DART' && evidence !== 'CONFIRMED_KRX') return null

  const listingDate = dateOnly(ipo.listingDate)
  if (!listingDate) {
    return { kind: 'unknown', label: '상장일 미정', dashed: false, confirmed: false }
  }

  if (evidence === 'CONFIRMED_KRX') {
    return { kind: 'confirmed', label: '상장 확정', dashed: false, confirmed: true }
  }

  if (listingDate < toSeoulYmd(today)) {
    return {
      kind: 'estimated-overdue',
      label: '상장예정일 경과 · 거래소 미확인',
      dashed: true,
      confirmed: false,
    }
  }

  return { kind: 'estimated', label: '상장 예정', dashed: true, confirmed: false }
}
