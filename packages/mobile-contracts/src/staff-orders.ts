import { isPositiveLongId as id } from './ids.js';
export type OwnFinance={payAmount:string|null;receivedAmount:string|null;creditAmount:string|null;creditReductionAmount:string|null;cashRefundedAmount:string|null;remainingCreditAmount:string|null;repaidAmount:string|null};
export type OwnOrder={orderId:string;orderNo:string;createdAt:string|null;state:number;customerLabel:string;finance:OwnFinance;deliveryType:number|null;deliveryStatus:number|null};
export type OwnOrders={storeId:string;orders:OwnOrder[];nextCursor:string|null};
export type OwnOrderItem={itemId:string;productName:string|null;quantity:string|null;unit:string|null;totalPrice:string|null;refundedQuantity:string|null;refundedAmount:string|null;packageGroupNo:number|null;packageName:string|null;packageQuantity:string|null;packageUnitPrice:string|null};
export type OwnOrderDetail={storeId:string;order:OwnOrder;items:OwnOrderItem[]};
export type OwnOrderFilter={from?:string;to?:string;state?:number;after?:string};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const keys=(v:Record<string,unknown>,names:string[])=>Object.keys(v).length===names.length&&names.every(n=>n in v);
const text=(v:unknown,max=200):v is string=>typeof v==='string'&&!!v.trim()&&v.length<=max;
const optionalText=(v:unknown,max=200)=>v===null||text(v,max);
const decimal=(v:unknown)=>v===null||(typeof v==='string'&&v.length<=40&&/^(0|[1-9]\d*)(\.\d{1,12})?$/.test(v));
const state=(v:unknown):v is number=>typeof v==='number'&&Number.isInteger(v)&&v>=1&&v<=9;
const time=(v:unknown)=>v===null||(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/.test(v));
const greater=(a:string,b:string)=>a.length===b.length?a>b:a.length>b.length;
function summary(v:unknown):v is OwnOrder {
 if(!object(v)||!keys(v,['orderId','orderNo','createdAt','state','customerLabel','finance','deliveryType','deliveryStatus'])||!id(v.orderId)||!text(v.orderNo,100)||!time(v.createdAt)||!state(v.state)||!text(v.customerLabel,100)
   ||!(v.deliveryType===null||[1,2,3].includes(v.deliveryType as number))||!(v.deliveryStatus===null||[1,2,3,4].includes(v.deliveryStatus as number))||!object(v.finance))return false;
 const fields=['payAmount','receivedAmount','creditAmount','creditReductionAmount','cashRefundedAmount','remainingCreditAmount','repaidAmount'];
 return keys(v.finance,fields)&&fields.every(k=>decimal((v.finance as Record<string,unknown>)[k]));
}
function item(v:unknown):v is OwnOrderItem {
 if(!object(v)||!keys(v,['itemId','productName','quantity','unit','totalPrice','refundedQuantity','refundedAmount','packageGroupNo','packageName','packageQuantity','packageUnitPrice'])||!id(v.itemId)||!optionalText(v.productName)||!optionalText(v.unit,50)
   ||![v.quantity,v.totalPrice,v.refundedQuantity,v.refundedAmount,v.packageQuantity,v.packageUnitPrice].every(decimal))return false;
 if(v.packageGroupNo===null)return v.packageName===null&&v.packageQuantity===null&&v.packageUnitPrice===null;
 return typeof v.packageGroupNo==='number'&&Number.isSafeInteger(v.packageGroupNo)&&v.packageGroupNo>0&&text(v.packageName)
   &&typeof v.packageQuantity==='string'&&/^[1-9]\d*$/.test(v.packageQuantity)&&v.packageUnitPrice!==null;
}
export function isOwnOrders(v:unknown):v is OwnOrders {
 if(!object(v)||!keys(v,['storeId','orders','nextCursor'])||!id(v.storeId)||!Array.isArray(v.orders)||v.orders.length>20||!(v.nextCursor===null||id(v.nextCursor)))return false;
 let previous:string|null=null;
 for(const order of v.orders){if(!summary(order)||(previous!==null&&!greater(previous,order.orderId)))return false;previous=order.orderId;}
 return v.nextCursor===null||(v.orders.length===20&&v.nextCursor===previous);
}
export function isOwnOrderDetail(v:unknown):v is OwnOrderDetail {
 return object(v)&&keys(v,['storeId','order','items'])&&id(v.storeId)&&summary(v.order)&&Array.isArray(v.items)&&v.items.length<=5000&&v.items.every(item)
  &&new Set(v.items.map(i=>i.itemId)).size===v.items.length;
}
function date(v:string){return /^\d{4}-\d{2}-\d{2}$/.test(v)&&v>='1900-01-01'&&v<='9998-12-31'&&!Number.isNaN(Date.parse(v+'T00:00:00Z'))&&new Date(v+'T00:00:00Z').toISOString().slice(0,10)===v;}
export function ownOrderPath(filter:OwnOrderFilter):string {
 if((filter.from&&!date(filter.from))||(filter.to&&!date(filter.to))||(filter.from&&filter.to&&filter.from>filter.to)
   ||(filter.state!==undefined&&!state(filter.state))||(filter.after!==undefined&&!id(filter.after)))throw Error('查询日期、状态或游标无效');
 const parts:string[]=[];
 for(const key of ['from','to','state','after'] as const)if(filter[key]!==undefined&&filter[key]!=='')parts.push(key+'='+encodeURIComponent(String(filter[key])));
 return '/api/admin/mobile/orders'+(parts.length?'?'+parts.join('&'):'');
}
export const ownOrderState=(v:number)=>(['','待付款','已付款','待自提','待发货','已发货','已完成','已取消','已退款','部分退款'][v]??'状态待核对');
export function ownDeliveryLabel(type:number|null,status:number|null){
 if(type===null)return '无配送记录，交付情况请核对原单';
 return ({1:'自提',2:'门店配送',3:'快递'}[type]??'交付方式待核对')+' · '+({1:'待付款',2:'待派单',3:'配送中',4:'已签收'}[status??0]??'状态待核对');
}
