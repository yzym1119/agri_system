import { isPositiveLongId as id } from './ids.js';
import type { RecoveryActor } from './submission.js';

export type ReceivingUnit={id:string;name:string;baseQuantityPerUnit:string};
export type ReceivingItem={id:string;productId:string;productCode:string;productName:string;orderedQuantity:string;receivedBaseQuantity:string;
  remainingBaseQuantity:string;orderedUnit:string;baseUnitName:string;allowFractionalBaseQuantity:boolean;units:ReceivingUnit[];scanCodes:string[]};
export type ReceivingDetail={id:string;storeId:string;orderNo:string;supplierName:string|null;items:ReceivingItem[]};
export type ReceivingOrders={storeId:string;page:number;hasMore:boolean;orders:{id:string;orderNo:string;supplierName:string|null}[]};
export type ReceiptBatch={originalUnit:string;originalQuantity:string;originalUnitCost:string;supplierBatchNo:string|null;productionDate:string|null;
  expiryDate:string|null;inspectionReference:string|null;baseQuantityPerUnit:string;baseQuantity:string};
export type ReceiptSnapshot={purchaseOrderId:string;orderNo:string;remark:string|null;exceptionReason:string|null;lines:{purchaseOrderItemId:string;productId:string;
  productName:string;baseUnitName:string;allowFractionalBaseQuantity:boolean;remark:string|null;batches:ReceiptBatch[]}[]};
export type ReceiptResult={requestKey:string;status:'UNKNOWN'|'COMPLETED';storeId:string;purchaseOrderId:string|null;receiptId:string|null;receiptNo:string|null;
  lines:{purchaseOrderItemId:string;productId:string;remark:string|null;batches:{id:string;internalBatchNo:string;supplierBatchNo:string|null;originalUnitName:string;
    originalQuantity:string;originalUnitCost:string;productionDate:string|null;expiryDate:string|null;dateStatus:'UNKNOWN'|'PARTIAL'|'KNOWN';inspectionReference:string|null}[]}[]};
export type PendingReceipt={schemaVersion:1;kind:'RECEIPT';requestKey:string;scope:{staffId:string;storeId:string};createdAt:number;snapshot:ReceiptSnapshot};
type Storage={readStorage(key:string):string|null;writeStorage(key:string,value:string):void;removeStorage(key:string):void};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const text=(v:unknown,max=200):v is string=>typeof v==='string'&&!!v.trim()&&v.length<=max;
const optionalText=(v:unknown,max=500)=>v===null||(typeof v==='string'&&v.length<=max);
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(v);
function plain(v:unknown):string {
  if(typeof v!=='string'||v.length>64||!/^\d+(\.\d+)?$/.test(v.trim()))throw Error('DECIMAL_INVALID');
  const [whole='',fraction='']=v.trim().split('.');const w=whole.replace(/^0+/,'')||'0',f=fraction.replace(/0+$/,'');return w+(f?'.'+f:'');
}
function decimal(v:unknown,zero=false):string {
  const p=plain(v),[w='',f='']=p.split('.');if(w.length>12||f.length>8||(!zero&&p==='0'))throw Error('DECIMAL_INVALID');return p;
}
const isDecimal=(v:unknown,zero=false):v is string=>{try{return decimal(v,zero)===v;}catch{return false;}};
const scaled8=(v:string)=>{const [w='',f='']=v.split('.');return (w+f.padEnd(8,'0')).replace(/^0+/,'')||'0';};
function addQuantity(a:string,b:string):string {
  const x=scaled8(a),y=scaled8(b);let result='',carry=0;
  for(let i=x.length-1,j=y.length-1;i>=0||j>=0||carry;i--,j--){const n=Number(x[i]??0)+Number(y[j]??0)+carry;result=String(n%10)+result;carry=Math.floor(n/10);}
  const digits=result.padStart(9,'0');return plain(digits.slice(0,-8)+'.'+digits.slice(-8));
}
function greaterQuantity(a:string,b:string){const x=scaled8(a),y=scaled8(b);return x.length!==y.length?x.length>y.length:x>y;}
const optionalDate=(v:unknown)=>v===null||(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(v)&&!Number.isNaN(Date.parse(v+'T00:00:00Z'))&&new Date(v+'T00:00:00Z').toISOString().slice(0,10)===v);
function nullableText(v:unknown,max=500):string|null {if(v===undefined||v===null||v==='')return null;if(typeof v!=='string'||v.length>max)throw Error('TEXT_INVALID');return v.trim()||null;}
function date(v:unknown):string|null {if(v===''||v===undefined)return null;if(!optionalDate(v))throw Error('DATE_INVALID');return v as string|null;}

