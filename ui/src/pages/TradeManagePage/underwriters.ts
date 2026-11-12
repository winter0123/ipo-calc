import type { IpoInsight } from '@/entities/ipo/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import { formatSecuritiesFirmName } from '@/shared/lib/formatSecuritiesFirmName.ts'

// 주관사 이름과 계좌 증권사 이름 사이에 확정된 ID 연결이 없어 공백만 정규화해 정확 일치한다(기존 청약 등록 폼과 같은 방식).
export function normalizeName(name: string | null | undefined): string {
  return (name ?? '')
    .normalize('NFKC')
    .replace(/주식회사|\(주\)|㈜/g, '')
    .replace(/\s+/g, '')
    .replace(/^아이비케이/, 'IBK')
    .replace(/^ibk/i, 'IBK')
    .replace(/[^a-zA-Z0-9가-힣]/g, '')
}

export const EMPTY_UNDERWRITERS: IpoInsight['underwriters'] = []

export function isUnderwriterAccount(
  underwriters: IpoInsight['underwriters'],
  securitiesFirmName: string | null | undefined,
): boolean {
  const firmName = normalizeName(securitiesFirmName)
  if (!firmName) return false
  return underwriters.some(
    (underwriter) =>
      normalizeName(underwriter.name).toLocaleLowerCase() === firmName.toLocaleLowerCase(),
  )
}

/** 계좌 select 옵션 라벨. 번호 없는 계좌도 허용하며(V42) 마스킹 번호가 있으면 함께 보여 준다. */
export function accountOptionLabel(
  account: Pick<SecuritiesAccount, 'securitiesFirmName' | 'accountName' | 'accountNumber'>,
): string {
  const rawFirm = account.securitiesFirmName?.trim()
  const firm = rawFirm ? formatSecuritiesFirmName(rawFirm) : '증권사'
  const parts = [firm]
  if (account.accountName) parts.push(account.accountName)
  // 서버가 마스킹해 내려주는 값만 쓴다(평문 계좌번호를 만들지 않는다).
  if (account.accountNumber) parts.push(account.accountNumber)
  return parts.join(' · ')
}
