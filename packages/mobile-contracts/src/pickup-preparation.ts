import { isPositiveLongId as id } from './ids.js';
import type { PickupAllocation } from './pickup.js';
export type PreparationLine={requestId:string;productName:string|null;productCode:string|null;status:'PENDING'|'FULFILLED';canPrepare:boolean;blockReason:string|null;revision:string|null;allocations:PickupAllocation[];fulfilledAt:string|null};
export type PickupPreparation={storeId:string;orderId:string;orderNo:string|null;customerLabel:string|null;pendingCount:number;fulfilledCount:number;lines:PreparationLine[]};
export type PreparationChecks=Record<string,string[]>;
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,fields:string[])=>Object.keys(v).length===fields.length&&fields.every(k=>k in v);
const text=(v:unknown,max=200):v is string=>typeof v==='string'&&!!v.trim()&&v.length<=max;
const label=(v:unknown,max=200)=>v===null||text(v,max);
const revision=(v:unknown):v is string=>typeof v==='string'&&/^[a-f0-9]{64}$/.test(v);
const quantity=(v:unknown)=>typeof v==='string'&&/^(0|[1-9]\d{0,11})(\.\d{1,8})?$/.test(v)&&/[1-9]/.test(v);
const time=(v:unknown)=>v===null||(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/.test(v));
const count=(v:unknown):v is number=>typeof v==='number'&&Number.isInteger(v)&&v>=0&&v<=1000;
function line(v:unknown):v is PreparationLine {
 if(!object(v)||!exact(v,['requestId','productName','productCode','status','canPrepare','blockReason','revision','allocations','fulfilledAt'])||!id(v.requestId)||!label(v.productName)||!label(v.productCode)
   ||!['PENDING','FULFILLED'].includes(v.status as string)||typeof v.canPrepare!=='boolean'||!time(v.fulfilledAt)||!Array.isArray(v.allocations)||v.allocations.length>500)return false;
 if(v.status==='FULFILLED'||!v.canPrepare)return !v.canPrepare&&v.revision===null&&v.allocations.length===0&&(v.status==='FULFILLED'?v.blockReason===null:text(v.blockReason,500))&&(v.status==='FULFILLED'||v.fulfilledAt===null);
 return v.fulfilledAt===null&&text(v.productName)&&v.blockReason===null&&revision(v.revision)&&v.allocations.length>0&&v.allocations.every(a=>object(a)
   &&exact(a,['id','batchId','internalBatchNo','locationId','locationName','quantity','unit'])&&id(a.id)&&id(a.batchId)&&id(a.locationId)&&text(a.internalBatchNo)&&text(a.locationName)&&text(a.unit,50)&&quantity(a.quantity));
}
export function isPickupPreparation(v:unknown):v is PickupPreparation {
 if(!object(v)||!exact(v,['storeId','orderId','orderNo','customerLabel','pendingCount','fulfilledCount','lines'])||!id(v.storeId)||!id(v.orderId)||!label(v.orderNo)||!label(v.customerLabel,100)
   ||!count(v.pendingCount)||!count(v.fulfilledCount)||!Array.isArray(v.lines)||v.lines.length<1||v.lines.length>1000||!v.lines.every(line))return false;
 const pending=v.lines.filter(l=>l.status==='PENDING').length,allocations=v.lines.flatMap(l=>l.allocations.map(a=>a.id));
 return v.pendingCount===pending&&v.fulfilledCount===v.lines.length-pending&&new Set(v.lines.map(l=>l.requestId)).size===v.lines.length&&new Set(allocations).size===allocations.length;
}
type Port={readStorage(key:string):string|null;writeStorage(key:string,value:string):void;removeStorage(key:string):void};
type SavedLine={requestId:string;revision:string;allocationIds:string[]};
const compare=(a:string,b:string)=>a.length-b.length||a.localeCompare(b);
/** 个人准备辅助，独立于原交货pending记录；从不产生可用于交货的确认令牌。 */
export function createPickupPreparationProgress(port:Port,actor:{staffId:string;storeId:string},orderId:string) {
 const staffId=actor.staffId,storeId=actor.storeId;
 if(!id(staffId)||!id(storeId)||!id(orderId))throw Error('PREPARATION_SCOPE_INVALID');
 const scope=staffId+':'+storeId+':'+orderId,key='agri.pickup.preparation.v1:'+scope;
 let expected:string|null=null,loaded=false;
 function verifySnapshot(snapshot:PickupPreparation){if(!isPickupPreparation(snapshot)||snapshot.storeId!==storeId||snapshot.orderId!==orderId)throw Error('PREPARATION_SCOPE_INVALID');}
 function parse(raw:string):SavedLine[]{
  const value:unknown=JSON.parse(raw);
  if(!object(value)||!exact(value,['version','scope','lines'])||value.version!==1||value.scope!==scope||!Array.isArray(value.lines)||value.lines.length>1000)throw Error('PREPARATION_INVALID');
  const requestIds:string[]=[];
  for(const v of value.lines){if(!object(v)||!exact(v,['requestId','revision','allocationIds'])||!id(v.requestId)||!revision(v.revision)||!Array.isArray(v.allocationIds)||v.allocationIds.length<1||v.allocationIds.length>500||!v.allocationIds.every(id)
    ||new Set(v.allocationIds).size!==v.allocationIds.length||requestIds.includes(v.requestId))throw Error('PREPARATION_INVALID');requestIds.push(v.requestId);}
  return value.lines as SavedLine[];
 }
 function persist(lines:SavedLine[]){
  if(!loaded||port.readStorage(key)!==expected)throw Error('PREPARATION_CONFLICT');
  const next=lines.length?JSON.stringify({version:1,scope,lines}):null;
  if(next!==expected){if(next===null)port.removeStorage(key);else port.writeStorage(key,next);if(port.readStorage(key)!==next)throw Error('PREPARATION_SAVE_UNVERIFIED');}
  expected=next;
 }
 return {
  restore(snapshot:PickupPreparation):{checked:PreparationChecks;invalidated:boolean}{
   loaded=false;verifySnapshot(snapshot);const raw=port.readStorage(key),saved=raw===null?[]:parse(raw);expected=raw;loaded=true;
   const valid=saved.filter(old=>snapshot.lines.some(l=>l.requestId===old.requestId&&l.canPrepare&&l.status==='PENDING'&&l.revision===old.revision&&old.allocationIds.every(a=>l.allocations.some(item=>item.id===a))));
   const invalidated=valid.length!==saved.length;if(invalidated)persist(valid);
   return {checked:Object.fromEntries(valid.map(l=>[l.requestId,[...l.allocationIds]])),invalidated};
  },
  save(snapshot:PickupPreparation,checked:PreparationChecks):void {
   verifySnapshot(snapshot);if(!object(checked))throw Error('PREPARATION_CHECK_INVALID');
   const saved:SavedLine[]=[];
   for(const [requestId,ids] of Object.entries(checked)){
    const l=snapshot.lines.find(l=>l.requestId===requestId);
    if(!l?.canPrepare||l.status!=='PENDING'||!l.revision||!Array.isArray(ids)||ids.length>500||new Set(ids).size!==ids.length||!ids.every(a=>id(a)&&l.allocations.some(item=>item.id===a)))throw Error('PREPARATION_CHECK_INVALID');
    if(ids.length)saved.push({requestId,revision:l.revision,allocationIds:[...ids].sort(compare)});
   }
   persist(saved.sort((a,b)=>compare(a.requestId,b.requestId)));
  },
  reset():void {port.removeStorage(key);if(port.readStorage(key)!==null)throw Error('PREPARATION_RESET_FAILED');expected=null;loaded=true;},
 };
}
