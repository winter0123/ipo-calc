import { useQuery } from '@tanstack/react-query'
import { fetchSecuritiesFirms } from './api-client.ts'
import { securitiesFirmKeys } from './queryKeys.ts'
import type { SecuritiesFirmResponse } from './types.ts'

// IPO 대표주관 실적이 전무한 증권사 (UI에서 숨김)
const NO_IPO_TRACK_RECORD = new Set([
  '카카오페이증권',
  '토스증권',
  '부국증권',
  '케이프투자증권',
  '한양증권',
  '코리아에셋투자증권',
  '다올투자증권',
  '리딩투자증권',
  'KDB산업은행',
  'DGB금융지주',
  'HSBC',
  'KTB투자증권',
  '한국포스증권',
])

// 정렬 우선순위가 낮은 증권사 (맨 아래로)
const LOW_PRIORITY_FIRMS = new Set([
  '부국증권',
  '케이프투자증권',
  '한양증권',
  '카카오페이증권',
  '코리아에셋투자증권',
  '토스증권',
  'KDB산업은행',
  'DGB금융지주',
  'HSBC',
  'KTB투자증권',
  '리딩투자증권',
  '한국포스증권',
])

const hasKorean = (name: string) => /[가-힣]/.test(name)

const sortFirms = (firms: SecuritiesFirmResponse[]): SecuritiesFirmResponse[] => {
  const normal = firms.filter((f) => !LOW_PRIORITY_FIRMS.has(f.name))
  const lowPriority = firms.filter((f) => LOW_PRIORITY_FIRMS.has(f.name))

  const sortByLang = (arr: SecuritiesFirmResponse[]) => {
    const korean = arr.filter((f) => hasKorean(f.name))
    const english = arr.filter((f) => !hasKorean(f.name))
    korean.sort((a, b) => a.name.localeCompare(b.name, 'ko'))
    english.sort((a, b) => a.name.localeCompare(b.name, 'en'))
    return [...korean, ...english]
  }

  return [...sortByLang(normal), ...sortByLang(lowPriority)]
}

export function useSecuritiesFirms() {
  const { data, ...rest } = useQuery<SecuritiesFirmResponse[]>({
    queryKey: securitiesFirmKeys.list(),
    queryFn: ({ signal }) => fetchSecuritiesFirms(signal),
  })

  const filtered = (data ?? []).filter((firm) => !NO_IPO_TRACK_RECORD.has(firm.name))

  return { ...rest, data: sortFirms(filtered) }
}
