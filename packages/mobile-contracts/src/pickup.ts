import { isPositiveLongId as id } from './ids.js';
export type PickupTask={requestId:string;orderNo:string|null;productName:string|null;status:'PENDING'|'FULFILLED';createdAt:string|null;fulfilledAt:string|null};
export type PickupTasks={storeId:string;state:'PENDING'|'FULFILLED';tasks:PickupTask[];nextCursor:string|null};
export type PickupAllocation={id:string;batchId:string;internalBatchNo:string;locationId:string;locationName:string;quantity:string;unit:string};
export type PickupPreview={storeId:string;requestId:string;orderId:string;orderNo:string|null;productName:string|null;status:'PENDING'|'FULFILLED';canConfirm:boolean;blockReason:string|null;confirmationKey:string|null;allocations:PickupAllocation[];fulfilledAt:string|null};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const text=(v:unknown,max=200):v is string=>typeof v==='string'&&!!v.trim()&&v.length<=max;
const label=(v:unknown)=>v===null||text(v);
const time=(v:unknown)=>v===null||(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/.test(v));
const state=(v:unknown):v is PickupTasks['state']=>v==='PENDING'||v==='FULFILLED';
const greater=(a:string,b:string)=>a.length===b.length?a>b:a.length>b.length;
function allocation(v:unknown):v is PickupAllocation {
 return object(v)&&id(v.id)&&id(v.batchId)&&id(v.locationId)&&text(v.internalBatchNo)&&text(v.locationName)&&text(v.unit,50)
 &&typeof v.quantity==='string'&&/^(0|[1-9]\d{0,11})(\.\d{1,8})?$/.test(v.quantity)&&/[1-9]/.test(v.quantity);
}
export function isPickupPreview(v:unknown):v is PickupPreview {
 if(!object(v)||!id(v.storeId)||!id(v.requestId)||!id(v.orderId)||!label(v.orderNo)||!label(v.productName)||!state(v.status)
 ||typeof v.canConfirm!=='boolean'||!time(v.fulfilledAt)||!Array.isArray(v.allocations)||v.allocations.length>500||!v.allocations.every(allocation))return false;
 if(new Set(v.allocations.map(a=>a.id)).size!==v.allocations.length)return false;
 if(v.status==='FULFILLED'||!v.canConfirm)return !v.canConfirm&&v.confirmationKey===null&&v.allocations.length===0
 &&(v.status==='FULFILLED'?v.blockReason===null:text(v.blockReason,500));
 return text(v.productName)&&v.blockReason===null&&typeof v.confirmationKey==='string'&&/^[a-f0-9]{64}$/.test(v.confirmationKey)&&v.allocations.length>0&&v.fulfilledAt===null;
}
export function isPickupTasks(v:unknown):v is PickupTasks {
 if(!object(v)||!id(v.storeId)||!state(v.state)||!Array.isArray(v.tasks)||v.tasks.length>20||!(v.nextCursor===null||id(v.nextCursor)))return false;
 let previous:string|null=null;
 for(const t of v.tasks){
  if(!object(t)||!id(t.requestId)||!label(t.orderNo)||!label(t.productName)||t.status!==v.state||!time(t.createdAt)||!time(t.fulfilledAt)||(previous!==null&&!greater(previous,t.requestId)))return false;
  previous=t.requestId;
 }
 return v.nextCursor===null||(v.tasks.length===20&&v.nextCursor===previous);
}
