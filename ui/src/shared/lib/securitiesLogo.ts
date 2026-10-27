export const SECURITIES_LOGOS_BASE = '/images/securities-logos'

export const SECURITIES_FIRM_LOGOS: Record<string, string> = {
  // 씨드 데이터 증권사들 (V15 + V22 기준)
  '한국투자증권': 'koreainvestment.svg',
  '키움증권': 'kiwoom.svg',
  'NH투자증권': 'nh.svg',
  '미래에셋증권': 'miraeasset.svg',
  '삼성증권': 'samsung.svg',
  'KB증권': 'kb.svg',
  '신한투자증권': 'shinhan.svg',
  '하나증권': 'hana.svg',
  '대신증권': 'daishin.svg',
  '유안타증권': 'yuanta.svg',
  '한화투자증권': 'hanwha.svg',
  'SK투자증권': 'sk.svg',
  '메리츠증권': 'meritz.svg',
  '신영증권': 'shinyoung.svg',
  'LS증권': 'ls.svg',
  '교보증권': 'kyobo.svg',
  '현대차증권': 'hyundai.svg',
  'IBK투자증권': 'ibk.svg',
  'DB증권': 'db.svg',
  'iM증권': 'im.svg',
  '유진투자증권': 'eugene.svg',
  '부국증권': 'bookuk.svg',
  '상상인증권': 'sangsangin.svg',
  'BNK투자증권': 'bnk.svg',
  '다올투자증권': 'daol.svg',
  '케이프투자증권': 'cape.svg',
  '한양증권': 'hanyang.svg',
  '카카오페이증권': 'kakaopay.svg',
  '코리아에셋투자증권': 'koreaasset.svg',
  '토스증권': 'toss.svg',
  'KDB산업은행': 'kdb.svg',
  'DGB금융지주': 'dgb.svg',
  'HSBC': 'hsbc.svg',
  'KTB투자증권': 'ktb.svg',
  '이베스트투자증권': 'ebest.svg',
  '하이투자증권': 'hi.svg',
  '리딩투자증권': 'leading.svg',
  '한국포스증권': 'koreapost.svg',
}


export function getSecuritiesLogoUrl(firmName: string): string {
  return `${SECURITIES_LOGOS_BASE}/${SECURITIES_FIRM_LOGOS[firmName] ?? 'default.svg'}`
}

export function getSecuritiesLogoAlt(firmName: string): string {
  return `${firmName} 로고`
}