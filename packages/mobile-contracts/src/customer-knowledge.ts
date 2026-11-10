import {isPositiveLongId} from './ids.js';
import {decodeResponse,type HttpResponse} from './session.js';
import type {PublicRequest} from './customer-catalog.js';
export type KnowledgeScope={region:string;crop:string;growthStage:string};
export type KnowledgeSummary={id:string;logicalKey:string;revision:number;title:string;summary:string;scope:KnowledgeScope;effectiveUntil:string;reviewedAt:string};
export type KnowledgeArticle={article:KnowledgeSummary;body:string;appliesTo:string;exclusions:string;sourceText:string;effectiveFrom:string;contentHash:string};
const key=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(v);
export {key as isKnowledgeKey};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const bad=()=>Error('KNOWLEDGE_RESPONSE_INVALID');
function text(v:unknown,max:number,multi=false):string{if(typeof v!=='string'||!v.trim()||v.length>max||/[\u0000-\u001f\u007f]/.test(multi?v.replace(/[\r\n]/g,' '):v))throw bad();return v;}
function date(v:unknown):string{if(typeof v!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(v)||v<'0001-01-01')throw bad();const d=new Date(v+'T00:00:00Z');if(!Number.isFinite(d.getTime())||d.toISOString().slice(0,10)!==v)throw bad();return v;}
function timestamp(v:unknown):v is string{if(typeof v!=='string'||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/.test(v))return false;try{date(v.slice(0,10))}catch{return false}return Number(v.slice(11,13))<24&&Number(v.slice(14,16))<60&&(v.length===16||Number(v.slice(17,19))<60)}
export function knowledgeScope(v:unknown):KnowledgeScope{if(!object(v))throw bad();return{region:text(v.region,100),crop:text(v.crop,50),growthStage:text(v.growthStage,100)}}
function sameScope(a:KnowledgeScope,b:KnowledgeScope){return a.region===b.region&&a.crop===b.crop&&a.growthStage===b.growthStage;}
export function decodeKnowledgeScopes(value:unknown):KnowledgeScope[]{if(!Array.isArray(value)||value.length>200)throw bad();const rows=value.map(knowledgeScope);if(new Set(rows.map(v=>JSON.stringify(v))).size!==rows.length)throw bad();return rows;}
function summary(v:unknown,scope:KnowledgeScope):KnowledgeSummary{if(!object(v)||!isPositiveLongId(v.id)||!key(v.logicalKey)||typeof v.revision!=='number'||!Number.isInteger(v.revision)||v.revision<=0||v.revision>2147483647||!timestamp(v.reviewedAt))throw bad();const actual=knowledgeScope(v.scope);if(!sameScope(actual,scope))throw bad();return{id:v.id,logicalKey:v.logicalKey,revision:v.revision,title:text(v.title,200),summary:text(v.summary,500,true),scope:actual,effectiveUntil:date(v.effectiveUntil),reviewedAt:v.reviewedAt};}
export function decodeKnowledgePage(value:unknown,expectedScope:KnowledgeScope):{rows:KnowledgeSummary[];nextAfter:string|null}{const scope=knowledgeScope(expectedScope);if(!object(value)||!Array.isArray(value.rows)||value.rows.length>20)throw bad();const rows=value.rows.map(v=>summary(v,scope));for(let n=1;n<rows.length;n++){const a=rows[n-1]!.id,b=rows[n]!.id;if(a.length>b.length||a.length===b.length&&a>=b)throw bad();}const next=value.nextAfter;if(next!==null&&(!isPositiveLongId(next)||next!==rows[rows.length-1]?.id))throw bad();return{rows,nextAfter:next as string|null};}
export function decodeKnowledgeDetail(value:unknown,expectedKey:string,expectedRevision:number,expectedScope:KnowledgeScope):KnowledgeArticle{if(!object(value)||typeof value.contentHash!=='string'||! /^[0-9a-f]{64}$/.test(value.contentHash))throw bad();const article=summary(value.article,knowledgeScope(expectedScope)),from=date(value.effectiveFrom);if(article.logicalKey!==expectedKey||article.revision!==expectedRevision||from>article.effectiveUntil)throw bad();return{article,body:text(value.body,20000,true),appliesTo:text(value.appliesTo,2000,true),exclusions:text(value.exclusions,2000,true),sourceText:text(value.sourceText,2000,true),effectiveFrom:from,contentHash:value.contentHash};}
export function createPublicKnowledge(send:(request:PublicRequest)=>Promise<HttpResponse>){
 async function read<T>(request:PublicRequest,decode:(v:unknown)=>T):Promise<T>{let response:HttpResponse;try{response=await send(request)}catch{throw Error('RESULT_UNKNOWN')}return decode(decodeResponse('raw',response,(v):v is unknown=>v!==null&&v!==undefined));}
 function scopeQuery(value:KnowledgeScope){try{return knowledgeScope(value)}catch{throw Error('KNOWLEDGE_REQUEST_INVALID')}}
 return{
  scopes(){return read({path:'/api/public/agronomy/knowledge/scopes',query:{}},decodeKnowledgeScopes)},
  page(value:KnowledgeScope,keyword?:string,after?:string){return Promise.resolve().then(()=>{const scope=scopeQuery(value);if(after!==undefined&&!isPositiveLongId(after)||keyword!==undefined&&(keyword.length>100||/[\u0000-\u001f\u007f]/.test(keyword)))throw Error('KNOWLEDGE_REQUEST_INVALID');return read({path:'/api/public/agronomy/knowledge',query:{...scope,...(keyword?.trim()?{keyword:keyword.trim()}:{}),...(after?{after}:{})}},v=>decodeKnowledgePage(v,scope))})},
  detail(logicalKey:string,revision:number,value:KnowledgeScope){return Promise.resolve().then(()=>{const scope=scopeQuery(value);if(!key(logicalKey)||!Number.isInteger(revision)||revision<=0||revision>2147483647)throw Error('KNOWLEDGE_REQUEST_INVALID');return read({path:'/api/public/agronomy/knowledge/'+logicalKey,query:{...scope,expectedRevision:String(revision)}},v=>decodeKnowledgeDetail(v,logicalKey,revision,scope))})},
 };
}
