import request from '@/utils/request'
import type {
    PageResponse,
    ProductPackageCreateRequest,
    ProductPackageDetailVO,
    ProductPackageQueryParams,
    ProductPackageUpdateRequest
} from '@/types/package'

/**
 * 套餐管理API
 */

/**
 * 分页查询套餐
 */
export function getPackageList(params: ProductPackageQueryParams): Promise<PageResponse<ProductPackageDetailVO>> {
  return request({
    url: '/api/admin/product-packages',
    method: 'get',
    params
  }).then((response: any) => {
    // 处理分页响应，可能直接返回Page对象或包装在data中
    const page = (response && response.records) ? response
      : (response && response.data && response.data.records) ? response.data
      : null
    if (page) {
      return {
        records: page.records || [],
        total: page.total ?? page.totalRow ?? 0,
        pageNum: page.pageNumber ?? params.pageNum ?? 1,
        pageSize: page.pageSize ?? params.pageSize ?? 10
      }
    }
    return { records: [], total: 0, pageNum: 1, pageSize: 10 }
  })
}

/**
 * 获取套餐详情
 */
export function getPackageDetail(id: number): Promise<ProductPackageDetailVO> {
  return request({
    url: `/api/admin/product-packages/${id}`,
    method: 'get'
  }).then((response: any) => {
    if (response && response.id) {
      return response
    } else if (response && response.data) {
      return response.data
    } else {
      console.error('无法解析套餐详情数据:', response)
      throw new Error('无法解析套餐详情数据')
    }
  })
}

/**
 * 创建套餐
 */
export function createPackage(data: ProductPackageCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/product-packages',
    method: 'post',
    data
  }).then((response: any) => response?.data || response)
}

/**
 * 更新套餐
 */
export function updatePackage(id: number, data: ProductPackageUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/product-packages/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除套餐
 */
export function deletePackage(id: number): Promise<void> {
  return request({
    url: `/api/admin/product-packages/${id}`,
    method: 'delete'
  })
}

/**
 * 上架/下架套餐
 */
export function updatePackageStatus(id: number, status: number): Promise<void> {
  return request({
    url: `/api/admin/product-packages/${id}/status`,
    method: 'put',
    params: { status }
  })
}
