export interface CostRegisterRequest {
  subscriptionId: number
  costType: string
  amount: number
  incurredAt: string
  memo?: string
}

export interface Cost {
  id: number
  subscriptionId: number
  costType: string
  amount: number
  incurredAt: string
  memo?: string
  status: string
  deletedAt?: string
  createdAt: string
  updatedAt: string
}
