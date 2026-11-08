import { staffAuth } from './auth';
import { isOrderId } from '@agri/mobile-contracts/sale';

export type SaleCustomer = { id: string; name: string; phone: string };
const object = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null;
const validPage = (v: unknown): v is { records: Record<string, unknown>[] } => object(v)
  && Array.isArray(v.records) && v.records.length <= 20 && v.records.every(row => object(row)
    && isOrderId(row.id) && typeof row.name === 'string' && row.name.length <= 100
    && (row.phone === null || typeof row.phone === 'string') && row.status === 1);

/** 复用已有客户列表权限和后端已有数据范围，仅保留选客所需字段，不缓存钱包资料。 */
export async function searchSaleCustomers(keyword: string, epoch: number): Promise<SaleCustomer[]> {
  const actor = staffAuth.current();
  if (!actor || actor.epoch !== epoch) throw new Error('STALE_SESSION');
  if (!actor.permissions.includes('*') && !actor.permissions.includes('customer:query')) throw new Error('FORBIDDEN');
  const query = keyword.trim();
  if (!query || query.length > 64) throw new Error('CUSTOMER_QUERY_REQUIRED');
  const field = /^\d+$/.test(query) ? 'phone' : 'name';
  const page = await staffAuth.request({
    url: '/api/admin/customer/list?pageNum=1&pageSize=20&status=1&' + field + '=' + encodeURIComponent(query), method:'GET',
  }, validPage, epoch);
  return page.records.map(row => ({ id:String(row.id), name:row.name as string, phone:row.phone as string || '' }));
}
