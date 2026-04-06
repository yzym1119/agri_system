import type {ProductDetailVO} from './product'
import type {ProductPackageDetailVO} from './package'

/**
 * POS 订单创建请求
 */
export interface POSOrderCreateRequest {
  storeId: number
  customerId?: number
  items: POSOrderItemDTO[]
  paymentType: number // 1-现金, 2-微信, 3-支付宝, 4-本金余额, 6-赊销, 10-混合支付
  receiveAmount?: number // 实收金额（现金支付时必填）
  walletAuthCode?: string // 钱包验证码（余额支付时必填）
  useSpecialFund?: boolean // 是否使用专用金（余额支付时可选）
  couponId?: number // 优惠券ID（可选）
  changeAmount?: number // 找零金额（现金支付时可选）
  staffId: number
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
}

/**
 * 购物车商品项
 */
export interface CartItem {
  id: string // 唯一ID
  itemType: 1 | 2 // 1-商品, 2-套餐（注意：新版本套餐会被炸开为多个商品）
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
  // 套餐特有（已废弃，保留用于兼容）
  packagePrice?: number
  vipPrice?: number
  packageItems?: ProductPackageItemVO[] // 套餐明细
  // 购物车状态
  currentUnit: string // 当前选择的单位
  unitPrice: number // 当前单价
  quantity: number // 数量
  subtotal: number // 小计
  stock?: number // 当前门店库存（商品）
  _expanded?: boolean // 套餐是否展开（仅前端使用）
  fromPackage?: string // 标记来自哪个套餐（用于UI显示分组）
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
  stock?: number // 仅商品有库存
  product?: ProductDetailVO
  package?: ProductPackageDetailVO
}

