export interface SecuritiesAccount {
  id: number
  investorId: number
  securitiesFirmId: number
  securitiesFirmName?: string
  accountNumber: string
  accountName?: string
  isDefault: boolean
  status: string
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}
