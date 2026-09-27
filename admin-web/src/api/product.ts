import request from '@/utils/request'
import type {
    CategoryCreateRequest,
    CategoryTreeVO,
    CategoryUpdateRequest,
    PageResponse,
    ProductCreateRequest,
    ProductDetailVO,
    ProductPriceRuleCreateRequest,
    ProductPriceRuleUpdateRequest,
    ProductPriceRuleVO,
    ProductQueryParams,
    ProductUnitUpsertCommand,
    ProductUnitVO,
    ProductUpdateRequest
} from '@/types/product'

/**
 * 商品管理API
 */

// ==================== 商品分类管理 ====================

/**
 * 获取分类树
 */
export function getCategoryTree(): Promise<CategoryTreeVO[]> {
  return request({
    url: '/api/admin/products/categories/tree',
    method: 'get'
  }).then((response: any) => {
    // 处理响应，可能直接返回数组或包装在data中
    if (Array.isArray(response)) {
      return response
    } else if (response && response.data && Array.isArray(response.data)) {
      return response.data
    } else {
      return []
    }
  })
}

/**
 * 创建分类
 */
export function createCategory(data: CategoryCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/products/categories',
    method: 'post',
    data
  }).then((response: any) => response?.data || response)
}

/**
 * 更新分类
 */
export function updateCategory(id: number, data: CategoryUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/products/categories/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除分类
 */
export function deleteCategory(id: number): Promise<void> {
  return request({
    url: `/api/admin/products/categories/${id}`,
    method: 'delete'
  })
}

// ==================== 商品管理 ====================

/**
 * 分页查询商品
 */
export function getProductList(params: ProductQueryParams): Promise<PageResponse<ProductDetailVO>> {
  return request({
    url: '/api/admin/products',
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
 * 获取商品详情
 */
export function getProductDetail(id: number): Promise<ProductDetailVO> {
  return request({
    url: `/api/admin/products/${id}`,
    method: 'get'
  }).then((response: any) => {
    // 处理响应，可能直接返回数据或包装在data中
    if (response && response.id) {
      return response
    } else if (response && response.data && response.data.id) {
      return response.data
    } else {
      console.error('商品详情响应格式错误:', response)
      throw new Error('商品详情数据格式错误')
    }
  })
}

/**
 * 创建商品
 */
export function createProduct(data: ProductCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/products',
    method: 'post',
    data
  })
  // 注意：request.ts 的响应拦截器已经提取了 data，所以这里直接返回 response
  // 如果后端返回 { code: "200", data: { id: xxx } }，拦截器会返回 { id: xxx }
}

/**
 * 更新商品
 */
export function updateProduct(id: number, data: ProductUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/products/${id}`,
    method: 'put',
    data
  })
}

/**
 * 上架/下架商品
 */
export function updateProductOnlineStatus(id: number, isOnline: number): Promise<void> {
  return request({
    url: `/api/admin/products/${id}/online-status`,
    method: 'put',
    params: { isOnline }
  })
}

/**
 * 删除商品
 */
export function deleteProduct(id: number): Promise<void> {
  return request({
    url: `/api/admin/products/${id}`,
    method: 'delete'
  })
}

// ==================== 价格规则管理 ====================

/**
 * 查询商品价格规则列表
 */
export function getProductPriceRules(productId: number): Promise<ProductPriceRuleVO[]> {
  return request({
    url: `/api/admin/products/${productId}/price-rules`,
    method: 'get'
  }).then((response: any) => {
    if (Array.isArray(response)) return response
    if (response && response.data && Array.isArray(response.data)) return response.data
    return []
  })
}

/**
 * 创建价格规则
 */
export function createProductPriceRule(data: ProductPriceRuleCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/products/price-rules',
    method: 'post',
    data
  }).then((response: any) => response.data || response)
}

/**
 * 更新价格规则
 */
export function updateProductPriceRule(id: number, data: ProductPriceRuleUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/products/price-rules/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除价格规则
 */
export function deleteProductPriceRule(id: number): Promise<void> {
  return request({
    url: `/api/admin/products/price-rules/${id}`,
    method: 'delete'
  })
}

// ==================== 商品单位管理（零售事实链 Task9） ====================

/**
 * 查询商品单位列表（含基准单位与直接换算系数）
 */
export function getProductUnits(productId: number): Promise<ProductUnitVO[]> {
  return request({
    url: `/api/admin/products/${productId}/units`,
    method: 'get'
  }).then((response: any) => {
    if (Array.isArray(response)) return response
    if (response && Array.isArray(response.data)) return response.data
    return []
  })
}

/**
 * 全量替换商品单位列表
 */
export function replaceProductUnits(productId: number, data: ProductUnitUpsertCommand[]): Promise<void> {
  return request({
    url: `/api/admin/products/${productId}/units`,
    method: 'put',
    data
  })
}
