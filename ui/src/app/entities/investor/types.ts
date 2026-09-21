export interface InvestorRegisterRequest {
  displayName: string
  relationship?: InvestorRelationship
}

export type InvestorRelationship = 'SELF' | 'SPOUSE' | 'PARENT' | 'CHILD' | 'OTHER'

export interface InvestorResponse {
  id: number
  displayName: string
  accountId: number
  status: string
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}
