import { isAssignedTasks,isAssignedReceipt,isDeliveryResult,type AssignedTasks } from '@agri/mobile-contracts/fulfillment';
import { staffAuth } from './auth';
async function own<T extends {storeId:string}>(url:string,method:'GET'|'POST',data:Record<string,unknown>|undefined,validate:(v:unknown)=>v is T,epoch:number){
  const actor=staffAuth.current();if(!actor||actor.epoch!==epoch)throw Error('STALE_SESSION');
  const result=await staffAuth.request({url,method,data},validate,epoch);if(result.storeId!==actor.storeId)throw Error('STORE_MISMATCH');return result;
}
export const readAssignedTasks=(state:AssignedTasks['state'],after:string|null,epoch:number)=>own('/api/admin/mobile/delivery/tasks?state='+state+(after?'&after='+encodeURIComponent(after):''),'GET',undefined,isAssignedTasks,epoch)
  .then(result=>{if(result.state!==state)throw Error('RESPONSE_INVALID');return result;});
export const readAssignedReceipt=(orderId:string,epoch:number)=>own('/api/admin/mobile/delivery/tasks/'+encodeURIComponent(orderId),'GET',undefined,isAssignedReceipt,epoch)
  .then(result=>{if(result.orderId!==orderId)throw Error('RESPONSE_INVALID');return result;});
export const confirmAssignedReceipt=(orderId:string,confirmationKey:string,epoch:number)=>own('/api/admin/mobile/delivery/tasks/'+encodeURIComponent(orderId)+'/confirm','POST',{confirmationKey},isDeliveryResult,epoch)
  .then(result=>{if(result.orderId!==orderId)throw Error('RESPONSE_INVALID');return result;});
