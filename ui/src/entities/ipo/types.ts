export interface Ipo {
  id: number
  companyName: string
  ipoName: string
  stockCode: string
  roundLabel: string
  lowerBandPrice: number
  upperBandPrice: number
  fixedPrice: number
  subscriptionStartDate: string
  subscriptionEndDate: string
  listingDate: string | null
  offeringShareCount: number
  status: string
  createdAt: string
  updatedAt: string
}
