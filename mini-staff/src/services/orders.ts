import { isOwnOrders,isOwnOrderDetail,ownOrderPath,type OwnOrderFilter } from '@agri/mobile-contracts/staff-orders';
import { staffAuth } from './auth';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
async function own<T extends {storeId:string}>(url:string,epoch:number,validate:(v:unknown)=>v is T):Promise<T> {
 const actor=staffAuth.current();if(!actor||actor.epoch!==epoch)throw Error('STALE_SESSION');
 const result=await staffAuth.request({url,method:'GET'},validate,epoch);
 if(result.storeId!==actor.storeId)throw Error('STORE_MISMATCH');return result;
}
export async function readOwnOrders(filter:OwnOrderFilter,epoch:number) {
 const result=await own(ownOrderPath(filter),epoch,isOwnOrders);
 const greater=(a:string,b:string)=>a.length===b.length?a>b:a.length>b.length;
 if(result.orders.some(o=>(filter.state!==undefined&&o.state!==filter.state)||(filter.after&&!greater(filter.after,o.orderId))
   ||(filter.from&&(!o.createdAt||o.createdAt.slice(0,10)<filter.from))||(filter.to&&(!o.createdAt||o.createdAt.slice(0,10)>filter.to))))throw Error('RESPONSE_INVALID');
 return result;
}
export async function readOwnOrder(orderId:string,epoch:number) {
 if(!isPositiveLongId(orderId))throw Error('ORDER_ID_INVALID');
 const result=await own('/api/admin/mobile/orders/'+encodeURIComponent(orderId),epoch,isOwnOrderDetail);
 if(result.order.orderId!==orderId)throw Error('RESPONSE_INVALID');return result;
}
