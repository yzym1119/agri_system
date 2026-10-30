import { isScanResolution,isInventoryMatches,isInventoryView,type InventoryTarget } from '@agri/mobile-contracts/inventory';
import { staffAuth } from './auth';

/** 新移动入口明确为裸DTO；不把旧InventoryController的字符串200包装猜成成功。 */
async function own<T extends {storeId:string}>(request:{url:string;method:'GET'|'POST';data?:Record<string,unknown>},validate:(v:unknown)=>v is T,epoch:number):Promise<T> {
  const actor=staffAuth.current();
  if(!actor||actor.epoch!==epoch)throw Error('STALE_SESSION');
  const answer=await staffAuth.request(request,validate,epoch);
  if(answer.storeId!==actor.storeId)throw Error('STORE_MISMATCH');
  return answer;
}
export const resolveCode=(code:string,epoch:number)=>own({url:'/api/admin/mobile/scan/resolve',method:'POST',data:{code,purpose:'INVENTORY_LOOKUP'}},isScanResolution,epoch);
export const searchProducts=(keyword:string,page:number,epoch:number)=>own({url:'/api/admin/mobile/inventory/products?keyword='+encodeURIComponent(keyword)+'&page='+page,method:'GET'},isInventoryMatches,epoch)
  .then(answer=>{if(answer.page!==page)throw Error('RESPONSE_INVALID');return answer;});
export const readLocationProducts=(locationId:string,page:number,epoch:number)=>own({url:'/api/admin/mobile/locations/'+encodeURIComponent(locationId)+'/products?page='+page,method:'GET'},isInventoryMatches,epoch)
  .then(answer=>{if(answer.page!==page)throw Error('RESPONSE_INVALID');return answer;});
export const readInventory=(target:InventoryTarget,page:number,epoch:number)=>{
  let query='?page='+page;
  if(target.batchId)query+='&batchId='+encodeURIComponent(target.batchId);
  if(target.locationId)query+='&locationId='+encodeURIComponent(target.locationId);
  return own({url:'/api/admin/mobile/inventory/'+encodeURIComponent(target.productId)+query,method:'GET'},isInventoryView,epoch)
    .then(answer=>{if(answer.productId!==target.productId||answer.page!==page)throw Error('RESPONSE_INVALID');return answer;});
};
