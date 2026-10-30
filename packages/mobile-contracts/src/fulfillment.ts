import { isPositiveLongId as id } from './ids.js';
export type AssignedTask={orderId:string;orderNo:string;status:'PENDING'|'COMPLETED';deliveryAt:string|null;receivedAt:string|null};
export type AssignedTasks={storeId:string;state:'PENDING'|'COMPLETED';tasks:AssignedTask[];nextCursor:string|null};
export type DeliveryItem={orderItemId:string;productName:string;quantity:string;unit:string};
export type AssignedReceipt={storeId:string;orderId:string;orderNo:string;status:'PENDING'|'COMPLETED';canConfirm:boolean;blockReason:string|null;
  receiverName:string|null;receiverPhone:string|null;receiverAddress:string|null;confirmationKey:string|null;items:DeliveryItem[];receivedAt:string|null};
export type DeliveryResult={storeId:string;orderId:string;status:'COMPLETED';receivedAt:string|null};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const text=(v:unknown,max=200):v is string=>typeof v==='string'&&!!v.trim()&&v.length<=max;
const time=(v:unknown)=>v===null||(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/.test(v));
const state=(v:unknown):v is 'PENDING'|'COMPLETED'=>v==='PENDING'||v==='COMPLETED';
const greater=(a:string,b:string)=>a.length===b.length?a>b:a.length>b.length;
function item(v:unknown):v is DeliveryItem {
  return object(v)&&id(v.orderItemId)&&text(v.productName)&&text(v.unit,50)&&typeof v.quantity==='string'
    &&/^(0|[1-9]\d{0,11})(\.\d{1,8})?$/.test(v.quantity)&&/[1-9]/.test(v.quantity);
}
export function deliveryItemKey(item:DeliveryItem){return item.orderItemId+':'+item.unit;}
export function isAssignedTasks(v:unknown):v is AssignedTasks {
  if(!object(v)||!id(v.storeId)||!state(v.state)||!Array.isArray(v.tasks)||v.tasks.length>20||!(v.nextCursor===null||id(v.nextCursor)))return false;
  let previous:string|null=null;
  for(const task of v.tasks){
    if(!object(task)||!id(task.orderId)||!text(task.orderNo,100)||task.status!==v.state||!time(task.deliveryAt)||!time(task.receivedAt)
      ||(previous!==null&&!greater(previous,task.orderId)))return false;
    previous=task.orderId;
  }
  return v.nextCursor===null||(v.tasks.length===20&&v.nextCursor===previous);
}
export function isAssignedReceipt(v:unknown):v is AssignedReceipt {
  if(!object(v)||!id(v.storeId)||!id(v.orderId)||!text(v.orderNo,100)||!state(v.status)||typeof v.canConfirm!=='boolean'
    ||!time(v.receivedAt)||!Array.isArray(v.items)||v.items.length>500||!v.items.every(item))return false;
  const keys=v.items.map(deliveryItemKey);if(new Set(keys).size!==keys.length)return false;
  if(v.status==='COMPLETED'||!v.canConfirm)return !v.canConfirm&&v.confirmationKey===null&&v.receiverName===null&&v.receiverPhone===null&&v.receiverAddress===null
    &&v.items.length===0&&(v.status==='COMPLETED'?v.blockReason===null:text(v.blockReason,500));
  return v.blockReason===null&&text(v.receiverName,100)&&text(v.receiverPhone,50)&&text(v.receiverAddress,500)
    &&typeof v.confirmationKey==='string'&&/^[a-f0-9]{64}$/.test(v.confirmationKey)&&v.items.length>0&&v.receivedAt===null;
}
export function isDeliveryResult(v:unknown):v is DeliveryResult {
  return object(v)&&id(v.storeId)&&id(v.orderId)&&v.status==='COMPLETED'&&time(v.receivedAt);
}
