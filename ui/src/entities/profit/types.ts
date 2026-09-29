export interface SaleProfit {
  saleId: number
  saleDate: string
  allocationId: number
  quantity: number
  saleAmount: number
  matchedAcquisitionAmount: number
  matchedCostAmount: number
  saleFee: number
  saleTax: number
  saleOtherCost: number
  preTaxProfit: number
  afterTaxProfit: number
}

export interface InvestorProfit {
  investorId: number
  sales: SaleProfit[]
  totalSaleAmount: number
  totalPreTaxProfit: number
  totalAfterTaxProfit: number
}
