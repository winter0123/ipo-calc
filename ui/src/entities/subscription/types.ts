export interface SubscriptionRegisterRequest {
  investorId: number
  securitiesAccountId: number
  ipoId: number
  quantity: number
  depositAmount: number
  subscribedAt: string
  memo?: string
}

export interface Subscription {
  id: number
  investorId: number
  securitiesAccountId: number
  ipoId: number
  quantity: number
  depositAmount: number
  subscribedAt: string
  memo?: string
  status: string
  createdAt: string
  updatedAt: string
}
