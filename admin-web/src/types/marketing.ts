/**
 * 营销管理相关类型定义
 */

/**
 * 优惠券模板VO
 */
export interface CouponTemplateVO {
  id: number
  name: string
  type: number // 1-满减券，2-折扣券
  amount: number // 面额/折扣率
  minOrderAmount: number // 使用门槛（元）
  limitCategoryId?: number // 限制分类ID
  limitCategoryName?: string // 限制分类名称
  limitProductId?: number // 限制商品ID
  limitProductName?: string // 限制商品名称
  validityType: number // 1-固定日期范围，2-领取后有效天数
  validStartTime?: string // 固定有效期开始
  validEndTime?: string // 固定有效期结束
  validDays?: number // 领取后有效天数
  totalCount: number // 发放总量限制（-1不限）
  status: number // 1-启用，0-停用
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 优惠券模板查询参数
 */
export interface CouponTemplateQuery {
  name?: string
  type?: number
  status?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 创建优惠券模板请求
 */
export interface CouponTemplateCreateRequest {
  name: string
  type: number // 1-满减券，2-折扣券
  amount: number // 面额/折扣率
  minOrderAmount?: number // 使用门槛（元），默认0
  limitCategoryId?: number // 限制分类ID
  limitProductId?: number // 限制商品ID
  validityType: number // 1-固定日期范围，2-领取后有效天数
  validStartTime?: string // 固定有效期开始（validityType=1时必填）
  validEndTime?: string // 固定有效期结束（validityType=1时必填）
  validDays?: number // 领取后有效天数（validityType=2时必填）
  totalCount?: number // 发放总量限制（-1不限），默认-1
  remark?: string
}

/**
 * 更新优惠券模板请求
 */
export interface CouponTemplateUpdateRequest {
  id: number
  name: string
  type: number
  amount: number
  minOrderAmount?: number
  limitCategoryId?: number
  limitProductId?: number
  validityType: number
  validStartTime?: string
  validEndTime?: string
  validDays?: number
  totalCount?: number
  status?: number
  remark?: string
}

/**
 * 充值奖励规则VO
 */
export interface RechargeRuleVO {
  id: number
  name: string
  minRechargeAmount: number
  targetVipLevel?: number
  giftDurationDays?: number
  giftSpecialFund: number
  giftTemplateId?: number | null
  giftTemplateName?: string
  giftTemplateStatus?: number // 优惠券模板状态（0-停用，1-启用）
  giftTemplateCount?: number
  status: number
  sortOrder: number
  createdAt?: string
  updatedAt?: string
}

/**
 * 创建充值奖励规则请求
 */
export interface RechargeRuleCreateRequest {
  name: string
  minRechargeAmount: number
  targetVipLevel?: number
  giftDurationDays?: number
  giftSpecialFund?: number
  giftTemplateId?: number | null
  giftTemplateCount?: number
  status?: number
  sortOrder?: number
}

/**
 * 更新充值奖励规则请求
 */
export interface RechargeRuleUpdateRequest {
  id: number
  name: string
  minRechargeAmount: number
  targetVipLevel?: number
  giftDurationDays?: number
  giftSpecialFund?: number
  giftTemplateId?: number | null
  giftTemplateCount?: number
  status?: number
  sortOrder?: number
}

/**
 * VIP月度权益VO
 */
export interface VipRightsVO {
  id?: number
  vipLevel: number
  benefitType?: number | null // 1-送券, 2-送专用金, null-未配置
  benefitValue?: number | null // 专用金金额
  templateId?: number | null
  templateName?: string
  templateStatus?: number // 优惠券模板状态（0-停用，1-启用）
  templateCount?: number | null
  status: number
  createdAt?: string
  updatedAt?: string
}

/**
 * 创建/更新VIP月度权益请求
 */
export interface VipRightsSaveRequest {
  id?: number
  vipLevel: number
  benefitType?: number | null // 1-送券, 2-送专用金, null-未配置
  benefitValue?: number | null // 专用金金额（benefitType=2时必填）
  templateId?: number | null // 优惠券模板ID（benefitType=1时必填）
  templateCount?: number | null // 赠送数量（benefitType=1时必填）
  status?: number
}

