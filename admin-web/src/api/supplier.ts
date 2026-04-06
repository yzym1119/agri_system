import request from '@/utils/request'
import type {
    PageResponse,
    SupplierCreateRequest,
    SupplierDetailVO,
    SupplierQueryParams,
    SupplierUpdateRequest
} from '@/types/supplier'

/**
 * 供应商管理API
 * 注意：如果后端没有单独的供应商管理接口，这些API可能需要根据实际后端接口调整
 */

/**
 * 分页查询供应商
 */
export function getSupplierList(params: SupplierQueryParams): Promise<PageResponse<SupplierDetailVO>> {
  return request({
    url: '/api/admin/suppliers',
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
 * 获取供应商详情
 */
export function getSupplierDetail(id: number): Promise<SupplierDetailVO> {
  return request({
    url: `/api/admin/suppliers/${id}`,
    method: 'get'
  }).then((response: any) => response.data)
}

/**
 * 创建供应商
 */
export function createSupplier(data: SupplierCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/suppliers',
    method: 'post',
    data
  }).then((response: any) => response.data)
}

/**
 * 更新供应商
 */
export function updateSupplier(id: number, data: SupplierUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/suppliers/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除供应商
 */
export function deleteSupplier(id: number): Promise<void> {
  return request({
    url: `/api/admin/suppliers/${id}`,
    method: 'delete'
  })
}

/**
 * 获取所有供应商列表（用于下拉选择）
 */
export function getAllSuppliers(): Promise<SupplierDetailVO[]> {
  return request({
    url: '/api/admin/suppliers',
    method: 'get',
    params: { pageNum: 1, pageSize: 1000 }
  }).then((response: PageResponse<SupplierDetailVO>) => response.records)
}

