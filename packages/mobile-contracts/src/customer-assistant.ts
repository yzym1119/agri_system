import { isPositiveLongId } from './ids.js';
import { isCaseRequestKey } from './customer-cases.js';
import type { createCustomerAuth } from './customer-session.js';
type Auth = Pick<ReturnType<typeof createCustomerAuth>, 'current' | 'requestCustomer'>;
export type AssistantCommand = { requestKey: string; expectedVersion: string; factsVersion: string; shareContextConfirmed: true };
export type AssistantAvailability = { storeId: string; enabled: boolean; reason: 'READY' | 'DISABLED' | 'STORE_UNAVAILABLE' };
export type AssistantCapability = { enabled: boolean; reason: 'READY' | 'DISABLED' | 'STORE_DISABLED' | 'PENDING' | 'QUOTA_EXHAUSTED'; version: string; factsVersion: string };
export type AssistantReceipt = { requestKey: string; caseId: string; state: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'UNKNOWN'; queuedVersion: string; factsVersion: string; outcomeKind: string; handoffStatus: '' | 'WAITING_REVIEW' };
export type AssistantState = 'CANCELLED' | 'CLOSED' | 'NOT_FOUND' | 'QUEUED' | 'RUNNING' | 'UNKNOWN' | 'NOT_DISPATCHED' | 'STALE' | 'UNAVAILABLE' | 'CLARIFY' | 'HUMAN' | 'REFERENCE';
export type AssistantResult = { requestKey: string; caseId: string; state: AssistantState; queuedVersion: string | null; factsVersion: string | null; currentFactsVersion: string; handoffStatus: '' | 'WAITING_REVIEW'; missingFacts: string[]; citations: { title: string; url: string; publishedOn: string; quote: string }[] };
function bad(): never { throw Error('ASSISTANT_RESPONSE_INVALID'); }
function object(v: unknown): Record<string, unknown> { return v && typeof v === 'object' && !Array.isArray(v) ? v as Record<string, unknown> : bad(); }
function id(v: unknown): string { return isPositiveLongId(v) ? v : bad(); }
function key(v: unknown): string { return isCaseRequestKey(v) ? v : bad(); }
function plain(v: unknown, max: number): string { return typeof v === 'string' && !!v.trim() && [...v].length <= max && !/[<>\u0000-\u001f\u007f\ufffd]/.test(v) ? v : bad(); }
function versions(v: unknown, f: unknown) { const version=id(v), factsVersion=id(f); if(BigInt(factsVersion)>BigInt(version))bad();return{version,factsVersion}; }
export function encodeAssistantCommand(v: AssistantCommand): AssistantCommand {
  const r=object(v), version=versions(r.expectedVersion,r.factsVersion);
  if(r.shareContextConfirmed!==true||BigInt(version.version)===9223372036854775807n)throw Error('ASSISTANT_REQUEST_INVALID');
  return{requestKey:key(r.requestKey),expectedVersion:version.version,factsVersion:version.factsVersion,shareContextConfirmed:true};
}
export function decodeAssistantAvailability(v: unknown, storeId: string): AssistantAvailability {
  const r=object(v);
  if(r.storeId!==id(storeId)||typeof r.enabled!=='boolean'||typeof r.reason!=='string'||!['READY','DISABLED','STORE_UNAVAILABLE'].includes(r.reason)||r.enabled!==(r.reason==='READY'))bad();
  return{storeId,enabled:r.enabled,reason:r.reason as AssistantAvailability['reason']};
}
export function decodeAssistantCapability(v: unknown): AssistantCapability {
  const r=object(v), value=versions(r.version,r.factsVersion);
  if(typeof r.enabled!=='boolean'||typeof r.reason!=='string'||!['READY','DISABLED','STORE_DISABLED','PENDING','QUOTA_EXHAUSTED'].includes(String(r.reason))||r.enabled!==(r.reason==='READY'))bad();
  return{enabled:r.enabled,reason:r.reason as AssistantCapability['reason'],...value};
}
export function decodeAssistantReceipt(v: unknown, caseId: string, command: AssistantCommand): AssistantReceipt {
  const r=object(v), c=encodeAssistantCommand(command), value=versions(r.queuedVersion,r.factsVersion);
  if(r.requestKey!==c.requestKey||r.caseId!==id(caseId)||BigInt(value.version)!==BigInt(c.expectedVersion)+1n||value.factsVersion!==c.factsVersion)bad();
  const allowed:Record<string,string[]>={QUEUED:[''],RUNNING:[''],COMPLETED:['STAGED','STALE'],FAILED:['NOT_DISPATCHED'],UNKNOWN:['UNKNOWN']};
  if(typeof r.state!=='string'||typeof r.outcomeKind!=='string'||typeof r.handoffStatus!=='string'||!Object.prototype.hasOwnProperty.call(allowed,r.state)||!allowed[r.state]!.includes(String(r.outcomeKind))||!['','WAITING_REVIEW'].includes(String(r.handoffStatus))||r.handoffStatus==='WAITING_REVIEW'&&(r.state!=='COMPLETED'||r.outcomeKind!=='STAGED'))bad();
  return{requestKey:c.requestKey,caseId,state:r.state as AssistantReceipt['state'],queuedVersion:value.version,factsVersion:value.factsVersion,outcomeKind:r.outcomeKind as string,handoffStatus:r.handoffStatus as AssistantReceipt['handoffStatus']};
}
export function decodeAssistantResult(v: unknown, caseId: string, requestKey: string): AssistantResult {
  const r=object(v);if(r.requestKey!==key(requestKey)||r.caseId!==id(caseId)||typeof r.state!=='string'||!['CANCELLED','CLOSED','NOT_FOUND','QUEUED','RUNNING','UNKNOWN','NOT_DISPATCHED','STALE','UNAVAILABLE','CLARIFY','HUMAN','REFERENCE'].includes(String(r.state)))bad();
  const currentFactsVersion=id(r.currentFactsVersion), absent=r.state==='NOT_FOUND'||r.state==='CANCELLED';
  if(absent&&(r.queuedVersion!==null||r.factsVersion!==null))bad();
  const value=absent?null:versions(r.queuedVersion,r.factsVersion);
  if(value&&BigInt(value.factsVersion)>BigInt(currentFactsVersion))bad();
  if(!Array.isArray(r.missingFacts)||r.missingFacts.length>3||new Set(r.missingFacts).size!==r.missingFacts.length||r.missingFacts.some(f=>!['region','crop','growthStage'].includes(f))||r.state!=='CLARIFY'&&r.missingFacts.length)bad();
  if(!Array.isArray(r.citations)||r.citations.length>3||r.state==='REFERENCE'&&r.citations.length===0||r.state!=='REFERENCE'&&r.citations.length)bad();
  if(r.handoffStatus!==(r.state==='HUMAN'?'WAITING_REVIEW':''))bad();
  const citations=r.citations.map(c=>{const s=object(c), title=plain(s.title,300),quote=plain(s.quote,160),url=plain(s.url,2048),publishedOn=plain(s.publishedOn,10);
    if([...quote].length<8||!/^https:\/\/(?:www\.)?moa\.gov\.cn\/[A-Za-z0-9/_\-.]+$/.test(url)||url.includes('..')||!/^\d{4}-\d{2}-\d{2}$/.test(publishedOn))bad();
    const date=new Date(publishedOn+'T00:00:00Z');if(!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==publishedOn)bad();
    return{title,url,publishedOn,quote};});
  return{requestKey,caseId,state:r.state as AssistantState,queuedVersion:value?.version??null,factsVersion:value?.factsVersion??null,currentFactsVersion,handoffStatus:r.handoffStatus as AssistantResult['handoffStatus'],missingFacts:r.missingFacts as string[],citations};
}
export type AssistantEndResult={requestKey:string;caseId:string;state:'CANCELLED'|'CLOSED'|'NOT_DISPATCHED'|'COMPLETED';expectedVersion:string;factsVersion:string};
export function decodeAssistantEnd(v:unknown,caseId:string,command:AssistantCommand):AssistantEndResult {
  const r=object(v),c=encodeAssistantCommand(command);
  if(r.requestKey!==c.requestKey||r.caseId!==id(caseId)||r.expectedVersion!==c.expectedVersion||r.factsVersion!==c.factsVersion||typeof r.state!=='string'||!['CANCELLED','CLOSED','NOT_DISPATCHED','COMPLETED'].includes(r.state))bad();
  return{requestKey:c.requestKey,caseId,state:r.state as AssistantEndResult['state'],expectedVersion:c.expectedVersion,factsVersion:c.factsVersion};
}
export function decodeAssistantLatest(v:unknown,caseId:string):{result:AssistantResult|null} {
  const r=object(v);if(r.result===null)return{result:null};const raw=object(r.result);return{result:decodeAssistantResult(raw,caseId,key(raw.requestKey))};
}
export function createCustomerAssistant(auth: Auth) {
  function epoch(expected?:number) {const a=auth.current();if(expected!==undefined&&a.epoch!==expected)throw Error('STALE_SESSION');if(a.kind!=='CUSTOMER')throw Error('CUSTOMER_LOGIN_REQUIRED');return a.epoch;}
  const base=(caseId:string)=>'/api/client/agronomy/cases/'+id(caseId)+'/assistant';
  return{
    async availability(storeId:string,expectedEpoch?:number){const t=epoch(expectedEpoch),store=id(storeId);return decodeAssistantAvailability(await auth.requestCustomer({url:'/api/client/agronomy/assistant/availability?storeId='+store,method:'GET'},t),store);},
    async latest(caseId:string,expectedEpoch?:number){const t=epoch(expectedEpoch);return decodeAssistantLatest(await auth.requestCustomer({url:base(caseId)+'/latest',method:'GET'},t),caseId);},
    async end(caseId:string,command:AssistantCommand,expectedEpoch?:number){const t=epoch(expectedEpoch),data=encodeAssistantCommand(command);return decodeAssistantEnd(await auth.requestCustomer({url:base(caseId)+'/end',method:'POST',data},t),caseId,data);},
    async capability(caseId:string,expectedEpoch?:number){const t=epoch(expectedEpoch);return decodeAssistantCapability(await auth.requestCustomer({url:base(caseId)+'/capability',method:'GET'},t));},
    async submit(caseId:string,command:AssistantCommand,expectedEpoch?:number){const t=epoch(expectedEpoch),data=encodeAssistantCommand(command);return decodeAssistantReceipt(await auth.requestCustomer({url:base(caseId)+'/requests',method:'POST',data},t),caseId,data);},
    async result(caseId:string,requestKey:string,expectedEpoch?:number){const t=epoch(expectedEpoch);return decodeAssistantResult(await auth.requestCustomer({url:base(caseId)+'/requests/'+key(requestKey),method:'GET'},t),caseId,requestKey);},
  };
}
