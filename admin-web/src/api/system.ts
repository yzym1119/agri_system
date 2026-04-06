import request from '@/utils/request'
import type {DictDataCreateRequest, DictDataManageVO, DictDataUpdateRequest, DictDataVO} from '@/types/dict'

/**
 * 字典管理API
 */

/**
 * 根据字典编码获取选项列表（公开接口，只返回启用状态）
 */
export function getDictDataByCode(typeCode: string): Promise<DictDataVO[]> {
  return request({
    url: `/api/public/dict/${typeCode}`,
    method: 'get'
  })
}

/**
 * 获取字典数据列表（管理端，包含所有状态和ID）
 */
export function getDictDataList(typeCode: string): Promise<DictDataManageVO[]> {
  return request({
    url: '/api/admin/dict/data',
    method: 'get',
    params: { typeCode }
  })
}

/**
 * 新增字典数据
 */
export function createDictData(data: DictDataCreateRequest): Promise<number> {
  return request({
    url: '/api/admin/dict/data',
    method: 'post',
    data
  })
}

/**
 * 更新字典数据
 */
export function updateDictData(data: DictDataUpdateRequest): Promise<void> {
  return request({
    url: '/api/admin/dict/data',
    method: 'put',
    data
  })
}

/**
 * 删除字典数据
 */
export function deleteDictData(id: number, typeCode: string): Promise<void> {
  return request({
    url: '/api/admin/dict/data',
    method: 'delete',
    params: { id, typeCode }
  })
}

