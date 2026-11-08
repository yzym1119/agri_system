import { isPositiveLongId as id } from './ids.js';
export type PickupOrderLine={requestId:string;productName:string|null;unit:string|null;remainingQuantity:string|null;
 status:'PENDING'|'FULFILLED';canConfirm:boolean;blockReason:string|null;fulfilledAt:string|null};
export type PickupOrder={orderId:string;orderNo:string|null;customerLabel:string|null;pendingCount:number;fulfilledCount:number;lines:PickupOrderLine[]};
export type PickupOrders={storeId:string;state:'PENDING'|'FULFILLED';orders:PickupOrder[];nextCursor:string|null};
export type PickupAllocation={id:string;batchId:string;internalBatchNo:string;locationId:string;locationName:string;quantity:string;unit:string};
export type PickupPreview={storeId:string;requestId:string;orderId:string;orderNo:string|null;productName:string|null;status:'PENDING'|'FULFILLED';canConfirm:boolean;blockReason:string|null;confirmationKey:string|null;allocations:PickupAllocation[];fulfilledAt:string|null};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const text=(v:unknown,max=200):v is string=>typeof v==='string'&&!!v.trim()&&v.length<=max;
const label=(v:unknown)=>v===null||text(v);
const time=(v:unknown)=>v===null||(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/.test(v));
const state=(v:unknown):v is PickupOrders['state']=>v==='PENDING'||v==='FULFILLED';
const count=(v:unknown):v is number=>typeof v==='number'&&Number.isInteger(v)&&v>=0&&v<=50;
const greater=(a:string,b:string)=>a.length===b.length?a>b:a.length>b.length;
const quantity=(v:unknown)=>typeof v==='string'&&/^(0|[1-9]\d{0,11})(\.\d{1,8})?$/.test(v)&&/[1-9]/.test(v);
function allocation(v:unknown):v is PickupAllocation {
 return object(v)&&id(v.id)&&id(v.batchId)&&id(v.locationId)&&text(v.internalBatchNo)&&text(v.locationName)&&text(v.unit,50)&&quantity(v.quantity);
}
function orderLine(v:unknown):v is PickupOrderLine {
 if(!object(v)||!id(v.requestId)||!label(v.productName)||!state(v.status)||typeof v.canConfirm!=='boolean'
  ||!label(v.blockReason)||!time(v.fulfilledAt))return false;
 if(v.unit!==null&&!text(v.unit,50))return false;
 if(v.remainingQuantity!==null&&!quantity(v.remainingQuantity))return false;
 // 已成交付的请求不反推历史数量；可确认的行必须同时给出单位和剩余可交数量。
 if(v.status==='PENDING')return v.fulfilledAt===null&&(!v.canConfirm||(v.unit!==null&&v.remainingQuantity!==null&&v.blockReason===null));
 return !v.canConfirm&&v.unit===null&&v.remainingQuantity===null&&v.blockReason===null;
}
function order(v:unknown):v is PickupOrder {
 if(!object(v)||!id(v.orderId)||!label(v.orderNo)||!count(v.pendingCount)||!count(v.fulfilledCount)
  ||(v.customerLabel!==null&&!text(v.customerLabel,100))
  ||!Array.isArray(v.lines)||!v.lines.length||v.lines.length>50||!v.lines.every(orderLine))return false;
 if(new Set(v.lines.map(line=>line.requestId)).size!==v.lines.length)return false;
 const pending=v.lines.filter(line=>line.status==='PENDING').length;
 return v.pendingCount===pending&&v.fulfilledCount===v.lines.length-pending;
}
export function isPickupPreview(v:unknown):v is PickupPreview {
 if(!object(v)||!id(v.storeId)||!id(v.requestId)||!id(v.orderId)||!label(v.orderNo)||!label(v.productName)||!state(v.status)
 ||typeof v.canConfirm!=='boolean'||!time(v.fulfilledAt)||!Array.isArray(v.allocations)||v.allocations.length>500||!v.allocations.every(allocation))return false;
 if(new Set(v.allocations.map(a=>a.id)).size!==v.allocations.length)return false;
 if(v.status==='FULFILLED'||!v.canConfirm)return !v.canConfirm&&v.confirmationKey===null&&v.allocations.length===0
 &&(v.status==='FULFILLED'?v.blockReason===null:text(v.blockReason,500));
 return text(v.productName)&&v.blockReason===null&&typeof v.confirmationKey==='string'&&/^[a-f0-9]{64}$/.test(v.confirmationKey)&&v.allocations.length>0&&v.fulfilledAt===null;
}
export function isPickupOrders(v:unknown):v is PickupOrders {
 if(!object(v)||!id(v.storeId)||!state(v.state)||!Array.isArray(v.orders)||v.orders.length>20
  ||!(v.nextCursor===null||id(v.nextCursor))||!v.orders.every(order))return false;
 const ids:string[]=[];let previous:string|null=null;
 for(const item of v.orders){
  if(ids.includes(item.orderId)||(previous!==null&&!greater(previous,item.orderId)))return false;
  ids.push(item.orderId);previous=item.orderId;
 }
 // 游标按订单倒序分页；筛选状态的订单必须真的含该状态项，不能把已交完的单留在待交列表。
 if(v.orders.some(item=>v.state==='PENDING'?item.pendingCount<1:item.fulfilledCount<1))return false;
 const requests=v.orders.flatMap(item=>item.lines.map(line=>line.requestId));
 if(new Set(requests).size!==requests.length)return false;
 return v.nextCursor===null||(v.orders.length===20&&v.nextCursor===previous);
}
