export function formatSecuritiesFirmName(name: string): string {
  const normalized = name.normalize('NFKC').trim().replace(/\s+/g, '')
  return /^(아이비케이|IBK)투자증권(?:주식회사|\(주\))?$/i.test(normalized) ? 'IBK' : name
}
