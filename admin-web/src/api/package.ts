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
    if (response && response.records) {
      return response
    } else if (response && response.data && response.data.records) {
      return response.data
    } else {
      return { records: [], total: 0, pageNum: 1, pageSize: 10 }
    }
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
    // 处理响应，可能直接返回数据或包装在data中
    console.log('套餐详情响应:', response)
    if (response && response.data) {
      // 如果响应有 data 字段，返回 data
      return response.data
    } else if (response && response.id) {
      // 如果响应本身就是数据对象
      return response
    } else if (response && response.code === 200 && response.data) {
      // 如果响应格式为 { code: 200, data: {...} }
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
  }).then((response: any) => response.data)
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

