export interface SaleRegisterRequest {
  allocationId: number
  quantity: number
  price?: number
  amount?: number
  saleDate: string
  fee?: number
  tax?: number
  otherCost?: number
}

export interface Sale {
  id: number
  allocationId: number
  quantity: number
  price?: number
  amount?: number
  saleDate: string
  fee?: number
  tax?: number
  otherCost?: number
  status: string
  createdAt: string
  updatedAt: string
}

export type SaleUpdateRequest = Partial<Omit<SaleRegisterRequest, 'allocationId'>>