/** 有界十进制字符串乘法，避免成本/数量有效尾数在浮点或旧手机BigInt支持上丢失。 */
export function receiptMultiply(a:string,b:string):string {
  const x=plain(a),y=plain(b),scale=(x.split('.')[1]?.length??0)+(y.split('.')[1]?.length??0);
  const u=x.replace('.',''),v=y.replace('.',''),out=Array<number>(u.length+v.length).fill(0);
  for(let i=u.length-1;i>=0;i--)for(let j=v.length-1;j>=0;j--){const k=i+j+1,n=out[k]!+Number(u[i])*Number(v[j]);out[k]=n%10;out[k-1]=out[k-1]!+Math.floor(n/10);}
  const digits=out.join('').padStart(scale+1,'0');return plain(scale?digits.slice(0,-scale)+'.'+digits.slice(-scale):digits);
}
export function isReceivingDetail(v:unknown):v is ReceivingDetail {
  if(!object(v)||!id(v.id)||!id(v.storeId)||!text(v.orderNo,100)||!optionalText(v.supplierName,200)||!Array.isArray(v.items)||!v.items.length||v.items.length>200)return false;
  const seen=new Set<string>();
  return v.items.every(i=>{
    if(!object(i)||!id(i.id)||seen.has(i.id)||!id(i.productId)||!text(i.productCode,100)||!text(i.productName)||!isDecimal(i.orderedQuantity)
      ||!isDecimal(i.receivedBaseQuantity,true)||!isDecimal(i.remainingBaseQuantity,true)||!text(i.orderedUnit,50)||!text(i.baseUnitName,50)
      ||typeof i.allowFractionalBaseQuantity!=='boolean'||!Array.isArray(i.units)||!i.units.length||i.units.length>50
      ||!Array.isArray(i.scanCodes)||i.scanCodes.length>52||!i.scanCodes.every(c=>text(c,100)))return false;
    seen.add(i.id);const names=new Set<string>();
    return i.units.every(u=>{if(!object(u)||!id(u.id)||!text(u.name,50)||names.has(u.name)||!isDecimal(u.baseQuantityPerUnit))return false;names.add(u.name);return true;})&&names.has(i.orderedUnit);
  });
}
export function isReceivingOrders(v:unknown):v is ReceivingOrders {
  return object(v)&&id(v.storeId)&&Number.isSafeInteger(v.page)&&Number(v.page)>0&&Number(v.page)<=10000&&typeof v.hasMore==='boolean'
    &&Array.isArray(v.orders)&&v.orders.length<=20&&v.orders.every(o=>object(o)&&id(o.id)&&text(o.orderNo,100)&&optionalText(o.supplierName,200));
}
export function buildReceiptSnapshot(detail:unknown,lines:unknown,options:unknown):ReceiptSnapshot {
  if(!isReceivingDetail(detail)||!Array.isArray(lines)||!lines.length||lines.length>200||!object(options))throw Error('RECEIPT_INVALID');
  const seen=new Set<string>();
  const exceptionReason=nullableText(options.exceptionReason);
  const snapshot:ReceiptSnapshot={purchaseOrderId:detail.id,orderNo:detail.orderNo,remark:nullableText(options.remark),exceptionReason,lines:lines.map(line=>{
    if(!object(line)||!id(line.purchaseOrderItemId)||seen.has(line.purchaseOrderItemId)||!Array.isArray(line.batches)||!line.batches.length||line.batches.length>50)throw Error('RECEIPT_INVALID');
    seen.add(line.purchaseOrderItemId);const item=detail.items.find(i=>i.id===line.purchaseOrderItemId);if(!item)throw Error('RECEIPT_INVALID');
    const batches:ReceiptBatch[]=line.batches.map(b=>{
      if(!object(b))throw Error('RECEIPT_INVALID');const unit=item.units.find(u=>u.name===b.originalUnit);if(!unit)throw Error('UNIT_INVALID');
      const quantity=decimal(b.originalQuantity),cost=decimal(b.originalUnitCost),base=decimal(receiptMultiply(quantity,unit.baseQuantityPerUnit));
      if(!item.allowFractionalBaseQuantity&&base.includes('.'))throw Error('QUANTITY_GRANULARITY');
      const production=date(b.productionDate),expiry=date(b.expiryDate);
      if((production&&expiry&&production>expiry)||(!production&&!expiry&&b.unknownDates!==true))throw Error('DATE_CONFIRMATION_REQUIRED');
      return {originalUnit:unit.name,originalQuantity:quantity,originalUnitCost:cost,baseQuantityPerUnit:unit.baseQuantityPerUnit,baseQuantity:base,
        supplierBatchNo:nullableText(b.supplierBatchNo,100),productionDate:production,expiryDate:expiry,inspectionReference:nullableText(b.inspectionReference,100)};
    });
    const total=decimal(batches.reduce((sum,batch)=>addQuantity(sum,batch.baseQuantity),'0'));
    if(greaterQuantity(total,item.remainingBaseQuantity)&&!exceptionReason)throw Error('OVER_RECEIPT_REASON_REQUIRED');
    return {purchaseOrderItemId:item.id,productId:item.productId,productName:item.productName,baseUnitName:item.baseUnitName,allowFractionalBaseQuantity:item.allowFractionalBaseQuantity,remark:nullableText(line.remark),batches};
  })};
  return copySnapshot(snapshot);
}
function copySnapshot(v:unknown):ReceiptSnapshot {
  if(!object(v)||!id(v.purchaseOrderId)||!text(v.orderNo,100)||!optionalText(v.remark)||!optionalText(v.exceptionReason)||!Array.isArray(v.lines)||!v.lines.length||v.lines.length>200)throw Error('SNAPSHOT_INVALID');
  const seen=new Set<string>();let count=0;
  const lines=v.lines.map(l=>{
    if(!object(l)||!id(l.purchaseOrderItemId)||seen.has(l.purchaseOrderItemId)||!id(l.productId)||!text(l.productName)||!text(l.baseUnitName,50)
      ||typeof l.allowFractionalBaseQuantity!=='boolean'||!optionalText(l.remark)||!Array.isArray(l.batches)||!l.batches.length||l.batches.length>50)throw Error('SNAPSHOT_INVALID');
    seen.add(l.purchaseOrderItemId);
    const batches=l.batches.map(b=>{
      count++;if(!object(b)||!text(b.originalUnit,50)||!isDecimal(b.originalQuantity)||!isDecimal(b.originalUnitCost)||!isDecimal(b.baseQuantityPerUnit)||!isDecimal(b.baseQuantity)
        ||receiptMultiply(b.originalQuantity,b.baseQuantityPerUnit)!==b.baseQuantity||(!l.allowFractionalBaseQuantity&&b.baseQuantity.includes('.'))
        ||!optionalText(b.supplierBatchNo,100)||!optionalText(b.inspectionReference,100)||!optionalDate(b.productionDate)||!optionalDate(b.expiryDate)
        ||(typeof b.productionDate==='string'&&typeof b.expiryDate==='string'&&b.productionDate>b.expiryDate))throw Error('SNAPSHOT_INVALID');
      return {originalUnit:b.originalUnit,originalQuantity:b.originalQuantity,originalUnitCost:b.originalUnitCost,baseQuantityPerUnit:b.baseQuantityPerUnit,baseQuantity:b.baseQuantity,
        supplierBatchNo:nullableText(b.supplierBatchNo,100),inspectionReference:nullableText(b.inspectionReference,100),productionDate:b.productionDate as string|null,expiryDate:b.expiryDate as string|null};
    });
    return {purchaseOrderItemId:l.purchaseOrderItemId,productId:l.productId,productName:l.productName,baseUnitName:l.baseUnitName,allowFractionalBaseQuantity:l.allowFractionalBaseQuantity,remark:nullableText(l.remark),batches};
  });
  if(count>200)throw Error('SNAPSHOT_INVALID');
  return {purchaseOrderId:v.purchaseOrderId,orderNo:v.orderNo,remark:nullableText(v.remark),exceptionReason:nullableText(v.exceptionReason),lines};
}
export function isReceiptResult(v:unknown):v is ReceiptResult {
  if(!object(v)||!text(v.requestKey,64)||!id(v.storeId)||!Array.isArray(v.lines))return false;
  if(v.status==='UNKNOWN')return v.purchaseOrderId===null&&v.receiptId===null&&v.receiptNo===null&&v.lines.length===0;
  if(v.status!=='COMPLETED'||!id(v.purchaseOrderId)||!id(v.receiptId)||!text(v.receiptNo,100)||!v.lines.length||v.lines.length>200)return false;
  const ids=new Set<string>(),lineIds=new Set<string>();let count=0;
  return v.lines.every(l=>{
    if(!object(l)||!id(l.purchaseOrderItemId)||lineIds.has(l.purchaseOrderItemId)||!id(l.productId)||!optionalText(l.remark)||!Array.isArray(l.batches)||!l.batches.length||l.batches.length>50)return false;
    lineIds.add(l.purchaseOrderItemId);
    return l.batches.every(b=>{
      count++;if(!object(b)||!id(b.id)||ids.has(b.id)||!text(b.internalBatchNo,100)||!optionalText(b.supplierBatchNo,100)||!text(b.originalUnitName,50)
        ||!isDecimal(b.originalQuantity)||!isDecimal(b.originalUnitCost)||!optionalDate(b.productionDate)||!optionalDate(b.expiryDate)||!optionalText(b.inspectionReference,100))return false;
      ids.add(b.id);const expected=b.productionDate&&b.expiryDate?'KNOWN':b.productionDate||b.expiryDate?'PARTIAL':'UNKNOWN';
      return b.dateStatus===expected&&!(typeof b.productionDate==='string'&&typeof b.expiryDate==='string'&&b.productionDate>b.expiryDate);
    });
  })&&count<=200;
}
const fact=(b:{originalQuantity:string;originalUnitCost:string;supplierBatchNo:string|null;productionDate:string|null;expiryDate:string|null;inspectionReference:string|null},unit:string)=>
  JSON.stringify([unit,b.originalQuantity,b.originalUnitCost,nullableText(b.supplierBatchNo,100),b.productionDate,b.expiryDate,nullableText(b.inspectionReference,100)]);
