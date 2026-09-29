export interface AllocationRegisterRequest {
  subscriptionId: number
  allocatedQuantity: number
  allocatedAt: string
  allocationMethod?: string
  allocationPrice?: number
  note?: string
}

export interface Allocation {
  id: number
  subscriptionId: number
  allocatedQuantity: number
  allocatedAt: string
  allocationMethod?: string
  allocationPrice?: number
  note?: string
  status: string
  createdAt: string
  updatedAt: string
}
