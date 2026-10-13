export const CSV_HEADERS = [
  'companyName',
  'ipoName',
  'stockCode',
  'roundLabel',
  'lowerBandPrice',
  'upperBandPrice',
  'fixedPrice',
  'subscriptionStartDate',
  'subscriptionEndDate',
  'listingDate',
  'offeringShareCount',
] as const

export const REQUIRED_HEADERS = [
  'companyName',
  'ipoName',
  'subscriptionStartDate',
  'subscriptionEndDate',
]

export const SAMPLE_ROW = [
  '예시전자',
  '예시전자 공모',
  '123456',
  '',
  '10000',
  '12000',
  '',
  '2026-11-02',
  '2026-11-03',
  '2026-11-12',
  '1000000',
]

export const MAX_CSV_BYTES = 5 * 1024 * 1024

// 엑셀이 한글을 깨뜨리지 않도록 UTF-8 BOM을 붙인다
export function buildTemplateBlob(): Blob {
  const text = `${CSV_HEADERS.join(',')}\n${SAMPLE_ROW.join(',')}\n`
  return new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' })
}
