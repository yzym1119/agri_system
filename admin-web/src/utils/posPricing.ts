import request from '@/utils/request'
import type { ProductPackageItemVO } from '@/types/package'
type NullableNumber = number | null | undefined

// ==================== 服务端报价（人工改价审计依据） ====================

/** 服务端报价请求参数：除 productId 外均可选 */
export interface ServerPriceQuoteParams {
  productId: number
  storeId?: NullableNumber
  channel?: string | null
  customerId?: NullableNumber
  customerType?: NullableNumber
  vipLevel?: NullableNumber
  saleUnit?: string | null
  /** 业务日期（yyyy-MM-dd，为空表示今天） */
  businessDate?: string | null
}

/** 服务端报价结果（对应后端 PriceQuote） */
export interface ServerPriceQuote {
  quoteId: string
  orderItemId: number | null
  productId: number
  storeId: number | null
  channel: string | null
  customerType: number | null
  vipLevel: number | null
  saleUnit: string | null
  saleUnitId: number | null
  businessDate: string | null
  suggestedUnitPrice: number
  ruleVersion: string | null
  referenceFloor: number | null
  belowReference: boolean
  legacyDerived: boolean
}

/**
 * 查询服务端报价（按销售单位/渠道/客户分层解析，POS 改价审计的唯一价格依据）。
 * 报价失败必须阻止结算，禁止回退前端本地价格。
 */
export const fetchServerPriceQuote = (params: ServerPriceQuoteParams): Promise<ServerPriceQuote> => {
  const { productId, storeId, channel, customerId, customerType, vipLevel, saleUnit, businessDate } = params
  return request({
    url: `/api/admin/products/${productId}/price-quote`,
    method: 'get',
    params: {
      storeId,
      channel: channel ?? undefined,
      customerId,
      customerType,
      vipLevel,
      saleUnit: saleUnit ?? undefined,
      businessDate: businessDate ?? undefined
    }
  }).then((response: any) => response as ServerPriceQuote)
}

export interface ServerPackageQuoteParams {
  packageId: number
  customerId?: NullableNumber
  storeId?: NullableNumber
  quantity: number
  dealUnitPrice?: number
}

export interface ServerPackageQuote {
  quoteId: string
  packageId: number
  packageName: string
  quantity: number
  suggestedUnitPrice: number
  referenceFloor: number | null
  belowReference: boolean
  dealUnitPrice: number
  totalAmount: number
  specialFundEligibleAmount: number
  items: ProductPackageItemVO[]
}

/** 套餐成交金额、物料和专用金金额均由后端计算，前端不拆分分摊。 */
export const fetchServerPackageQuote = ({ packageId, ...params }: ServerPackageQuoteParams): Promise<ServerPackageQuote> => {
  return request({
    url: `/api/admin/product-packages/${packageId}/price-quote`,
    method: 'get',
    params
  }).then((response: any) => response as ServerPackageQuote)
}