function checkedResult(v:unknown,record:PendingReceipt):ReceiptResult {
  if(!isReceiptResult(v)||v.requestKey!==record.requestKey||v.storeId!==record.scope.storeId)throw Error('RESPONSE_INVALID');
  if(v.status==='COMPLETED') {
    const s=record.snapshot;if(v.purchaseOrderId!==s.purchaseOrderId||v.lines.length!==s.lines.length)throw Error('FACT_MISMATCH');
    for(const line of s.lines){const actual=v.lines.find(l=>l.purchaseOrderItemId===line.purchaseOrderItemId);
      if(!actual||actual.productId!==line.productId||nullableText(actual.remark)!==line.remark
        ||JSON.stringify(actual.batches.map(b=>fact(b,b.originalUnitName)).sort())!==JSON.stringify(line.batches.map(b=>fact(b,b.originalUnit)).sort()))throw Error('FACT_MISMATCH');}
  }
  return JSON.parse(JSON.stringify(v)) as ReceiptResult;
}
function wire(record:PendingReceipt) {
  const s=record.snapshot;
  return freeze({requestKey:record.requestKey,purchaseOrderId:s.purchaseOrderId,remark:s.remark,exceptionReason:s.exceptionReason,
    lines:s.lines.map(l=>({purchaseOrderItemId:l.purchaseOrderItemId,productId:l.productId,remark:l.remark,batches:l.batches.map(b=>({
      originalUnit:b.originalUnit,originalQuantity:b.originalQuantity,originalUnitCost:b.originalUnitCost,supplierBatchNo:b.supplierBatchNo,
      productionDate:b.productionDate,expiryDate:b.expiryDate,inspectionReference:b.inspectionReference}))}))});
}
function freeze<T>(v:T):T {if(v&&typeof v==='object'){for(const child of Object.values(v))freeze(child);Object.freeze(v);}return v;}
type Send=(command:ReturnType<typeof wire>,actor:RecoveryActor)=>Promise<unknown>;
type Read=(key:string,actor:RecoveryActor)=>Promise<unknown>;

