import { createReceiptRecovery,isReceiptResult,isReceivingDetail,isReceivingOrders } from '@agri/mobile-contracts/receipt';
import type { RecoveryActor } from '@agri/mobile-contracts/submission';
import { staffAuth } from './auth';
import { mobilePlatform } from './platform';
export const receiptRecovery=createReceiptRecovery(mobilePlatform,()=>staffAuth.current(),()=>mobilePlatform.createRequestKey());
async function own<T extends {storeId:string}>(url:string,validate:(v:unknown)=>v is T,epoch:number){
  const actor=staffAuth.current();if(!actor||actor.epoch!==epoch)throw Error('STALE_SESSION');
  const result=await staffAuth.request({url,method:'GET'},validate,epoch);
  if(result.storeId!==actor.storeId)throw Error('STORE_MISMATCH');return result;
}
export const readReceivingOrders=(page:number,orderNo:string,epoch:number)=>own('/api/admin/purchase/receiving/mobile/orders?page='+page+'&orderNo='+encodeURIComponent(orderNo),isReceivingOrders,epoch)
  .then(result=>{if(result.page!==page)throw Error('RESPONSE_INVALID');return result;});
export const readReceivingDetail=(orderId:string,epoch:number)=>own('/api/admin/purchase/receiving/mobile/orders/'+encodeURIComponent(orderId),isReceivingDetail,epoch)
  .then(result=>{if(result.id!==orderId)throw Error('RESPONSE_INVALID');return result;});
export const readReceiptResult=(requestKey:string,actor:RecoveryActor)=>own('/api/admin/purchase/receipts/submissions/'+encodeURIComponent(requestKey),isReceiptResult,actor.epoch)
  .then(result=>{if(result.requestKey!==requestKey)throw Error('RESPONSE_INVALID');return result;});
const posted=(v:unknown):v is unknown=>!!v&&typeof v==='object'&&!Array.isArray(v)&&(v as Record<string,unknown>).code==='200';
export const sendReceipt=(command:Record<string,unknown>,actor:RecoveryActor)=>staffAuth.request({url:'/api/admin/purchase/receipts',method:'POST',data:command},posted,actor.epoch);
