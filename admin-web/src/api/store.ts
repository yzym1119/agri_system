import request from '@/utils/request'
import type {PageResponse, StoreCreateRequest, StoreDetailVO, StoreQueryParams, StoreUpdateRequest} from '@/types/store'

/**
 * 门店管理API
 */

/**
 * 分页查询门店
 */
export function getStoreList(params: StoreQueryParams): Promise<PageResponse<StoreDetailVO>> {
  return request({
    url: '/api/admin/stores',
    method: 'get',
    params
  }).then((page: any) => ({
    records: page?.records || [],
    total: page?.total ?? page?.totalRow ?? 0,
    pageNum: page?.pageNumber ?? params.pageNum ?? 1,
    pageSize: page?.pageSize ?? params.pageSize ?? 10
  }))
}

/**
 * 获取门店详情
 */
export function getStoreDetail(id: number): Promise<StoreDetailVO> {
  return request({
    url: `/api/admin/stores/${id}`,
    method: 'get'
  })
}

/**
 * 创建门店
 */
export function createStore(data: StoreCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/stores',
    method: 'post',
    data
  })
}

/**
 * 更新门店
 */
export function updateStore(id: number, data: StoreUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/stores/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除门店（逻辑删除）
 */
export function deleteStore(id: number): Promise<void> {
  return request({
    url: `/api/admin/stores/${id}`,
    method: 'delete'
  })
}

/**
 * 获取所有门店列表（用于下拉选择）
 */
export function getAllStores(): Promise<StoreDetailVO[]> {
  return request({
    url: '/api/admin/stores',
    method: 'get',
    params: { pageNum: 1, pageSize: 1000 }
  }).then((response: any) => response?.records || response?.data?.records || [])
}
