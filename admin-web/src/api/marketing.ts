import request from '@/utils/request'
import type {
    CouponTemplateCreateRequest,
    CouponTemplateQuery,
    CouponTemplateUpdateRequest,
    CouponTemplateVO,
    RechargeRuleCreateRequest,
    RechargeRuleUpdateRequest,
    RechargeRuleVO,
    VipRightsSaveRequest,
    VipRightsVO
} from '@/types/marketing'
import type {PageResponse} from '@/types/store'

/**
 * 营销管理API
 */

/**
 * 分页查询优惠券模板列表
 */
export function getCouponTemplateList(
  params: CouponTemplateQuery
): Promise<PageResponse<CouponTemplateVO>> {
  return request({
    url: '/api/admin/marketing/coupon-template',
    method: 'get',
    params
  }).then((response: any) => {
    const page =
      (response && response.records ? response : null) ||
      (response && response.data && response.data.records ? response.data : null)
    if (page) {
      return {
        records: page.records || [],
        total: page.total ?? page.totalRow ?? 0,
        pageNum: page.pageNum ?? page.current ?? params.pageNum ?? 1,
        pageSize: page.pageSize ?? page.size ?? params.pageSize ?? 10
      }
    }
    return { records: [], total: 0, pageNum: params.pageNum || 1, pageSize: params.pageSize || 10 }
  })
}

/**
 * 获取优惠券模板详情
 */
export function getCouponTemplateDetail(id: number): Promise<CouponTemplateVO> {
  return request({
    url: `/api/admin/marketing/coupon-template/${id}`,
    method: 'get'
  }).then((response: any) => {
    if (response && response.id) {
      return response
    } else if (response && response.data && response.data.id) {
      return response.data
    } else {
      console.error('优惠券模板详情响应格式错误:', response)
      throw new Error('优惠券模板详情数据格式错误')
    }
  })
}

/**
 * 创建优惠券模板
 */
export function createCouponTemplate(
  data: CouponTemplateCreateRequest
): Promise<{ id: number }> {
  return request({
    url: '/api/admin/marketing/coupon-template',
    method: 'post',
    data
  })
}

/**
 * 更新优惠券模板
 */
export function updateCouponTemplate(
  id: number,
  data: CouponTemplateUpdateRequest
): Promise<void> {
  return request({
    url: `/api/admin/marketing/coupon-template/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除优惠券模板
 */
export function deleteCouponTemplate(id: number): Promise<void> {
  return request({
    url: `/api/admin/marketing/coupon-template/${id}`,
    method: 'delete'
  })
}

/**
 * 发布/下架优惠券模板
 */
export function toggleCouponTemplateStatus(id: number, status: number): Promise<void> {
  return request({
    url: `/api/admin/marketing/coupon-template/${id}/status`,
    method: 'put',
    params: { status }
  })
}

// ==================== 充值奖励规则 ====================

/**
 * 查询充值奖励规则列表
 */
export function getRechargeRuleList(): Promise<RechargeRuleVO[]> {
  return request({
    url: '/api/admin/marketing/recharge-rule',
    method: 'get'
  }).then((response: any) => {
    if (Array.isArray(response)) {
      return response
    } else if (response && response.data && Array.isArray(response.data)) {
      return response.data
    } else if (response && response.records && Array.isArray(response.records)) {
      return response.records
    }
    return []
  })
}

/**
 * 创建充值奖励规则
 */
export function createRechargeRule(data: RechargeRuleCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/marketing/recharge-rule',
    method: 'post',
    data
  })
}

/**
 * 更新充值奖励规则
 */
export function updateRechargeRule(id: number, data: RechargeRuleUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/marketing/recharge-rule/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除充值奖励规则
 */
export function deleteRechargeRule(id: number): Promise<void> {
  return request({
    url: `/api/admin/marketing/recharge-rule/${id}`,
    method: 'delete'
  })
}

/**
 * 切换充值奖励规则状态
 */
export function toggleRechargeRuleStatus(id: number, status: number): Promise<void> {
  return request({
    url: `/api/admin/marketing/recharge-rule/${id}/status`,
    method: 'put',
    params: { status }
  })
}

// ==================== VIP月度权益 ====================

/**
 * 查询VIP月度权益列表（按VIP等级分组）
 */
export function getVipRightsList(): Promise<VipRightsVO[]> {
  return request({
    url: '/api/admin/marketing/vip-rights',
    method: 'get'
  }).then((response: any) => {
    if (Array.isArray(response)) {
      return response
    } else if (response && response.data && Array.isArray(response.data)) {
      return response.data
    } else if (response && response.records && Array.isArray(response.records)) {
      return response.records
    }
    return []
  })
}

/**
 * 根据VIP等级查询权益
 */
export function getVipRightsByLevel(vipLevel: number): Promise<VipRightsVO[]> {
  return request({
    url: `/api/admin/marketing/vip-rights/level/${vipLevel}`,
    method: 'get'
  }).then((response: any) => {
    if (Array.isArray(response)) {
      return response
    } else if (response && response.data && Array.isArray(response.data)) {
      return response.data
    }
    return []
  })
}

/**
 * 保存VIP月度权益（创建或更新，单条）
 */
export function saveVipRights(data: VipRightsSaveRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/marketing/vip-rights',
    method: 'post',
    data
  })
}

/**
 * 批量保存VIP月度权益（支持一个等级多条权益）
 */
export function saveVipRightsBatch(
  vipLevel: number,
  rightsList: VipRightsSaveRequest[]
): Promise<void> {
  return request({
    url: '/api/admin/marketing/vip-rights/batch',
    method: 'post',
    params: { vipLevel },
    data: rightsList
  })
}

/**
 * 删除VIP月度权益
 */
export function deleteVipRights(id: number): Promise<void> {
  return request({
    url: `/api/admin/marketing/vip-rights/${id}`,
    method: 'delete'
  })
}