/** 收货专用原请求协议；不套用POS取消状态，没有强制结束未知结果的入口。 */
export function createReceiptRecovery(storage:Storage,currentActor:()=>RecoveryActor|null,createKey:()=>Promise<string>) {
  const observed=new Map<string,string>(),blocked=new Set<string>(),busy=new Set<string>();const MAX=200000;
  function actor():RecoveryActor {const a=currentActor();if(!a||!id(a.staffId)||!id(a.storeId)||!Number.isSafeInteger(a.epoch)||a.epoch<1)throw Error('UNAUTHORIZED');return Object.freeze({staffId:a.staffId,storeId:a.storeId,epoch:a.epoch});}
  const key=(a:RecoveryActor)=>`agri:receipt-pending:v1:${a.staffId}:${a.storeId}`;
  function same(a:RecoveryActor){const now=currentActor();if(!now||now.staffId!==a.staffId||now.storeId!==a.storeId||now.epoch!==a.epoch)throw Error('STALE_SESSION');}
  function read(a:RecoveryActor):PendingReceipt|null {
    const k=key(a);if(blocked.has(k))throw Error('STORAGE_BLOCKED');
    try{const raw=storage.readStorage(k);if(observed.has(k)&&observed.get(k)!==raw)throw Error();if(raw===null)return null;
      if(typeof raw!=='string'||raw.length>MAX)throw Error();const r:unknown=JSON.parse(raw);
      if(!object(r)||r.schemaVersion!==1||r.kind!=='RECEIPT'||!uuid(r.requestKey)||!object(r.scope)||r.scope.staffId!==a.staffId||r.scope.storeId!==a.storeId
        ||!Number.isSafeInteger(r.createdAt)||Number(r.createdAt)<1)throw Error();
      const result:PendingReceipt={schemaVersion:1,kind:'RECEIPT',requestKey:r.requestKey,scope:{staffId:a.staffId,storeId:a.storeId},createdAt:Number(r.createdAt),snapshot:copySnapshot(r.snapshot)};
      observed.set(k,raw);return result;
    }catch{blocked.add(k);throw Error('STORAGE_BLOCKED');}
  }
  async function query(original:PendingReceipt,a:RecoveryActor,lookup:Read){let result:unknown;try{result=await lookup(original.requestKey,a);}catch{same(a);read(a);throw Error('RESULT_UNKNOWN');}
    same(a);read(a);return checkedResult(result,original);}
  async function send(original:PendingReceipt,a:RecoveryActor,request:Send){try{await request(wire(original),a);}catch{/* 无响应也不改变原请求，后续只读核对。 */}same(a);read(a);}
  async function action(kind:'read'|'retry'|'finish',confirmed:boolean,expected:string|null,request:Send|null,lookup:Read) {
    if(kind!=='read'&&!confirmed)throw Error('CONFIRMATION_REQUIRED');const a=actor(),k=key(a);if(busy.has(k))throw Error('BUSY');
    const original=read(a);if(!original)throw Error('NO_PENDING_REQUEST');busy.add(k);
    try{let result=await query(original,a,lookup);
      if(kind==='retry'&&result.status==='UNKNOWN'){await send(original,a,request!);result=await query(original,a,lookup);}
      if(kind==='finish'){
        if(result.status!=='COMPLETED'||result.receiptId!==expected)throw Error('RESULT_CHANGED');
        read(a);try{storage.removeStorage(k);if(storage.readStorage(k)!==null)throw Error();observed.delete(k);}catch{blocked.add(k);throw Error('STORAGE_BLOCKED');}
      }return result;
    }finally{busy.delete(k);}
  }
  return {
    restore:()=>read(actor()),
    async submit(snapshot:unknown,request:Send,lookup:Read){
      const a=actor(),k=key(a);if(busy.has(k))throw Error('BUSY');if(read(a))throw Error('RECOVERY_REQUIRED');const copy=copySnapshot(snapshot);busy.add(k);
      try{const requestKey=await createKey();same(a);if(!uuid(requestKey))throw Error('REQUEST_KEY_INVALID');if(read(a))throw Error('RECOVERY_REQUIRED');
        const record:PendingReceipt={schemaVersion:1,kind:'RECEIPT',requestKey,scope:{staffId:a.staffId,storeId:a.storeId},createdAt:Date.now(),snapshot:copy};
        const raw=JSON.stringify(record);if(raw.length>MAX)throw Error('SNAPSHOT_INVALID');
        try{storage.writeStorage(k,raw);if(storage.readStorage(k)!==raw)throw Error();observed.set(k,raw);}catch{blocked.add(k);throw Error('STORAGE_BLOCKED');}
        await send(record,a,request);return await query(record,a,lookup);
      }finally{busy.delete(k);}
    },
    lookupOriginal:(lookup:Read)=>action('read',false,null,null,lookup),
    retryOriginal:(confirmed:boolean,request:Send,lookup:Read)=>action('retry',confirmed,null,request,lookup),
    finishCompleted:(receiptId:string,confirmed:boolean,lookup:Read)=>action('finish',confirmed,receiptId,null,lookup),
  };
}
