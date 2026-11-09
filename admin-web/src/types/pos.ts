import type {ProductDetailVO} from './product'
import type {ProductPackageDetailVO, ProductPackageItemVO} from './package'

/**
 * POS 订单创建请求
 */
export interface POSOrderCreateRequest {
  requestKey?: string
  storeId: number
  customerId?: number
  items: POSOrderItemDTO[]
  paymentType: number // 1-现金, 2-微信, 3-支付宝, 4-本金余额, 6-赊销, 10-混合支付
  payments?: POSPaymentDetail[]
  receiveAmount?: number // 实收金额（现金支付时必填）
  walletAuthCode?: string // 钱包验证码（余额支付时必填）
  useSpecialFund?: boolean // 是否使用专用金（余额支付时可选）
  couponId?: number // 优惠券ID（可选）
  changeAmount?: number // 找零金额（现金支付时可选）
  staffId: number
  deliveryType?: 1 | 2 // 1-当场交货（默认），2-门店人工配送，针对整单
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  remark?: string
}

/**
 * POS 订单明细 DTO
 */
export interface POSOrderItemDTO {
  productId?: number // itemType=1时必填
  packageId?: number // itemType=2时必填
  itemType: number // 1-商品, 2-套餐
  quantity: number
  unit?: string
  unitPrice?: number // 单价（可选，如果不提供则从价格体系查询）
  quotedUnitPrice?: number // 展示时的标准报价，结算时重新校验
  packageQuoteId?: string // 套餐价格及构成版本，不能使用过期挂单报价
  warehousePickup?: boolean // 是否库房取货（true 时后端生成取货请求）
  priceChangeReason?: string // 人工改价原因（提交价与服务端报价不一致时留痕）
}

export interface POSPaymentDetail {
  paymentType: number
  amount: number
  authCode?: string
}

/**
 * 购物车商品项
 */
export interface CartItem {
  baseUnitName?: string | null
  allowFractionalBaseQuantity?: boolean | null
  id: string // 唯一ID
  itemType: 1 | 2 // 1-商品，2-独立定价套餐；前端不拆分套餐
  productId?: number
  packageId?: number
  name: string
  specification?: string
  manufacturer?: string
  imageUrl?: string
  // 商品特有
  mainUnit?: string
  auxUnit?: string
  mainToAuxRatio?: number
  allowSpecialFund?: number
  // 套餐特有：以下物料数量和金额来自本次套数对应的后端报价
  packageItems?: ProductPackageItemVO[]
  specialFundEligibleAmount?: number
  // 购物车状态
  currentUnit: string // 当前选择的单位
  unitPrice: number // 当前单价
  quantity: number // 数量
  subtotal: number // 小计
  stock?: number // 当前门店库存（商品）
  _expanded?: boolean // 套餐是否展开（仅前端使用）
  fromPackage?: string // 标记来自哪个套餐（用于UI显示分组）
  // 服务端报价（商品与套餐共用；套餐 quoteId 包含价格和组成版本）
  quoteId?: string | null // 服务端报价ID（后端返回字符串标识）
  belowReference?: boolean // 报价低于参考下限（需人工确认）
  referenceFloor?: number | null // 参考下限价格（低于下限时展示）
  priceChangeReason?: string // 人工改价原因（提交时留痕）
  priceFallback?: boolean // 服务端报价失败，禁止结算
  pricePending?: boolean
  quoteRequestId?: number
  suggestedUnitPrice?: number
  saleUnits?: { unitName: string; baseQuantityPerUnit?: number }[]
  warehousePickup?: boolean // 已出库物料的库房取货核对；人工配送不创建自提任务
}

/**
 * 客户信息（用于 POS）
 */
export interface POSCustomerInfo {
  id: number
  name: string
  phone: string
  type: number // 1-普通, 2-VIP
  vipLevel?: number
  balance: number // 本金余额
  specialFund: number // 专用金余额
  creditLimit?: number // 信用额度
  totalDebt?: number // 总欠款
}

/**
 * 商品/套餐卡片数据
 */
export interface GoodsCard {
  id: number
  type: 'product' | 'package'
  name: string
  imageUrl?: string
  specification?: string
  manufacturer?: string
  price: number
  stock?: number | null // null means availability is unknown
  product?: ProductDetailVO
  package?: ProductPackageDetailVO
}
