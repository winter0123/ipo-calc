import { z } from 'zod'

// saleAmount는 매도 금액이 선택 입력(DB nullable)이라 null일 수 있다.
export const saleProfitSchema = z.object({
  saleId: z.number(),
  saleDate: z.string(),
  allocationId: z.number(),
  quantity: z.number(),
  saleAmount: z.number().nullable(),
  matchedAcquisitionAmount: z.number(),
  saleFee: z.number(),
  saleTax: z.number(),
  saleOtherCost: z.number(),
  preTaxProfit: z.number(),
  afterTaxProfit: z.number(),
  realizedProfit: z.number(),
})

export const investorProfitSchema = z.object({
  investorId: z.number(),
  sales: z.array(saleProfitSchema),
  totalSaleAmount: z.number(),
  totalPreTaxProfit: z.number(),
  totalAfterTaxProfit: z.number(),
  totalRealizedProfit: z.number(),
})

export type SaleProfit = z.infer<typeof saleProfitSchema>
export type InvestorProfit = z.infer<typeof investorProfitSchema>
