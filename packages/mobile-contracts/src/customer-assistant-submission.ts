import { encodeAssistantCommand,decodeAssistantEnd,decodeAssistantReceipt,decodeAssistantResult,type AssistantCommand,type AssistantResult,type createCustomerAssistant } from './customer-assistant.js';
import { isPositiveLongId } from './ids.js';
import type { createCustomerAuth } from './customer-session.js';
export type PendingAssistant={schemaVersion:1;customerId:string;caseId:string;phase:'PREPARED'|'SENT'|'ENDING'|'VERIFIED'|'CANCELLED'|'CLOSED';command:AssistantCommand};
type Options={auth:Pick<ReturnType<typeof createCustomerAuth>,'current'>;api:Pick<ReturnType<typeof createCustomerAssistant>,'submit'|'result'|'end'>;storage:{read(customerId:string,caseId:string):string|null;write(customerId:string,caseId:string,value:string):void};createKey():Promise<string>};
/** 本机原号恢复槽，不是第二份业务结果。业务拒绝不证明多事务协调器未曾提交。 */
export function createAssistantSubmissions({auth,api,storage,createKey}:Options){
 const pins=new Map<string,string|null>();let busy=false;
 function id(v:unknown):string{if(!isPositiveLongId(v))throw Error('ASSISTANT_REQUEST_INVALID');return v;}
 function actor(){const a=auth.current();if(a.kind!=='CUSTOMER')throw Error('CUSTOMER_LOGIN_REQUIRED');return{customerId:id(a.customerId),epoch:a.epoch};}
 type Actor=ReturnType<typeof actor>;
 function same(a:Actor){const b=actor();if(a.customerId!==b.customerId||a.epoch!==b.epoch)throw Error('STALE_SESSION');}
 function raw(a:Actor,caseId:string){try{return storage.read(a.customerId,caseId);}catch{throw Error('STORAGE_READ_FAILED');}}
 function load(a:Actor,caseId:string):PendingAssistant|null{
  same(a);id(caseId);const slot=a.customerId+'/'+caseId,saved=raw(a,caseId);
  if(pins.has(slot)&&pins.get(slot)!==saved)throw Error('STORAGE_RECORD_CHANGED');
  if(saved===null){pins.set(slot,null);return null;}
  try{const r=JSON.parse(saved);if(r.schemaVersion!==1||r.customerId!==a.customerId||r.caseId!==caseId||!['PREPARED','SENT','ENDING','VERIFIED','CANCELLED','CLOSED'].includes(r.phase))throw Error();
   const value:PendingAssistant={schemaVersion:1,customerId:a.customerId,caseId,phase:r.phase,command:encodeAssistantCommand(r.command)};
   if(JSON.stringify(value)!==saved)throw Error();pins.set(slot,saved);return value;
  }catch{throw Error('STORAGE_RECORD_INVALID');}
 }
 function save(a:Actor,next:PendingAssistant,previous:PendingAssistant|null){
  same(a);if(JSON.stringify(load(a,next.caseId))!==JSON.stringify(previous))throw Error('STORAGE_RECORD_CHANGED');
  const text=JSON.stringify(next);pins.set(a.customerId+'/'+next.caseId,text);
  try{storage.write(a.customerId,next.caseId,text);}catch{throw Error('STORAGE_WRITE_FAILED');}
  if(raw(a,next.caseId)!==text)throw Error('STORAGE_WRITE_FAILED');
 }
 const terminal=(r:AssistantResult)=>!['NOT_FOUND','QUEUED','RUNNING','UNKNOWN','CANCELLED','CLOSED'].includes(r.state);
 async function send(a:Actor,record:PendingAssistant):Promise<AssistantResult>{
  same(a);if(JSON.stringify(load(a,record.caseId))!==JSON.stringify(record))throw Error('STORAGE_RECORD_CHANGED');
  let sent=record;if(record.phase==='PREPARED'){sent={...record,phase:'SENT'};save(a,sent,record);}
  const receipt=decodeAssistantReceipt(await api.submit(sent.caseId,sent.command,a.epoch),sent.caseId,sent.command);same(a);
  const result=decodeAssistantResult(await api.result(sent.caseId,sent.command.requestKey,a.epoch),sent.caseId,sent.command.requestKey);same(a);
  if(result.queuedVersion!==receipt.queuedVersion||result.factsVersion!==receipt.factsVersion)throw Error('ASSISTANT_RESPONSE_INVALID');
  if(terminal(result)&&(receipt.state==='COMPLETED'||receipt.state==='FAILED')){
   if((result.state==='NOT_DISPATCHED')!==(receipt.state==='FAILED'))throw Error('ASSISTANT_RESPONSE_INVALID');
   if(sent.phase!=='VERIFIED')save(a,{...sent,phase:'VERIFIED'},sent);
  }
  return result;
 }
 async function endOriginal(a:Actor,record:PendingAssistant):Promise<AssistantResult>{
  same(a);if(JSON.stringify(load(a,record.caseId))!==JSON.stringify(record))throw Error('STORAGE_RECORD_CHANGED');
  let original=record;
  // 结束选择必须先可靠保存；丢响应/重建运行时后不可再走原查询的派出路径。
  if(!['ENDING','CANCELLED','CLOSED'].includes(record.phase)){original={...record,phase:'ENDING'};save(a,original,record);}
  const response=decodeAssistantEnd(await api.end(original.caseId,original.command,a.epoch),original.caseId,original.command);same(a);
  if(response.state==='CANCELLED'||response.state==='CLOSED'){
   if(original.phase!==response.state)save(a,{...original,phase:response.state},original);
   const result=decodeAssistantResult(await api.result(original.caseId,original.command.requestKey,a.epoch),original.caseId,original.command.requestKey);same(a);
   if(result.state!==response.state)throw Error('ASSISTANT_RESPONSE_INVALID');return result;
  }
  return send(a,original);
 }
 async function run<T>(work:()=>Promise<T>){if(busy)throw Error('ASSISTANT_BUSY');busy=true;try{return await work();}finally{busy=false;}}
 return{
  current(caseId:string){return load(actor(),caseId);},
  submit(caseId:string,input:Omit<AssistantCommand,'requestKey'>){return run(async()=>{
   const a=actor(),previous=load(a,caseId);if(previous&&['PREPARED','SENT','ENDING'].includes(previous.phase))throw Error('ASSISTANT_PENDING');
   const requestKey=await createKey();same(a);const command=encodeAssistantCommand({...input,requestKey});
   if(previous&&(command.requestKey===previous.command.requestKey||BigInt(command.expectedVersion)<BigInt(previous.command.expectedVersion)+(previous.phase==='CANCELLED'?0n:1n)))throw Error('ASSISTANT_REQUEST_INVALID');
   const next:PendingAssistant={schemaVersion:1,customerId:a.customerId,caseId,phase:'PREPARED',command};save(a,next,previous);return send(a,next);
  });},
  query(caseId:string){return run(async()=>{const a=actor(),record=load(a,caseId);if(!record)return null;
   if(record.phase==='ENDING')return endOriginal(a,record);
   const result=decodeAssistantResult(await api.result(caseId,record.command.requestKey,a.epoch),caseId,record.command.requestKey);same(a);
   // 未找到、仍在执行或未知时绝不POST；仅核到终态，才同原体重放摘要并再读有效结果。
   return ['CANCELLED','CLOSED'].includes(result.state)?endOriginal(a,record):terminal(result)?send(a,record):result;
  });},
  end(caseId:string){return run(async()=>{const a=actor(),record=load(a,caseId);return record?endOriginal(a,record):null;});},
  adopt(caseId:string,value:AssistantResult){return run(async()=>{
   const a=actor(),r=decodeAssistantResult(value,caseId,value.requestKey),previous=load(a,caseId);
   if(r.state==='CANCELLED'||r.state==='NOT_FOUND')return previous;
   if(!r.queuedVersion||!r.factsVersion||BigInt(r.queuedVersion)<2n)throw Error('ASSISTANT_RESPONSE_INVALID');
   const command=encodeAssistantCommand({requestKey:r.requestKey,expectedVersion:(BigInt(r.queuedVersion)-1n).toString(),factsVersion:r.factsVersion,shareContextConfirmed:true});
   if(previous?.command.requestKey===command.requestKey){if(JSON.stringify(previous.command)!==JSON.stringify(command))throw Error('STORAGE_RECORD_CHANGED');return previous;}
   if(previous&&(['PREPARED','SENT','ENDING'].includes(previous.phase)||BigInt(previous.command.expectedVersion)>BigInt(command.expectedVersion)))throw Error('ASSISTANT_PENDING');
   const next:PendingAssistant={schemaVersion:1,customerId:a.customerId,caseId,phase:'SENT',command};save(a,next,previous);return next;
  });},
  retry(caseId:string){return run(async()=>{const a=actor(),record=load(a,caseId);return record?(['ENDING','CANCELLED','CLOSED'].includes(record.phase)?endOriginal(a,record):send(a,record)):null;});},
 };
}
