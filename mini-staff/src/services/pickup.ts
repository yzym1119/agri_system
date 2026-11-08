import { isPickupOrders,isPickupPreview,type PickupOrders } from '@agri/mobile-contracts/pickup';
import { staffAuth } from './auth';
async function own<T extends {storeId:string}>(url:string,validate:(v:unknown)=>v is T,epoch:number){
 const actor=staffAuth.current();if(!actor||actor.epoch!==epoch)throw Error('STALE_SESSION');
 const answer=await staffAuth.request({url,method:'GET'},validate,epoch);
 if(answer.storeId!==actor.storeId)throw Error('STORE_MISMATCH');return answer;
}
export const readPickupOrders=(state:PickupOrders['state'],after:string|null,epoch:number)=>own('/api/admin/mobile/pickup/orders?state='+state+(after?'&after='+encodeURIComponent(after):''),isPickupOrders,epoch)
 .then(answer=>{if(answer.state!==state)throw Error('RESPONSE_INVALID');return answer;});
export const readPickupPreview=(id:string,epoch:number)=>own('/api/admin/mobile/pickup/tasks/'+encodeURIComponent(id),isPickupPreview,epoch)
 .then(answer=>{if(answer.requestId!==id)throw Error('RESPONSE_INVALID');return answer;});
// 原入口成功为200空响应；业务错误对象不能作为成功，随后仍须读取同一请求事实。
const empty=(v:unknown):v is null|undefined|''=>v===null||v===undefined||v==='';
export const confirmPickup=(id:string,confirmationKey:string,allocationIds:string[],epoch:number)=>staffAuth.request({
 url:'/api/admin/orders/pickup/requests/'+encodeURIComponent(id)+'/fulfill',method:'POST',data:{confirmationKey,allocationIds:[...allocationIds]},
},empty,epoch);
